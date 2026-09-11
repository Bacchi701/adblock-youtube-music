package com.adblocker.ytmusic.data.blocklist

import com.adblocker.ytmusic.domain.model.BlocklistRules
import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Implementacion del repositorio sobre assets locales, con cache en memoria. */
class AssetBlocklistRepository(
    private val dataSource: AssetBlocklistDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BlocklistRepository {

    private val mutex = Mutex()
    private var cachedRules: BlocklistRules? = null
    private var cachedSources: List<BlocklistSource>? = null

    override suspend fun loadRules(): BlocklistRules =
        cachedRules ?: mutex.withLock { cachedRules ?: load().first }

    override suspend fun sources(): List<BlocklistSource> =
        cachedSources ?: mutex.withLock { cachedSources ?: load().second }

    private suspend fun load(): Pair<BlocklistRules, List<BlocklistSource>> =
        withContext(ioDispatcher) {
            // Se acumula en un unico par de conjuntos: `BlocklistRules.plus` copiaba
            // todas las reglas leidas por cada fichero adicional.
            val blocked = LinkedHashSet<String>()
            val allowed = LinkedHashSet<String>()
            val sources = dataSource.listFiles().map { fileName ->
                val rules = dataSource.read(fileName)
                blocked += rules.blocked
                allowed += rules.allowed
                BlocklistSource(
                    id = fileName,
                    displayName = fileName.removeSuffix(".txt").replace('_', ' '),
                    ruleCount = rules.blocked.size,
                )
            }
            val merged = BlocklistRules(blocked, allowed)
            cachedRules = merged
            cachedSources = sources
            merged to sources
        }
}
