package com.adblocker.ytmusic.ui.shell

import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.adblocker.ytmusic.data.web.HookScript
import com.adblocker.ytmusic.data.web.PlayerPageEvents
import com.adblocker.ytmusic.data.web.PlayerWebViewFactory
import com.adblocker.ytmusic.domain.navigation.YtmTab
import com.adblocker.ytmusic.domain.player.PlayerCommand
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase

/**
 * Dueño del WebView del reproductor dentro de la Activity: lo crea una sola vez, lo recrea si
 * muere el renderer y traduce las ordenes nativas (barra, atras, notificacion) a llamadas a la
 * pagina. Todo en el hilo principal.
 */
class PlayerWebController(
    private val context: Context,
    private val filterPlayer: FilterPlayerResponseUseCase,
    private val evaluateDomain: EvaluateDomainUseCase,
    private val events: PlayerPageEvents,
    private val isPlaying: () -> Boolean,
) {

    /** Estado de Compose: al recrearse, la pantalla monta el WebView nuevo. */
    var webView: WebView? by mutableStateOf(null)
        private set

    val canGoBack: Boolean get() = webView?.canGoBack() == true

    val historyIndex: Int get() = webView?.copyBackForwardList()?.currentIndex ?: 0

    fun start() {
        if (webView != null) return
        webView = PlayerWebViewFactory.create(context, filterPlayer, evaluateDomain, events)
            .also { it.loadUrl(PlayerWebViewFactory.START_URL) }
    }

    /** Solo se descarta el WebView que murio; un aviso tardio de uno viejo no hace nada. */
    fun recreate(dead: WebView) {
        if (dead !== webView) return
        release(dead)
        webView = null
        start()
    }

    fun destroy() {
        webView?.let(::release)
        webView = null
    }

    fun navigate(tab: YtmTab) {
        val web = webView ?: return
        if (tab == YtmTab.SEARCH) {
            call(web, "openSearch()") { opened -> if (opened) showKeyboard(web) }
            return
        }
        call(web, "navigate('${tab.browseId}')") { navigated ->
            // Sin menu en la pagina (aun cargando o cambio su DOM): recargar es el ultimo recurso
            // y nunca con musica sonando, porque la cortaria.
            if (!navigated && !isPlaying()) {
                web.loadUrl(PlayerWebViewFactory.START_URL.trimEnd('/') + tab.fallbackPath)
            }
        }
    }

    fun goBack() {
        webView?.goBack()
    }

    fun closeSearch() {
        val web = webView ?: return
        call(web, "closeSearch()")
    }

    /**
     * Un clic simulado desde JavaScript no cuenta como gesto del usuario, asi que Android no
     * abre el teclado por su cuenta: se le pide para el campo de busqueda ya enfocado.
     */
    private fun showKeyboard(web: WebView) {
        web.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(web, InputMethodManager.SHOW_IMPLICIT)
    }

    fun collapsePlayer() {
        val web = webView ?: return
        call(web, "collapsePlayer()") { collapsed -> if (!collapsed && web.canGoBack()) web.goBack() }
    }

    fun execute(command: PlayerCommand) {
        val web = webView ?: return
        val call = when (command) {
            PlayerCommand.Play -> "play()"
            PlayerCommand.Pause, PlayerCommand.Stop -> "pause()"
            PlayerCommand.Next -> "next()"
            PlayerCommand.Previous -> "previous()"
            is PlayerCommand.SeekTo -> "seek(${command.positionMs / 1000.0})"
        }
        call(web, call)
    }

    /**
     * Llama a `window.__nativeShell` si existe. Si la pagina aun carga o es otra (inicio de
     * sesion), no hay objeto y el resultado es `false`: nunca lanza dentro de la pagina.
     */
    private fun call(web: WebView, expression: String, onResult: (Boolean) -> Unit = {}) {
        val shell = "window.${HookScript.CONTROL_OBJECT}"
        web.evaluateJavascript("(function(){try{return !!($shell&&$shell.$expression)}catch(e){return false}})()") {
            onResult(it == "true")
        }
    }

    private fun release(web: WebView) {
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
