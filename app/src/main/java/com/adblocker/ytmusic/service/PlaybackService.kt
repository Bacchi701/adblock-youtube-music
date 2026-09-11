package com.adblocker.ytmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.adblocker.ytmusic.R
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.player.PlaybackCoordinator
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.domain.player.PlayerCommand
import com.adblocker.ytmusic.ui.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que sostiene la reproduccion del WebView cuando la app deja de
 * estar visible, y publica una `MediaSession` para que el sistema ofrezca los controles en
 * la pantalla de bloqueo y en la notificacion.
 *
 * No reproduce nada por su cuenta: el audio sigue saliendo del WebView. Traduce las acciones
 * del sistema a ordenes que el reproductor ejecuta via [PlaybackCoordinator].
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: PlaybackCoordinator
    private var session: MediaSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        coordinator = ServiceLocator.playbackCoordinator()
        ensureChannel(this)
        session = MediaSession(this, TAG).apply {
            setCallback(SessionCallback(coordinator))
            isActive = true
        }
        scope.launch {
            coordinator.metadata.collect { metadata -> publish(metadata) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> coordinator.send(coordinator.toggleCommand())
            ACTION_NEXT -> coordinator.send(PlayerCommand.Next)
            ACTION_PREVIOUS -> coordinator.send(PlayerCommand.Previous)
        }

        runCatching { publish(coordinator.metadata.value) }
            .onFailure { error ->
                Log.w(TAG, "No se pudo pasar a primer plano", error)
                stopSelf()
            }
        return START_STICKY
    }

    /** Vuelca el estado actual en la sesion y en la notificacion. */
    private fun publish(metadata: PlaybackMetadata) {
        val session = session ?: return

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, metadata.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, metadata.artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, metadata.durationMs)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (metadata.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    metadata.positionMs,
                    if (metadata.isPlaying) 1f else 0f,
                )
                .build(),
        )

        startInForeground(buildNotification(this, session, metadata))
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            isActive = false
            release()
        }
        session = null
        super.onDestroy()
    }

    private class SessionCallback(
        private val coordinator: PlaybackCoordinator,
    ) : MediaSession.Callback() {

        override fun onPlay() {
            coordinator.send(PlayerCommand.Play)
        }

        override fun onPause() {
            coordinator.send(PlayerCommand.Pause)
        }

        override fun onSkipToNext() {
            coordinator.send(PlayerCommand.Next)
        }

        override fun onSkipToPrevious() {
            coordinator.send(PlayerCommand.Previous)
        }

        override fun onSeekTo(pos: Long) {
            coordinator.send(PlayerCommand.SeekTo(pos))
        }

        override fun onStop() {
            coordinator.send(PlayerCommand.Stop)
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_STOP = "com.adblocker.ytmusic.action.STOP_PLAYBACK"
        const val ACTION_TOGGLE = "com.adblocker.ytmusic.action.TOGGLE_PLAYBACK"
        const val ACTION_NEXT = "com.adblocker.ytmusic.action.NEXT_TRACK"
        const val ACTION_PREVIOUS = "com.adblocker.ytmusic.action.PREVIOUS_TRACK"

        /**
         * Debe llamarse con la app visible: a partir de API 31 no se permite arrancar un
         * servicio en primer plano desde segundo plano.
         */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(TAG, "Arranque rechazado por el sistema", it) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.playback_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) },
            )
        }

        private fun action(context: Context, name: String): PendingIntent = PendingIntent.getService(
            context,
            name.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun buildNotification(
            context: Context,
            session: MediaSession,
            metadata: PlaybackMetadata,
        ): Notification {
            val openPlayer = PendingIntent.getActivity(
                context,
                0,
                Intent(context, PlayerActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val toggleIcon = if (metadata.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            val toggleLabel = context.getString(
                if (metadata.isPlaying) R.string.playback_pause else R.string.playback_play,
            )

            return Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(metadata.title.ifEmpty { context.getString(R.string.playback_title) })
                .setContentText(metadata.artist.ifEmpty { context.getString(R.string.playback_text) })
                .setContentIntent(openPlayer)
                .setOngoing(metadata.isPlaying)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_previous,
                        context.getString(R.string.playback_previous),
                        action(context, ACTION_PREVIOUS),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        toggleIcon,
                        toggleLabel,
                        action(context, ACTION_TOGGLE),
                    ).build(),
                )
                .addAction(
                    Notification.Action.Builder(
                        R.drawable.ic_next,
                        context.getString(R.string.playback_next),
                        action(context, ACTION_NEXT),
                    ).build(),
                )
                .setStyle(
                    Notification.MediaStyle()
                        .setMediaSession(session.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2),
                )
                .build()
        }
    }
}
