package com.adblocker.ytmusic.ui

import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.model.PrivateDnsStatus

data class FilterUiState(
    val isRunning: Boolean = false,
    val isLoadingRules: Boolean = true,
    val dnsStats: FilterStats = FilterStats(),
    val playerStats: PlayerFilterStats = PlayerFilterStats(),
    val sources: List<BlocklistSource> = emptyList(),
    val totalRules: Int = 0,
    val privateDns: PrivateDnsStatus = PrivateDnsStatus.Off,
) {
    /** Porcentaje de consultas DNS bloqueadas, 0 si aun no hay trafico. */
    val blockedPercent: Int
        get() = if (dnsStats.queriesInspected == 0L) 0
        else ((dnsStats.queriesBlocked * 100) / dnsStats.queriesInspected).toInt()

    /** El reproductor integrado filtra aunque el VPN este parado: son capas independientes. */
    val hasPlayerActivity: Boolean
        get() = playerStats.responsesInspected > 0 || playerStats.requestsBlocked > 0

    /**
     * El DNS privado estricto saca las consultas del tunel: hay que avisar aunque el
     * servicio este corriendo, porque el filtro DNS no esta viendo nada.
     */
    val dnsFilterBypassed: Boolean
        get() = privateDns.bypassesFilter

    val isDnsFilterEffective: Boolean
        get() = isRunning && !dnsFilterBypassed
}
