package com.adblocker.ytmusic.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import com.adblocker.ytmusic.domain.player.PlayerFilterResult
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Saneado de un `playerResponse` realista (cientos de KB). Se compara con [legacyFilter],
 * que reproduce el recorrido anterior: una cadena de ruta por cada clave del arbol.
 */
@RunWith(AndroidJUnit4::class)
class PlayerResponseFilterBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val filter = PlayerResponseFilter()
    private val body = buildPlayerResponse()

    @Test
    fun sanityCheck() {
        val result = filter.filter(body)
        assertTrue("el cuerpo sintetico debe contener anuncios", result.changed)
        assertTrue(result.body.length < body.length)
    }

    @Test
    fun filterWithAds() = benchmarkRule.measureRepeated {
        filter.filter(body)
    }

    @Test
    fun filterWithAdsLegacy() = benchmarkRule.measureRepeated {
        legacyFilter(body)
    }

    /** Caso mas frecuente en reproduccion continua: nada que quitar. */
    @Test
    fun filterWithoutAds() = benchmarkRule.measureRepeated {
        filter.filter(cleanBody)
    }

    private val cleanBody = filter.filter(body).body

    // --- Implementacion previa a la optimizacion, para comparar ---

    private fun legacyFilter(source: String): PlayerFilterResult {
        val root = MiniJson.parseOrNull(source) ?: return PlayerFilterResult(source, changed = false)
        val removed = mutableListOf<String>()
        legacyStrip(root, "", removed)
        if (removed.isEmpty()) return PlayerFilterResult(source, changed = false)
        return PlayerFilterResult(MiniJson.write(root), removed)
    }

    private fun legacyStrip(value: JsonValue, path: String, removed: MutableList<String>) {
        when (value) {
            is JsonValue.Obj -> {
                val iterator = value.entries.entries.iterator()
                val pending = mutableListOf<Pair<String, JsonValue>>()
                while (iterator.hasNext()) {
                    val (key, child) = iterator.next()
                    when {
                        key in PlayerResponseFilter.DEFAULT_AD_KEYS -> {
                            removed += if (path.isEmpty()) key else "$path.$key"
                            iterator.remove()
                        }
                        key in PlayerResponseFilter.DEFAULT_PROTECTED_KEYS -> Unit
                        else -> pending += key to child
                    }
                }
                pending.forEach { (key, child) ->
                    legacyStrip(child, if (path.isEmpty()) key else "$path.$key", removed)
                }
            }
            is JsonValue.Arr -> value.items.forEachIndexed { index, item ->
                legacyStrip(item, "$path[$index]", removed)
            }
            else -> Unit
        }
    }

    /** Genera un cuerpo con la forma y el tamano de una respuesta real de InnerTube. */
    private fun buildPlayerResponse(): String {
        val formats = (0 until FORMAT_COUNT).joinToString(",") { index ->
            """{"itag":${251 + index},"url":"https://rr3---sn-test.googlevideo.com/videoplayback?expire=1754700000&itag=${251 + index}&source=youtube&requiressl=yes","mimeType":"audio/webm; codecs=\"opus\"","bitrate":${141235 + index},"contentLength":"${3521456 + index}","approxDurationMs":"212345","loudnessDb":-1.2299995}"""
        }
        val shelves = (0 until SHELF_COUNT).joinToString(",") { index ->
            """{"musicShelfRenderer":{"title":{"runs":[{"text":"Seccion $index"}]},"contents":[${
                (0 until ITEM_COUNT).joinToString(",") { item ->
                    """{"musicResponsiveListItemRenderer":{"trackingParams":"CAAQu2kiEwjT$item","flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Cancion $item","navigationEndpoint":{"watchEndpoint":{"videoId":"vid$item"}}}]}}}],"menu":{"menuRenderer":{"items":[{"menuNavigationItemRenderer":{"text":{"runs":[{"text":"Reproducir"}]}}}]}}}}"""
                }
            }]}}"""
        }
        return """
            {"responseContext":{"visitorData":"Cgt0ZXN0LXZpc2l0b3I%3D"},
             "playabilityStatus":{"status":"OK"},
             "streamingData":{"expiresInSeconds":"21540","adaptiveFormats":[$formats]},
             "playerAds":[{"playerLegacyDesktopWatchAdsRenderer":{"playerAdParams":{"showContentThumbnail":true}}}],
             "adPlacements":[{"adPlacementRenderer":{"config":{"adPlacementConfig":{"kind":"AD_PLACEMENT_KIND_START"}}}}],
             "adSlots":[{"adSlotRenderer":{"adSlotMetadata":{"slotId":"0"}}}],
             "adBreakHeartbeatParams":"Q2dJSUJCQUJHQUU9",
             "playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"https://s.youtube.com/api/stats/playback?cl=1"},"adParams":"vpv=3"},
             "contents":{"sections":[$shelves]},
             "videoDetails":{"videoId":"dQw4w9WgXcQ","title":"Cancion de prueba","lengthSeconds":"212"}}
        """.trimIndent()
    }

    private companion object {
        const val FORMAT_COUNT = 12
        const val SHELF_COUNT = 20
        const val ITEM_COUNT = 25
    }
}
