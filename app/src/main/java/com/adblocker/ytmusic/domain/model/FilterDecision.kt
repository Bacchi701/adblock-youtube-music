package com.adblocker.ytmusic.domain.model

/** Resultado de evaluar un dominio contra las listas de filtrado. */
sealed interface FilterDecision {

    /** El dominio debe resolverse con normalidad. */
    data object Allow : FilterDecision

    /**
     * El dominio esta bloqueado.
     *
     * @param matchedRule regla exacta de la lista que produjo el bloqueo.
     */
    data class Block(val matchedRule: String) : FilterDecision

    val isBlocked: Boolean get() = this is Block
}
