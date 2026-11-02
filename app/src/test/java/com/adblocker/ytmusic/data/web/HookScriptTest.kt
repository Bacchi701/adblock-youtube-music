package com.adblocker.ytmusic.data.web

import com.adblocker.ytmusic.core.json.JsonValue
import com.adblocker.ytmusic.core.json.MiniJson
import com.adblocker.ytmusic.domain.player.PlayerResponseFilter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class HookScriptTest {

    private val config = HookConfig(
        adKeys = PlayerResponseFilter.DEFAULT_AD_KEYS,
        protectedKeys = PlayerResponseFilter.DEFAULT_PROTECTED_KEYS,
        bridgeName = PlayerBridge.NAME,
        debug = false,
    )

    private fun asset(path: String) = File(NodeRunner.ASSETS_DIR, path).readText()

    private fun strings(value: JsonValue?): Set<String> =
        (value as JsonValue.Arr).items.map { (it as JsonValue.Str).value }.toSet()

    @Test
    fun `la configuracion lleva exactamente las claves de Kotlin`() {
        val json = MiniJson.parse(HookScript.configJson(config)) as JsonValue.Obj

        assertEquals(PlayerResponseFilter.DEFAULT_AD_KEYS, strings(json["adKeys"]))
        assertEquals(PlayerResponseFilter.DEFAULT_PROTECTED_KEYS, strings(json["protectedKeys"]))
        assertEquals(JsonValue.Str("NativeShell"), json["bridge"])
        assertEquals(JsonValue.Bool(false), json["debug"])
    }

    @Test
    fun `todo queda dentro de una funcion y solo se publica el objeto de mandos`() {
        val script = HookScript.build("/*poda*/", "/*hook*/", "/*shell*/", config)

        assertTrue(script.startsWith("(function (config) {"))
        assertTrue(script.trimEnd().endsWith("})(${HookScript.configJson(config)});"))
        // Solo en el documento principal y una sola vez.
        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("Object.defineProperty(window, '${HookScript.CONTROL_OBJECT}'"))
        // Orden: la poda se define antes de que el hook la use.
        assertTrue(script.indexOf("/*poda*/") < script.indexOf("/*hook*/"))
        assertTrue(script.indexOf("/*hook*/") < script.indexOf("/*shell*/"))
    }

    @Test
    fun `los assets reales forman un script valido`() {
        assumeTrue("Node.js no esta instalado: se omite la comprobacion de sintaxis", NodeRunner.isAvailable)
        val script = HookScript.build(
            prune = asset(HookScript.PRUNE_ASSET),
            hook = asset(HookScript.HOOK_ASSET),
            shell = asset(HookScript.SHELL_ASSET),
            config = config,
        )
        val checker = NodeRunner.tempScript(
            "new (require('vm').Script)(require('fs').readFileSync(process.argv[2], 'utf8')); console.log('ok');",
        )

        assertEquals("ok", NodeRunner.run(checker, NodeRunner.tempScript(script).absolutePath).trim())
    }

    @Test
    fun `la pagina no ve nombres que delaten un bloqueador`() {
        val all = listOf(HookScript.PRUNE_ASSET, HookScript.HOOK_ASSET, HookScript.SHELL_ASSET).joinToString("\n") { asset(it) }

        assertTrue("AdBlockBridge" !in all)
        assertTrue("__adBlock" !in all)
        assertTrue("AdBlock" !in PlayerBridge.NAME && "AdBlock" !in HookScript.CONTROL_OBJECT)
    }
}
