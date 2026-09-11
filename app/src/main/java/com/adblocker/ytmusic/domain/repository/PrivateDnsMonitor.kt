package com.adblocker.ytmusic.domain.repository

import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import kotlinx.coroutines.flow.StateFlow

/** Observa el modo de DNS privado del sistema mientras la app esta en marcha. */
interface PrivateDnsMonitor {

    val status: StateFlow<PrivateDnsStatus>

    /** Recalcula el estado de inmediato (por ejemplo al volver de los ajustes del sistema). */
    fun refresh()
}
