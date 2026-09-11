package com.adblocker.ytmusic.domain.network

/**
 * Instantanea de la configuracion DNS de una red, desacoplada de `LinkProperties`
 * para poder evaluarla con pruebas locales.
 *
 * @param privateDnsActive el sistema esta usando DoT en esa red.
 * @param privateDnsServerName hostname fijado por el usuario; no nulo solo en modo estricto.
 */
data class NetworkDnsInfo(
    val isVpn: Boolean = false,
    val privateDnsActive: Boolean = false,
    val privateDnsServerName: String? = null,
)
