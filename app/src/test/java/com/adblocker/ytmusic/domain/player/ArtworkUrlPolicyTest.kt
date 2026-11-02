package com.adblocker.ytmusic.domain.player

import com.adblocker.ytmusic.domain.player.ArtworkUrlPolicy.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlPolicyTest {

    private val cdn = "https://yt3.googleusercontent.com/abc=w544-h544"

    @Test
    fun `solo admite https hacia los CDN de imagenes de Google`() {
        assertTrue(ArtworkUrlPolicy.isAllowed(cdn))
        assertTrue(ArtworkUrlPolicy.isAllowed("https://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertTrue(ArtworkUrlPolicy.isAllowed("https://lh3.googleusercontent.com/x"))
        assertTrue(ArtworkUrlPolicy.isAllowed("https://yt3.ggpht.com/x"))
    }

    @Test
    fun `rechaza cualquier otra cosa`() {
        assertFalse(ArtworkUrlPolicy.isAllowed("http://i.ytimg.com/vi/x/hqdefault.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("https://evil.com/x.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("https://ytimg.com.evil.com/x.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("https://i.ytimg.com@evil.com/x.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("https://i.ytimg.com:8080/x.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("file:///sdcard/x.jpg"))
        assertFalse(ArtworkUrlPolicy.isAllowed("data:image/png;base64,AAAA"))
        assertFalse(ArtworkUrlPolicy.isAllowed("https://i.ytimg.com/" + "x".repeat(3_000)))
    }

    @Test
    fun `elige la mas pequeña que cubre el tamaño de la notificacion`() {
        val candidates = listOf(
            Candidate("https://i.ytimg.com/60.jpg", "60x60"),
            Candidate("https://i.ytimg.com/544.jpg", "544x544"),
            Candidate("https://i.ytimg.com/1200.jpg", "1200x1200"),
            Candidate("https://i.ytimg.com/226.jpg", "226x226"),
        )
        assertEquals("https://i.ytimg.com/544.jpg", ArtworkUrlPolicy.pick(candidates))
    }

    @Test
    fun `si ninguna llega, la mayor`() {
        val candidates = listOf(
            Candidate("https://i.ytimg.com/60.jpg", "60x60"),
            Candidate("https://i.ytimg.com/226.jpg", "226x226"),
            Candidate("https://i.ytimg.com/any.jpg", "any"),
        )
        assertEquals("https://i.ytimg.com/226.jpg", ArtworkUrlPolicy.pick(candidates))
    }

    @Test
    fun `descarta las no permitidas antes de elegir`() {
        val candidates = listOf(
            Candidate("https://evil.com/big.jpg", "2000x2000"),
            Candidate("https://i.ytimg.com/small.jpg", "120x90"),
        )
        assertEquals("https://i.ytimg.com/small.jpg", ArtworkUrlPolicy.pick(candidates))
        assertNull(ArtworkUrlPolicy.pick(listOf(Candidate("https://evil.com/x.jpg", "600x600"))))
        assertNull(ArtworkUrlPolicy.pick(emptyList()))
    }

    @Test
    fun `entiende varios tamaños y medidas mal formadas`() {
        val candidates = listOf(
            Candidate("https://i.ytimg.com/multi.jpg", "64x64 600x600"),
            Candidate("https://i.ytimg.com/broken.jpg", "axb 12"),
        )
        assertEquals("https://i.ytimg.com/multi.jpg", ArtworkUrlPolicy.pick(candidates))
    }
}
