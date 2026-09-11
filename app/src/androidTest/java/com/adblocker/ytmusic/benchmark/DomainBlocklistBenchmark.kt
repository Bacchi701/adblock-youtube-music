package com.adblocker.ytmusic.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adblocker.ytmusic.domain.filter.DomainBlocklist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Coste de decidir una consulta DNS. Se mide contra [LegacyDomainBlocklist], copia de la
 * implementacion previa a la optimizacion, para cuantificar la diferencia real en ART.
 */
@RunWith(AndroidJUnit4::class)
class DomainBlocklistBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val blockedRules = buildList {
        add("doubleclick.net")
        add("googlesyndication.com")
        add("app-measurement.com")
        repeat(RULE_COUNT) { add("tracker$it.example.com") }
    }

    private val allowedRules = listOf("googlevideo.com", "youtubei.googleapis.com")

    private val engine = DomainBlocklist(blockedRules, allowedRules)
    private val legacy = LegacyDomainBlocklist(blockedRules, allowedRules)

    /** Nombres tal y como salen del parser DNS: ya normalizados. */
    private val queries = listOf(
        "googleads.g.doubleclick.net", // bloqueado por subdominio
        "music.youtube.com", // permitido, sin coincidencia
        "rr3---sn-x.googlevideo.com", // permitido por excepcion
        "tracker4321.example.com", // bloqueado exacto
    )

    @Test
    fun evaluate() = benchmarkRule.measureRepeated {
        for (index in queries.indices) {
            engine.isBlocked(queries[index])
        }
    }

    @Test
    fun evaluateLegacy() = benchmarkRule.measureRepeated {
        for (index in queries.indices) {
            legacy.isBlocked(queries[index])
        }
    }

    /** Entrada sin normalizar: obliga al camino lento en ambas implementaciones. */
    @Test
    fun evaluateUnnormalized() = benchmarkRule.measureRepeated {
        engine.isBlocked(" GoogleAds.G.DoubleClick.NET. ")
    }

    private class LegacyDomainBlocklist(
        blockedRules: Collection<String>,
        allowedRules: Collection<String>,
    ) {
        private val blocked: Set<String> = blockedRules.mapNotNullTo(HashSet(), ::normalize)
        private val allowed: Set<String> = allowedRules.mapNotNullTo(HashSet(), ::normalize)

        fun isBlocked(domain: String): Boolean {
            val normalized = normalize(domain) ?: return false
            forEachSuffix(normalized) { suffix -> if (suffix in allowed) return false }
            forEachSuffix(normalized) { suffix -> if (suffix in blocked) return true }
            return false
        }

        private inline fun forEachSuffix(domain: String, action: (String) -> Unit) {
            var index = 0
            while (index in domain.indices) {
                action(domain.substring(index))
                val dot = domain.indexOf('.', index)
                if (dot < 0) return
                index = dot + 1
            }
        }

        private companion object {
            fun normalize(raw: String): String? =
                raw.trim().trim('.').lowercase().ifEmpty { null }
        }
    }

    private companion object {
        const val RULE_COUNT = 5_000
    }
}
