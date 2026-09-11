package com.adblocker.ytmusic.domain.network

import com.adblocker.ytmusic.domain.model.PrivateDnsStatus

/**
 * Deduce el modo de DNS privado a partir de las redes del sistema.
 *
 * Se ignoran las redes VPN: la del propio tunel anuncia el DNS virtual y falsearia el
 * diagnostico. Lo que decide es la configuracion de la red fisica subyacente.
 */
object PrivateDnsEvaluator {

    fun evaluate(networks: List<NetworkDnsInfo>): PrivateDnsStatus {
        val physical = networks.filterNot(NetworkDnsInfo::isVpn)

        physical.firstNotNullOfOrNull { it.privateDnsServerName?.trim()?.ifEmpty { null } }
            ?.let { return PrivateDnsStatus.Strict(it) }

        return if (physical.any(NetworkDnsInfo::privateDnsActive)) {
            PrivateDnsStatus.Opportunistic
        } else {
            PrivateDnsStatus.Off
        }
    }
}
