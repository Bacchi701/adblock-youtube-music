package com.adblocker.ytmusic.domain.filter

import com.adblocker.ytmusic.domain.model.FilterDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlocklistTest {

    private val blocklist = DomainBlocklist(
        blockedRules = listOf("doubleclick.net", "ads.youtube.com", "GOOGLE-ANALYTICS.COM."),
        allowedRules = listOf("safe.doubleclick.net"),
    )

    @Test
    fun `bloquea coincidencia exacta`() {
        assertTrue(blocklist.isBlocked("doubleclick.net"))
    }

    @Test
    fun `bloquea subdominios`() {
        assertTrue(blocklist.isBlocked("googleads.g.doubleclick.net"))
        assertTrue(blocklist.isBlocked("static.ads.youtube.com"))
    }

    @Test
    fun `no bloquea dominios que solo comparten sufijo textual`() {
        assertFalse(blocklist.isBlocked("notdoubleclick.net"))
        assertFalse(blocklist.isBlocked("youtube.com"))
    }

    @Test
    fun `la excepcion gana sobre el bloqueo`() {
        assertFalse(blocklist.isBlocked("safe.doubleclick.net"))
        assertFalse(blocklist.isBlocked("cdn.safe.doubleclick.net"))
        assertTrue(blocklist.isBlocked("other.doubleclick.net"))
    }

    @Test
    fun `normaliza mayusculas y punto final`() {
        assertTrue(blocklist.isBlocked("WWW.Google-Analytics.com."))
    }

    @Test
    fun `expone la regla que produjo el bloqueo`() {
        val decision = blocklist.evaluate("pixel.ads.youtube.com")
        assertEquals(FilterDecision.Block("ads.youtube.com"), decision)
    }

    @Test
    fun `dominio vacio se permite`() {
        assertEquals(FilterDecision.Allow, blocklist.evaluate("   "))
    }
}
