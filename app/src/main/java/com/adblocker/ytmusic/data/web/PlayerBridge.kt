package com.adblocker.ytmusic.data.web

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat

/**
 * Puente de la pagina hacia la app (`addWebMessageListener`). A diferencia del antiguo
 * `addJavascriptInterface`, el objeto solo existe en documentos de `https://music.youtube.com`
 * (ni iframes ni otros origenes lo ven) y los mensajes son asincronos: la pagina nunca se
 * queda esperando a Kotlin.
 *
 * Deliberadamente delgado: el parseo y las reglas viven en [BridgeMessage], que se prueba con JUnit.
 */
class PlayerBridge(
    private val onMessage: (BridgeMessage) -> Unit,
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy,
    ) {
        if (!BridgeMessage.acceptsSource(sourceOrigin.toString(), isMainFrame)) return
        val parsed = BridgeMessage.parse(message.data) ?: return
        // Un fallo de quien escucha no debe propagarse al WebView.
        runCatching { onMessage(parsed) }
    }

    companion object {
        /** Nombre neutro: la pagina lo ve como `window.NativeShell`. */
        const val NAME = "NativeShell"
    }
}
