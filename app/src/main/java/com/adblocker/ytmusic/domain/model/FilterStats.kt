package com.adblocker.ytmusic.domain.model

/** Contadores en memoria del filtro. Nunca se persisten ni se envian a ningun servidor. */
data class FilterStats(
    val queriesInspected: Long = 0,
    val queriesBlocked: Long = 0,
) {
    val queriesAllowed: Long get() = queriesInspected - queriesBlocked
}
