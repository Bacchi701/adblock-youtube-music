package com.adblocker.ytmusic.domain.navigation

import com.adblocker.ytmusic.domain.player.PlayerRequestMatcher

/**
 * Secciones de la barra inferior. [browseId] es el identificador con el que la propia web de
 * YouTube Music navega a cada una (no depende del idioma de la interfaz). Buscar no tiene
 * pagina propia: abre el cuadro de busqueda de la web.
 */
enum class YtmTab(val browseId: String?, val fallbackPath: String) {
    HOME("FEmusic_home", "/"),
    EXPLORE("FEmusic_explore", "/explore"),
    SEARCH(null, "/search"),
    LIBRARY("FEmusic_library_landing", "/library"),
    ;

    companion object {

        private const val MUSIC_HOST = "music.youtube.com"

        /**
         * Pestaña a la que pertenece una URL de primer nivel; `null` para paginas interiores
         * (un album, una lista, la cancion) y para cualquier otro host, que mantienen la actual.
         */
        fun fromUrl(url: String): YtmTab? {
            if (PlayerRequestMatcher.hostOf(url) != MUSIC_HOST) return null
            val path = PlayerRequestMatcher.pathOf(url).trimEnd('/')
            return when {
                path.isEmpty() -> HOME
                path == "/explore" -> EXPLORE
                path == "/search" -> SEARCH
                path == "/library" || path.startsWith("/library/") -> LIBRARY
                else -> null
            }
        }
    }
}
