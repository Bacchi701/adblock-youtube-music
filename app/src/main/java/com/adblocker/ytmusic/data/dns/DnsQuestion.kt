package com.adblocker.ytmusic.data.dns

/** Primera pregunta de un mensaje DNS. */
data class DnsQuestion(
    val name: String,
    val type: Int,
    val dnsClass: Int,
    /** Offset (dentro del payload DNS) del primer byte posterior a la seccion de preguntas. */
    val endOffset: Int,
) {
    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
    }
}
