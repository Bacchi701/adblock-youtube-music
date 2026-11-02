package com.adblocker.ytmusic.domain.web

/** Que hacer con una navegacion que la pagina intenta abrir. */
enum class NavigationDecision {
    /** Se carga en el WebView del reproductor. */
    IN_APP,

    /** Se entrega al navegador del sistema: el WebView (y la musica) no se tocan. */
    EXTERNAL,

    /** No se abre en ningun sitio. */
    BLOCK,
}

/**
 * Confina el WebView a YouTube Music y al inicio de sesion de Google.
 *
 * Solo `https` llega a cargarse dentro: `intent:`, `javascript:`, `data:`, `file:`... se bloquean
 * aunque el host parezca de YouTube (`intent://music.youtube.com/...` abriria otra app).
 */
object NavigationPolicy {

    private val IN_APP_HOSTS = setOf(
        "music.youtube.com",
        // Inicio y cierre de sesion de Google y su vuelta a YouTube Music.
        "accounts.google.com",
        "accounts.youtube.com",
        "www.youtube.com",
        "youtube.com",
        "myaccount.google.com",
        "gds.google.com",
        "www.google.com",
        // Pantallas de consentimiento de cookies.
        "consent.youtube.com",
        "consent.google.com",
    )

    private const val HTTPS_PORT = 443

    fun decide(url: String, isMainFrame: Boolean = true): NavigationDecision {
        val parts = UrlParts.parse(url) ?: return NavigationDecision.BLOCK
        when (parts.scheme) {
            "https" -> Unit
            // Texto claro: nunca dentro de la app, pero un enlace normal puede abrirse fuera.
            "http" -> return if (isMainFrame) NavigationDecision.EXTERNAL else NavigationDecision.BLOCK
            else -> return NavigationDecision.BLOCK
        }

        // Los iframes de la propia pagina (inicio de sesion, reproductor) se dejan cargar.
        if (!isMainFrame) return NavigationDecision.IN_APP

        if (parts.hasUserInfo) return NavigationDecision.BLOCK
        val host = parts.host ?: return NavigationDecision.BLOCK
        if (parts.port != null && parts.port != HTTPS_PORT) return NavigationDecision.EXTERNAL

        return if (host in IN_APP_HOSTS) NavigationDecision.IN_APP else NavigationDecision.EXTERNAL
    }
}
