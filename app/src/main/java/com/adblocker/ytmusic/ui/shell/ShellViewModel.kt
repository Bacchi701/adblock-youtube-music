package com.adblocker.ytmusic.ui.shell

import androidx.lifecycle.ViewModel
import com.adblocker.ytmusic.domain.navigation.BackState
import com.adblocker.ytmusic.domain.navigation.TabHistory
import com.adblocker.ytmusic.domain.navigation.YtmTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ShellUiState(
    val selectedTab: YtmTab = YtmTab.HOME,
    /** La pagina de la cancion ocupa la pantalla: la barra se esconde, como en la app oficial. */
    val playerPageOpen: Boolean = false,
    /** La web ya pinto algo: se retira la capa de arranque. */
    val pageVisible: Boolean = false,
    val blockerOpen: Boolean = false,
    /** Cuadro de busqueda de la web abierto. */
    val searchOpen: Boolean = false,
) {
    /** Con la busqueda abierta se marca Buscar, aunque la pagina de debajo sea otra. */
    val highlightedTab: YtmTab get() = if (searchOpen) YtmTab.SEARCH else selectedTab
}

/**
 * Estado de la pantalla unica. No conoce el WebView: recibe lo que la pagina cuenta (URL,
 * pagina de la cancion) y lo que pide el usuario (pestañas, hoja del bloqueador).
 */
class ShellViewModel : ViewModel() {

    private val _state = MutableStateFlow(ShellUiState())
    val state: StateFlow<ShellUiState> = _state.asStateFlow()

    private val tabHistory = TabHistory()

    /**
     * La pestaña se marca al instante para que el toque se note; la URL la confirma despues.
     * Buscar no navega (abre el cuadro de busqueda), asi que solo se marca cuando hay resultados.
     */
    fun onTabRequested(tab: YtmTab) {
        if (tab == YtmTab.SEARCH) return
        tabHistory.onTabRequested(tab)
        _state.update { it.copy(selectedTab = tab) }
    }

    fun onHistoryChanged(url: String, historyIndex: Int) {
        val tab = YtmTab.fromUrl(url)
        tabHistory.onHistoryChanged(tab, historyIndex)
        if (tab != null) _state.update { it.copy(selectedTab = tab) }
    }

    fun onPlayerPageChanged(open: Boolean) {
        _state.update { it.copy(playerPageOpen = open) }
    }

    fun onSearchChanged(open: Boolean) {
        _state.update { it.copy(searchOpen = open) }
    }

    fun onPageVisible() {
        _state.update { it.copy(pageVisible = true) }
    }

    /** El WebView se recreo (murio su renderer): la pagina empieza de cero. */
    fun onPageReset() {
        tabHistory.reset()
        _state.update {
            it.copy(selectedTab = YtmTab.HOME, playerPageOpen = false, pageVisible = false, searchOpen = false)
        }
    }

    fun setBlockerOpen(open: Boolean) {
        _state.update { it.copy(blockerOpen = open) }
    }

    fun backState(canGoBack: Boolean, historyIndex: Int): BackState {
        val current = _state.value
        return BackState(
            playerPageOpen = current.playerPageOpen,
            canGoBack = canGoBack,
            historyIndex = historyIndex,
            tabRootIndex = tabHistory.rootIndex,
            selectedTab = current.selectedTab,
            searchOpen = current.searchOpen,
        )
    }
}
