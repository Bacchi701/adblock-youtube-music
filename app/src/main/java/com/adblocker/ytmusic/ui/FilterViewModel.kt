package com.adblocker.ytmusic.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import com.adblocker.ytmusic.domain.repository.PrivateDnsMonitor
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import com.adblocker.ytmusic.service.VpnFilterController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado de la pantalla principal: une el filtro DNS ([FilterController]) y el filtro del
 * reproductor integrado ([FilterPlayerResponseUseCase]) en un unico [FilterUiState].
 */
class FilterViewModel(
    private val repository: BlocklistRepository,
    private val controller: FilterController,
    private val playerFilter: FilterPlayerResponseUseCase,
    private val privateDnsMonitor: PrivateDnsMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilterUiState())
    val uiState: StateFlow<FilterUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = repository.sources()
            val rules = repository.loadRules()
            _uiState.update {
                it.copy(sources = sources, totalRules = rules.blocked.size, isLoadingRules = false)
            }
        }
        viewModelScope.launch {
            combine(
                controller.isRunning,
                controller.stats,
                playerFilter.stats,
                privateDnsMonitor.status,
            ) { running, dnsStats, playerStats, privateDns ->
                FilterSnapshot(running, dnsStats, playerStats, privateDns)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        isRunning = snapshot.isRunning,
                        dnsStats = snapshot.dnsStats,
                        playerStats = snapshot.playerStats,
                        privateDns = snapshot.privateDns,
                    )
                }
            }
        }
    }

    private data class FilterSnapshot(
        val isRunning: Boolean,
        val dnsStats: FilterStats,
        val playerStats: PlayerFilterStats,
        val privateDns: PrivateDnsStatus,
    )

    /** Tras volver de los ajustes del sistema el modo de DNS privado puede haber cambiado. */
    fun onScreenResumed() = privateDnsMonitor.refresh()

    /** Se llama cuando el usuario ya ha concedido el permiso de VPN del sistema. */
    fun onFilterStarted() = controller.start()

    fun onFilterStopped() = controller.stop()

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = FilterViewModel(
            repository = ServiceLocator.blocklistRepository(appContext),
            controller = VpnFilterController(appContext),
            playerFilter = ServiceLocator.filterPlayerResponseUseCase(),
            privateDnsMonitor = ServiceLocator.privateDnsMonitor(appContext),
        ) as T
    }
}
