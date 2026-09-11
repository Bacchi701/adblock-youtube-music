package com.adblocker.ytmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.adblocker.ytmusic.ui.theme.AdBlockerTheme

@Composable
fun FilterScreen(
    state: FilterUiState,
    onToggle: (Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenPrivateDnsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { StatusCard(state) }

            if (state.dnsFilterBypassed) {
                item { PrivateDnsWarningCard(state, onOpenPrivateDnsSettings) }
            }

            item {
                Button(
                    onClick = { onToggle(!state.isRunning) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (state.isRunning) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(if (state.isRunning) "Detener filtrado DNS" else "Iniciar filtrado DNS")
                }
            }

            item { PlayerCard(state, onOpenPlayer) }

            item { DnsStatsCard(state) }

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
                    text = "Procesamiento 100% local: ninguna consulta ni estadistica sale del " +
                        "dispositivo. Las listas viajan dentro del APK.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatusCard(state: FilterUiState) {
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(state)
                Text(
                    text = when {
                        !state.isRunning -> "Filtro DNS detenido"
                        state.dnsFilterBypassed -> "Filtro DNS sin efecto"
                        else -> "Filtro DNS activo"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Text(
                text = when {
                    !state.isRunning ->
                        "El reproductor integrado sigue filtrando anuncios aunque el VPN este parado."
                    state.dnsFilterBypassed ->
                        "El tunel esta levantado, pero el DNS privado estricto se lleva las " +
                            "consultas por fuera."
                    else -> "${state.totalRules} reglas aplicandose a todo el dispositivo."
                },
                style = MaterialTheme.typography.bodyMedium,
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
                text = "El sistema envia las consultas cifradas por TLS a $hostname, fuera del " +
                    "tunel. El filtro DNS no puede verlas ni bloquearlas, y forzar el corte " +
                    "dejaria el dispositivo sin resolucion: el modo estricto no admite reserva.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "Cambia el DNS privado a \"Automatico\" o \"Desactivado\" para que el " +
                    "filtro vuelva a aplicarse. El reproductor integrado no se ve afectado.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir ajustes de DNS privado")
            }
        }
    }
}

@Composable
private fun StatusDot(state: FilterUiState) {
    val color = when {
        state.isDnsFilterEffective -> Color(0xFF4CAF50)
        state.isRunning -> Color(0xFFFFA000) // activo pero sin efecto
        else -> MaterialTheme.colorScheme.outline
    }
    Column(
        modifier = Modifier
            .size(12.dp)
            .background(color, CircleShape),
    ) {}
}

@Composable
private fun PlayerCard(state: FilterUiState, onOpenPlayer: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Reproductor de YouTube Music",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Unico lugar donde se pueden quitar los anuncios insertados en el propio " +
                    "stream: la respuesta de youtubei/v1/player se sanea antes de reproducir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.hasPlayerActivity) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatItem("Bloques de anuncios", state.playerStats.adSectionsRemoved.toString())
                    StatItem("Peticiones cortadas", state.playerStats.requestsBlocked.toString())
                    StatItem("Respuestas revisadas", state.playerStats.responsesInspected.toString())
                }
            }

            OutlinedButton(onClick = onOpenPlayer, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir reproductor filtrado")
            }
        }
    }
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
private fun FilterScreenPreview() {
    AdBlockerTheme {
        FilterScreen(
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
            onToggle = {},
            onOpenPlayer = {},
            onOpenPrivateDnsSettings = {},
        )
    }
}
