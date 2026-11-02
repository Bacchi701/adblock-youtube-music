package com.adblocker.ytmusic.data.web

import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adblocker.ytmusic.domain.web.NavigationDecision
import com.adblocker.ytmusic.domain.web.NavigationPolicy

/**
 * Minimos privilegios para la pagina: sin camara, microfono ni ubicacion, sin ventanas nuevas
 * dentro de la app y sin pantalla completa (el reproductor de musica no la necesita).
 */
class PlayerChromeClient(
    private val events: PlayerPageEvents,
    private val logConsole: Boolean,
) : WebChromeClient() {

    override fun onPermissionRequest(request: PermissionRequest) {
        request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback,
    ) {
        callback.invoke(origin, false, false)
    }

    /**
     * Los enlaces `target=_blank` y `window.open` llegan aqui. Cargarlos en el WebView principal
     * descargaria YouTube Music y cortaria la musica: se captura la URL con un WebView temporal
     * que no llega a cargar nada y se decide con [NavigationPolicy].
     */
    override fun onCreateWindow(
        view: WebView,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message,
    ): Boolean {
        if (!isUserGesture) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val catcher = WebView(view.context)
        catcher.webViewClient = UrlCatcher(events)
        transport.webView = catcher
        resultMsg.sendToTarget()
        return true
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        callback?.onCustomViewHidden()
    }

    /** Sin esto WebView dibuja un cartel gris con un boton de play sobre cada `<video>` vacio. */
    override fun getDefaultVideoPoster(): Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    /** En release la consola de la pagina no llega a logcat (puede contener URLs y datos). */
    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = !logConsole

    /**
     * Cliente del WebView temporal de [onCreateWindow]: segun la version de WebView la primera
     * URL llega por `shouldOverrideUrlLoading` o por `onPageStarted`; se atiende la primera y se
     * destruye sin dejar que cargue (ni JavaScript tiene activado).
     */
    private class UrlCatcher(private val events: PlayerPageEvents) : WebViewClient() {

        private var handled = false

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            capture(view, request.url.toString())
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            if (url == null || url.startsWith("about:")) return
            view.stopLoading()
            capture(view, url)
        }

        private fun capture(view: WebView, url: String) {
            // La ventana nueva arranca en about:blank antes de recibir la URL real.
            if (handled || url.startsWith("about:")) return
            handled = true
            if (NavigationPolicy.decide(url) != NavigationDecision.BLOCK) events.onExternalLink(url)
            view.post { view.destroy() }
        }
    }
}
