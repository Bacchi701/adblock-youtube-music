package com.adblocker.ytmusic.domain.model

/** Contadores del filtro de reproductor. Solo en memoria, nunca se envian a ningun sitio. */
data class PlayerFilterStats(
    val responsesInspected: Long = 0,
    val adSectionsRemoved: Long = 0,
    val requestsBlocked: Long = 0,
)
