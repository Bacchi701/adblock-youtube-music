package com.adblocker.ytmusic.data.web

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adblocker.ytmusic.domain.web.NavigationDecision
import com.adblocker.ytmusic.domain.web.NavigationPolicy

/**
 * Cliente del WebView del reproductor:
 *  - corta anuncios, dominios de la lista y medicion ([RequestInterceptor]);
 *  - confina la navegacion a YouTube Music y al inicio de sesion ([NavigationPolicy]);
 *  - informa de los cambios de URL de la SPA y de la muerte del renderer.
 */
class PlayerWebViewClient(
    private val interceptor: RequestInterceptor,
    private val events: PlayerPageEvents,
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        interceptor.intercept(request.url.toString())

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return when (NavigationPolicy.decide(url, request.isForMainFrame)) {
            NavigationDecision.IN_APP -> false
            NavigationDecision.EXTERNAL -> {
                events.onExternalLink(url)
                true
            }
            NavigationDecision.BLOCK -> true
        }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (url != null) events.onHistoryChanged(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String?) {
        events.onPageVisible()
    }

    /**
     * Sin esto, cuando el sistema mata el renderer (falta de memoria en segundo plano) Android
     * cierra la app entera. Devolver `true` obliga a no volver a usar este WebView.
     */
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        events.onRenderProcessGone(view)
        return true
    }
}
