package com.adblocker.ytmusic.data.web

import android.content.res.AssetManager
import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson

/** Parametros que Kotlin fija en el script: las claves salen de una unica fuente (Kotlin). */
data class HookConfig(
    val adKeys: Set<String>,
    val protectedKeys: Set<String>,
    /** Nombre del objeto que `addWebMessageListener` inyecta en la pagina. */
    val bridgeName: String,
    val debug: Boolean,
)

/**
 * Arma el script que se inyecta en document-start: los tres assets dentro de una sola funcion
 * para no dejar nada suelto en `window` salvo `__nativeShell`, que es lo que invoca Kotlin.
 */
object HookScript {

    const val PRUNE_ASSET = "js/json_prune.js"
    const val HOOK_ASSET = "js/player_hook.js"
    const val SHELL_ASSET = "js/ytm_shell.js"

    /** Nombre del objeto de mandos que Kotlin usa con `evaluateJavascript`. */
    const val CONTROL_OBJECT = "__nativeShell"

    fun load(assets: AssetManager, config: HookConfig): String = build(
        prune = assets.read(PRUNE_ASSET),
        hook = assets.read(HOOK_ASSET),
        shell = assets.read(SHELL_ASSET),
        config = config,
    )

    fun build(prune: String, hook: String, shell: String, config: HookConfig): String = buildString {
        append("(function (config) {\n'use strict';\n")
        // Solo el documento principal: los iframes del mismo origen no tienen reproductor.
        append("if (window.top !== window || window.$CONTROL_OBJECT) return;\n")
        append("var api = {};\n")
        append(prune).append('\n')
        append(hook).append('\n')
        append(shell).append('\n')
        append("Object.defineProperty(window, '$CONTROL_OBJECT', { value: Object.freeze(api) });\n")
        append("})(").append(configJson(config)).append(");\n")
    }

    fun configJson(config: HookConfig): String = MiniJson.write(
        JsonValue.Obj(
            linkedMapOf(
                "adKeys" to JsonValue.Arr(config.adKeys.map(JsonValue::Str).toMutableList()),
                "protectedKeys" to JsonValue.Arr(config.protectedKeys.map(JsonValue::Str).toMutableList()),
                "bridge" to JsonValue.Str(config.bridgeName),
                "debug" to JsonValue.Bool(config.debug),
            ),
        ),
    )

    private fun AssetManager.read(path: String): String =
        open(path).bufferedReader().use { it.readText() }
}
