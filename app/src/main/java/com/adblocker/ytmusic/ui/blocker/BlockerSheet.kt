package com.adblocker.ytmusic.ui.blocker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import com.adblocker.ytmusic.ui.FilterUiState
import com.adblocker.ytmusic.ui.theme.AdBlockerTheme

/**
 * Hoja del boton "Bloqueador": lo que hace el filtro del reproductor (siempre activo) y el
 * filtro DNS para todo el telefono, que es opcional y empieza apagado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockerSheet(
    state: FilterUiState,
    onDismiss: () -> Unit,
    onToggleDns: (Boolean) -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BlockerContent(state, onToggleDns, onOpenPrivateDnsSettings)
    }
}

@Composable
private fun BlockerContent(
    state: FilterUiState,
    onToggleDns: (Boolean) -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PlayerCard(state) }

        item { DnsCard(state, onToggleDns) }

        if (state.dnsFilterBypassed) {
            item { PrivateDnsWarningCard(state, onOpenPrivateDnsSettings) }
        }

        if (state.isRunning) {
            item { DnsStatsCard(state) }
        }

        item {
            Text(
                text = "Listas locales",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.isLoadingRules) {
            item { CircularProgressIndicator(Modifier.size(28.dp)) }
        }

        items(state.sources, key = BlocklistSource::id) { source ->
            SourceRow(source)
        }

        item {
            Text(
                text = "Procesamiento 100 % local: ninguna consulta ni estadística sale del " +
                    "dispositivo. Las listas viajan dentro del APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerCard(state: FilterUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(Color(0xFF4CAF50))
                Text(
                    text = "Anuncios de YouTube Music bloqueados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "Siempre activo: los anuncios se quitan de la respuesta del reproductor " +
                    "antes de que suene la canción.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Anuncios quitados", state.playerStats.adSectionsRemoved.toString())
                StatItem("Peticiones cortadas", state.playerStats.requestsBlocked.toString())
                StatItem("Canciones", state.playerStats.responsesInspected.toString())
            }
        }
    }
}

@Composable
private fun DnsCard(state: FilterUiState, onToggleDns: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isDnsFilterEffective) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(dnsDotColor(state))
                Text(
                    text = "Bloqueo en todo el teléfono",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.isRunning, onCheckedChange = onToggleDns)
            }
            Text(
                text = when {
                    !state.isRunning ->
                        "Opcional. Bloquea rastreadores también en otras apps con una VPN local " +
                            "(el tráfico no sale del teléfono). No hace falta para YouTube Music."
                    state.dnsFilterBypassed ->
                        "El túnel está levantado, pero el DNS privado estricto se lleva las " +
                            "consultas por fuera."
                    else -> "${state.totalRules} reglas aplicándose a todo el dispositivo."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrivateDnsWarningCard(state: FilterUiState, onOpenSettings: () -> Unit) {
    val hostname = (state.privateDns as? PrivateDnsStatus.Strict)?.hostname.orEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "DNS privado estricto activo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "El sistema envía las consultas cifradas por TLS a $hostname, fuera del " +
                    "túnel. El filtro DNS no puede verlas ni bloquearlas, y forzar el corte " +
                    "dejaría el dispositivo sin resolución: el modo estricto no admite reserva.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Cambia el DNS privado a \"Automático\" o \"Desactivado\" para que el " +
                    "filtro vuelva a aplicarse. YouTube Music no se ve afectado.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir ajustes de DNS privado")
            }
        }
    }
}

@Composable
private fun dnsDotColor(state: FilterUiState): Color = when {
    state.isDnsFilterEffective -> Color(0xFF4CAF50)
    state.isRunning -> Color(0xFFFFA000) // activo pero sin efecto
    else -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusDot(color: Color) {
    Column(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape),
    ) {}
}

@Composable
private fun DnsStatsCard(state: FilterUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Filtro DNS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatItem("Inspeccionadas", state.dnsStats.queriesInspected.toString())
                StatItem("Bloqueadas", state.dnsStats.queriesBlocked.toString())
                StatItem("Ratio", "${state.blockedPercent}%")
                StatItem("Reglas", state.totalRules.toString())
            }
        }
    }
}

@Composable
private fun SourceRow(source: BlocklistSource) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(source.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${source.ruleCount} reglas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun BlockerContentPreview() {
    AdBlockerTheme(darkTheme = true) {
        BlockerContent(
            state = FilterUiState(
                isRunning = true,
                isLoadingRules = false,
                dnsStats = FilterStats(queriesInspected = 420, queriesBlocked = 63),
                playerStats = PlayerFilterStats(
                    responsesInspected = 12,
                    adSectionsRemoved = 34,
                    requestsBlocked = 8,
                ),
                sources = listOf(
                    BlocklistSource("trackers.txt", "trackers", 27),
                    BlocklistSource("youtube_music_ads.txt", "youtube music ads", 18),
                ),
                totalRules = 45,
                privateDns = PrivateDnsStatus.Strict("dns.example.com"),
            ),
            onToggleDns = {},
            onOpenPrivateDnsSettings = {},
        )
    }
}
