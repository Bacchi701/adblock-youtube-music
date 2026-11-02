package com.adblocker.ytmusic.domain.player

/** Que partes de la sesion multimedia hay que volver a publicar tras un cambio. */
data class PlaybackUpdate(
    /** Titulo, artista, album o duracion: `MediaSession.setMetadata`. */
    val sessionMetadata: Boolean,
    /** Play/pausa, carga o salto de posicion: `MediaSession.setPlaybackState`. */
    val sessionState: Boolean,
    /** Lo que se ve en la notificacion: republicarla obliga a SystemUI a rehacer su reproductor. */
    val notification: Boolean,
    /** Hay que (re)descargar la caratula. */
    val artwork: Boolean,
) {
    val isEmpty: Boolean get() = !sessionMetadata && !sessionState && !notification && !artwork
}

/**
 * Evita el trabajo que hacia el servicio antes: re-publicar notificacion y sesion cada pocos
 * segundos aunque solo avanzara la posicion. La posicion por si sola no notifica nada: el
 * sistema la extrapola a partir del ultimo estado publicado.
 */
object PlaybackUpdatePolicy {

    fun between(previous: PlaybackMetadata?, current: PlaybackMetadata): PlaybackUpdate {
        if (previous == null) {
            return PlaybackUpdate(
                sessionMetadata = true,
                sessionState = true,
                notification = true,
                artwork = current.artworkUrl != null,
            )
        }
        val trackChanged = previous.title != current.title ||
            previous.artist != current.artist ||
            previous.album != current.album
        val stateChanged = previous.isPlaying != current.isPlaying ||
            previous.isBuffering != current.isBuffering
        return PlaybackUpdate(
            sessionMetadata = trackChanged || previous.durationMs != current.durationMs,
            sessionState = stateChanged || previous.positionMs != current.positionMs,
            notification = trackChanged || previous.isPlaying != current.isPlaying,
            artwork = previous.artworkUrl != current.artworkUrl && current.artworkUrl != null,
        )
    }
}
