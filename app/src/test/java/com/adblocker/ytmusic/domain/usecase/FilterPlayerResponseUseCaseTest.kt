package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import com.adblocker.ytmusic.domain.player.TelemetryMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPlayerResponseUseCaseTest {

    private val useCase = FilterPlayerResponseUseCase()

    private val playerUrl = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"

    @Test
    fun `contabiliza lo que poda la pagina`() {
        useCase.onPlayerResponsePruned(3)
        useCase.onPlayerResponsePruned(0)

        assertEquals(
            PlayerFilterStats(responsesInspected = 2, adSectionsRemoved = 3),
            useCase.stats.value,
        )
    }

    @Test
    fun `un recuento negativo de la pagina no resta`() {
        useCase.onPlayerResponsePruned(-5)

        assertEquals(PlayerFilterStats(responsesInspected = 1, adSectionsRemoved = 0), useCase.stats.value)
    }

    @Test
    fun `bloquea peticiones publicitarias pero nunca la del reproductor`() {
        assertTrue(useCase.shouldBlockRequest("https://s.youtube.com/api/stats/ads?ver=2"))
        assertFalse(useCase.shouldBlockRequest(playerUrl))
        assertFalse(useCase.shouldBlockRequest("https://rr3---sn-x.googlevideo.com/videoplayback"))
        assertEquals(1, useCase.stats.value.requestsBlocked)
    }

    @Test
    fun `la verificacion anti-bots de YouTube sale a la red`() {
        assertFalse(useCase.shouldBlockRequest("https://music.youtube.com/youtubei/v1/att/get?prettyPrint=false"))
        assertNull(useCase.telemetryResponse("https://music.youtube.com/youtubei/v1/att/get"))
    }

    @Test
    fun `la medicion se contesta en local y cuenta como bloqueada`() {
        assertEquals(
            TelemetryMatcher.Response.EMPTY_JSON,
            useCase.telemetryResponse("https://music.youtube.com/youtubei/v1/log_event?alt=json"),
        )
        assertNull(useCase.telemetryResponse("https://s.youtube.com/api/stats/watchtime"))
        assertEquals(1, useCase.stats.value.requestsBlocked)
    }

    @Test
    fun `las claves de la poda son las de la referencia Kotlin`() {
        assertEquals(PlayerResponseFilter.DEFAULT_AD_KEYS, useCase.adKeys)
        assertEquals(PlayerResponseFilter.DEFAULT_PROTECTED_KEYS, useCase.protectedKeys)
    }
}
