package com.adblocker.ytmusic.service

import com.adblocker.ytmusic.data.dns.DnsMessage
import com.adblocker.ytmusic.data.dns.DnsQuestion
import com.adblocker.ytmusic.data.dns.DnsTestFixtures
import com.adblocker.ytmusic.data.net.Ipv4UdpCodec
import com.adblocker.ytmusic.data.net.Ipv4UdpDatagram
import com.adblocker.ytmusic.domain.model.BlocklistRules
import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prueba de integracion del camino completo: TUN -> IPv4/UDP -> DNS -> decision. */
class DnsFilterTunnelTest {

    private val clientAddress = Ipv4UdpCodec.addressToInt("10.215.173.1")
    private val dnsAddress = Ipv4UdpCodec.addressToInt("10.215.173.2")

    private class FakeRepository(private val rules: BlocklistRules) : BlocklistRepository {
        override suspend fun loadRules() = rules
        override suspend fun sources(): List<BlocklistSource> = emptyList()
    }

    private fun queryPacket(domain: String, type: Int = DnsQuestion.TYPE_A): ByteArray =
        Ipv4UdpCodec.build(
            Ipv4UdpDatagram(
                sourceAddress = clientAddress,
                destinationAddress = dnsAddress,
                sourcePort = 51234,
                destinationPort = 53,
                payload = DnsTestFixtures.query(domain, type),
            ),
        )

    private suspend fun useCase(blocked: Set<String>): EvaluateDomainUseCase =
        EvaluateDomainUseCase(FakeRepository(BlocklistRules(blocked)))
            .also { it.prepare() }

    @Test
    fun `una consulta bloqueada se responde localmente`() = runTest {
        val output = ByteArrayOutputStream()
        var lastStats = FilterStats()

        DnsFilterTunnel(
            tunInput = ByteArrayInputStream(queryPacket("googleads.g.doubleclick.net")),
            tunOutput = output,
            evaluateDomain = useCase(setOf("doubleclick.net")),
            upstreamDns = InetAddress.getByName("127.0.0.1"),
            protectSocket = { true },
            onStats = { lastStats = it },
        ).run()

        val reply = requireNotNull(Ipv4UdpCodec.parse(output.toByteArray()))
        assertEquals(dnsAddress, reply.sourceAddress)
        assertEquals(clientAddress, reply.destinationAddress)
        assertEquals(53, reply.sourcePort)
        assertEquals(51234, reply.destinationPort)

        val question = DnsQuestion(
            name = "googleads.g.doubleclick.net",
            type = DnsQuestion.TYPE_A,
            dnsClass = 1,
            endOffset = reply.payload.size - 16,
        )
        assertEquals(1, DnsTestFixtures.readUShort(reply.payload, 6)) // ANCOUNT
        val rdata = reply.payload.copyOfRange(question.endOffset + 12, question.endOffset + 16)
        assertEquals(listOf<Byte>(0, 0, 0, 0), rdata.toList())

        assertEquals(FilterStats(queriesInspected = 1, queriesBlocked = 1), lastStats)
    }

    @Test
    fun `una consulta permitida no se responde localmente`() = runTest {
        val output = ByteArrayOutputStream()
        var lastStats = FilterStats()

        DnsFilterTunnel(
            tunInput = ByteArrayInputStream(queryPacket("music.youtube.com")),
            tunOutput = output,
            evaluateDomain = useCase(setOf("doubleclick.net")),
            upstreamDns = InetAddress.getByName("127.0.0.1"),
            // Evita salir a la red durante la prueba: el reenvio se aborta.
            protectSocket = { false },
            onStats = { lastStats = it },
        ).run()

        assertTrue(output.toByteArray().isEmpty())
        assertEquals(FilterStats(queriesInspected = 1, queriesBlocked = 0), lastStats)
    }

    @Test
    fun `el trafico que no es DNS se ignora`() = runTest {
        val output = ByteArrayOutputStream()
        val packet = Ipv4UdpCodec.build(
            Ipv4UdpDatagram(clientAddress, dnsAddress, 51234, 443, ByteArray(8)),
        )

        DnsFilterTunnel(
            tunInput = ByteArrayInputStream(packet),
            tunOutput = output,
            evaluateDomain = useCase(setOf("doubleclick.net")),
            upstreamDns = InetAddress.getByName("127.0.0.1"),
            protectSocket = { true },
        ).run()

        assertTrue(output.toByteArray().isEmpty())
    }

    /** El TUN entrega un paquete por lectura; un `ByteArrayInputStream` los uniria. */
    private class PacketInputStream(private vararg val packets: ByteArray) : InputStream() {
        private var next = 0

        override fun read(destination: ByteArray, offset: Int, length: Int): Int {
            if (next >= packets.size) return -1
            val packet = packets[next++]
            packet.copyInto(destination, offset)
            return packet.size
        }

        override fun read(): Int = throw UnsupportedOperationException()
    }

    @Test
    fun `los contadores finales se publican aunque lleguen en rafaga`() = runTest {
        var lastStats = FilterStats()

        DnsFilterTunnel(
            tunInput = PacketInputStream(
                queryPacket("googleads.g.doubleclick.net"),
                queryPacket("pixel.doubleclick.net"),
            ),
            tunOutput = ByteArrayOutputStream(),
            evaluateDomain = useCase(setOf("doubleclick.net")),
            upstreamDns = InetAddress.getByName("127.0.0.1"),
            protectSocket = { true },
            onStats = { lastStats = it },
        ).run()

        // La segunda consulta cae dentro de la ventana de agrupacion: el cierre debe volcarla.
        assertEquals(FilterStats(queriesInspected = 2, queriesBlocked = 2), lastStats)
    }

    @Test
    fun `respuesta de bloqueo coherente con el parser DNS`() {
        val query = DnsTestFixtures.query("ads.example.com")
        val question = requireNotNull(DnsMessage.parseQuestion(query))
        val response = DnsMessage.buildBlockedResponse(query, question)
        assertEquals(query.size + 16, response.size)
    }
}
