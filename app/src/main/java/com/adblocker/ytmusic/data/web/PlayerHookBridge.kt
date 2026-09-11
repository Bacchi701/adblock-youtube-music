package com.adblocker.ytmusic.data.web

import android.webkit.JavascriptInterface
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase

/**
 * Puente expuesto a la pagina como `AdBlockBridge`. Es deliberadamente delgado: toda la
 * decision vive en [FilterPlayerResponseUseCase], que se prueba con JUnit.
 *
 * Los metodos corren en el hilo JavaBridge de WebView, asi que no tocan la UI y jamas lanzan:
 * ante cualquier fallo se devuelve el cuerpo original y la reproduccion sigue.
 */
class PlayerHookBridge(
    private val useCase: FilterPlayerResponseUseCase,
    private val playbackListener: (Boolean) -> Unit = {},
    private val metadataListener: (PlaybackMetadata) -> Unit = {},
) {

    /** Avisa de que la pagina empezo o detuvo la reproduccion. */
    @JavascriptInterface
    fun onPlaybackStateChanged(playing: Boolean) {
        runCatching { playbackListener(playing) }
    }

    /** Titulo, interprete y tiempos del elemento multimedia activo. */
    @JavascriptInterface
    fun onMetadataChanged(
        title: String?,
        artist: String?,
        durationSeconds: Double,
        positionSeconds: Double,
        playing: Boolean,
    ) {
        runCatching {
            metadataListener(
                PlaybackMetadata.fromSeconds(
                    title = title,
                    artist = artist,
                    durationSeconds = durationSeconds,
                    positionSeconds = positionSeconds,
                    isPlaying = playing,
                ),
            )
        }
    }

    @JavascriptInterface
    fun isPlayerRequest(url: String?): Boolean =
        url != null && runCatching { useCase.isPlayerRequest(url) }.getOrDefault(false)

    @JavascriptInterface
    fun filterPlayerResponse(url: String?, body: String?): String {
        val original = body ?: return ""
        if (url == null) return original
        return runCatching { useCase(url, original).body }.getOrDefault(original)
    }

    companion object {
        const val NAME = "AdBlockBridge"
    }
}
