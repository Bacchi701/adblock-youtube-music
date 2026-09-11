package com.adblocker.ytmusic.ui.player

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlayerScreen(
    statsFlow: StateFlow<PlayerFilterStats>,
    onBack: () -> Unit,
    createWebView: (Context) -> WebView,
    modifier: Modifier = Modifier,
) {
    val stats by statsFlow.collectAsStateWithLifecycle()

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("Atras") }
                Text(
                    text = "${stats.adSectionsRemoved} bloques de anuncios · " +
                        "${stats.requestsBlocked} peticiones",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            AndroidView(
                factory = createWebView,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
