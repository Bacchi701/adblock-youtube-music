package com.adblocker.ytmusic.domain.player

/** Lo que se muestra en la pantalla de bloqueo y en la notificacion. */
data class PlaybackMetadata(
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val album: String = "",
    /** Caratula elegida por la pagina; se valida con [ArtworkUrlPolicy] antes de descargarla. */
    val artworkUrl: String? = null,
    /** Reproduccion pedida pero esperando datos (la pagina emitio `waiting`). */
    val isBuffering: Boolean = false,
) {
    val hasTrack: Boolean get() = title.isNotEmpty()

    companion object {
        /**
         * Normaliza lo que reporta la pagina: los tiempos llegan en segundos y pueden ser
         * `NaN`, infinitos o negativos mientras el reproductor carga.
         */
        fun fromSeconds(
            title: String?,
            artist: String?,
            durationSeconds: Double,
            positionSeconds: Double,
            isPlaying: Boolean,
            album: String? = null,
            artworkUrl: String? = null,
        ) = PlaybackMetadata(
            title = title.orEmpty().trim(),
            artist = artist.orEmpty().trim(),
            durationMs = secondsToMillis(durationSeconds),
            positionMs = secondsToMillis(positionSeconds),
            isPlaying = isPlaying,
            album = album.orEmpty().trim(),
            artworkUrl = artworkUrl?.trim()?.ifEmpty { null },
        )

        fun secondsToMillis(seconds: Double): Long =
            if (seconds.isNaN() || seconds.isInfinite() || seconds <= 0.0) 0L else (seconds * 1_000).toLong()
    }
}
