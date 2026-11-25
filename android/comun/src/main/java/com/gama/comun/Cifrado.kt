package com.gama.comun

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra secretos (tokens) con una clave AES-256-GCM que vive en el Android Keystore
 * y nunca sale del dispositivo. Cada app tiene su propio Keystore, así que la misma
 * clave lógica no se comparte entre GAMA y GAMA MDM.
 */
object Cifrado {
    private const val ALIAS = "gama_tokens"
    private const val PROVEEDOR = "AndroidKeyStore"
    private const val TRANSFORMACION = "AES/GCM/NoPadding"
    private const val BITS_ETIQUETA = 128

    private fun clave(): SecretKey {
        val almacen = KeyStore.getInstance(PROVEEDOR).apply { load(null) }
        (almacen.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
        generador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generador.generateKey()
    }

    fun cifrar(texto: String): String {
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.ENCRYPT_MODE, clave())
        val datos = cifrador.iv + cifrador.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(datos, Base64.NO_WRAP)
    }

    /** Devuelve null si el dato no se puede descifrar (por ejemplo, tras reinstalar la app). */
    fun descifrar(base64: String): String? = try {
        val datos = Base64.decode(base64, Base64.NO_WRAP)
        val iv = datos.copyOfRange(0, 12)
        val cifrador = Cipher.getInstance(TRANSFORMACION)
        cifrador.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(BITS_ETIQUETA, iv))
        String(cifrador.doFinal(datos, 12, datos.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
