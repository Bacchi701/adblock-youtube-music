package com.adblocker.ytmusic.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TabHistoryTest {

    private val history = TabHistory()

    @Test
    fun `la raiz se fija cuando la web confirma la pestaña pedida`() {
        history.onTabRequested(YtmTab.LIBRARY)
        history.onHistoryChanged(null, 3) // una pagina intermedia no cuenta
        assertEquals(0, history.rootIndex)

        history.onHistoryChanged(YtmTab.LIBRARY, 4)
        assertEquals(4, history.rootIndex)
    }

    @Test
    fun `navegar dentro de la seccion no mueve la raiz`() {
        history.onTabRequested(YtmTab.EXPLORE)
        history.onHistoryChanged(YtmTab.EXPLORE, 2)
        history.onHistoryChanged(null, 3)
        history.onHistoryChanged(null, 4)

        assertEquals(2, history.rootIndex)
    }

    @Test
    fun `una pestaña que la web nunca confirmo no fija nada`() {
        history.onTabRequested(YtmTab.EXPLORE)
        history.onHistoryChanged(YtmTab.HOME, 6)

        assertEquals(0, history.rootIndex)
    }

    @Test
    fun `retroceder antes de la raiz la arrastra`() {
        history.onTabRequested(YtmTab.LIBRARY)
        history.onHistoryChanged(YtmTab.LIBRARY, 5)
        history.onHistoryChanged(YtmTab.HOME, 3)

        assertEquals(3, history.rootIndex)
    }

    @Test
    fun `reiniciar vuelve al principio`() {
        history.onTabRequested(YtmTab.LIBRARY)
        history.onHistoryChanged(YtmTab.LIBRARY, 5)
        history.reset()
        history.onHistoryChanged(YtmTab.LIBRARY, 7)

        assertEquals(0, history.rootIndex)
    }
}
