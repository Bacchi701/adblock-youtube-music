package com.adblocker.ytmusic.domain.navigation

/**
 * Recuerda en que entrada del historial empezo la seccion actual, para que atras recorra solo
 * las paginas abiertas dentro de ella y despues salte a Principal, como la app oficial.
 *
 * La raiz se fija cuando la web confirma (via historial) la navegacion pedida desde la barra:
 * pulsar la pestaña en la que ya se esta no crea entrada nueva y no debe mover la raiz.
 */
class TabHistory {

    var rootIndex: Int = 0
        private set

    private var pendingTab: YtmTab? = null

    fun onTabRequested(tab: YtmTab) {
        pendingTab = tab
    }

    fun onHistoryChanged(tab: YtmTab?, index: Int) {
        val pending = pendingTab
        if (pending != null && tab == pending) {
            rootIndex = index
            pendingTab = null
        } else if (index < rootIndex) {
            // Se retrocedio mas alla del inicio de la seccion: la raiz no puede quedar por delante.
            rootIndex = index
        }
    }

    /** Una pagina nueva (p. ej. tras recrear el WebView) empieza su historial desde cero. */
    fun reset() {
        rootIndex = 0
        pendingTab = null
    }
}
