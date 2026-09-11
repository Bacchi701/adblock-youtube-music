package com.adblocker.ytmusic.core.json

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {

    private fun sample(): String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("player_response_sample.json"))
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `parsea los tipos basicos`() {
        val value = MiniJson.parse("""{"a":1,"b":"x","c":true,"d":null,"e":[1,2]}""")
        val obj = value as JsonValue.Obj

        assertEquals(JsonValue.Num("1"), obj["a"])
        assertEquals(JsonValue.Str("x"), obj["b"])
        assertEquals(JsonValue.Bool(true), obj["c"])
        assertEquals(JsonValue.Null, obj["d"])
        assertEquals(2, (obj["e"] as JsonValue.Arr).items.size)
    }

    @Test
    fun `el ciclo parsear serializar no altera el contenido`() {
        val original = MiniJson.parse(sample())
        val reparsed = MiniJson.parse(MiniJson.write(original))
        assertEquals(original, reparsed)
    }

    @Test
    fun `los numeros conservan su formato original`() {
        val obj = MiniJson.parse("""{"a":-1.2299995,"b":1e10,"c":0.0}""") as JsonValue.Obj
        assertEquals(JsonValue.Num("-1.2299995"), obj["a"])
        assertEquals("""{"a":-1.2299995,"b":1e10,"c":0.0}""", MiniJson.write(obj))
    }

    @Test
    fun `mantiene el orden de las claves`() {
        val json = """{"z":1,"a":2,"m":3}"""
        assertEquals(json, MiniJson.write(MiniJson.parse(json)))
    }

    @Test
    fun `decodifica y reescribe escapes`() {
        val obj = MiniJson.parse("""{"k":"a\"b\\c\ndé"}""") as JsonValue.Obj
        assertEquals("a\"b\\c\ndé", (obj["k"] as JsonValue.Str).value)

        val written = MiniJson.write(obj)
        assertTrue(written.contains("\\n"))
        assertTrue(written.contains("\\u0007")) // control sin escape corto
        assertTrue(written.contains("é")) // el no-ASCII se emite tal cual
        assertEquals(obj, MiniJson.parse(written))
    }

    @Test
    fun `escapa los separadores de linea unicode`() {
        val written = MiniJson.write(JsonValue.Str("a b c"))
        assertEquals("\"a\\u2028b\\u2029c\"", written)
    }

    @Test
    fun `ignora espacios entre tokens`() {
        val obj = MiniJson.parse("  {  \"a\" : [ 1 , 2 ] }  ") as JsonValue.Obj
        assertEquals(2, (obj["a"] as JsonValue.Arr).items.size)
    }

    @Test
    fun `objetos y arrays vacios`() {
        assertEquals("{}", MiniJson.write(MiniJson.parse("{}")))
        assertEquals("[]", MiniJson.write(MiniJson.parse("[]")))
    }

    @Test
    fun `parseOrNull devuelve null ante entrada invalida`() {
        assertNull(MiniJson.parseOrNull(""))
        assertNull(MiniJson.parseOrNull("<html>no soy json</html>"))
        assertNull(MiniJson.parseOrNull("""{"a":1"""))
        assertNull(MiniJson.parseOrNull("""{"a":1}basura"""))
        assertNull(MiniJson.parseOrNull("""{"a":tru}"""))
    }

    @Test
    fun `la profundidad esta limitada`() {
        val deep = "[".repeat(200) + "]".repeat(200)
        assertNull(MiniJson.parseOrNull(deep))
        assertNull(MiniJson.parseOrNull("[[[]]]", maxDepth = 1))
    }
}
