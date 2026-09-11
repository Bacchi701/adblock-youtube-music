package com.adblocker.ytmusic.data.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsMessageTest {

    @Test
    fun `extrae el nombre y el tipo de la pregunta`() {
        val query = DnsTestFixtures.query("googleads.g.doubleclick.net")
        val question = DnsMessage.parseQuestion(query)
        assertNotNull(question)
        requireNotNull(question)

        assertEquals("googleads.g.doubleclick.net", question.name)
        assertEquals(DnsQuestion.TYPE_A, question.type)
        assertEquals(1, question.dnsClass)
        assertEquals(query.size, question.endOffset)
    }

    @Test
    fun `normaliza el nombre a minusculas`() {
        val question = DnsMessage.parseQuestion(DnsTestFixtures.query("ADS.Example.COM"))!!
        assertEquals("ads.example.com", question.name)
    }

    @Test
    fun `rechaza payloads truncados`() {
        val query = DnsTestFixtures.query("ads.example.com")
        assertNull(DnsMessage.parseQuestion(query, length = 8))
        assertNull(DnsMessage.parseQuestion(query, length = query.size - 3))
    }

    @Test
    fun `rechaza mensajes que ya son respuesta`() {
        val query = DnsTestFixtures.query("ads.example.com")
        query[2] = (query[2].toInt() or 0x80).toByte() // QR = 1
        assertNull(DnsMessage.parseQuestion(query))
    }

    @Test
    fun `la respuesta bloqueada de tipo A devuelve 0 0 0 0`() {
        val query = DnsTestFixtures.query("ads.example.com", DnsQuestion.TYPE_A, id = 0xBEEF)
        val question = DnsMessage.parseQuestion(query)!!
        val response = DnsMessage.buildBlockedResponse(query, question)

        assertEquals(0xBEEF, DnsTestFixtures.readUShort(response, 0)) // mismo ID
        val flags = DnsTestFixtures.readUShort(response, 2)
        assertEquals(0x8000, flags and 0x8000) // QR = 1
        assertEquals(0x0100, flags and 0x0100) // RD conservado
        assertEquals(0, flags and 0x000F) // RCODE = NOERROR
        assertEquals(1, DnsTestFixtures.readUShort(response, 4)) // QDCOUNT
        assertEquals(1, DnsTestFixtures.readUShort(response, 6)) // ANCOUNT

        val answer = question.endOffset
        assertEquals(0xC00C, DnsTestFixtures.readUShort(response, answer)) // puntero al nombre
        assertEquals(DnsQuestion.TYPE_A, DnsTestFixtures.readUShort(response, answer + 2))
        assertEquals(4, DnsTestFixtures.readUShort(response, answer + 10))
        assertEquals(
            listOf<Byte>(0, 0, 0, 0),
            response.copyOfRange(answer + 12, answer + 16).toList(),
        )
        assertEquals(question.endOffset + 16, response.size)
    }

    @Test
    fun `AAAA bloqueado responde NODATA`() {
        val query = DnsTestFixtures.query("ads.example.com", DnsQuestion.TYPE_AAAA)
        val question = DnsMessage.parseQuestion(query)!!
        val response = DnsMessage.buildBlockedResponse(query, question)

        assertEquals(0, DnsTestFixtures.readUShort(response, 2) and 0x000F) // NOERROR
        assertEquals(0, DnsTestFixtures.readUShort(response, 6)) // sin respuestas
    }

    @Test
    fun `otros tipos bloqueados responden NXDOMAIN`() {
        val query = DnsTestFixtures.query("ads.example.com", type = 15) // MX
        val question = DnsMessage.parseQuestion(query)!!
        val response = DnsMessage.buildBlockedResponse(query, question)

        assertEquals(3, DnsTestFixtures.readUShort(response, 2) and 0x000F)
        assertEquals(0, DnsTestFixtures.readUShort(response, 6))
    }
}
