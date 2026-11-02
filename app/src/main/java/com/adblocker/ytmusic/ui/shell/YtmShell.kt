package com.adblocker.ytmusic.ui.shell

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adblocker.ytmusic.R
import com.adblocker.ytmusic.domain.navigation.YtmTab

/** Fondo de la web de YouTube Music: el mismo detras de la barra de estado y de navegacion. */
val YtmBackground = Color(0xFF030303)

/**
 * Pantalla unica: YouTube Music a pantalla completa y la barra nativa debajo, con el
 * mini-reproductor de la web justo encima de ella, como en la app oficial.
 *
 * El WebView no se redimensiona en cada fotograma (la pagina tiene miles de nodos): la barra
 * aparece y desaparece de golpe y el teclado se aplica con su tamaño final
 * (`imeAnimationTarget`), asi la pagina recalcula su layout una sola vez.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YtmShell(
    state: ShellUiState,
    webView: WebView?,
    onTabSelected: (YtmTab) -> Unit,
    onBlockerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardOpen = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    val showBar = !state.playerPageOpen && !keyboardOpen

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtmBackground)
            .windowInsetsPadding(
                WindowInsets.statusBars
                    .union(WindowInsets.navigationBars)
                    .union(WindowInsets.imeAnimationTarget),
            ),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (webView != null) {
                // Un WebView nuevo (renderer recreado) exige un AndroidView nuevo.
                key(webView) {
                    AndroidView(
                        factory = {
                            // Si venia de una composicion anterior, hay que soltarlo antes.
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (!state.pageVisible) StartupOverlay()
        }
        if (showBar) {
            YtmBottomBar(
                selectedTab = state.highlightedTab,
                blockerOpen = state.blockerOpen,
                onTabSelected = onTabSelected,
                onBlockerClick = onBlockerClick,
            )
        }
    }
}

/** Mientras la web carga: el mismo fondo y un indicador discreto, sin destello blanco. */
@Composable
private fun StartupOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YtmBackground),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            color = Color.White,
            strokeWidth = 2.dp,
        )
    }
}

/** El WebView del sistema es demasiado antiguo para aislar el hook: no se carga nada. */
@Composable
fun WebViewOutdatedScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(YtmBackground)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.webview_outdated_title),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.webview_outdated_text),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}
