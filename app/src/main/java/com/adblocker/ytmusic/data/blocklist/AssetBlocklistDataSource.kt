package com.adblocker.ytmusic.data.blocklist

import android.content.res.AssetManager
import com.adblocker.ytmusic.domain.model.BlocklistRules

/** Lee las listas empaquetadas en `assets/blocklists`. Unica fuente de reglas: nunca la red. */
class AssetBlocklistDataSource(
    private val assets: AssetManager,
    private val directory: String = DIRECTORY,
) {

    fun listFiles(): List<String> = assets.list(directory).orEmpty()
        .filter { it.endsWith(".txt") }
        .sorted()

    fun read(fileName: String): BlocklistRules =
        assets.open("$directory/$fileName").bufferedReader().use { reader ->
            BlocklistParser.parse(reader.lineSequence())
        }

    private companion object {
        const val DIRECTORY = "blocklists"
    }
}
