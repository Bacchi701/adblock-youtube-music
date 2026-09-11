package com.adblocker.ytmusic.domain.usecase

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import com.adblocker.ytmusic.domain.model.PlayerFilterStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterPlayerResponseUseCaseTest {

    private val useCase = FilterPlayerResponseUseCase()

    private val playerUrl = "https://music.youtube.com/youtubei/v1/player?prettyPrint=false"

    private val body = """
        {"playabilityStatus":{"status":"OK"},"adPlacements":[{"a":1}],
         "streamingData":{"adaptiveFormats":[{"itag":251}]},"adSlots":[1]}
    """.trimIndent()

    @Test
    fun `filtra el playerResponse y contabiliza`() {
        val result = useCase(playerUrl, body)
        val filtered = MiniJson.parse(result.body) as JsonValue.Obj

        assertTrue(result.changed)
        assertNull(filtered["adPlacements"])
        assertNull(filtered["adSlots"])
        assertNotNull(filtered["streamingData"])
        assertEquals(
            PlayerFilterStats(responsesInspected = 1, adSectionsRemoved = 2),
            useCase.stats.value,
        )
    }

    @Test
    fun `una URL ajena al reproductor no se toca`() {
        val result = useCase("https://music.youtube.com/youtubei/v1/browse", body)

        assertFalse(result.changed)
        assertSame(body, result.body)
        assertEquals(PlayerFilterStats(), useCase.stats.value)
    }

    @Test
    fun `bloquea peticiones publicitarias pero nunca la del reproductor`() {
        assertTrue(useCase.shouldBlockRequest("https://s.youtube.com/api/stats/ads?ver=2"))
        assertFalse(useCase.shouldBlockRequest(playerUrl))
        assertFalse(useCase.shouldBlockRequest("https://rr3---sn-x.googlevideo.com/videoplayback"))
        assertEquals(1, useCase.stats.value.requestsBlocked)
    }

    @Test
    fun `las estadisticas acumulan entre llamadas`() {
        useCase(playerUrl, body)
        useCase(playerUrl, body)

        assertEquals(2, useCase.stats.value.responsesInspected)
        assertEquals(4, useCase.stats.value.adSectionsRemoved)
    }
}
