package com.adblocker.ytmusic.domain.player

/**
 * Resultado de sanear una respuesta de `youtubei/v1/player`.
 *
 * @param body cuerpo a entregar al reproductor. Si [changed] es `false` es el original intacto.
 * @param removedPaths rutas JSON eliminadas, en orden de aparicion (para diagnostico local).
 */
data class PlayerFilterResult(
    val body: String,
    val removedPaths: List<String> = emptyList(),
    val changed: Boolean = removedPaths.isNotEmpty(),
) {
    val removedCount: Int get() = removedPaths.size
}
