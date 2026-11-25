package com.gama.mdm

import com.gama.comun.Confianza
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

val JsonMdm = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/**
 * Lee y valida el QR de registro. Devuelve null si no es un QR de GAMA válido:
 * versión 1, servidor https (o http solo sin huella), huella bien formada y código de 10 caracteres.
 */
fun leerQr(texto: String): QrRegistro? {
    val qr = runCatching { JsonMdm.decodeFromString(QrRegistro.serializer(), texto.trim()) }.getOrNull() ?: return null
    if (qr.gama != 1) return null
    val https = qr.s.startsWith("https://")
    if (!https && !qr.s.startsWith("http://")) return null
    if (qr.h != null && (!https || !Confianza.esValida(qr.h))) return null
    val codigo = normalizarCodigo(qr.c)
    if (codigo.length != 10) return null
    return qr.copy(s = qr.s.trimEnd('/'), h = qr.h?.let(Confianza::legible), c = codigo)
}

/** "sshf3-kbvw4 " → "SSHF3KBVW4". */
fun normalizarCodigo(codigo: String): String = codigo.uppercase().filter { it.isLetterOrDigit() }

fun sha256(archivo: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    archivo.inputStream().use { entrada ->
        val bufer = ByteArray(1 shl 16)
        while (true) {
            val n = entrada.read(bufer)
            if (n < 0) break
            digest.update(bufer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
