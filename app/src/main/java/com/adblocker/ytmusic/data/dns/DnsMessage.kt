package com.adblocker.ytmusic.data.dns

/**
 * Lectura minima y construccion de respuestas DNS (RFC 1035). Logica pura, sin Android,
 * para poder validarla con pruebas unitarias locales.
 */
object DnsMessage {

    const val HEADER_SIZE = 12
    private const val MAX_NAME_LENGTH = 253
    private const val TYPICAL_NAME_LENGTH = 48
    private const val FLAG_QR = 0x8000
    private const val FLAG_RD = 0x0100
    private const val FLAG_RA = 0x0080
    private const val RCODE_NO_ERROR = 0
    private const val RCODE_NXDOMAIN = 3
    private const val DEFAULT_TTL = 60

    /** Devuelve la primera pregunta, o `null` si el mensaje no es una consulta valida. */
    fun parseQuestion(payload: ByteArray, length: Int = payload.size): DnsQuestion? {
        if (length < HEADER_SIZE) return null
        val flags = payload.readUShort(2)
        if (flags and FLAG_QR != 0) return null // es una respuesta, no una consulta
        if (payload.readUShort(4) < 1) return null // sin preguntas

        val name = StringBuilder(TYPICAL_NAME_LENGTH)
        var offset = HEADER_SIZE
        while (true) {
            if (offset >= length) return null
            val labelLength = payload[offset].toInt() and 0xFF
            if (labelLength and 0xC0 != 0) return null // punteros no permitidos en preguntas
            offset++
            if (labelLength == 0) break
            if (offset + labelLength > length) return null
            if (name.isNotEmpty()) name.append('.')
            for (i in 0 until labelLength) {
                // Se normaliza a minusculas al vuelo: evita copiar el nombre entero despues.
                name.append((payload[offset + i].toInt() and 0xFF).toChar().lowercaseChar())
            }
            offset += labelLength
            if (name.length > MAX_NAME_LENGTH) return null
        }
        if (offset + 4 > length) return null

        return DnsQuestion(
            name = name.toString(),
            type = payload.readUShort(offset),
            dnsClass = payload.readUShort(offset + 2),
            endOffset = offset + 4,
        )
    }

    /**
     * Respuesta de bloqueo para [query]:
     *  - A     -> registro con 0.0.0.0 (la conexion falla de inmediato)
     *  - AAAA  -> NODATA (sin respuestas, sin error)
     *  - resto -> NXDOMAIN
     */
    fun buildBlockedResponse(query: ByteArray, question: DnsQuestion): ByteArray {
        val questionBytes = question.endOffset - HEADER_SIZE
        val withARecord = question.type == DnsQuestion.TYPE_A
        val answerSize = if (withARecord) 16 else 0
        val rcode = when (question.type) {
            DnsQuestion.TYPE_A, DnsQuestion.TYPE_AAAA -> RCODE_NO_ERROR
            else -> RCODE_NXDOMAIN
        }

        val response = ByteArray(HEADER_SIZE + questionBytes + answerSize)
        // Header: se conserva el ID y el bit RD de la consulta original.
        response[0] = query[0]
        response[1] = query[1]
        response.writeUShort(2, FLAG_QR or (query.readUShort(2) and FLAG_RD) or FLAG_RA or rcode)
        response.writeUShort(4, 1)
        response.writeUShort(6, if (withARecord) 1 else 0)
        response.writeUShort(8, 0)
        response.writeUShort(10, 0)

        query.copyInto(response, HEADER_SIZE, HEADER_SIZE, question.endOffset)

        if (withARecord) {
            var o = HEADER_SIZE + questionBytes
            response.writeUShort(o, 0xC000 or HEADER_SIZE) // puntero al nombre de la pregunta
            response.writeUShort(o + 2, DnsQuestion.TYPE_A)
            response.writeUShort(o + 4, question.dnsClass)
            response.writeUInt(o + 6, DEFAULT_TTL)
            response.writeUShort(o + 10, 4)
            o += 12
            response[o] = 0; response[o + 1] = 0; response[o + 2] = 0; response[o + 3] = 0
        }
        return response
    }

    private fun ByteArray.readUShort(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.writeUShort(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.writeUInt(offset: Int, value: Int) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }
}
