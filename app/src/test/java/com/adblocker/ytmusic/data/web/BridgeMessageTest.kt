package com.adblocker.ytmusic.data.web

import com.adblocker.ytmusic.domain.player.ArtworkUrlPolicy
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMessageTest {

    @Test
    fun `entiende cada tipo de aviso`() {
        assertEquals(BridgeMessage.PlayerPage(true), BridgeMessage.parse("""{"t":"page","open":true}"""))
        assertEquals(BridgeMessage.Search(false), BridgeMessage.parse("""{"t":"search","open":false}"""))
        assertEquals(BridgeMessage.ResponsePruned(4), BridgeMessage.parse("""{"t":"pruned","removed":4}"""))
        assertEquals(
            BridgeMessage.Playback(playing = true, buffering = false, positionSeconds = 12.5, durationSeconds = 226.0),
            BridgeMessage.parse("""{"t":"state","playing":true,"buffering":false,"position":12.5,"duration":226}"""),
        )
        assertEquals(
            BridgeMessage.Track(
                title = "Tu Boda",
                artist = "Oscar Maydon",
                album = "Tu Boda",
                artwork = listOf(ArtworkUrlPolicy.Candidate("https://yt3.googleusercontent.com/x", "544x544")),
            ),
            BridgeMessage.parse(
                """{"t":"meta","title":"Tu Boda","artist":"Oscar Maydon","album":"Tu Boda",
                   "artwork":[{"src":"https://yt3.googleusercontent.com/x","sizes":"544x544"}]}""",
            ),
        )
    }

    @Test
    fun `descarta tipos desconocidos y formas inesperadas`() {
        assertNull(BridgeMessage.parse(null))
        assertNull(BridgeMessage.parse("no es json"))
        assertNull(BridgeMessage.parse("""["page",true]"""))
        assertNull(BridgeMessage.parse("""{"t":"otro","open":true}"""))
        assertNull(BridgeMessage.parse("""{"open":true}"""))
        assertNull(BridgeMessage.parse("""{"t":"page","open":"si"}"""))
        assertNull(BridgeMessage.parse("""{"t":"state","position":3}"""))
        assertNull(BridgeMessage.parse("""{"t":5}"""))
    }

    @Test
    fun `los tiempos raros quedan en cero`() {
        // JSON.stringify convierte NaN e Infinity en null.
        val parsed = BridgeMessage.parse("""{"t":"state","playing":false,"position":null,"duration":-3}""")
        assertEquals(BridgeMessage.Playback(playing = false, buffering = false, positionSeconds = 0.0, durationSeconds = 0.0), parsed)
        assertEquals(BridgeMessage.ResponsePruned(0), BridgeMessage.parse("""{"t":"pruned","removed":-7}"""))
    }

    @Test
    fun `limita tamaño, textos y caratulas`() {
        assertNull(BridgeMessage.parse("""{"t":"page","open":true,"x":"${"a".repeat(BridgeMessage.MAX_LENGTH)}"}"""))

        val longTitle = "t".repeat(2_000)
        val artwork = (1..30).joinToString(",") { """{"src":"https://i.ytimg.com/$it.jpg","sizes":"${it}x$it"}""" }
        val track = BridgeMessage.parse("""{"t":"meta","title":"$longTitle","artwork":[$artwork,{"sizes":"1x1"},3]}""")
            as BridgeMessage.Track
        assertEquals(512, track.title.length)
        assertEquals("", track.artist)
        assertEquals(10, track.artwork.size)
    }

    @Test
    fun `un JSON demasiado anidado se descarta`() {
        val deep = "[".repeat(20) + "]".repeat(20)
        assertNull(BridgeMessage.parse("""{"t":"meta","title":"x","artwork":$deep}"""))
    }

    @Test
    fun `solo escucha al documento principal de YouTube Music`() {
        assertTrue(BridgeMessage.acceptsSource("https://music.youtube.com", isMainFrame = true))
        assertFalse(BridgeMessage.acceptsSource("https://music.youtube.com", isMainFrame = false))
        assertFalse(BridgeMessage.acceptsSource("https://www.youtube.com", isMainFrame = true))
        assertFalse(BridgeMessage.acceptsSource("https://music.youtube.com.evil.com", isMainFrame = true))
        assertFalse(BridgeMessage.acceptsSource("null", isMainFrame = true))
    }

    @Test
    fun `el estado se aplica sin tocar la pista`() {
        val current = PlaybackMetadata(title = "A", artist = "B", durationMs = 100_000, artworkUrl = "https://i.ytimg.com/a.jpg")
        val updated = BridgeMessage.Playback(playing = true, buffering = true, positionSeconds = 5.0, durationSeconds = 0.0)
            .applyTo(current)

        assertEquals(current.copy(isPlaying = true, isBuffering = true, positionMs = 5_000), updated)
    }

    @Test
    fun `la pista se aplica con la caratula validada`() {
        val current = PlaybackMetadata(isPlaying = true, positionMs = 3_000)
        val updated = BridgeMessage.Track(
            title = " Cancion ",
            artist = "Artista",
            album = "",
            artwork = listOf(
                ArtworkUrlPolicy.Candidate("https://evil.com/x.jpg", "544x544"),
                ArtworkUrlPolicy.Candidate("https://i.ytimg.com/ok.jpg", "226x226"),
            ),
        ).applyTo(current)

        assertEquals("Cancion", updated.title)
        assertEquals("https://i.ytimg.com/ok.jpg", updated.artworkUrl)
        assertTrue(updated.isPlaying)
        assertEquals(3_000, updated.positionMs)
    }
}
