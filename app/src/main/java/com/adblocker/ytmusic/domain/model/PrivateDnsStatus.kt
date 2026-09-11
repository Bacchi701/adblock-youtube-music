package com.adblocker.ytmusic.domain.model

/**
 * Estado del DNS privado (DNS-over-TLS) del sistema, que determina si el filtro
 * llega a ver las consultas.
 */
sealed interface PrivateDnsStatus {

    /** Sin DoT: las consultas viajan en UDP/53 y entran en el tunel. */
    data object Off : PrivateDnsStatus

    /**
     * Modo automatico: el sistema intenta DoT y cae a texto claro si falla. Contra el DNS
     * virtual del tunel el sondeo DoT no responde, asi que el filtro sigue viendo todo.
     */
    data object Opportunistic : PrivateDnsStatus

    /**
     * Modo estricto con [hostname] fijo: el resolutor abre TLS al puerto 853 del servidor
     * indicado, fuera de la ruta del tunel, y no admite reserva en texto claro.
     * El filtro DNS no puede ver ni bloquear esas consultas.
     */
    data class Strict(val hostname: String) : PrivateDnsStatus

    /** `true` cuando las consultas esquivan el filtro DNS. */
    val bypassesFilter: Boolean get() = this is Strict
}
