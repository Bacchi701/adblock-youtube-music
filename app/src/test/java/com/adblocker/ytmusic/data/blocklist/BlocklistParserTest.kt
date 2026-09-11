package com.adblocker.ytmusic.data.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistParserTest {

    @Test
    fun `acepta formato hosts y dominio plano`() {
        val rules = BlocklistParser.parse(
            """
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.example.org
            pagead2.googlesyndication.com
            """.trimIndent(),
        )
        assertEquals(
            setOf("ads.example.com", "tracker.example.org", "pagead2.googlesyndication.com"),
            rules.blocked,
        )
    }

    @Test
    fun `ignora comentarios y lineas vacias`() {
        val rules = BlocklistParser.parse(
            """
            # cabecera
            ! otro estilo de comentario

            ads.example.com # comentario al final
            """.trimIndent(),
        )
        assertEquals(setOf("ads.example.com"), rules.blocked)
    }

    @Test
    fun `separa excepciones`() {
        val rules = BlocklistParser.parse("@@googlevideo.com\ndoubleclick.net")
        assertEquals(setOf("doubleclick.net"), rules.blocked)
        assertEquals(setOf("googlevideo.com"), rules.allowed)
    }

    @Test
    fun `descarta entradas invalidas`() {
        val rules = BlocklistParser.parse(
            """
            localhost
            192.168.1.1 router
            ::1 ip6-localhost
            http://ads.example.com/banner
            -mal.example.com
            """.trimIndent(),
        )
        assertTrue(rules.blocked.isEmpty())
    }

    @Test
    fun `normaliza a minusculas y sin punto final`() {
        val rules = BlocklistParser.parse("0.0.0.0 ADS.Example.COM.")
        assertEquals(setOf("ads.example.com"), rules.blocked)
    }
}
