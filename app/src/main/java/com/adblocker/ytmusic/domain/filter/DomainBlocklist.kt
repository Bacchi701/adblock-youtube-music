package com.adblocker.ytmusic.domain.filter

import com.adblocker.ytmusic.domain.model.FilterDecision

/**
 * Motor de coincidencia de dominios. Logica pura (sin dependencias de Android)
 * para poder cubrirla al 100% con pruebas JUnit locales.
 *
 * Reglas soportadas:
 *  - `ads.example.com`  -> bloquea el dominio y cualquier subdominio suyo.
 *  - `@@allowed.com`    -> excepcion; gana siempre sobre cualquier bloqueo.
 *
 * Se evalua una vez por consulta DNS, asi que evita basura: sin excepciones no materializa
 * ninguna lista, y con ellas genera los sufijos una sola vez en lugar de una por pasada.
 */
class DomainBlocklist(
    blockedRules: Collection<String>,
    allowedRules: Collection<String> = emptySet(),
) {

    private val blocked: Set<String> = blockedRules.mapNotNullTo(HashSet(), ::normalize)
    private val allowed: Set<String> = allowedRules.mapNotNullTo(HashSet(), ::normalize)

    val size: Int get() = blocked.size

    fun evaluate(domain: String): FilterDecision {
        val normalized = normalize(domain) ?: return FilterDecision.Allow

        if (allowed.isEmpty()) {
            forEachSuffix(normalized) { suffix ->
                if (suffix in blocked) return FilterDecision.Block(suffix)
            }
            return FilterDecision.Allow
        }

        // Una excepcion en cualquier nivel gana sobre un bloqueo mas especifico, asi que hay
        // que recorrer todos los sufijos antes de bloquear. Basta con recordar el primer
        // bloqueo por el camino: una sola pasada y sin estructuras intermedias.
        var match: String? = null
        forEachSuffix(normalized) { suffix ->
            if (suffix in allowed) return FilterDecision.Allow
            if (match == null && suffix in blocked) match = suffix
        }
        return match?.let(FilterDecision::Block) ?: FilterDecision.Allow
    }

    fun isBlocked(domain: String): Boolean = evaluate(domain).isBlocked

    /** Recorre `a.b.c`, `b.c`, `c` para permitir coincidencias por subdominio. */
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
        /**
         * Devuelve la misma cadena cuando ya esta normalizada, que es el caso de las consultas
         * que llegan del parser DNS: asi el camino caliente no copia ninguna cadena.
         */
        fun normalize(raw: String): String? {
            if (isNormalized(raw)) return raw
            val trimmed = raw.trim().trim('.').lowercase()
            return trimmed.ifEmpty { null }
        }

        private fun isNormalized(raw: String): Boolean {
            if (raw.isEmpty()) return false
            val first = raw.first()
            val last = raw.last()
            if (first.isWhitespace() || last.isWhitespace()) return false
            if (first == '.' || last == '.') return false
            for (index in raw.indices) {
                val char = raw[index]
                // Cualquier caracter que cambie al pasar a minusculas obliga al camino lento.
                if (char != char.lowercaseChar()) return false
            }
            return true
        }
    }
}
