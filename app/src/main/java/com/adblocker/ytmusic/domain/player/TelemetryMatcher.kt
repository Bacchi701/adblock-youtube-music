package com.adblocker.ytmusic.domain.player

/**
 * Peticiones de medicion de YouTube Music que se responden en local sin salir del dispositivo.
 *
 * Lista deliberadamente corta: no incluye `/api/stats/watchtime` ni `/api/stats/playback`
 * (alimentan el historial y "Vuelve a escucharlo"), ni la verificacion anti-bots
 * (`/youtubei/v1/att/get`, `jnn-pa.googleapis.com`), ni `generate_204`. Cortarlas romperia
 * funciones visibles o la propia reproduccion.
 */
object TelemetryMatcher {

    enum class Response {
        /** La web espera JSON: un `{}` la da por enviada y no reintenta. */
        EMPTY_JSON,

        /** Pings sin cuerpo. */
        NO_CONTENT,
    }

    private const val YOUTUBE = "youtube.com"
    private const val PLAY_LOG_HOST = "play.google.com"

    fun match(url: String): Response? {
        val host = PlayerRequestMatcher.hostOf(url) ?: return null
        val path = PlayerRequestMatcher.pathOf(url)
        val youtube = host == YOUTUBE || host.endsWith(".$YOUTUBE")
        return when {
            youtube && path == "/youtubei/v1/log_event" -> Response.EMPTY_JSON
            youtube && (path == "/api/stats/qoe" || path == "/csi_204") -> Response.NO_CONTENT
            host == PLAY_LOG_HOST && path == "/log" -> Response.NO_CONTENT
            else -> null
        }
    }
}
