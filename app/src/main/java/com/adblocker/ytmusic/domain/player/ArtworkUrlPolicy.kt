package com.adblocker.ytmusic.domain.player

import com.adblocker.ytmusic.domain.web.UrlParts

/**
 * Decide que caratula descargar para la notificacion. La URL llega de la pagina, asi que es
 * entrada no confiable: solo `https` hacia los CDN de imagenes de Google, sin usuario ni
 * puertos raros. Asi la app nunca se convierte en un descargador de URLs arbitrarias.
 */
object ArtworkUrlPolicy {

    /** Una entrada de `MediaMetadata.artwork`: `sizes` es del estilo "544x544" o "any". */
    data class Candidate(val src: String, val sizes: String = "")

    private val ALLOWED_HOST_SUFFIXES = listOf("ytimg.com", "googleusercontent.com", "ggpht.com")
    private const val HTTPS_PORT = 443
    private const val MAX_URL_LENGTH = 2_048
    const val TARGET_PX = 512

    fun isAllowed(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        val parts = UrlParts.parse(url) ?: return false
        if (parts.scheme != "https" || parts.hasUserInfo) return false
        if (parts.port != null && parts.port != HTTPS_PORT) return false
        val host = parts.host ?: return false
        return ALLOWED_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
    }

    /**
     * La mas pequeña que cubra [targetPx] (no tiene sentido bajar 1200 px para un icono de
     * 512); si ninguna llega, la mayor. Las que no pasan [isAllowed] ni se consideran.
     */
    fun pick(candidates: List<Candidate>, targetPx: Int = TARGET_PX): String? {
        val allowed = candidates.filter { isAllowed(it.src) }
        if (allowed.isEmpty()) return null
        val sized = allowed.map { it to largestSide(it.sizes) }
        val covering = sized.filter { (_, side) -> side >= targetPx }
        return if (covering.isNotEmpty()) {
            covering.minBy { (_, side) -> side }.first.src
        } else {
            sized.maxBy { (_, side) -> side }.first.src
        }
    }

    /** Lado mayor declarado; 0 si no se declara o no se entiende ("any" incluido). */
    private fun largestSide(sizes: String): Int =
        sizes.split(' ')
            .mapNotNull { size ->
                val dims = size.lowercase().split('x')
                if (dims.size != 2) return@mapNotNull null
                val width = dims[0].toIntOrNull() ?: return@mapNotNull null
                val height = dims[1].toIntOrNull() ?: return@mapNotNull null
                maxOf(width, height)
            }
            .maxOrNull() ?: 0
}
