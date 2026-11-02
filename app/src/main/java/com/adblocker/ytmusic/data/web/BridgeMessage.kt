package com.adblocker.ytmusic.data.web

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import com.adblocker.ytmusic.domain.player.ArtworkUrlPolicy
import com.adblocker.ytmusic.domain.player.PlaybackMetadata

/**
 * Avisos que la pagina manda por el puente. Todo lo que llega es entrada no confiable: se
 * limita el tamaño, se recortan los textos y cualquier forma inesperada se descarta.
 */
sealed interface BridgeMessage {

    /** La pagina de la cancion se abrio o se cerro. */
    data class PlayerPage(val open: Boolean) : BridgeMessage

    /** El cuadro de busqueda de la web se abrio o se cerro. */
    data class Search(val open: Boolean) : BridgeMessage

    data class Playback(
        val playing: Boolean,
        val buffering: Boolean,
        val positionSeconds: Double,
        val durationSeconds: Double,
    ) : BridgeMessage {
        /** Solo cambia el estado; la duracion se conserva si este aviso aun no la conoce. */
        fun applyTo(current: PlaybackMetadata): PlaybackMetadata {
            val duration = PlaybackMetadata.secondsToMillis(durationSeconds)
            return current.copy(
                isPlaying = playing,
                isBuffering = buffering,
                positionMs = PlaybackMetadata.secondsToMillis(positionSeconds),
                durationMs = if (duration > 0) duration else current.durationMs,
            )
        }
    }

    data class Track(
        val title: String,
        val artist: String,
        val album: String,
        val artwork: List<ArtworkUrlPolicy.Candidate>,
    ) : BridgeMessage {
        /** Solo cambia la pista; la caratula pasa antes por [ArtworkUrlPolicy]. */
        fun applyTo(current: PlaybackMetadata): PlaybackMetadata = current.copy(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            artworkUrl = ArtworkUrlPolicy.pick(artwork),
        )
    }

    /** Se reviso un `playerResponse` y se quitaron [removedSections] bloques de anuncios. */
    data class ResponsePruned(val removedSections: Int) : BridgeMessage

    companion object {

        const val ORIGIN = "https://music.youtube.com"
        const val MAX_LENGTH = 16_384
        private const val MAX_TEXT = 512
        private const val MAX_ARTWORK = 10
        private const val MAX_DEPTH = 4

        /** Solo el documento principal de YouTube Music puede hablar con la app. */
        fun acceptsSource(origin: String, isMainFrame: Boolean): Boolean =
            isMainFrame && origin == ORIGIN

        fun parse(raw: String?): BridgeMessage? {
            if (raw == null || raw.length > MAX_LENGTH) return null
            val root = MiniJson.parseOrNull(raw, MAX_DEPTH) as? JsonValue.Obj ?: return null
            return when (root.string("t")) {
                "page" -> root.bool("open")?.let(::PlayerPage)
                "search" -> root.bool("open")?.let(::Search)
                "state" -> Playback(
                    playing = root.bool("playing") ?: return null,
                    buffering = root.bool("buffering") ?: false,
                    positionSeconds = root.number("position"),
                    durationSeconds = root.number("duration"),
                )
                "meta" -> Track(
                    title = root.string("title").orEmpty().take(MAX_TEXT),
                    artist = root.string("artist").orEmpty().take(MAX_TEXT),
                    album = root.string("album").orEmpty().take(MAX_TEXT),
                    artwork = root.artwork(),
                )
                "pruned" -> ResponsePruned(root.number("removed").toInt().coerceIn(0, Int.MAX_VALUE))
                else -> null
            }
        }

        private fun JsonValue.Obj.string(key: String): String? = (this[key] as? JsonValue.Str)?.value

        private fun JsonValue.Obj.bool(key: String): Boolean? = (this[key] as? JsonValue.Bool)?.value

        /** `NaN`, infinitos y negativos llegan como `null` o valores raros: se quedan en 0. */
        private fun JsonValue.Obj.number(key: String): Double {
            val value = (this[key] as? JsonValue.Num)?.raw?.toDoubleOrNull() ?: return 0.0
            return if (value.isFinite() && value > 0.0) value else 0.0
        }

        private fun JsonValue.Obj.artwork(): List<ArtworkUrlPolicy.Candidate> {
            val items = (this["artwork"] as? JsonValue.Arr)?.items ?: return emptyList()
            return items.asSequence()
                .mapNotNull { it as? JsonValue.Obj }
                .mapNotNull { item ->
                    val src = item.string("src") ?: return@mapNotNull null
                    ArtworkUrlPolicy.Candidate(src, item.string("sizes").orEmpty().take(MAX_TEXT))
                }
                .take(MAX_ARTWORK)
                .toList()
        }
    }
}
