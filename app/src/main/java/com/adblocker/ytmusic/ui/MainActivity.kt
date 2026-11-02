package com.adblocker.ytmusic.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adblocker.ytmusic.data.web.BridgeMessage
import com.adblocker.ytmusic.data.web.PlayerPageEvents
import com.adblocker.ytmusic.data.web.PlayerWebViewFactory
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.navigation.BackAction
import com.adblocker.ytmusic.domain.navigation.BackNavigator
import com.adblocker.ytmusic.domain.navigation.YtmTab
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.service.PlaybackService
import com.adblocker.ytmusic.ui.blocker.BlockerSheet
import com.adblocker.ytmusic.ui.shell.PlayerWebController
import com.adblocker.ytmusic.ui.shell.ShellViewModel
import com.adblocker.ytmusic.ui.shell.WebViewOutdatedScreen
import com.adblocker.ytmusic.ui.shell.YtmShell
import com.adblocker.ytmusic.ui.theme.AdBlockerTheme
import kotlinx.coroutines.launch

/**
 * Unica pantalla de la app: YouTube Music con el filtrado ya activo, sin pasos previos.
 *
 * El WebView vive mientras viva esta Activity (los `configChanges` del manifest evitan que se
 * recree), y atras en la raiz la manda al fondo en lugar de cerrarla: asi la musica sigue.
 */
class MainActivity : ComponentActivity() {

    private val shellViewModel: ShellViewModel by viewModels()

    /** Se crea al abrir la hoja del bloqueador: el monitor de red no arranca con la app. */
    private val filterViewModelLazy = viewModels<FilterViewModel> { FilterViewModel.Factory(this) }
    private val filterViewModel: FilterViewModel by filterViewModelLazy

    private val coordinator = ServiceLocator.playbackCoordinator()
    private val filterPlayer = ServiceLocator.filterPlayerResponseUseCase()

    private var player: PlayerWebController? = null

    /**
     * El servicio en primer plano se arranca la primera vez que suena algo y se mantiene
     * mientras exista el WebView. Detenerlo en cada pausa obligaria a rearrancarlo al cambiar
     * de cancion, y desde segundo plano el sistema lo rechazaria.
     */
    private var playbackServiceStarted = false

