package com.adblocker.ytmusic.domain.player

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResponseFilterTest {

    private val filter = PlayerResponseFilter()

    private val sample: String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("player_response_sample.json"))
            .bufferedReader()
            .use { it.readText() }

    private fun obj(json: String) = MiniJson.parse(json) as JsonValue.Obj

    @Test
    fun `elimina las secciones de anuncios de primer nivel`() {
        val result = filter.filter(sample)
        val filtered = obj(result.body)

        assertTrue(result.changed)
        assertNull(filtered["adPlacements"])
        assertNull(filtered["adSlots"])
        assertNull(filtered["playerAds"])
        assertNull(filtered["adBreakHeartbeatParams"])
    }

    @Test
    fun `elimina claves de anuncios anidadas fuera de zonas protegidas`() {
        val filtered = obj(filter.filter(sample).body)
        val tracking = filtered["playbackTracking"] as JsonValue.Obj

        assertNull(tracking["adParams"])
        // El resto del bloque de tracking sigue intacto.
        assertNotNull(tracking["videostatsPlaybackUrl"])
    }

    @Test
    fun `no toca streamingData ni aunque contenga claves de anuncios`() {
        val original = obj(sample)["streamingData"]
        val filtered = obj(filter.filter(sample).body)["streamingData"]

        assertEquals(original, filtered)
        assertNotNull((filtered as JsonValue.Obj)["adPlacements"])
    }

    @Test
    fun `conserva intactos los bloques necesarios para reproducir`() {
        val before = obj(sample)
        val after = obj(filter.filter(sample).body)

        for (key in PlayerResponseFilter.DEFAULT_PROTECTED_KEYS) {
            assertEquals("cambio la clave protegida $key", before[key], after[key])
        }
        val formats = (after["streamingData"] as JsonValue.Obj)["adaptiveFormats"] as JsonValue.Arr
        val first = formats.items.first() as JsonValue.Obj
        assertEquals(JsonValue.Num("251"), first["itag"])
        assertEquals(JsonValue.Str("3521456"), first["contentLength"])
        assertEquals(JsonValue.Num("-1.2299995"), first["loudnessDb"])
    }

    @Test
    fun `informa de las rutas eliminadas`() {
        val removed = filter.filter(sample).removedPaths

        assertTrue(removed.contains("adPlacements"))
        assertTrue(removed.contains("adSlots"))
        assertTrue(removed.contains("playerAds"))
        assertTrue(removed.contains("adBreakHeartbeatParams"))
        assertTrue(removed.contains("playbackTracking.adParams"))
        assertEquals(5, removed.size)
    }

    @Test
    fun `sin anuncios devuelve el cuerpo original sin reserializar`() {
        val clean = """{"streamingData":{"a":1},   "videoDetails":{"b":2}}"""
        val result = filter.filter(clean)

        assertFalse(result.changed)
        assertSame(clean, result.body)
        assertEquals(0, result.removedCount)
    }

    @Test
    fun `un cuerpo no JSON se devuelve intacto`() {
        val html = "<html><body>error 500</body></html>"
        val result = filter.filter(html)

        assertFalse(result.changed)
        assertSame(html, result.body)
    }

    @Test
    fun `limpia arrays de objetos`() {
        val json = """{"contents":[{"adSlots":[1]},{"ok":true}]}"""
        val result = filter.filter(json)

        assertEquals(listOf("contents[0].adSlots"), result.removedPaths)
        assertEquals("""{"contents":[{},{"ok":true}]}""", result.body)
    }

    @Test
    fun `las claves configurables se respetan`() {
        val custom = PlayerResponseFilter(adKeys = setOf("miAnuncio"), protectedKeys = setOf("mio"))
        val result = custom.filter("""{"miAnuncio":1,"mio":{"miAnuncio":2},"adSlots":3}""")

        assertEquals("""{"mio":{"miAnuncio":2},"adSlots":3}""", result.body)
        assertEquals(listOf("miAnuncio"), result.removedPaths)
    }
}
