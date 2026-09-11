package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.player.PlayerFilterResult
import com.adblocker.ytmusic.domain.player.PlayerRequestMatcher
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Punto unico de decision para el trafico del reproductor web:
 * que peticiones se cortan y como se sanea el `playerResponse`.
 */
class FilterPlayerResponseUseCase(
    private val responseFilter: PlayerResponseFilter = PlayerResponseFilter(),
) {

    private val _stats = MutableStateFlow(PlayerFilterStats())
    val stats: StateFlow<PlayerFilterStats> = _stats.asStateFlow()

    fun isPlayerRequest(url: String): Boolean = PlayerRequestMatcher.isPlayerRequest(url)

    /** Peticion que se puede cortar entera sin afectar a la reproduccion. */
    fun shouldBlockRequest(url: String): Boolean {
        if (PlayerRequestMatcher.isPlayerRequest(url)) return false
        val blocked = PlayerRequestMatcher.isAdRequest(url)
        if (blocked) _stats.update { it.copy(requestsBlocked = it.requestsBlocked + 1) }
        return blocked
    }

    /** Sanea el cuerpo del playerResponse. Devuelve el original si la URL no es del reproductor. */
    operator fun invoke(url: String, body: String): PlayerFilterResult {
        if (!isPlayerRequest(url)) return PlayerFilterResult(body, changed = false)

        val result = responseFilter.filter(body)
        _stats.update {
            it.copy(
                responsesInspected = it.responsesInspected + 1,
                adSectionsRemoved = it.adSectionsRemoved + result.removedCount,
            )
        }
        return result
    }
}