    /** Dialogo del sistema que autoriza el VpnService; sin este consentimiento no se filtra. */
    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) filterViewModel.onFilterStarted()
    }

    /** Solo para el filtro DNS: la notificacion de la sesion multimedia no necesita permiso. */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* la notificacion es informativa: el filtro funciona igual si se rechaza */ }

    /**
     * Siempre activo, tambien en la raiz: el sistema solo manda la tarea al fondo (en vez de
     * cerrarla y cortar la musica) si se abrio desde el lanzador, no desde la notificacion.
     */
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = handleBack()
    }

    private val pageEvents = object : PlayerPageEvents {
        override fun onBridgeMessage(message: BridgeMessage) = handleBridgeMessage(message)

        override fun onHistoryChanged(url: String) {
            shellViewModel.onHistoryChanged(url, player?.historyIndex ?: 0)
        }

        override fun onPageVisible() = shellViewModel.onPageVisible()

        override fun onExternalLink(url: String) = openExternal(url)

        override fun onRenderProcessGone(webView: WebView) {
            shellViewModel.onPageReset()
            // La pagina nueva empieza sin cancion: la notificacion no debe seguir en "sonando".
            coordinator.updateMetadata(PlaybackMetadata())
            player?.recreate(webView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        if (!PlayerWebViewFactory.isSupported()) {
            setContent { AdBlockerTheme(darkTheme = true) { WebViewOutdatedScreen() } }
            return
        }

        val evaluateDomain = ServiceLocator.evaluateDomainUseCase(applicationContext)
        val controller = PlayerWebController(
            context = this,
            filterPlayer = filterPlayer,
            evaluateDomain = evaluateDomain,
            events = pageEvents,
            isPlaying = { coordinator.metadata.value.isPlaying },
        )
        player = controller
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Las listas se cargan antes que la pagina: asi sus primeras peticiones ya se filtran.
        lifecycleScope.launch {
            evaluateDomain.prepare()
            controller.start()
        }

        // Las ordenes de la notificacion se ejecutan en la pagina, que es quien realmente
        // reproduce. Se escuchan mientras la Activity exista, tambien en segundo plano.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch { coordinator.commands.collect(controller::execute) }
                launch { coordinator.metadata.collect { if (it.isPlaying) ensurePlaybackService() } }
            }
        }

        setContent {
            AdBlockerTheme(darkTheme = true) {
                val state by shellViewModel.state.collectAsStateWithLifecycle()
                YtmShell(
                    state = state,
                    webView = controller.webView,
                    onTabSelected = { tab ->
                        shellViewModel.onTabRequested(tab)
                        controller.navigate(tab)
                    },
                    onBlockerClick = { shellViewModel.setBlockerOpen(true) },
                )
                if (state.blockerOpen) {
                    val filterState by filterViewModel.uiState.collectAsStateWithLifecycle()
                    BlockerSheet(
                        state = filterState,
                        onDismiss = { shellViewModel.setBlockerOpen(false) },
                        onToggleDns = { enable -> if (enable) requestDnsStart() else filterViewModel.onFilterStopped() },
                        onOpenPrivateDnsSettings = ::openPrivateDnsSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede haber cambiado el DNS privado en los ajustes del sistema.
        if (filterViewModelLazy.isInitialized()) filterViewModel.onScreenResumed()
    }

    override fun onStop() {
        super.onStop()
        // Un inicio de sesion recien hecho no debe perderse si el sistema mata el proceso.
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        // Sin WebView no hay musica: el servicio y su notificacion sobran.
        if (playbackServiceStarted) {
            playbackServiceStarted = false
            PlaybackService.stop(applicationContext)
        }
        player?.destroy()
        player = null
        super.onDestroy()
    }

    private fun handleBridgeMessage(message: BridgeMessage) {
        when (message) {
            is BridgeMessage.PlayerPage -> shellViewModel.onPlayerPageChanged(message.open)
            is BridgeMessage.Search -> shellViewModel.onSearchChanged(message.open)
            is BridgeMessage.Playback -> coordinator.updateMetadata(message::applyTo)
            is BridgeMessage.Track -> coordinator.updateMetadata(message::applyTo)
            is BridgeMessage.ResponsePruned -> filterPlayer.onPlayerResponsePruned(message.removedSections)
        }
    }

    /** Solo con la app visible: desde segundo plano Android no deja arrancar el servicio. */
    private fun ensurePlaybackService() {
        if (playbackServiceStarted || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        playbackServiceStarted = true
        PlaybackService.start(applicationContext)
    }

    private fun handleBack() {
        val controller = player ?: return
        when (BackNavigator.decide(currentBackState(controller))) {
            BackAction.GO_BACK -> controller.goBack()
            BackAction.COLLAPSE_PLAYER -> controller.collapsePlayer()
            BackAction.CLOSE_SEARCH -> controller.closeSearch()
            BackAction.GO_HOME -> {
                shellViewModel.onTabRequested(YtmTab.HOME)
                controller.navigate(YtmTab.HOME)
            }
            BackAction.SEND_TO_BACKGROUND -> moveTaskToBack(true)
        }
    }

    private fun currentBackState(controller: PlayerWebController) =
        shellViewModel.backState(controller.canGoBack, controller.historyIndex)

    private fun requestDnsStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) vpnConsent.launch(consentIntent) else filterViewModel.onFilterStarted()
    }

    /** Los enlaces que no son de YouTube Music se abren en el navegador, sin tocar el WebView. */
    private fun openExternal(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        runCatching { startActivity(intent) }
    }

    /**
     * `ACTION_PRIVATE_DNS_SETTINGS` no es constante publica y no existe en todas las capas
     * de fabricante: si falla se cae a los ajustes de red.
     */
    private fun openPrivateDnsSettings() {
        val candidates = listOf(
            Intent("android.settings.PRIVATE_DNS_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        candidates.firstOrNull { intent ->
            runCatching { startActivity(intent); true }.getOrDefault(false)
        }
    }
}
