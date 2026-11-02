package com.adblocker.ytmusic.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRequestMatcherTest {

    @Test
    fun `reconoce la peticion del reproductor absoluta y relativa`() {
        assertTrue(PlayerRequestMatcher.isPlayerRequest("https://music.youtube.com/youtubei/v1/player?key=A&prettyPrint=false"))
        assertTrue(PlayerRequestMatcher.isPlayerRequest("https://youtubei.googleapis.com/youtubei/v1/player"))
        assertTrue(PlayerRequestMatcher.isPlayerRequest("/youtubei/v1/player?key=A"))
        assertTrue(PlayerRequestMatcher.isPlayerRequest("youtubei/v1/player"))
    }

    @Test
    fun `no confunde otros endpoints de InnerTube`() {
        assertFalse(PlayerRequestMatcher.isPlayerRequest("/youtubei/v1/next"))
        assertFalse(PlayerRequestMatcher.isPlayerRequest("/youtubei/v1/browse"))
        assertFalse(PlayerRequestMatcher.isPlayerRequest("/youtubei/v1/player/ad_break"))
        assertFalse(PlayerRequestMatcher.isPlayerRequest("/youtubei/v1/player_extra"))
    }

    @Test
    fun `rechaza hosts ajenos a YouTube`() {
        assertFalse(PlayerRequestMatcher.isPlayerRequest("https://malicioso.example/youtubei/v1/player"))
        assertFalse(PlayerRequestMatcher.isPlayerRequest("https://notyoutube.com/youtubei/v1/player"))
    }

    @Test
    fun `detecta peticiones publicitarias por host`() {
        assertTrue(PlayerRequestMatcher.isAdRequest("https://googleads.g.doubleclick.net/pagead/id"))
        assertTrue(PlayerRequestMatcher.isAdRequest("https://pagead2.googlesyndication.com/x"))
        assertTrue(PlayerRequestMatcher.isAdRequest("https://s0.2mdn.net/creative.mp4"))
    }

    @Test
    fun `detecta peticiones publicitarias por ruta`() {
        assertTrue(PlayerRequestMatcher.isAdRequest("https://www.youtube.com/pagead/interaction/?ai=x"))
        assertTrue(PlayerRequestMatcher.isAdRequest("https://s.youtube.com/api/stats/ads?ver=2"))
        assertTrue(PlayerRequestMatcher.isAdRequest("https://s.youtube.com/ptracking?video_id=x"))
        assertTrue(PlayerRequestMatcher.isAdRequest("/youtubei/v1/player/ad_break"))
    }

    @Test
    fun `no marca como anuncio el trafico de reproduccion`() {
        assertFalse(PlayerRequestMatcher.isAdRequest("https://rr3---sn-x.googlevideo.com/videoplayback?itag=251"))
        assertFalse(PlayerRequestMatcher.isAdRequest("https://music.youtube.com/youtubei/v1/player"))
        assertFalse(PlayerRequestMatcher.isAdRequest("https://i.ytimg.com/vi/x/maxres.jpg"))
        assertFalse(PlayerRequestMatcher.isAdRequest("https://s.youtube.com/api/stats/playback?cl=1"))
    }

    @Test
    fun `la verificacion anti-bots no es publicidad`() {
        // Cortarla provoca fallos de reproduccion y avisos de "confirma que no eres un bot".
        assertFalse(PlayerRequestMatcher.isAdRequest("https://music.youtube.com/youtubei/v1/att/get?prettyPrint=false"))
        assertFalse(PlayerRequestMatcher.isAdRequest("https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create"))
    }

    @Test
    fun `extrae host y ruta`() {
        assertEquals("music.youtube.com", PlayerRequestMatcher.hostOf("https://MUSIC.youtube.com:443/a/b?x=1"))
        assertEquals("/a/b", PlayerRequestMatcher.pathOf("https://music.youtube.com:443/a/b?x=1"))
        assertEquals("/", PlayerRequestMatcher.pathOf("https://music.youtube.com"))
        assertNull(PlayerRequestMatcher.hostOf("/ruta/relativa"))
        assertEquals("/ruta/relativa", PlayerRequestMatcher.pathOf("/ruta/relativa#frag"))
    }
}
