package com.adblocker.ytmusic.data.web

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.WebView

/**
 * WebView que no deja que Chromium suspenda el reproductor al ocultarse la ventana.
 *
 * Cuando se bloquea la pantalla o se pasa a otra app, el sistema notifica `GONE` y el motor
 * detiene los elementos `<video>`/`<audio>`. Se le sigue informando de ventana visible para
 * que el audio continue; el resto de la reproduccion en primer plano no cambia.
 */
@SuppressLint("ViewConstructor")
class BackgroundPlaybackWebView(context: Context) : WebView(context) {

    /** Mientras sea `true` la ventana se declara visible aunque el sistema diga lo contrario. */
    var keepPlayingInBackground: Boolean = true

    override fun onWindowVisibilityChanged(visibility: Int) {
        val reported = if (keepPlayingInBackground && visibility != View.VISIBLE) {
            View.VISIBLE
        } else {
            visibility
        }
        super.onWindowVisibilityChanged(reported)
    }
}
