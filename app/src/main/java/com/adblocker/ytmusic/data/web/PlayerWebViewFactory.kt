package com.adblocker.ytmusic.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Construye el WebView del reproductor con sus capas de filtrado y aislamiento:
 *  1. [RequestInterceptor] corta anuncios, dominios de la lista y medicion (tambien en el
 *     service worker de la pagina, que no pasa por el `WebViewClient`).
 *  2. El hook JS ([HookScript]) poda el `playerResponse` y se inyecta en document-start solo en
 *     `https://music.youtube.com`.
 *  3. El puente ([PlayerBridge]) solo existe para ese origen y es asincrono.
 *  4. La navegacion queda confinada por `NavigationPolicy` y [PlayerChromeClient].
 */
object PlayerWebViewFactory {

    const val START_URL = "https://music.youtube.com/"

    /** Fondo de YouTube Music: evita el destello blanco mientras carga la pagina. */
    private const val BACKGROUND = 0xFF030303.toInt()

    private val serviceWorkerConfigured = AtomicBoolean(false)

    /**
     * Sin estas funciones el hook no se podria limitar al origen de YouTube Music ni inyectar
     * antes que sus scripts. No hay respaldo inseguro: se pide actualizar el WebView del sistema.
     */
    fun isSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    fun create(
        context: Context,
        filterPlayer: FilterPlayerResponseUseCase,
        evaluateDomain: EvaluateDomainUseCase,
        events: PlayerPageEvents,
    ): WebView {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val interceptor = RequestInterceptor(filterPlayer, evaluateDomain)
        configureServiceWorkers(interceptor)
        val script = HookScript.load(
            context.assets,
            HookConfig(
                adKeys = filterPlayer.adKeys,
                protectedKeys = filterPlayer.protectedKeys,
                bridgeName = PlayerBridge.NAME,
                debug = debuggable,
            ),
        )
        val origins = setOf(BridgeMessage.ORIGIN)

        return BackgroundPlaybackWebView(context).apply {
            // Sin layoutParams, AndroidView le asigna WRAP_CONTENT y Chromium fija a 0 el alto que ven
            // las media queries: YouTube Music se cree en horizontal y abre el reproductor en
            // pantalla completa negra. MATCH_PARENT le da el alto real del contenedor.
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(BACKGROUND)
            applySettings(settings)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            WebViewCompat.addWebMessageListener(this, PlayerBridge.NAME, origins, PlayerBridge(events::onBridgeMessage))
            WebViewCompat.addDocumentStartJavaScript(this, script, origins)
            webViewClient = PlayerWebViewClient(interceptor, events)
            webChromeClient = PlayerChromeClient(events, logConsole = debuggable)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(settings: WebSettings) = with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        mediaPlaybackRequiresUserGesture = false
        // Zero telemetry: Safe Browsing enviaria las URLs visitadas a un servidor externo.
        safeBrowsingEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        setGeolocationEnabled(false)
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        // Las ventanas nuevas las atiende PlayerChromeClient (se abren fuera); sin gesto, ninguna.
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(true)
        setSupportZoom(false)
        // Medicion de conversiones publicitarias de Privacy Sandbox: fuera.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
            WebSettingsCompat.setAttributionRegistrationBehavior(
                this,
                WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED,
            )
        }
        // La web ya es oscura: oscurecerla por algoritmo solo estropearia colores.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, false)
        }
    }

    /** El cliente de service workers es global al proceso: se configura una sola vez. */
    private fun configureServiceWorkers(interceptor: RequestInterceptor) {
        if (!serviceWorkerConfigured.compareAndSet(false, true)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) return
        val controller = ServiceWorkerControllerCompat.getInstance()
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            controller.setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                        interceptor.intercept(request.url.toString())
                },
            )
        }
        val workerSettings = controller.serviceWorkerWebSettings
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS)) {
            workerSettings.allowFileAccess = false
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)) {
            workerSettings.allowContentAccess = false
        }
    }
}
