package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.player.PlayerRequestMatcher
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import com.adblocker.ytmusic.domain.player.TelemetryMatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Punto unico de decision para el trafico del reproductor web: que peticiones se cortan o se
 * responden en local, y con que claves se poda el `playerResponse`.
 *
 * La poda corre dentro de la pagina (`player_hook.js`, sincrona y sin cruzar el puente), con
 * las claves de [adKeys] y [protectedKeys]; la pagina informa de cada respuesta podada con
 * [onPlayerResponsePruned].
 */
class FilterPlayerResponseUseCase(
    val adKeys: Set<String> = PlayerResponseFilter.DEFAULT_AD_KEYS,
    val protectedKeys: Set<String> = PlayerResponseFilter.DEFAULT_PROTECTED_KEYS,
) {

    private val _stats = MutableStateFlow(PlayerFilterStats())
    val stats: StateFlow<PlayerFilterStats> = _stats.asStateFlow()

    /** Peticion que se puede cortar entera sin afectar a la reproduccion. */
    fun shouldBlockRequest(url: String): Boolean {
        if (PlayerRequestMatcher.isPlayerRequest(url)) return false
        val blocked = PlayerRequestMatcher.isAdRequest(url)
        if (blocked) countBlockedRequest()
        return blocked
    }

    /** Medicion que se contesta en local; `null` si la peticion debe salir. */
    fun telemetryResponse(url: String): TelemetryMatcher.Response? =
        TelemetryMatcher.match(url)?.also { countBlockedRequest() }

    /** La pagina reviso un `playerResponse` y quito [removedSections] bloques de anuncios. */
    fun onPlayerResponsePruned(removedSections: Int) {
        _stats.update {
            it.copy(
                responsesInspected = it.responsesInspected + 1,
                adSectionsRemoved = it.adSectionsRemoved + removedSections.coerceAtLeast(0),
            )
        }
    }

    private fun countBlockedRequest() {
        _stats.update { it.copy(requestsBlocked = it.requestsBlocked + 1) }
    }
}
