package com.adblocker.ytmusic.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.adblocker.ytmusic.domain.player.ArtworkUrlPolicy
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Descarga la caratula para la notificacion y la pantalla de bloqueo. La URL viene de la
 * pagina: solo se admite lo que acepta [ArtworkUrlPolicy], sin redirecciones, con limite de
 * tamaño y reducida a [ArtworkUrlPolicy.TARGET_PX] para no cargar imagenes enormes en memoria.
 * No envia cookies ni nada del usuario: es la misma imagen publica que ya pinta la web.
 */
class ArtworkLoader {

    private var cachedUrl: String? = null
    private var cachedBitmap: Bitmap? = null

    suspend fun load(url: String): Bitmap? {
        if (url == cachedUrl) return cachedBitmap
        if (!ArtworkUrlPolicy.isAllowed(url)) return null
        val bitmap = withContext(Dispatchers.IO) { runCatching { download(url) }.getOrNull() }
        if (bitmap != null) {
            cachedUrl = url
            cachedBitmap = bitmap
        }
        return bitmap
    }

    private fun download(url: String): Bitmap? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            if (connection.contentLengthLong > MAX_BYTES) return null
            val bytes = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > MAX_BYTES) return null
                }
                out.toByteArray()
            }
            return decodeScaled(bytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ArtworkUrlPolicy.TARGET_PX) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000
        const val MAX_BYTES = 1_048_576L
        const val BUFFER_BYTES = 8_192
    }
}
