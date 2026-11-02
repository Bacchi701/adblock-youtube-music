package com.adblocker.ytmusic.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigatorTest {

    private val root = BackState(
        playerPageOpen = false,
        canGoBack = false,
        historyIndex = 0,
        tabRootIndex = 0,
        selectedTab = YtmTab.HOME,
    )

    @Test
    fun `con la cancion abierta atras la cierra`() {
        val state = root.copy(playerPageOpen = true, canGoBack = true, historyIndex = 1)
        assertEquals(BackAction.GO_BACK, BackNavigator.decide(state))
    }

    @Test
    fun `cancion abierta sin historial se pliega a mano`() {
        val state = root.copy(playerPageOpen = true)
        assertEquals(BackAction.COLLAPSE_PLAYER, BackNavigator.decide(state))
    }

    @Test
    fun `la cancion abierta gana a la busqueda abierta`() {
        val state = root.copy(playerPageOpen = true, searchOpen = true, canGoBack = true, historyIndex = 2)
        assertEquals(BackAction.GO_BACK, BackNavigator.decide(state))
    }

    @Test
    fun `con la busqueda abierta atras la cierra`() {
        val state = root.copy(searchOpen = true, canGoBack = true, historyIndex = 3, tabRootIndex = 1)
        assertEquals(BackAction.CLOSE_SEARCH, BackNavigator.decide(state))
    }

    @Test
    fun `dentro de una seccion atras recorre su historial`() {
        val state = root.copy(canGoBack = true, historyIndex = 4, tabRootIndex = 2, selectedTab = YtmTab.LIBRARY)
        assertEquals(BackAction.GO_BACK, BackNavigator.decide(state))
    }

    @Test
    fun `en la raiz de otra seccion atras vuelve a Principal`() {
        val state = root.copy(canGoBack = true, historyIndex = 2, tabRootIndex = 2, selectedTab = YtmTab.EXPLORE)
        assertEquals(BackAction.GO_HOME, BackNavigator.decide(state))
    }

    @Test
    fun `en Principal sin historial propio la app va al fondo`() {
        assertEquals(BackAction.SEND_TO_BACKGROUND, BackNavigator.decide(root))
        // Aunque el WebView tenga historial de otras secciones, la raiz de Principal es la salida.
        val visitedOthers = root.copy(canGoBack = true, historyIndex = 5, tabRootIndex = 5)
        assertEquals(BackAction.SEND_TO_BACKGROUND, BackNavigator.decide(visitedOthers))
    }
}
