package com.adblocker.ytmusic.ui.player

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.adblocker.ytmusic.data.web.PlayerWebViewFactory
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.player.PlayerCommand
import com.adblocker.ytmusic.service.PlaybackService
import com.adblocker.ytmusic.ui.theme.AdBlockerTheme
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.launch

/** Reproductor propio: es el unico contexto donde se puede sanear el `playerResponse`. */
class PlayerActivity : ComponentActivity() {

    private var webView: WebView? = null

    private val coordinator = ServiceLocator.playbackCoordinator()

    /**
     * El servicio en primer plano se arranca la primera vez que suena algo y se mantiene
     * mientras la pantalla exista. Detenerlo en cada pausa obligaria a rearrancarlo al
     * cambiar de cancion, y desde segundo plano el sistema lo rechazaria.
     */
    private val playbackServiceStarted = AtomicBoolean(false)

    /**
     * Sin este permiso la notificacion del servicio queda oculta: la reproduccion funciona,
     * pero el usuario no ve que hay algo sonando ni tiene los controles a mano.
     */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // El servicio ya esta en primer plano, pero su notificacion se descarto por falta de
        // permiso: hay que volver a publicarla. Si se rechaza, la musica suena igual.
        if (granted && playbackServiceStarted.get()) {
            PlaybackService.start(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this) {
            val web = webView
            if (web != null && web.canGoBack()) web.goBack() else finish()
        }

        val filterPlayer = ServiceLocator.filterPlayerResponseUseCase()
        val evaluateDomain = ServiceLocator.evaluateDomainUseCase(applicationContext)
        lifecycleScope.launch { evaluateDomain.prepare() }

        // Las ordenes de la pantalla de bloqueo se ejecutan en la pagina, que es quien
        // realmente reproduce. Se escuchan mientras la actividad exista, tambien parada.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                coordinator.commands.collect(::execute)
            }
        }

        setContent {
            AdBlockerTheme {
                PlayerScreen(
                    statsFlow = filterPlayer.stats,
                    onBack = { if (webView?.canGoBack() == true) webView?.goBack() else finish() },
                    createWebView = { context ->
                        PlayerWebViewFactory.create(
                            context = context,
                            filterPlayer = filterPlayer,
                            evaluateDomain = evaluateDomain,
                            onPlaybackStateChanged = ::onPlaybackStateChanged,
                            onMetadataChanged = coordinator::updateMetadata,
                        ).also { created ->
                            webView = created
                            created.loadUrl(PlayerWebViewFactory.START_URL)
                        }
                    },
                )
            }
        }
    }

    private fun execute(command: PlayerCommand) {
        val script = when (command) {
            PlayerCommand.Play -> "window.__adBlockPlayerControl.play()"
            PlayerCommand.Pause, PlayerCommand.Stop -> "window.__adBlockPlayerControl.pause()"
            PlayerCommand.Next -> "window.__adBlockPlayerControl.next()"
            PlayerCommand.Previous -> "window.__adBlockPlayerControl.previous()"
            is PlayerCommand.SeekTo ->
                "window.__adBlockPlayerControl.seek(${command.positionMs / 1000.0})"
        }
        // El hook puede no estar inyectado todavia si la pagina sigue cargando.
        webView?.evaluateJavascript("try{$script}catch(e){}", null)
    }

    /** Llega desde el hilo JavaBridge del WebView, de ahi el salto al hilo principal. */
    private fun onPlaybackStateChanged(playing: Boolean) {
        if (!playing) return
        if (!playbackServiceStarted.compareAndSet(false, true)) return

        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            PlaybackService.start(applicationContext)
        }
    }

    /**
     * No se llama a `WebView.onPause()` ni a `pauseTimers()`: eso es justo lo que cortaba la
     * musica al bloquear el movil o cambiar de app.
     */
    override fun onDestroy() {
        if (playbackServiceStarted.getAndSet(false)) {
            PlaybackService.stop(applicationContext)
        }
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
