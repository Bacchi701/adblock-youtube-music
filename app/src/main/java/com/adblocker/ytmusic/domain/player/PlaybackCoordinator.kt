package com.adblocker.ytmusic.domain.player

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Une el WebView (que es quien reproduce) con la `MediaSession` (que es quien recibe los
 * mandos de la pantalla de bloqueo). Ninguno de los dos conoce al otro:
 *
 *  - la pagina publica su estado con [updateMetadata];
 *  - la sesion pide acciones con [send], que el reproductor ejecuta.
 */
class PlaybackCoordinator {

    private val _metadata = MutableStateFlow(PlaybackMetadata())
    val metadata: StateFlow<PlaybackMetadata> = _metadata.asStateFlow()

    /**
     * `replay = 0`: una orden pendiente no debe ejecutarse al reabrir el reproductor.
     * El buffer extra evita perder pulsaciones rapidas si el consumidor va lento.
     */
    private val _commands = MutableSharedFlow<PlayerCommand>(extraBufferCapacity = COMMAND_BUFFER)
    val commands: SharedFlow<PlayerCommand> = _commands.asSharedFlow()

    fun updateMetadata(metadata: PlaybackMetadata) {
        _metadata.value = metadata
    }

    /** La pagina informa por separado de la pista y del estado: cada aviso cambia solo lo suyo. */
    fun updateMetadata(transform: (PlaybackMetadata) -> PlaybackMetadata) {
        _metadata.update(transform)
    }

    /** Devuelve `false` si la orden se descarto por saturacion del buffer. */
    fun send(command: PlayerCommand): Boolean = _commands.tryEmit(command)

    /** La orden contraria a lo que suena ahora; la usan el boton unico de la notificacion. */
    fun toggleCommand(): PlayerCommand =
        if (_metadata.value.isPlaying) PlayerCommand.Pause else PlayerCommand.Play

    private companion object {
        const val COMMAND_BUFFER = 8
    }
}
