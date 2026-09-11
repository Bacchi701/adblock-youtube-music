package com.adblocker.ytmusic.domain.network

import com.adblocker.ytmusic.domain.model.PrivateDnsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsEvaluatorTest {

    @Test
    fun `sin redes el estado es apagado`() {
        assertEquals(PrivateDnsStatus.Off, PrivateDnsEvaluator.evaluate(emptyList()))
    }

    @Test
    fun `red sin DoT es apagado`() {
        val status = PrivateDnsEvaluator.evaluate(listOf(NetworkDnsInfo()))

        assertEquals(PrivateDnsStatus.Off, status)
        assertFalse(status.bypassesFilter)
    }

    @Test
    fun `DoT activo sin hostname es modo automatico`() {
        val status = PrivateDnsEvaluator.evaluate(
            listOf(NetworkDnsInfo(privateDnsActive = true)),
        )

        assertEquals(PrivateDnsStatus.Opportunistic, status)
        // El sondeo DoT contra el DNS virtual no responde: el filtro sigue viendo el trafico.
        assertFalse(status.bypassesFilter)
    }

    @Test
    fun `hostname fijado es modo estricto y esquiva el filtro`() {
        val status = PrivateDnsEvaluator.evaluate(
            listOf(NetworkDnsInfo(privateDnsActive = true, privateDnsServerName = "dns.adguard.com")),
        )

        assertEquals(PrivateDnsStatus.Strict("dns.adguard.com"), status)
        assertTrue(status.bypassesFilter)
    }

    @Test
    fun `ignora la red del propio tunel VPN`() {
        val status = PrivateDnsEvaluator.evaluate(
            listOf(
                NetworkDnsInfo(isVpn = true, privateDnsServerName = "vpn.interno"),
                NetworkDnsInfo(privateDnsActive = false),
            ),
        )

        assertEquals(PrivateDnsStatus.Off, status)
    }

    @Test
    fun `el modo estricto de cualquier red fisica manda sobre el automatico`() {
        val status = PrivateDnsEvaluator.evaluate(
            listOf(
                NetworkDnsInfo(privateDnsActive = true),
                NetworkDnsInfo(privateDnsActive = true, privateDnsServerName = "one.one.one.one"),
            ),
        )

        assertEquals(PrivateDnsStatus.Strict("one.one.one.one"), status)
    }

    @Test
    fun `un hostname vacio o en blanco no cuenta como estricto`() {
        val status = PrivateDnsEvaluator.evaluate(
            listOf(NetworkDnsInfo(privateDnsActive = true, privateDnsServerName = "   ")),
        )

        assertEquals(PrivateDnsStatus.Opportunistic, status)
    }
}
