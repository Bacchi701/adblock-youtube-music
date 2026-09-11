package com.adblocker.ytmusic.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adblocker.ytmusic.data.dns.DnsMessage
import com.adblocker.ytmusic.data.net.Ipv4UdpCodec
import com.adblocker.ytmusic.data.net.Ipv4UdpDatagram
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Camino completo de una consulta bloqueada tal y como lo ejecuta el tunel:
 * desempaquetar IPv4/UDP, leer la pregunta DNS y construir la respuesta local.
 */
@RunWith(AndroidJUnit4::class)
class DnsPacketBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val clientAddress = Ipv4UdpCodec.addressToInt("10.215.173.1")
    private val dnsAddress = Ipv4UdpCodec.addressToInt("10.215.173.2")

    private val queryPacket = Ipv4UdpCodec.build(
        Ipv4UdpDatagram(
            sourceAddress = clientAddress,
            destinationAddress = dnsAddress,
            sourcePort = 51234,
            destinationPort = 53,
            payload = dnsQuery("googleads.g.doubleclick.net"),
        ),
    )

    @Test
    fun blockedQueryRoundTrip() = benchmarkRule.measureRepeated {
        val datagram = Ipv4UdpCodec.parse(queryPacket, queryPacket.size)!!
        val question = DnsMessage.parseQuestion(datagram.payload)!!
        val response = DnsMessage.buildBlockedResponse(datagram.payload, question)
        Ipv4UdpCodec.build(datagram.reply(response))
    }

    @Test
    fun parseQuestionOnly() = benchmarkRule.measureRepeated {
        val datagram = Ipv4UdpCodec.parse(queryPacket, queryPacket.size)!!
        DnsMessage.parseQuestion(datagram.payload)
    }

    private fun dnsQuery(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun writeShort(value: Int) {
            out.write(value ushr 8 and 0xFF)
            out.write(value and 0xFF)
        }
        writeShort(0x1234)
        writeShort(0x0100)
        writeShort(1)
        writeShort(0)
        writeShort(0)
        writeShort(0)
        for (label in name.split('.')) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        writeShort(1)
        writeShort(1)
        return out.toByteArray()
    }
}
