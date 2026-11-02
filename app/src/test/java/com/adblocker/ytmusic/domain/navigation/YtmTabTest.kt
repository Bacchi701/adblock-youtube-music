package com.adblocker.ytmusic.domain.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YtmTabTest {

    @Test
    fun `las URLs de primer nivel marcan su pestaña`() {
        assertEquals(YtmTab.HOME, YtmTab.fromUrl("https://music.youtube.com/"))
        assertEquals(YtmTab.HOME, YtmTab.fromUrl("https://music.youtube.com"))
        assertEquals(YtmTab.EXPLORE, YtmTab.fromUrl("https://music.youtube.com/explore"))
        assertEquals(YtmTab.LIBRARY, YtmTab.fromUrl("https://music.youtube.com/library"))
        assertEquals(YtmTab.LIBRARY, YtmTab.fromUrl("https://music.youtube.com/library/playlists"))
        assertEquals(YtmTab.SEARCH, YtmTab.fromUrl("https://music.youtube.com/search?q=ed+sheeran"))
    }

    @Test
    fun `las paginas interiores conservan la pestaña actual`() {
        assertNull(YtmTab.fromUrl("https://music.youtube.com/watch?v=abc&list=RDAMVMabc"))
        assertNull(YtmTab.fromUrl("https://music.youtube.com/playlist?list=PL123"))
        assertNull(YtmTab.fromUrl("https://music.youtube.com/browse/MPREb_x"))
        assertNull(YtmTab.fromUrl("https://music.youtube.com/new_releases"))
        assertNull(YtmTab.fromUrl("https://music.youtube.com/libraryx"))
    }

    @Test
    fun `otros hosts no cambian la pestaña`() {
        assertNull(YtmTab.fromUrl("https://accounts.google.com/"))
        assertNull(YtmTab.fromUrl("https://www.youtube.com/explore"))
        assertNull(YtmTab.fromUrl("https://music.youtube.com.evil.com/explore"))
    }

    @Test
    fun `cada pestaña navegable tiene el browseId de la web`() {
        assertEquals("FEmusic_home", YtmTab.HOME.browseId)
        assertEquals("FEmusic_explore", YtmTab.EXPLORE.browseId)
        assertEquals("FEmusic_library_landing", YtmTab.LIBRARY.browseId)
        assertNull(YtmTab.SEARCH.browseId)
        // La ruta de respaldo vuelve a su propia pestaña.
        for (tab in YtmTab.entries) {
            assertEquals(tab, YtmTab.fromUrl("https://music.youtube.com${tab.fallbackPath}"))
        }
    }
}
