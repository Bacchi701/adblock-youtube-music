package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.domain.filter.DomainBlocklist
import com.adblocker.ytmusic.domain.model.FilterDecision
import com.adblocker.ytmusic.domain.repository.BlocklistRepository

/**
 * Decide si una consulta DNS debe bloquearse. Mantiene el motor cargado en memoria
 * para que la ruta caliente del VpnService sea O(numero de etiquetas del dominio).
 */
class EvaluateDomainUseCase(
    private val repository: BlocklistRepository,
) {

    @Volatile
    private var engine: DomainBlocklist? = null

    suspend fun prepare(): DomainBlocklist {
        engine?.let { return it }
        val rules = repository.loadRules()
        val loaded = DomainBlocklist(rules.blocked, rules.allowed)
        engine = loaded
        return loaded
    }

    /** Debe llamarse tras [prepare]; si el motor no esta listo, se permite el trafico. */
    operator fun invoke(domain: String): FilterDecision =
        engine?.evaluate(domain) ?: FilterDecision.Allow
}
