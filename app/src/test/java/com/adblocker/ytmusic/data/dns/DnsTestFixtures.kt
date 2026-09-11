package com.adblocker.ytmusic.data.dns

import java.io.ByteArrayOutputStream

/** Construye consultas DNS reales para las pruebas (no hay builder en produccion). */
object DnsTestFixtures {

    fun query(name: String, type: Int = DnsQuestion.TYPE_A, id: Int = 0x1234): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeUShort(id)
        out.writeUShort(0x0100) // RD = 1
        out.writeUShort(1) // QDCOUNT
        out.writeUShort(0) // ANCOUNT
        out.writeUShort(0) // NSCOUNT
        out.writeUShort(0) // ARCOUNT
        for (label in name.split('.')) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        out.writeUShort(type)
        out.writeUShort(1) // IN
        return out.toByteArray()
    }

    fun readUShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun ByteArrayOutputStream.writeUShort(value: Int) {
        write(value ushr 8 and 0xFF)
        write(value and 0xFF)
    }
}
