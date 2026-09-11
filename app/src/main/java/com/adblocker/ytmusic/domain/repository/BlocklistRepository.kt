package com.adblocker.ytmusic.domain.repository

import com.adblocker.ytmusic.domain.model.BlocklistRules
import com.adblocker.ytmusic.domain.model.BlocklistSource

/** Acceso a las reglas de filtrado. La implementacion lee unicamente recursos locales. */
interface BlocklistRepository {

    /** Carga (o devuelve cacheadas) las reglas de bloqueo y excepcion. */
    suspend fun loadRules(): BlocklistRules

    /** Metadatos de las listas locales disponibles. */
    suspend fun sources(): List<BlocklistSource>
}
