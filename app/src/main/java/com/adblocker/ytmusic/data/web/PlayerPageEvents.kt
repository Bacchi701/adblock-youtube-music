package com.adblocker.ytmusic.data.web

import android.webkit.WebView

/**
 * Lo que el WebView del reproductor cuenta a la pantalla que lo aloja. Todos los metodos llegan
 * en el hilo principal.
 */
interface PlayerPageEvents {

    fun onBridgeMessage(message: BridgeMessage)

    /** Cambio la URL del documento, tambien con `pushState` (la web es una SPA). */
    fun onHistoryChanged(url: String)

    /** La pagina ya pinto su primer contenido. */
    fun onPageVisible()

    /** Un enlace que se abre fuera de la app (ver `NavigationPolicy`). */
    fun onExternalLink(url: String)

    /** El renderer murio (falta de memoria o fallo): hay que descartar [webView] y crear otro. */
    fun onRenderProcessGone(webView: WebView)
}
