package com.adblocker.ytmusic.domain.model

/** Reglas ya normalizadas listas para alimentar el motor de coincidencia. */
data class BlocklistRules(
    val blocked: Set<String> = emptySet(),
    val allowed: Set<String> = emptySet(),
) {
    operator fun plus(other: BlocklistRules) = BlocklistRules(
        blocked = blocked + other.blocked,
        allowed = allowed + other.allowed,
    )
}
