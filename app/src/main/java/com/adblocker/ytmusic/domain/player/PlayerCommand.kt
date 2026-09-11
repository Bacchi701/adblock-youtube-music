package com.adblocker.ytmusic.domain.player

/** Ordenes que llegan desde la pantalla de bloqueo o la notificacion hacia el reproductor. */
sealed interface PlayerCommand {

    data object Play : PlayerCommand

    data object Pause : PlayerCommand

    data object Next : PlayerCommand

    data object Previous : PlayerCommand

    data object Stop : PlayerCommand

    data class SeekTo(val positionMs: Long) : PlayerCommand
}
