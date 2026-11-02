package com.adblocker.ytmusic.domain.web

/**
 * Despiece minimo de una URL absoluta para las politicas de seguridad. Logica pura sobre
 * cadenas (`android.net.Uri` no funciona en pruebas JVM) y estricta: ante cualquier forma
 * rara devuelve datos que las politicas rechazan, nunca intenta "arreglar" la URL.
 */
data class UrlParts(
    /** En minusculas. */
    val scheme: String,
    /** `null` si la URL no tiene autoridad (`javascript:`, `data:`...). */
    val authority: String?,
    /** En minusculas y sin el punto final que admite el DNS. */
    val host: String?,
    /** `null` sin puerto explicito; -1 si no es un numero valido. */
    val port: Int?,
) {
    /** `https://music.youtube.com@evil.com` lleva a evil.com: un usuario en la URL es un engaño. */
    val hasUserInfo: Boolean get() = authority?.contains('@') == true

    companion object {

        private val AUTHORITY_END = charArrayOf('/', '\\', '?', '#')

        fun parse(url: String): UrlParts? {
            val schemeEnd = url.indexOf(':')
            if (schemeEnd <= 0) return null
            val scheme = url.substring(0, schemeEnd).lowercase()
            if (!scheme.all { it in 'a'..'z' || it in '0'..'9' || it == '+' || it == '-' || it == '.' }) return null

            if (!url.startsWith("//", schemeEnd + 1)) return UrlParts(scheme, null, null, null)
            val start = schemeEnd + 3
            val end = url.indexOfAny(AUTHORITY_END, start).let { if (it < 0) url.length else it }
            val authority = url.substring(start, end)
            val hostPort = authority.substringAfterLast('@')
            if (hostPort.startsWith("[")) {
                // IPv6 literal: ninguna politica lo admite, se conserva tal cual para rechazarlo.
                return UrlParts(scheme, authority, hostPort.lowercase(), null)
            }
            val colon = hostPort.indexOf(':')
            val host = (if (colon < 0) hostPort else hostPort.substring(0, colon)).lowercase().trimEnd('.')
            val port = if (colon < 0) null else hostPort.substring(colon + 1).toIntOrNull() ?: -1
            return UrlParts(scheme, authority, host.ifEmpty { null }, port)
        }
    }
}
