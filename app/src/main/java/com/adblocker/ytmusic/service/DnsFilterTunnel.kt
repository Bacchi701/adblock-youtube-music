package com.adblocker.ytmusic.service

import com.adblocker.ytmusic.data.dns.DnsMessage
import com.adblocker.ytmusic.data.net.Ipv4UdpCodec
import com.adblocker.ytmusic.data.net.Ipv4UdpDatagram
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Bucle de filtrado: lee paquetes del TUN, resuelve localmente lo bloqueado y
 * reenvia el resto al DNS del sistema. Todo el procesamiento ocurre en el dispositivo.
 *
 * Notas de rendimiento (es la ruta mas caliente de la app y corre con la pantalla apagada):
 *  - El bucle entero vive en `Dispatchers.IO`, no un `withContext` por paquete.
 *  - Una consulta bloqueada se resuelve en el propio bucle: no cuesta ninguna corrutina.
 *  - Los contadores son atomicos y se publican como mucho cada [STATS_INTERVAL_MS].
 *  - El buffer de recepcion se reutiliza por hilo de E/S.
 */
class DnsFilterTunnel(
    private val tunInput: InputStream,
    private val tunOutput: OutputStream,
    private val evaluateDomain: EvaluateDomainUseCase,
    private val upstreamDns: InetAddress,
    private val protectSocket: (DatagramSocket) -> Boolean,
    private val onStats: (FilterStats) -> Unit = {},
) {

    private val writeMutex = Mutex()

    private val inspected = AtomicLong()
    private val blocked = AtomicLong()

    @Volatile
    private var lastStatsAt = 0L

    /** Bloquea hasta que el TUN se cierra o se cancela la corrutina. */
    suspend fun run() = coroutineScope {
        try {
            withContext(Dispatchers.IO) { readLoop(this@coroutineScope) }
        } finally {
            // Publica el recuento definitivo aunque la ultima consulta cayera dentro del intervalo.
            publishStats(force = true)
        }
    }

    private suspend fun readLoop(scope: CoroutineScope) {
        val buffer = ByteArray(MAX_PACKET_SIZE)
        while (scope.isActive) {
            val read = tunInput.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            val datagram = Ipv4UdpCodec.parse(buffer, read) ?: continue
            if (datagram.destinationPort != DNS_PORT) continue
            handleQuery(datagram, scope)
        }
    }

    private suspend fun handleQuery(datagram: Ipv4UdpDatagram, scope: CoroutineScope) {
        val question = DnsMessage.parseQuestion(datagram.payload)
        if (question == null) {
            scope.launch { forward(datagram) }
            return
        }

        val decision = evaluateDomain(question.name)
        inspected.incrementAndGet()
        if (decision.isBlocked) blocked.incrementAndGet()
        publishStats()

        if (decision.isBlocked) {
            // Trabajo puramente local: responder aqui evita despachar una corrutina por anuncio.
            writePacket(datagram.reply(DnsMessage.buildBlockedResponse(datagram.payload, question)))
        } else {
            scope.launch { forward(datagram) }
        }
    }

    /** Reenvia la consulta al resolutor real usando un socket protegido (fuera del tunel). */
    private suspend fun forward(datagram: Ipv4UdpDatagram) {
        val response = withContext(Dispatchers.IO) {
            runCatching {
                DatagramSocket().use { socket ->
                    if (!protectSocket(socket)) return@use null
                    socket.soTimeout = UPSTREAM_TIMEOUT_MS
                    socket.send(
                        DatagramPacket(datagram.payload, datagram.payload.size, upstreamDns, DNS_PORT),
                    )
                    val buffer = receiveBuffer.get()
                    val incoming = DatagramPacket(buffer, buffer.size)
                    socket.receive(incoming)
                    buffer.copyOf(incoming.length)
                }
            }.getOrNull()
        }
        if (response != null) writePacket(datagram.reply(response))
    }

    private suspend fun writePacket(reply: Ipv4UdpDatagram) {
        val packet = Ipv4UdpCodec.build(reply)
        writeMutex.withLock {
            withContext(Dispatchers.IO) {
                tunOutput.write(packet)
                tunOutput.flush()
            }
        }
    }

    /**
     * Publica el recuento sin crear un [FilterStats] por consulta: una rafaga de DNS no debe
     * traducirse en una recomposicion de Compose por paquete.
     */
    private fun publishStats(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastStatsAt < STATS_INTERVAL_MS) return
        lastStatsAt = now
        onStats(FilterStats(queriesInspected = inspected.get(), queriesBlocked = blocked.get()))
    }

    private companion object {
        const val DNS_PORT = 53
        const val MAX_PACKET_SIZE = 4096
        const val UPSTREAM_TIMEOUT_MS = 5_000
        const val STATS_INTERVAL_MS = 250L

        /** Un buffer por hilo del pool de E/S en lugar de 4 KB por consulta reenviada. */
        val receiveBuffer: ThreadLocal<ByteArray> =
            ThreadLocal.withInitial { ByteArray(MAX_PACKET_SIZE) }
    }
}
