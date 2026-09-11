package com.adblocker.ytmusic.ui

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adblocker.ytmusic.ui.player.PlayerActivity
import com.adblocker.ytmusic.ui.theme.AdBlockerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FilterViewModel by viewModels { FilterViewModel.Factory(this) }

    /** Dialogo del sistema que autoriza el VpnService; sin este consentimiento no se filtra. */
    private val vpnConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onFilterStarted()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* la notificacion es informativa: el filtro funciona igual si se rechaza */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdBlockerTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                FilterScreen(
                    state = state,
                    onToggle = { enable -> if (enable) requestStart() else viewModel.onFilterStopped() },
                    onOpenPlayer = { startActivity(Intent(this, PlayerActivity::class.java)) },
                    onOpenPrivateDnsSettings = ::openPrivateDnsSettings,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // El usuario puede haber cambiado el DNS privado en los ajustes del sistema.
        viewModel.onScreenResumed()
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

    private fun requestStart() {
        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        val consentIntent = VpnService.prepare(this)
        if (consentIntent != null) vpnConsent.launch(consentIntent) else viewModel.onFilterStarted()
    }
}
