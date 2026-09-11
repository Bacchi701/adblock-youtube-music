package com.adblocker.ytmusic.core.json

/**
 * Parser y serializador JSON sin dependencias. El cuerpo de `youtubei/v1/player` es
 * entrada no confiable, asi que el parser limita la profundidad y nunca lanza fuera
 * de [JsonParseException].
 */
object MiniJson {

    const val DEFAULT_MAX_DEPTH = 128

    private const val MIN_CAPACITY = 16

    private const val FORM_FEED = ''
    private const val LINE_SEPARATOR = ' '
    private const val PARAGRAPH_SEPARATOR = ' '

    class JsonParseException(message: String) : Exception(message)

    fun parse(text: String, maxDepth: Int = DEFAULT_MAX_DEPTH): JsonValue {
        val parser = Parser(text, maxDepth)
        val value = parser.parseValue(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonParseException("Contenido sobrante en ${parser.position}")
        return value
    }

    /** Variante segura: devuelve `null` en lugar de fallar ante un cuerpo no JSON. */
    fun parseOrNull(text: String, maxDepth: Int = DEFAULT_MAX_DEPTH): JsonValue? =
        runCatching { parse(text, maxDepth) }.getOrNull()

    /**
     * @param capacityHint tamano aproximado del resultado; evita que el buffer se copie
     *   varias veces al serializar cuerpos grandes.
     */
    fun write(value: JsonValue, capacityHint: Int = 0): String =
        StringBuilder(capacityHint.coerceAtLeast(MIN_CAPACITY))
            .also { writeTo(value, it) }
            .toString()

    private fun writeTo(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((key, entry) in value.entries) {
                    if (!first) out.append(',')
                    first = false
                    writeString(key, out)
                    out.append(':')
                    writeTo(entry, out)
                }
                out.append('}')
            }
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeTo(item, out)
                }
                out.append(']')
            }
            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Num -> out.append(value.raw)
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            JsonValue.Null -> out.append("null")
        }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')

        // La inmensa mayoria de claves y valores no llevan nada que escapar: copiarlos de una
        // vez evita recorrer caracter a caracter todo el cuerpo del reproductor.
        val firstEscape = value.indexOfFirst(::needsEscape)
        if (firstEscape < 0) {
            out.append(value).append('"')
            return
        }
        out.append(value, 0, firstEscape)

        for (index in firstEscape until value.length) {
            val char = value[index]
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                FORM_FEED -> out.append("\\f")
                // Los separadores de linea de Unicode rompen el JSON embebido en JavaScript.
                LINE_SEPARATOR, PARAGRAPH_SEPARATOR -> out.appendUnicodeEscape(char)
                else -> if (char < ' ') out.appendUnicodeEscape(char) else out.append(char)
            }
        }
        out.append('"')
    }

    private fun needsEscape(char: Char): Boolean = char == '"' ||
        char == '\\' ||
        char < ' ' || // incluye \n \r \t \b y el avance de pagina
        char == LINE_SEPARATOR ||
        char == PARAGRAPH_SEPARATOR

    private fun StringBuilder.appendUnicodeEscape(char: Char) {
        append("\\u").append(char.code.toString(16).padStart(4, '0'))
    }

    private class Parser(private val text: String, private val maxDepth: Int) {

        var position = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isJsonWhitespace()) position++
        }

        fun parseValue(depth: Int): JsonValue {
            if (depth > maxDepth) throw JsonParseException("Profundidad maxima superada")
            skipWhitespace()
            if (atEnd()) throw JsonParseException("Fin de entrada inesperado")
            return when (val char = text[position]) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> JsonValue.Str(parseString())
                't' -> parseLiteral("true", JsonValue.Bool(true))
                'f' -> parseLiteral("false", JsonValue.Bool(false))
                'n' -> parseLiteral("null", JsonValue.Null)
                else ->
                    if (char == '-' || char.isDigit()) parseNumber()
                    else throw JsonParseException("Caracter inesperado '$char' en $position")
            }
        }

        private fun parseObject(depth: Int): JsonValue.Obj {
            expect('{')
            val result = JsonValue.Obj()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result.entries[key] = parseValue(depth + 1)
                skipWhitespace()
                when (val separator = next()) {
                    ',' -> Unit
                    '}' -> return result
                    else -> throw JsonParseException("Se esperaba ',' o '}' y llego '$separator'")
                }
            }
        }

        private fun parseArray(depth: Int): JsonValue.Arr {
            expect('[')
            val result = JsonValue.Arr()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return result
            }
            while (true) {
                result.items += parseValue(depth + 1)
                skipWhitespace()
                when (val separator = next()) {
                    ',' -> Unit
                    ']' -> return result
                    else -> throw JsonParseException("Se esperaba ',' o ']' y llego '$separator'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                when (val char = next()) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(parseEscape())
                    else -> builder.append(char)
                }
            }
        }

        private fun parseEscape(): Char = when (val marker = next()) {
            '"', '\\', '/' -> marker
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'b' -> '\b'
            'f' -> FORM_FEED
            'u' -> {
                if (position + 4 > text.length) throw JsonParseException("Escape \\u truncado")
                val hex = text.substring(position, position + 4)
                position += 4
                hex.toIntOrNull(16)?.toChar() ?: throw JsonParseException("Escape \\u invalido: $hex")
            }
            else -> throw JsonParseException("Escape desconocido")
        }

        private fun parseNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-') position++
            while (!atEnd() && (text[position].isDigit() || text[position] in ".eE+-")) position++
            val raw = text.substring(start, position)
            if (raw.toDoubleOrNull() == null) throw JsonParseException("Numero invalido: $raw")
            return JsonValue.Num(raw)
        }

        private fun parseLiteral(literal: String, value: JsonValue): JsonValue {
            if (!text.startsWith(literal, position)) {
                throw JsonParseException("Literal invalido en $position")
            }
            position += literal.length
            return value
        }

        private fun peek(): Char? = text.getOrNull(position)

        private fun next(): Char {
            if (atEnd()) throw JsonParseException("Fin de entrada inesperado")
            return text[position++]
        }

        private fun expect(expected: Char) {
            val actual = next()
            if (actual != expected) {
                throw JsonParseException("Se esperaba '$expected' y llego '$actual' en ${position - 1}")
            }
        }

        private fun Char.isJsonWhitespace(): Boolean =
            this == ' ' || this == '\n' || this == '\r' || this == '\t'
    }
}
