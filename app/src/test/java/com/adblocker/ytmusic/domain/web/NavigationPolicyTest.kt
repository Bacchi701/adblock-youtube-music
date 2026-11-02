package com.adblocker.ytmusic.domain.web

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPolicyTest {

    private fun decide(url: String, mainFrame: Boolean = true) = NavigationPolicy.decide(url, mainFrame)

    @Test
    fun `YouTube Music y el inicio de sesion se quedan en la app`() {
        assertEquals(NavigationDecision.IN_APP, decide("https://music.youtube.com/watch?v=x"))
        assertEquals(NavigationDecision.IN_APP, decide("https://accounts.google.com/ServiceLogin?service=youtube"))
        assertEquals(NavigationDecision.IN_APP, decide("https://accounts.youtube.com/accounts/SetSID"))
        assertEquals(NavigationDecision.IN_APP, decide("https://www.youtube.com/signin?next=%2F"))
        assertEquals(NavigationDecision.IN_APP, decide("https://consent.youtube.com/m?continue=x"))
    }

    @Test
    fun `mayusculas, punto final y puerto 443 se normalizan`() {
        assertEquals(NavigationDecision.IN_APP, decide("https://MUSIC.YouTube.com/"))
        assertEquals(NavigationDecision.IN_APP, decide("https://music.youtube.com./explore"))
        assertEquals(NavigationDecision.IN_APP, decide("https://music.youtube.com:443/"))
        assertEquals(NavigationDecision.IN_APP, decide("HTTPS://music.youtube.com/"))
    }

    @Test
    fun `los esquemas que no son https se bloquean aunque el host sea de YouTube`() {
        assertEquals(NavigationDecision.BLOCK, decide("intent://music.youtube.com/watch#Intent;scheme=https;end"))
        assertEquals(NavigationDecision.BLOCK, decide("javascript:alert(1)"))
        assertEquals(NavigationDecision.BLOCK, decide("file:///sdcard/x.html"))
        assertEquals(NavigationDecision.BLOCK, decide("data:text/html,<b>x</b>"))
        assertEquals(NavigationDecision.BLOCK, decide("blob:https://music.youtube.com/123"))
        assertEquals(NavigationDecision.BLOCK, decide("market://details?id=com.google.android.apps.youtube.music"))
        assertEquals(NavigationDecision.BLOCK, decide("about:blank"))
        assertEquals(NavigationDecision.BLOCK, decide("sin-esquema"))
        assertEquals(NavigationDecision.BLOCK, decide(""))
    }

    @Test
    fun `http normal se abre fuera pero nunca dentro`() {
        assertEquals(NavigationDecision.EXTERNAL, decide("http://music.youtube.com/"))
        assertEquals(NavigationDecision.BLOCK, decide("http://music.youtube.com/", mainFrame = false))
    }

    @Test
    fun `hosts parecidos no pasan por YouTube`() {
        assertEquals(NavigationDecision.EXTERNAL, decide("https://music.youtube.com.evil.com/"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://evilyoutube.com/"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://youtube.com.evil.com/"))
    }

    @Test
    fun `un usuario en la URL o un puerto raro no se cargan dentro`() {
        assertEquals(NavigationDecision.BLOCK, decide("https://music.youtube.com@evil.com/"))
        // Chromium lee la barra invertida como "/": el host real es evil.com, nunca YouTube.
        assertEquals(NavigationDecision.EXTERNAL, decide("https://evil.com\\@music.youtube.com/"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://music.youtube.com:8443/"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://music.youtube.com:abc/"))
    }

    @Test
    fun `el resto de enlaces de Google se abren en el navegador`() {
        assertEquals(NavigationDecision.EXTERNAL, decide("https://support.google.com/youtubemusic"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://policies.google.com/privacy"))
        assertEquals(NavigationDecision.EXTERNAL, decide("https://example.com/"))
    }

    @Test
    fun `los iframes https se cargan pero sus esquemas raros no`() {
        assertEquals(NavigationDecision.IN_APP, decide("https://accounts.google.com/RotateCookiesPage", mainFrame = false))
        assertEquals(NavigationDecision.BLOCK, decide("intent://x#Intent;end", mainFrame = false))
        assertEquals(NavigationDecision.BLOCK, decide("javascript:void(0)", mainFrame = false))
    }
}
