package com.adblocker.ytmusic.domain.model

/**
 * Origen de una lista de bloqueo. Solo se admiten origenes locales:
 * el proyecto no descarga listas remotas (Zero Telemetry).
 */
data class BlocklistSource(
    val id: String,
    val displayName: String,
    val ruleCount: Int,
)
