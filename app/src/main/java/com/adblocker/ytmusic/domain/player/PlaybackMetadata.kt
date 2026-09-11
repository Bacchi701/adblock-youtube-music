package com.adblocker.ytmusic.domain.player

/** Lo que se muestra en la pantalla de bloqueo y en la notificacion. */
data class PlaybackMetadata(
    val title: String = "",
    val artist: String = "",
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
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
        ) = PlaybackMetadata(
            title = title.orEmpty().trim(),
            artist = artist.orEmpty().trim(),
            durationMs = durationSeconds.toMillis(),
            positionMs = positionSeconds.toMillis(),
            isPlaying = isPlaying,
        )

        private fun Double.toMillis(): Long =
            if (isNaN() || isInfinite() || this <= 0.0) 0L else (this * 1_000).toLong()
    }
}
