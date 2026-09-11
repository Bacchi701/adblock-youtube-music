package com.adblocker.ytmusic.data.blocklist

import com.adblocker.ytmusic.domain.model.BlocklistRules

/**
 * Convierte listas de texto en reglas normalizadas. Logica pura y cubierta por JUnit.
 *
 * Formatos aceptados:
 *  - hosts:      `0.0.0.0 ads.example.com` / `127.0.0.1 ads.example.com`
 *  - dominio:    `ads.example.com`
 *  - excepcion:  `@@musica.example.com`
 *  - comentario: lineas que empiezan por `#` o `!`, y todo lo que siga a `#`
 */
object BlocklistParser {

    private val HOSTS_PREFIXES = setOf("0.0.0.0", "127.0.0.1", "::1", "::")
    private val VALID_DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    fun parse(lines: Sequence<String>): BlocklistRules {
        val blocked = LinkedHashSet<String>()
        val allowed = LinkedHashSet<String>()

        for (rawLine in lines) {
            val line = rawLine.substringBefore('#').substringBefore('!').trim()
            if (line.isEmpty()) continue

            val isException = line.startsWith("@@")
            val body = if (isException) line.removePrefix("@@").trim() else line

            val domain = extractDomain(body) ?: continue
            if (isException) allowed += domain else blocked += domain
        }
        return BlocklistRules(blocked, allowed)
    }

    fun parse(text: String): BlocklistRules = parse(text.lineSequence())

    private fun extractDomain(body: String): String? {
        val tokens = body.split(' ', '\t').filter(String::isNotEmpty)
        val candidate = when {
            tokens.isEmpty() -> return null
            tokens.size >= 2 && tokens[0] in HOSTS_PREFIXES -> tokens[1]
            tokens.size == 1 -> tokens[0]
            else -> return null
        }
        val normalized = candidate.trim().trim('.').lowercase()
        return normalized.takeIf { VALID_DOMAIN.matches(it) }
    }
}
