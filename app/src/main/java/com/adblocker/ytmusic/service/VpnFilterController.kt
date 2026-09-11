package com.adblocker.ytmusic.service

import android.content.Context
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.ui.FilterController
import kotlinx.coroutines.flow.StateFlow

/** Implementacion real del puerto: habla con [AdBlockVpnService]. */
class VpnFilterController(private val context: Context) : FilterController {

    override val isRunning: StateFlow<Boolean> get() = AdBlockVpnService.isRunning

    override val stats: StateFlow<FilterStats> get() = AdBlockVpnService.stats

    override fun start() = AdBlockVpnService.start(context)

    override fun stop() = AdBlockVpnService.stop(context)
}
