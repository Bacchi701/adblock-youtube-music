package com.adblocker.ytmusic.ui

import com.adblocker.ytmusic.domain.model.FilterStats
import kotlinx.coroutines.flow.StateFlow

/**
 * Puerto que aisla la UI del `VpnService`. Permite probar el [FilterViewModel] con JUnit
 * sin arrancar un servicio de Android.
 */
interface FilterController {

    val isRunning: StateFlow<Boolean>

    val stats: StateFlow<FilterStats>

    /** Solo debe llamarse con el consentimiento de VPN ya concedido. */
    fun start()

    fun stop()
}
