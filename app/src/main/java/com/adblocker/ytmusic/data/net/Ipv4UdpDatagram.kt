package com.adblocker.ytmusic.data.net

/** Datagrama UDP sobre IPv4 ya desempaquetado del TUN. Las direcciones van en orden de red. */
class Ipv4UdpDatagram(
    val sourceAddress: Int,
    val destinationAddress: Int,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
) {
    /** Datagrama de vuelta: intercambia origen y destino y sustituye la carga util. */
    fun reply(newPayload: ByteArray) = Ipv4UdpDatagram(
        sourceAddress = destinationAddress,
        destinationAddress = sourceAddress,
        sourcePort = destinationPort,
        destinationPort = sourcePort,
        payload = newPayload,
    )
}
