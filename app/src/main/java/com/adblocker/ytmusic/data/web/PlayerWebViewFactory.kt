package com.adblocker.ytmusic.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import java.io.ByteArrayInputStream

/**
 * Construye el WebView del reproductor con las tres capas de filtrado:
 *  1. `shouldInterceptRequest` corta peticiones puramente publicitarias y dominios de la lista.
 *  2. El hook JS sanea el `playerResponse` (unica via posible: el cuerpo va en un POST).
 *  3. La navegacion queda confinada a los hosts de YouTube.
 */
object PlayerWebViewFactory {

    const val START_URL = "https://music.youtube.com/"
    private const val HOOK_ASSET = "js/player_hook.js"

    private val ALLOWED_HOSTS = setOf(
        "youtube.com",
        "youtube-nocookie.com",
        "google.com",
        "googleapis.com",
        "googlevideo.com",
        "gstatic.com",
        "ggpht.com",
        "ytimg.com",
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        filterPlayer: FilterPlayerResponseUseCase,
        evaluateDomain: EvaluateDomainUseCase,
        onPlaybackStateChanged: (Boolean) -> Unit = {},
        onMetadataChanged: (PlaybackMetadata) -> Unit = {},
    ): WebView {
        val hookSource = context.assets.open(HOOK_ASSET).bufferedReader().use { it.readText() }

        return BackgroundPlaybackWebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            // Zero telemetry: Safe Browsing enviaria las URLs visitadas a un servidor externo.
            settings.safeBrowsingEnabled = false

            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            addJavascriptInterface(
                PlayerHookBridge(filterPlayer, onPlaybackStateChanged, onMetadataChanged),
                PlayerHookBridge.NAME,
            )
            webViewClient = FilteringWebViewClient(hookSource, filterPlayer, evaluateDomain)
        }
    }

    private class FilteringWebViewClient(
        private val hookSource: String,
        private val filterPlayer: FilterPlayerResponseUseCase,
        private val evaluateDomain: EvaluateDomainUseCase,
    ) : WebViewClient() {

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            // Se reinyecta en cada documento: el hook se auto-desactiva si ya estaba puesto.
            view.evaluateJavascript(hookSource, null)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url.toString()
            val host = request.url.host
            val blockedDomain = host != null && evaluateDomain(host).isBlocked
            return if (blockedDomain || filterPlayer.shouldBlockRequest(url)) EMPTY_RESPONSE else null
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val host = request.url.host?.lowercase() ?: return true
            // El puente JS solo debe existir en paginas de YouTube: fuera de ahi no se navega.
            return ALLOWED_HOSTS.none { host == it || host.endsWith(".$it") }
        }

        private companion object {
            val EMPTY_RESPONSE
                get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
    }
}
