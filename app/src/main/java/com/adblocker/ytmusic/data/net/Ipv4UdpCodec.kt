package com.adblocker.ytmusic.data.net

/**
 * Empaqueta y desempaqueta datagramas UDP/IPv4 leidos del descriptor TUN.
 * Logica pura para poder probar cabeceras y checksums con JUnit.
 */
object Ipv4UdpCodec {

    const val IPV4_HEADER_SIZE = 20
    const val UDP_HEADER_SIZE = 8
    private const val PROTOCOL_UDP = 17
    private const val DEFAULT_TTL = 64

    /** Devuelve `null` si el paquete no es IPv4/UDP intacto (fragmentos incluidos). */
    fun parse(buffer: ByteArray, length: Int = buffer.size): Ipv4UdpDatagram? {
        if (length < IPV4_HEADER_SIZE) return null
        val versionAndIhl = buffer[0].toInt() and 0xFF
        if (versionAndIhl ushr 4 != 4) return null

        val headerLength = (versionAndIhl and 0x0F) * 4
        if (headerLength < IPV4_HEADER_SIZE || length < headerLength) return null
        if (buffer[9].toInt() and 0xFF != PROTOCOL_UDP) return null

        val fragmentInfo = buffer.readUShort(6)
        if (fragmentInfo and 0x1FFF != 0) return null // fragmento no inicial

        val totalLength = buffer.readUShort(2).coerceAtMost(length)
        if (totalLength < headerLength + UDP_HEADER_SIZE) return null

        val udpLength = buffer.readUShort(headerLength + 4)
        val payloadLength = (udpLength - UDP_HEADER_SIZE)
            .coerceIn(0, totalLength - headerLength - UDP_HEADER_SIZE)
        val payloadStart = headerLength + UDP_HEADER_SIZE

        return Ipv4UdpDatagram(
            sourceAddress = buffer.readInt(12),
            destinationAddress = buffer.readInt(16),
            sourcePort = buffer.readUShort(headerLength),
            destinationPort = buffer.readUShort(headerLength + 2),
            payload = buffer.copyOfRange(payloadStart, payloadStart + payloadLength),
        )
    }

    fun build(datagram: Ipv4UdpDatagram): ByteArray {
        val udpLength = UDP_HEADER_SIZE + datagram.payload.size
        val totalLength = IPV4_HEADER_SIZE + udpLength
        val packet = ByteArray(totalLength)

        packet[0] = 0x45 // IPv4, IHL = 5
        packet[1] = 0
        packet.writeUShort(2, totalLength)
        packet.writeUShort(4, 0) // identification
        packet.writeUShort(6, 0x4000) // don't fragment
        packet[8] = DEFAULT_TTL.toByte()
        packet[9] = PROTOCOL_UDP.toByte()
        packet.writeInt(12, datagram.sourceAddress)
        packet.writeInt(16, datagram.destinationAddress)
        packet.writeUShort(10, checksum(packet, 0, IPV4_HEADER_SIZE, 0))

        packet.writeUShort(IPV4_HEADER_SIZE, datagram.sourcePort)
        packet.writeUShort(IPV4_HEADER_SIZE + 2, datagram.destinationPort)
        packet.writeUShort(IPV4_HEADER_SIZE + 4, udpLength)
        datagram.payload.copyInto(packet, IPV4_HEADER_SIZE + UDP_HEADER_SIZE)

        // Checksum UDP con pseudo-cabecera IPv4.
        val pseudoSum = (datagram.sourceAddress ushr 16 and 0xFFFF) +
            (datagram.sourceAddress and 0xFFFF) +
            (datagram.destinationAddress ushr 16 and 0xFFFF) +
            (datagram.destinationAddress and 0xFFFF) +
            PROTOCOL_UDP + udpLength
        val udpChecksum = checksum(packet, IPV4_HEADER_SIZE, udpLength, pseudoSum)
        packet.writeUShort(IPV4_HEADER_SIZE + 6, if (udpChecksum == 0) 0xFFFF else udpChecksum)
        return packet
    }

    /** Suma en complemento a uno de 16 bits sobre `[offset, offset + length)`. */
    fun checksum(data: ByteArray, offset: Int, length: Int, initialSum: Int): Int {
        var sum = initialSum
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }

    fun addressToInt(dotted: String): Int = dotted.split('.')
        .fold(0) { acc, octet -> (acc shl 8) or (octet.toInt() and 0xFF) }

    fun intToAddress(address: Int): String =
        "${address ushr 24 and 0xFF}.${address ushr 16 and 0xFF}.${address ushr 8 and 0xFF}.${address and 0xFF}"

    private fun ByteArray.readUShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.readInt(offset: Int): Int =
        (readUShort(offset) shl 16) or readUShort(offset + 2)

    private fun ByteArray.writeUShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.writeInt(offset: Int, value: Int) {
        writeUShort(offset, value ushr 16 and 0xFFFF)
        writeUShort(offset + 2, value and 0xFFFF)
    }
}
