package com.adblocker.ytmusic.data.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4UdpCodecTest {

    private val source = Ipv4UdpCodec.addressToInt("10.215.173.1")
    private val destination = Ipv4UdpCodec.addressToInt("10.215.173.2")

    @Test
    fun `ida y vuelta conserva direcciones puertos y carga util`() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val packet = Ipv4UdpCodec.build(
            Ipv4UdpDatagram(source, destination, 43210, 53, payload),
        )

        val parsed = requireNotNull(Ipv4UdpCodec.parse(packet))
        assertEquals(source, parsed.sourceAddress)
        assertEquals(destination, parsed.destinationAddress)
        assertEquals(43210, parsed.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertEquals(payload.toList(), parsed.payload.toList())
    }

    @Test
    fun `la cabecera IPv4 lleva checksum valido`() {
        val packet = Ipv4UdpCodec.build(
            Ipv4UdpDatagram(source, destination, 1234, 53, ByteArray(9)),
        )
        // Sumar la cabecera completa incluyendo su checksum debe dar 0.
        assertEquals(0, Ipv4UdpCodec.checksum(packet, 0, Ipv4UdpCodec.IPV4_HEADER_SIZE, 0))
        assertEquals(packet.size, ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF))
    }

    @Test
    fun `reply intercambia origen y destino`() {
        val original = Ipv4UdpDatagram(source, destination, 43210, 53, byteArrayOf(7))
        val reply = original.reply(byteArrayOf(9, 9))

        assertEquals(destination, reply.sourceAddress)
        assertEquals(source, reply.destinationAddress)
        assertEquals(53, reply.sourcePort)
        assertEquals(43210, reply.destinationPort)
        assertEquals(listOf<Byte>(9, 9), reply.payload.toList())
    }

    @Test
    fun `descarta lo que no es IPv4 UDP`() {
        val tcp = Ipv4UdpCodec.build(Ipv4UdpDatagram(source, destination, 1, 2, ByteArray(4)))
        tcp[9] = 6 // TCP
        assertNull(Ipv4UdpCodec.parse(tcp))

        val ipv6 = Ipv4UdpCodec.build(Ipv4UdpDatagram(source, destination, 1, 2, ByteArray(4)))
        ipv6[0] = 0x65 // version 6
        assertNull(Ipv4UdpCodec.parse(ipv6))

        assertNull(Ipv4UdpCodec.parse(ByteArray(10)))
    }

    @Test
    fun `conversion de direcciones ida y vuelta`() {
        assertEquals("192.168.1.254", Ipv4UdpCodec.intToAddress(Ipv4UdpCodec.addressToInt("192.168.1.254")))
        assertEquals("255.255.255.255", Ipv4UdpCodec.intToAddress(Ipv4UdpCodec.addressToInt("255.255.255.255")))
    }
}
