package com.adblocker.ytmusic.ui

import com.adblocker.ytmusic.domain.model.BlocklistRules
import com.adblocker.ytmusic.domain.model.BlocklistSource
import com.adblocker.ytmusic.domain.model.FilterStats
import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import com.adblocker.ytmusic.domain.repository.BlocklistRepository
import com.adblocker.ytmusic.domain.repository.PrivateDnsMonitor
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FilterViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepository : BlocklistRepository {
        override suspend fun loadRules() = BlocklistRules(
            blocked = setOf("doubleclick.net", "ads.example.com"),
            allowed = setOf("googlevideo.com"),
        )

        override suspend fun sources() = listOf(
            BlocklistSource("youtube_music_ads.txt", "youtube music ads", 1),
            BlocklistSource("trackers.txt", "trackers", 1),
        )
    }

    private class FakeController : FilterController {
        private val _isRunning = MutableStateFlow(false)
        private val _stats = MutableStateFlow(FilterStats())

        var startCalls = 0
            private set
        var stopCalls = 0
            private set

        override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
        override val stats: StateFlow<FilterStats> = _stats.asStateFlow()

        override fun start() {
            startCalls++
            _isRunning.value = true
        }

        override fun stop() {
            stopCalls++
            _isRunning.value = false
        }

        fun emitStats(stats: FilterStats) {
            _stats.value = stats
        }
    }

    private class FakePrivateDnsMonitor : PrivateDnsMonitor {
        private val _status = MutableStateFlow<PrivateDnsStatus>(PrivateDnsStatus.Off)

        var refreshCalls = 0
            private set

        override val status: StateFlow<PrivateDnsStatus> = _status.asStateFlow()

        override fun refresh() {
            refreshCalls++
        }

        fun emit(status: PrivateDnsStatus) {
            _status.value = status
        }
    }

    private val playerFilter = FilterPlayerResponseUseCase()
    private val controller = FakeController()
    private val privateDns = FakePrivateDnsMonitor()

    private fun viewModel() =
        FilterViewModel(FakeRepository(), controller, playerFilter, privateDns)

    @Test
    fun `carga las listas locales al iniciarse`() = runTest {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoadingRules)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoadingRules)
        assertEquals(2, state.sources.size)
        assertEquals(2, state.totalRules)
    }

    @Test
    fun `refleja el estado y las estadisticas del filtro DNS`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRunning)

        viewModel.onFilterStarted()
        controller.emitStats(FilterStats(queriesInspected = 200, queriesBlocked = 50))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRunning)
        assertEquals(200, state.dnsStats.queriesInspected)
        assertEquals(25, state.blockedPercent)
        assertEquals(1, controller.startCalls)
    }

    @Test
    fun `detener delega en el controlador`() = runTest {
        val viewModel = viewModel()
        viewModel.onFilterStarted()
        advanceUntilIdle()

        viewModel.onFilterStopped()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals(1, controller.stopCalls)
    }

    @Test
    fun `muestra la actividad del reproductor aunque el VPN este parado`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.hasPlayerActivity)

        playerFilter(
            "https://music.youtube.com/youtubei/v1/player",
            """{"adSlots":[1],"streamingData":{"itag":251}}""",
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRunning)
        assertTrue(state.hasPlayerActivity)
        assertEquals(1, state.playerStats.responsesInspected)
        assertEquals(1, state.playerStats.adSectionsRemoved)
    }

    @Test
    fun `el ratio de bloqueo es cero sin trafico`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.blockedPercent)
    }

    @Test
    fun `el DNS privado estricto marca el filtro como sin efecto`() = runTest {
        val viewModel = viewModel()
        viewModel.onFilterStarted()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDnsFilterEffective)

        privateDns.emit(PrivateDnsStatus.Strict("dns.adguard.com"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isRunning) // el tunel sigue levantado
        assertTrue(state.dnsFilterBypassed)
        assertFalse(state.isDnsFilterEffective)
        assertEquals(PrivateDnsStatus.Strict("dns.adguard.com"), state.privateDns)
    }

    @Test
    fun `el modo automatico no invalida el filtro`() = runTest {
        val viewModel = viewModel()
        viewModel.onFilterStarted()
        privateDns.emit(PrivateDnsStatus.Opportunistic)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.dnsFilterBypassed)
        assertTrue(state.isDnsFilterEffective)
    }

    @Test
    fun `con el filtro parado nunca se considera efectivo`() = runTest {
        val viewModel = viewModel()
        privateDns.emit(PrivateDnsStatus.Off)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDnsFilterEffective)
    }

    @Test
    fun `volver a la pantalla revisa el modo de DNS privado`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onScreenResumed()

        assertEquals(1, privateDns.refreshCalls)
    }
}
