package com.adblocker.ytmusic.domain.player

/**
 * Clasifica URLs del reproductor web. Acepta URLs absolutas y relativas porque el hook de
 * JavaScript ve ambas formas (`fetch("/youtubei/v1/player?...")`).
 */
object PlayerRequestMatcher {

    private val API_HOST_SUFFIXES = setOf(
        "youtube.com",
        "youtube-nocookie.com",
        "googleapis.com",
        "googlevideo.com",
    )

    private val AD_HOST_SUFFIXES = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "2mdn.net",
    )

    private val AD_PATH_MARKERS = listOf(
        "/pagead/",
        "/api/stats/ads",
        "/ptracking",
        "/get_midroll_info",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/att/get",
    )

    private const val PLAYER_PATH = "/youtubei/v1/player"

    /** `true` solo para la llamada que devuelve el playerResponse (no sus variantes de anuncio). */
    fun isPlayerRequest(url: String): Boolean {
        val host = hostOf(url)
        if (host != null && !host.matchesAnySuffix(API_HOST_SUFFIXES)) return false
        val path = pathOf(url)
        return path == PLAYER_PATH || path == "$PLAYER_PATH/"
    }

    /** Peticiones puramente publicitarias o de medicion: se pueden cortar enteras. */
    fun isAdRequest(url: String): Boolean {
        hostOf(url)?.let { host ->
            if (host.matchesAnySuffix(AD_HOST_SUFFIXES)) return true
        }
        val path = pathOf(url)
        return AD_PATH_MARKERS.any { path.startsWith(it) || path.contains(it) }
    }

    /** Host en minusculas sin credenciales ni puerto; `null` si la URL es relativa. */
    fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) return null
        val authorityStart = schemeEnd + 3
        val authorityEnd = url.indexOfFirst(authorityStart, '/', '?', '#')
        val authority = url.substring(authorityStart, authorityEnd)
        return authority
            .substringAfterLast('@')
            .substringBefore(':')
            .trim('.')
            .lowercase()
            .ifEmpty { null }
    }

    /** Ruta sin query ni fragmento. Las URLs relativas se devuelven tal cual. */
    fun pathOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        val start = if (schemeEnd < 0) 0 else url.indexOfFirst(schemeEnd + 3, '/', '?', '#')
        val end = url.indexOfFirst(start, '?', '#')
        val path = url.substring(start, end)
        return if (path.startsWith("/")) path.lowercase() else "/${path.lowercase()}"
    }

    private fun String.matchesAnySuffix(suffixes: Set<String>): Boolean = suffixes.any { suffix ->
        this == suffix || endsWith(".$suffix")
    }

    private fun String.indexOfFirst(from: Int, vararg chars: Char): Int {
        for (index in from until length) {
            if (this[index] in chars) return index
        }
        return length
    }
}
