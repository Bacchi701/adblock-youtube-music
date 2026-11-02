package com.adblocker.ytmusic.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMetadataTest {

    @Test
    fun `convierte segundos a milisegundos`() {
        val metadata = PlaybackMetadata.fromSeconds(
            title = "Thinking out Loud",
            artist = "Ed Sheeran",
            durationSeconds = 290.5,
            positionSeconds = 11.25,
            isPlaying = true,
        )

        assertEquals("Thinking out Loud", metadata.title)
        assertEquals("Ed Sheeran", metadata.artist)
        assertEquals(290_500, metadata.durationMs)
        assertEquals(11_250, metadata.positionMs)
        assertTrue(metadata.isPlaying)
        assertTrue(metadata.hasTrack)
    }

    @Test
    fun `descarta tiempos no finitos mientras carga el reproductor`() {
        val metadata = PlaybackMetadata.fromSeconds(
            title = "x",
            artist = "y",
            durationSeconds = Double.NaN,
            positionSeconds = Double.POSITIVE_INFINITY,
            isPlaying = false,
        )

        assertEquals(0, metadata.durationMs)
        assertEquals(0, metadata.positionMs)
    }

    @Test
    fun `descarta tiempos negativos`() {
        val metadata = PlaybackMetadata.fromSeconds("x", "y", -5.0, -1.0, false)

        assertEquals(0, metadata.durationMs)
        assertEquals(0, metadata.positionMs)
    }

    @Test
    fun `tolera titulo e interprete nulos`() {
        val metadata = PlaybackMetadata.fromSeconds(null, null, 10.0, 0.0, false)

        assertEquals("", metadata.title)
        assertEquals("", metadata.artist)
        assertFalse(metadata.hasTrack)
    }

    @Test
    fun `album y caratula son opcionales`() {
        val bare = PlaybackMetadata.fromSeconds("x", "y", 1.0, 0.0, false)
        assertEquals("", bare.album)
        assertEquals(null, bare.artworkUrl)

        val full = PlaybackMetadata.fromSeconds("x", "y", 1.0, 0.0, false, album = " Disco ", artworkUrl = "  ")
        assertEquals("Disco", full.album)
        assertEquals(null, full.artworkUrl)
    }

    @Test
    fun `recorta los espacios que a veces trae la pagina`() {
        val metadata = PlaybackMetadata.fromSeconds("  Cancion  ", "  Artista ", 1.0, 0.0, true)

        assertEquals("Cancion", metadata.title)
        assertEquals("Artista", metadata.artist)
    }
}
