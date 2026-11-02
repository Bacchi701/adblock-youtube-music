package com.adblocker.ytmusic.data.web

import android.webkit.WebResourceResponse
import com.adblocker.ytmusic.domain.player.PlayerRequestMatcher
import com.adblocker.ytmusic.domain.player.TelemetryMatcher
import com.adblocker.ytmusic.domain.usecase.EvaluateDomainUseCase
import com.adblocker.ytmusic.domain.usecase.FilterPlayerResponseUseCase
import java.io.ByteArrayInputStream

/**
 * Decide, para cada recurso que pide la pagina o su service worker, si sale a la red o se
 * contesta en local. Corre en hilos de red del WebView: solo hace comprobaciones de cadenas.
 */
class RequestInterceptor(
    private val filterPlayer: FilterPlayerResponseUseCase,
    private val evaluateDomain: EvaluateDomainUseCase,
) {

    fun intercept(url: String): WebResourceResponse? {
        val host = PlayerRequestMatcher.hostOf(url)
        if (host != null && evaluateDomain(host).isBlocked) return blocked()
        if (filterPlayer.shouldBlockRequest(url)) return blocked()
        return when (filterPlayer.telemetryResponse(url)) {
            TelemetryMatcher.Response.EMPTY_JSON -> response(200, "OK", "application/json", "{}")
            TelemetryMatcher.Response.NO_CONTENT -> response(204, "No Content", "text/plain", "")
            null -> null
        }
    }

    private fun blocked(): WebResourceResponse = response(200, "OK", "text/plain", "")

    private companion object {
        /**
         * Respuestas nuevas en cada llamada (el flujo se consume). Las cabeceras CORS evitan que
         * la pagina vea un error de red en peticiones a otros dominios y las reintente.
         */
        fun response(status: Int, reason: String, mime: String, body: String) = WebResourceResponse(
            mime,
            "utf-8",
            status,
            reason,
            mapOf(
                "Access-Control-Allow-Origin" to BridgeMessage.ORIGIN,
                "Access-Control-Allow-Credentials" to "true",
                "Cache-Control" to "no-store",
            ),
            ByteArrayInputStream(body.toByteArray()),
        )
    }
}
