package com.adblocker.ytmusic.data.web

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import java.io.File
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * La poda que corre en la pagina (`json_prune.js`) debe dar el mismo JSON que la referencia
 * Kotlin ([PlayerResponseFilter]) para las mismas muestras. Se ejecuta con Node.js.
 */
class JsonPruneScriptTest {

    private val sample: String =
        requireNotNull(javaClass.classLoader?.getResourceAsStream("player_response_sample.json"))
            .bufferedReader()
            .use { it.readText() }

    private val runner by lazy {
        NodeRunner.tempScript(
            """
            const fs = require('fs');
            const vm = require('vm');
            const context = vm.createContext({});
            vm.runInContext(fs.readFileSync(process.argv[2], 'utf8'), context);
            let input = '';
            process.stdin.on('data', (chunk) => { input += chunk; });
            process.stdin.on('end', () => {
              const cases = JSON.parse(input);
              const results = cases.map((c) => context.createPruner(c.adKeys, c.protectedKeys).text(c.text));
              process.stdout.write(JSON.stringify(results));
            });
            """.trimIndent(),
        )
    }

    @Before
    fun requireNode() {
        assumeTrue("Node.js no esta instalado: se omite la prueba del script", NodeRunner.isAvailable)
    }

    private data class Pruned(val text: String, val removed: Int)

    private fun prune(
        texts: List<String>,
        adKeys: Set<String> = PlayerResponseFilter.DEFAULT_AD_KEYS,
        protectedKeys: Set<String> = PlayerResponseFilter.DEFAULT_PROTECTED_KEYS,
    ): List<Pruned> {
        val cases = JsonValue.Arr(
            texts.map { text ->
                JsonValue.Obj(
                    linkedMapOf(
                        "text" to JsonValue.Str(text),
                        "adKeys" to JsonValue.Arr(adKeys.map(JsonValue::Str).toMutableList()),
                        "protectedKeys" to JsonValue.Arr(protectedKeys.map(JsonValue::Str).toMutableList()),
                    ),
                )
            }.toMutableList(),
        )
        val pruneAsset = File(NodeRunner.ASSETS_DIR, HookScript.PRUNE_ASSET)
        val output = MiniJson.parse(NodeRunner.run(runner, pruneAsset.absolutePath, stdin = MiniJson.write(cases)))
        return (output as JsonValue.Arr).items.map { item ->
            val obj = item as JsonValue.Obj
            Pruned((obj["text"] as JsonValue.Str).value, (obj["removed"] as JsonValue.Num).raw.toInt())
        }
    }

    /** JS y Kotlin pueden escribir el mismo numero de forma distinta (1.0 frente a 1). */
    private fun normalized(value: JsonValue): JsonValue = when (value) {
        is JsonValue.Obj -> JsonValue.Obj(value.entries.mapValuesTo(LinkedHashMap()) { normalized(it.value) })
        is JsonValue.Arr -> JsonValue.Arr(value.items.map(::normalized).toMutableList())
        is JsonValue.Num -> JsonValue.Num(BigDecimal(value.raw).stripTrailingZeros().toPlainString())
        else -> value
    }

    private fun assertSameAsKotlin(input: String, adKeys: Set<String>, protectedKeys: Set<String>, js: Pruned) {
        val kotlin = PlayerResponseFilter(adKeys, protectedKeys).filter(input)
        assertEquals("claves quitadas", kotlin.removedCount, js.removed)
        if (!kotlin.changed) {
            assertEquals("sin cambios debe salir la cadena original", input, js.text)
        } else {
            assertEquals(normalized(MiniJson.parse(kotlin.body)), normalized(MiniJson.parse(js.text)))
        }
    }

    @Test
    fun `poda la muestra real igual que la referencia Kotlin`() {
        val js = prune(listOf(sample)).single()

        assertEquals(5, js.removed)
        assertSameAsKotlin(sample, PlayerResponseFilter.DEFAULT_AD_KEYS, PlayerResponseFilter.DEFAULT_PROTECTED_KEYS, js)
    }

    @Test
    fun `respeta las claves protegidas y los arrays igual que Kotlin`() {
        val inputs = listOf(
            """{"contents":[{"adSlots":[1]},{"ok":true}]}""",
            """{"streamingData":{"adPlacements":[1]},"playerAds":[],"x":{"adParams":"p","y":[{"adSlots":2}]}}""",
            """{"playerConfig":{"adSlots":1},"adBreakHeartbeatParams":"h","n":1.50}""",
        )
        prune(inputs).zip(inputs).forEach { (js, input) ->
            assertSameAsKotlin(input, PlayerResponseFilter.DEFAULT_AD_KEYS, PlayerResponseFilter.DEFAULT_PROTECTED_KEYS, js)
        }
    }

    @Test
    fun `sin anuncios o con un cuerpo roto devuelve el original`() {
        val clean = """{"streamingData":{"a":1},   "videoDetails":{"b":2}}"""
        val html = "<html><body>error 500</body></html>"
        val truncated = """{"adPlacements":[{"a":1}"""
        val results = prune(listOf(clean, html, truncated))

        assertEquals(listOf(Pruned(clean, 0), Pruned(html, 0), Pruned(truncated, 0)), results)
    }

    @Test
    fun `usa exactamente las claves que le pasa Kotlin`() {
        val input = """{"miAnuncio":1,"mio":{"miAnuncio":2},"adSlots":3}"""
        val js = prune(listOf(input), adKeys = setOf("miAnuncio"), protectedKeys = setOf("mio")).single()

        assertSameAsKotlin(input, setOf("miAnuncio"), setOf("mio"), js)
        assertEquals(1, js.removed)
    }
}
