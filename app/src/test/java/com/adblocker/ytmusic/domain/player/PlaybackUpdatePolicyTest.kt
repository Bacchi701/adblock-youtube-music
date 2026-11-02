package com.adblocker.ytmusic.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackUpdatePolicyTest {

    private val track = PlaybackMetadata(
        title = "Cancion",
        artist = "Artista",
        album = "Album",
        durationMs = 200_000,
        positionMs = 10_000,
        isPlaying = true,
        artworkUrl = "https://i.ytimg.com/a.jpg",
    )

    @Test
    fun `la primera publicacion lo envia todo`() {
        val update = PlaybackUpdatePolicy.between(null, track)
        assertEquals(PlaybackUpdate(sessionMetadata = true, sessionState = true, notification = true, artwork = true), update)
    }

    @Test
    fun `sin cambios no hace nada`() {
        assertTrue(PlaybackUpdatePolicy.between(track, track).isEmpty)
    }

    @Test
    fun `avanzar la posicion no republica la notificacion`() {
        val update = PlaybackUpdatePolicy.between(track, track.copy(positionMs = 70_000))

        assertTrue(update.sessionState)
        assertFalse(update.notification)
        assertFalse(update.sessionMetadata)
        assertFalse(update.artwork)
    }

    @Test
    fun `pausar cambia estado y notificacion`() {
        val update = PlaybackUpdatePolicy.between(track, track.copy(isPlaying = false))

        assertTrue(update.sessionState)
        assertTrue(update.notification)
        assertFalse(update.sessionMetadata)
    }

    @Test
    fun `quedarse cargando solo cambia el estado`() {
        val update = PlaybackUpdatePolicy.between(track, track.copy(isBuffering = true))

        assertTrue(update.sessionState)
        assertFalse(update.notification)
    }

    @Test
    fun `cambiar de cancion lo republica todo`() {
        val next = track.copy(title = "Otra", artworkUrl = "https://i.ytimg.com/b.jpg")
        val update = PlaybackUpdatePolicy.between(track, next)

        assertTrue(update.sessionMetadata)
        assertTrue(update.notification)
        assertTrue(update.artwork)
    }

    @Test
    fun `la duracion que llega tarde actualiza la sesion pero no la notificacion`() {
        val update = PlaybackUpdatePolicy.between(track.copy(durationMs = 0), track)

        assertTrue(update.sessionMetadata)
        assertFalse(update.notification)
    }

    @Test
    fun `perder la caratula no pide descargar nada`() {
        val update = PlaybackUpdatePolicy.between(track, track.copy(artworkUrl = null))

        assertFalse(update.artwork)
    }
}
