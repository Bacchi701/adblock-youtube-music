package com.adblocker.ytmusic.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.adblocker.ytmusic.R
import com.adblocker.ytmusic.data.media.ArtworkLoader
import com.adblocker.ytmusic.di.ServiceLocator
import com.adblocker.ytmusic.domain.player.PlaybackCoordinator
import com.adblocker.ytmusic.domain.player.PlaybackMetadata
import com.adblocker.ytmusic.domain.player.PlaybackUpdatePolicy
import com.adblocker.ytmusic.domain.player.PlayerCommand
import com.adblocker.ytmusic.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano que sostiene la reproduccion del WebView cuando la app deja de
 * estar visible, y publica una `MediaSession` para que el sistema ofrezca los controles en
 * la pantalla de bloqueo y en la notificacion.
 *
 * No reproduce nada por su cuenta: el audio sigue saliendo del WebView. Traduce las acciones
 * del sistema a ordenes que el reproductor ejecuta via [PlaybackCoordinator]. Solo republica lo
 * que cambio ([PlaybackUpdatePolicy]): cada notificacion nueva obliga a SystemUI a rehacer su
 * reproductor.
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var coordinator: PlaybackCoordinator
    private var session: MediaSession? = null
    private val artworkLoader = ArtworkLoader()
    private var artworkJob: Job? = null
    private var artwork: Bitmap? = null
    private var published: PlaybackMetadata? = null
    private var inForeground = false
    private var noisyReceiverRegistered = false

    /** Auriculares desconectados: se pausa, como cualquier reproductor del sistema. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                coordinator.send(PlayerCommand.Pause)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        coordinator = ServiceLocator.playbackCoordinator()
        ensureChannel(this)
        session = MediaSession(this, TAG).apply {
            setCallback(SessionCallback(coordinator))
            setSessionActivity(openAppIntent(this@PlaybackService))
            isActive = true
        }
        scope.launch {
            coordinator.metadata.collect(::onMetadata)
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

        if (!inForeground) {
            runCatching { startInForeground(buildNotification(coordinator.metadata.value)) }
                .onFailure { error ->
                    Log.w(TAG, "No se pudo pasar a primer plano", error)
                    stopSelf()
                }
        }
        // Si el sistema mata el proceso no hay WebView que reproducir: no se reinicia vacio.
        return START_NOT_STICKY
    }

    /** Quitar la app de recientes destruye el WebView: la notificacion ya no tiene sentido. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    private fun onMetadata(metadata: PlaybackMetadata) {
        val session = session ?: return
        val update = PlaybackUpdatePolicy.between(published, metadata)
        published = metadata
        if (update.isEmpty) return

        if (update.artwork) {
            artwork = null
            loadArtwork(metadata.artworkUrl)
        }
        if (update.sessionMetadata || update.artwork) session.setMetadata(sessionMetadata(metadata))
        if (update.sessionState) session.setPlaybackState(playbackState(metadata))
        if (update.notification && inForeground) notifyNotification(metadata)
        updateNoisyReceiver(metadata.isPlaying)
    }

    private fun loadArtwork(url: String?) {
        artworkJob?.cancel()
        if (url == null) return
        artworkJob = scope.launch {
            val bitmap = artworkLoader.load(url) ?: return@launch
            // Puede haber cambiado de cancion mientras se descargaba.
            val current = published ?: return@launch
            if (current.artworkUrl != url) return@launch
            artwork = bitmap
            session?.setMetadata(sessionMetadata(current))
            if (inForeground) notifyNotification(current)
        }
    }

    private fun sessionMetadata(metadata: PlaybackMetadata): MediaMetadata =
        MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, metadata.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, metadata.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, metadata.album)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, metadata.durationMs)
            .apply { artwork?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
            .build()

    private fun playbackState(metadata: PlaybackMetadata): PlaybackState {
        val state = when {
            metadata.isBuffering -> PlaybackState.STATE_BUFFERING
            metadata.isPlaying -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        return PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP,
            )
            // Con la velocidad y la marca de tiempo, el sistema hace avanzar la barra solo.
            .setState(
                state,
                metadata.positionMs,
                if (metadata.isPlaying && !metadata.isBuffering) 1f else 0f,
                SystemClock.elapsedRealtime(),
            )
            .build()
    }

    private fun updateNoisyReceiver(playing: Boolean) {
        if (playing && !noisyReceiverRegistered) {
            // Difusion del sistema: no hace falta (ni conviene) exportar el receptor.
            ContextCompat.registerReceiver(
                this,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        } else if (!playing && noisyReceiverRegistered) {
            unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
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
        inForeground = true
    }

    private fun notifyNotification(metadata: PlaybackMetadata) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(metadata))
    }

    private fun buildNotification(metadata: PlaybackMetadata): Notification {
        val session = requireNotNull(session)
        val toggleIcon = if (metadata.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val toggleLabel = getString(
            if (metadata.isPlaying) R.string.playback_pause else R.string.playback_play,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(metadata.title.ifEmpty { getString(R.string.playback_title) })
            .setContentText(metadata.artist.ifEmpty { getString(R.string.playback_text) })
            .setLargeIcon(artwork)
            .setContentIntent(openAppIntent(this))
            .setOngoing(metadata.isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(notificationAction(R.drawable.ic_previous, R.string.playback_previous, ACTION_PREVIOUS))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, toggleIcon),
                    toggleLabel,
                    action(this, ACTION_TOGGLE),
                ).build(),
            )
            .addAction(notificationAction(R.drawable.ic_next, R.string.playback_next, ACTION_NEXT))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
            .build()
    }

    private fun notificationAction(icon: Int, label: Int, name: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(this, icon), getString(label), action(this, name))
            .build()

    override fun onDestroy() {
        scope.cancel()
        if (noisyReceiverRegistered) {
            unregisterReceiver(noisyReceiver)
            noisyReceiverRegistered = false
        }
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
            runCatching { context.stopService(Intent(context, PlaybackService::class.java)) }
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

        /** Reabre la pantalla existente (singleTask): nunca un segundo WebView. */
        private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private fun action(context: Context, name: String): PendingIntent = PendingIntent.getService(
            context,
            name.hashCode(),
            Intent(context, PlaybackService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
