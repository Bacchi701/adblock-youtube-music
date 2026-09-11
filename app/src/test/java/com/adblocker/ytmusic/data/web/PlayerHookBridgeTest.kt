package com.adblocker.ytmusic.data.web

import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El puente JS nunca debe lanzar: cualquier fallo devolveria el cuerpo original. */
class PlayerHookBridgeTest {

    private val useCase = FilterPlayerResponseUseCase()
    private val playbackStates = mutableListOf<Boolean>()
    private val metadataUpdates = mutableListOf<PlaybackMetadata>()
    private val bridge = PlayerHookBridge(
        useCase = useCase,
        playbackListener = { playbackStates += it },
        metadataListener = { metadataUpdates += it },
    )

    private val playerUrl = "/youtubei/v1/player?prettyPrint=false"

    @Test
    fun `filtra el cuerpo del reproductor`() {
        val filtered = bridge.filterPlayerResponse(playerUrl, """{"adSlots":[1],"videoDetails":{"videoId":"x"}}""")
        assertEquals("""{"videoDetails":{"videoId":"x"}}""", filtered)
    }

    @Test
    fun `devuelve el original si la URL no es del reproductor`() {
        val body = """{"adSlots":[1]}"""
        assertEquals(body, bridge.filterPlayerResponse("/youtubei/v1/browse", body))
    }

    @Test
    fun `tolera nulos sin lanzar`() {
        assertEquals("", bridge.filterPlayerResponse(playerUrl, null))
        assertEquals("cuerpo", bridge.filterPlayerResponse(null, "cuerpo"))
        assertFalse(bridge.isPlayerRequest(null))
    }

    @Test
    fun `tolera cuerpos corruptos`() {
        val roto = """{"adSlots":[1"""
        assertEquals(roto, bridge.filterPlayerResponse(playerUrl, roto))
    }

    @Test
    fun `reenvia el estado de reproduccion`() {
        bridge.onPlaybackStateChanged(true)
        bridge.onPlaybackStateChanged(false)

        assertEquals(listOf(true, false), playbackStates)
    }

    @Test
    fun `un listener que falla no propaga la excepcion a la pagina`() {
        val explosive = PlayerHookBridge(useCase) { error("fallo del oyente") }

        explosive.onPlaybackStateChanged(true) // no debe lanzar
    }

    @Test
    fun `publica los metadatos saneados`() {
        bridge.onMetadataChanged("Cancion", "Artista", 290.5, 11.25, true)

        assertEquals(
            listOf(PlaybackMetadata("Cancion", "Artista", 290_500, 11_250, true)),
            metadataUpdates,
        )
    }

    @Test
    fun `unos metadatos incompletos no rompen la reproduccion`() {
        bridge.onMetadataChanged(null, null, Double.NaN, Double.NaN, false)

        assertEquals(1, metadataUpdates.size)
        assertEquals(PlaybackMetadata(), metadataUpdates.first())
    }

    @Test
    fun `expone el reconocimiento de URL a la pagina`() {
        assertTrue(bridge.isPlayerRequest(playerUrl))
        assertFalse(bridge.isPlayerRequest("https://otro.example/youtubei/v1/player"))
    }
}
