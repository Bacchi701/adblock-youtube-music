package com.adblocker.ytmusic.domain.player

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson

/**
 * Elimina las secciones de publicidad del cuerpo de `youtubei/v1/player`.
 *
 * Invariantes que garantizan que no se rompe la reproduccion:
 *  1. Las claves de [protectedKeys] (`streamingData`, `videoDetails`, ...) no se tocan
 *     ni se recorren: salen tal cual llegaron.
 *  2. Si el cuerpo no es JSON valido, se devuelve intacto (mejor un anuncio que un fallo).
 *  3. Si no habia nada que quitar, se devuelve la cadena original sin reserializar.
 */
class PlayerResponseFilter(
    private val adKeys: Set<String> = DEFAULT_AD_KEYS,
    private val protectedKeys: Set<String> = DEFAULT_PROTECTED_KEYS,
    private val maxDepth: Int = MiniJson.DEFAULT_MAX_DEPTH,
) {

    /** `"adSlots"` con comillas: la clave siempre aparece asi en el cuerpo sin formatear. */
    private val adKeyTokens: List<String> = adKeys.map { "\"$it\"" }

    fun filter(body: String): PlayerFilterResult {
        // Si ninguna clave de anuncio aparece siquiera como texto, no hay nada que quitar:
        // un barrido de la cadena sale mucho mas barato que parsear y reserializar el JSON.
        if (adKeyTokens.none { body.contains(it) }) {
            return PlayerFilterResult(body, changed = false)
        }

        val root = MiniJson.parseOrNull(body, maxDepth)
            ?: return PlayerFilterResult(body, changed = false)

        val removed = mutableListOf<String>()
        strip(root, StringBuilder(), removed)
        if (removed.isEmpty()) return PlayerFilterResult(body, changed = false)

        // El resultado tiene el tamano del original menos los anuncios: reservar de golpe
        // ahorra las copias sucesivas del StringBuilder sobre cuerpos de cientos de KB.
        return PlayerFilterResult(MiniJson.write(root, body.length), removed)
    }

    /**
     * [path] se construye de forma incremental y solo se materializa en cadena cuando hay algo
     * que eliminar: recorrer un playerResponse concatenando rutas generaba una cadena por clave.
     */
    private fun strip(value: JsonValue, path: StringBuilder, removed: MutableList<String>) {
        when (value) {
            is JsonValue.Obj -> {
                val iterator = value.entries.entries.iterator()
                while (iterator.hasNext()) {
                    val key = iterator.next().key
                    if (key in adKeys) {
                        val mark = path.length
                        path.appendKey(key)
                        removed += path.toString()
                        path.setLength(mark)
                        iterator.remove()
                    }
                }
                for ((key, child) in value.entries) {
                    // Subarbol intocable: ni se elimina ni se recorre.
                    if (key in protectedKeys || !child.isContainer()) continue
                    val mark = path.length
                    path.appendKey(key)
                    strip(child, path, removed)
                    path.setLength(mark)
                }
            }
            is JsonValue.Arr -> value.items.forEachIndexed { index, item ->
                if (!item.isContainer()) return@forEachIndexed
                val mark = path.length
                path.append('[').append(index).append(']')
                strip(item, path, removed)
                path.setLength(mark)
            }
            else -> Unit
        }
    }

    private fun JsonValue.isContainer(): Boolean = this is JsonValue.Obj || this is JsonValue.Arr

    private fun StringBuilder.appendKey(key: String) {
        if (isNotEmpty()) append('.')
        append(key)
    }

    companion object {
        /** Claves que solo contienen publicidad en las respuestas de InnerTube. */
        val DEFAULT_AD_KEYS = setOf(
            "adPlacements",
            "adSlots",
            "playerAds",
            "adBreakHeartbeatParams",
            "adParams",
        )

        /** Claves imprescindibles para reproducir: se dejan exactamente como llegan. */
        val DEFAULT_PROTECTED_KEYS = setOf(
            "streamingData",
            "videoDetails",
            "playabilityStatus",
            "playerConfig",
            "captions",
            "storyboards",
            "microformat",
        )
    }
}
