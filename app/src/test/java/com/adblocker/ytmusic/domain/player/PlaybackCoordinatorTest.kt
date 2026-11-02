package com.adblocker.ytmusic.domain.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackCoordinatorTest {

    private val coordinator = PlaybackCoordinator()

    @Test
    fun `publica los metadatos a quien observe`() {
        val metadata = PlaybackMetadata(title = "Cancion", artist = "Artista", isPlaying = true)

        coordinator.updateMetadata(metadata)

        assertEquals(metadata, coordinator.metadata.value)
    }

    @Test
    fun `cada aviso de la pagina cambia solo su parte`() {
        coordinator.updateMetadata(PlaybackMetadata(title = "Cancion", artist = "Artista"))

        coordinator.updateMetadata { it.copy(isPlaying = true, positionMs = 5_000) }

        assertEquals(
            PlaybackMetadata(title = "Cancion", artist = "Artista", isPlaying = true, positionMs = 5_000),
            coordinator.metadata.value,
        )
    }

    @Test
    fun `el boton unico alterna segun lo que suene`() {
        assertEquals(PlayerCommand.Play, coordinator.toggleCommand())

        coordinator.updateMetadata(PlaybackMetadata(isPlaying = true))
        assertEquals(PlayerCommand.Pause, coordinator.toggleCommand())

        coordinator.updateMetadata(PlaybackMetadata(isPlaying = false))
        assertEquals(PlayerCommand.Play, coordinator.toggleCommand())
    }

    @Test
    fun `las ordenes llegan al reproductor`() = runTest {
        val received = mutableListOf<PlayerCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.commands.collect { received += it }
        }

        assertTrue(coordinator.send(PlayerCommand.Next))
        assertTrue(coordinator.send(PlayerCommand.SeekTo(30_000)))

        assertEquals(listOf(PlayerCommand.Next, PlayerCommand.SeekTo(30_000)), received)
    }

    @Test
    fun `una orden emitida sin nadie escuchando no se repite despues`() = runTest {
        coordinator.send(PlayerCommand.Next)

        val received = mutableListOf<PlayerCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            coordinator.commands.collect { received += it }
        }

        // Reabrir el reproductor no debe disparar una orden vieja.
        assertTrue(received.isEmpty())
    }
}
