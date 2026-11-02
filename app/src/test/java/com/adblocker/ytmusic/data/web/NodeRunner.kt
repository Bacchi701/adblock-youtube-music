package com.adblocker.ytmusic.data.web

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ejecuta JavaScript con Node.js para probar los scripts que se inyectan en la pagina. Node no
 * es dependencia del proyecto: si no esta instalado, las pruebas que lo usan se omiten (Assume).
 */
object NodeRunner {

    /** Assets de la app, relativos al modulo: Gradle ejecuta las pruebas desde `app/`. */
    val ASSETS_DIR = File("src/main/assets")

    val isAvailable: Boolean by lazy {
        runCatching {
            val process = ProcessBuilder("node", "--version").redirectErrorStream(true).start()
            process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0
        }.getOrDefault(false)
    }

    /** Ejecuta [script] con [stdin] y devuelve la salida estandar; falla si Node termina con error. */
    fun run(script: File, vararg args: String, stdin: String = ""): String {
        val process = ProcessBuilder(listOf("node", script.absolutePath) + args).start()
        process.outputStream.bufferedWriter().use { it.write(stdin) }
        val output = process.inputStream.bufferedReader().readText()
        val errors = process.errorStream.bufferedReader().readText()
        check(process.waitFor(30, TimeUnit.SECONDS)) { "Node no termino a tiempo" }
        check(process.exitValue() == 0) { "Node fallo: $errors" }
        return output
    }

    fun tempScript(source: String): File =
        File.createTempFile("node-test", ".js").apply {
            deleteOnExit()
            writeText(source)
        }
}
