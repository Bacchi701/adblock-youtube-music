package com.adblocker.ytmusic.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelemetryMatcherTest {

    @Test
    fun `log_event recibe un JSON vacio`() {
        assertEquals(
            TelemetryMatcher.Response.EMPTY_JSON,
            TelemetryMatcher.match("https://music.youtube.com/youtubei/v1/log_event?alt=json"),
        )
    }

    @Test
    fun `los pings de medicion reciben 204`() {
        assertEquals(TelemetryMatcher.Response.NO_CONTENT, TelemetryMatcher.match("https://s.youtube.com/api/stats/qoe?cpn=x"))
        assertEquals(TelemetryMatcher.Response.NO_CONTENT, TelemetryMatcher.match("https://music.youtube.com/api/stats/qoe"))
        assertEquals(TelemetryMatcher.Response.NO_CONTENT, TelemetryMatcher.match("https://www.youtube.com/csi_204?v=3"))
        assertEquals(TelemetryMatcher.Response.NO_CONTENT, TelemetryMatcher.match("https://play.google.com/log?format=json"))
    }

    @Test
    fun `nunca toca historial, reproduccion ni la verificacion anti-bots`() {
        assertNull(TelemetryMatcher.match("https://s.youtube.com/api/stats/watchtime?ns=yt"))
        assertNull(TelemetryMatcher.match("https://s.youtube.com/api/stats/playback?ns=yt"))
        assertNull(TelemetryMatcher.match("https://music.youtube.com/youtubei/v1/player"))
        assertNull(TelemetryMatcher.match("https://music.youtube.com/youtubei/v1/att/get"))
        assertNull(TelemetryMatcher.match("https://jnn-pa.googleapis.com/\$rpc/x"))
        assertNull(TelemetryMatcher.match("https://www.youtube.com/generate_204"))
    }

    @Test
    fun `prefijos y hosts parecidos no casan`() {
        assertNull(TelemetryMatcher.match("https://music.youtube.com/youtubei/v1/log_event_extra"))
        assertNull(TelemetryMatcher.match("https://music.youtube.com/api/stats/qoe2"))
        assertNull(TelemetryMatcher.match("https://evilyoutube.com/youtubei/v1/log_event"))
        assertNull(TelemetryMatcher.match("https://play.google.com/store/apps"))
        assertNull(TelemetryMatcher.match("/youtubei/v1/log_event"))
    }
}
