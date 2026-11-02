package com.adblocker.ytmusic.ui.shell

import com.adblocker.ytmusic.domain.navigation.BackAction
import com.adblocker.ytmusic.domain.navigation.BackNavigator
import com.adblocker.ytmusic.domain.navigation.YtmTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellViewModelTest {

    private val viewModel = ShellViewModel()

    private fun back(canGoBack: Boolean, index: Int) = BackNavigator.decide(viewModel.backState(canGoBack, index))

    @Test
    fun `la pestaña se marca al tocarla y la URL la confirma`() {
        viewModel.onTabRequested(YtmTab.EXPLORE)
        assertEquals(YtmTab.EXPLORE, viewModel.state.value.selectedTab)

        viewModel.onHistoryChanged("https://music.youtube.com/library", 3)
        assertEquals(YtmTab.LIBRARY, viewModel.state.value.selectedTab)
    }

    @Test
    fun `una pagina interior no cambia la pestaña`() {
        viewModel.onHistoryChanged("https://music.youtube.com/explore", 1)
        viewModel.onHistoryChanged("https://music.youtube.com/playlist?list=PL1", 2)

        assertEquals(YtmTab.EXPLORE, viewModel.state.value.selectedTab)
    }

    @Test
    fun `Buscar se marca mientras el cuadro esta abierto`() {
        viewModel.onTabRequested(YtmTab.SEARCH)
        assertEquals(YtmTab.HOME, viewModel.state.value.highlightedTab)

        viewModel.onSearchChanged(true)
        assertEquals(YtmTab.SEARCH, viewModel.state.value.highlightedTab)
        assertEquals(YtmTab.HOME, viewModel.state.value.selectedTab)

        viewModel.onSearchChanged(false)
        assertEquals(YtmTab.HOME, viewModel.state.value.highlightedTab)
    }

    @Test
    fun `recorrido de atras como en la app oficial`() {
        // Principal -> Biblioteca -> una lista dentro de Biblioteca.
        viewModel.onHistoryChanged("https://music.youtube.com/", 0)
        viewModel.onTabRequested(YtmTab.LIBRARY)
        viewModel.onHistoryChanged("https://music.youtube.com/library", 1)
        viewModel.onHistoryChanged("https://music.youtube.com/playlist?list=PL1", 2)

        assertEquals(BackAction.GO_BACK, back(canGoBack = true, index = 2))

        viewModel.onHistoryChanged("https://music.youtube.com/library", 1)
        assertEquals(BackAction.GO_HOME, back(canGoBack = true, index = 1))

        viewModel.onTabRequested(YtmTab.HOME)
        viewModel.onHistoryChanged("https://music.youtube.com/", 2)
        assertEquals(BackAction.SEND_TO_BACKGROUND, back(canGoBack = true, index = 2))
    }

    @Test
    fun `la cancion abierta esconde la barra y atras la cierra`() {
        viewModel.onPlayerPageChanged(true)
        assertTrue(viewModel.state.value.playerPageOpen)
        assertEquals(BackAction.GO_BACK, back(canGoBack = true, index = 1))

        viewModel.onPlayerPageChanged(false)
        assertFalse(viewModel.state.value.playerPageOpen)
    }

    @Test
    fun `un WebView nuevo empieza de cero`() {
        viewModel.onTabRequested(YtmTab.LIBRARY)
        viewModel.onHistoryChanged("https://music.youtube.com/library", 4)
        viewModel.onPlayerPageChanged(true)
        viewModel.onSearchChanged(true)
        viewModel.onPageVisible()

        viewModel.onPageReset()

        val state = viewModel.state.value
        assertEquals(ShellUiState(), state)
        assertEquals(BackAction.SEND_TO_BACKGROUND, back(canGoBack = false, index = 0))
    }
}
