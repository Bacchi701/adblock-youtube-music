package com.adblocker.ytmusic.core.json

/**
 * Arbol JSON minimo. Se escribe a mano porque el proyecto prohibe librerias de terceros
 * y `org.json` no existe en las pruebas unitarias de JVM.
 *
 * Los numeros se guardan como texto original ([Num.raw]) para que el ciclo
 * parsear -> serializar no altere precisiones ni formatos del servidor.
 */
sealed interface JsonValue {

    data class Obj(val entries: MutableMap<String, JsonValue> = LinkedHashMap()) : JsonValue {
        operator fun get(key: String): JsonValue? = entries[key]
    }

    data class Arr(val items: MutableList<JsonValue> = mutableListOf()) : JsonValue

    data class Str(val value: String) : JsonValue

    data class Num(val raw: String) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue
}
