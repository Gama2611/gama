package com.gama.comun

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.X509TrustManager

/**
 * Confianza fijada en el certificado propio del servidor de planta.
 *
 * El instalador de GAMA crea un certificado autofirmado. En lugar de desactivar la verificación,
 * las apps aceptan solo el certificado cuya huella SHA-256 conocen: la reciben en el QR de registro
 * del MDM, por configuración administrada o porque la persona la confirmó contra la consola.
 * Cualquier otro certificado (un equipo que se haga pasar por el servidor) se rechaza.
 */
object Confianza {
    /** Huella con el mismo formato que muestra el servidor: "AB:CD:...", 32 bytes en hexadecimal. */
    fun huella(certificado: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificado.encoded).joinToString(":") { "%02X".format(it) }

    /** Quita separadores y pasa a mayúsculas para comparar sin importar el formato. */
    fun normalizar(huella: String): String = huella.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }

    fun esValida(huella: String): Boolean = normalizar(huella).length == 64

    /** Huella legible en grupos, para mostrarla y compararla a simple vista. */
    fun legible(huella: String): String = normalizar(huella).chunked(2).joinToString(":")

    /** Configura el cliente para hablar solo con el servidor cuya huella es [huella]. */
    fun fijar(builder: OkHttpClient.Builder, huella: String): OkHttpClient.Builder {
        require(esValida(huella)) { "Huella SHA-256 inválida" }
        val gestor = GestorFijado(normalizar(huella))
        val contexto = SSLContext.getInstance("TLS")
        contexto.init(null, arrayOf(gestor), null)
        return builder
            .sslSocketFactory(contexto.socketFactory, gestor)
            // La IP o el nombre del servidor pueden cambiar en la planta; la identidad es el certificado
            // fijado, que el gestor ya comprobó durante el saludo TLS. Aquí se vuelve a comprobar.
            .hostnameVerifier { _, sesion ->
                val hoja = runCatching { sesion.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                hoja != null && normalizar(huella(hoja)) == gestor.esperada
            }
    }

    /**
     * Conecta una vez a [servidor] (https) para leer la huella de su certificado. No envía credenciales
     * ni datos: la huella se muestra a la persona para que la compare con la consola antes de confiar.
     */
    suspend fun leerHuella(servidor: String, base: OkHttpClient = OkHttpClient()): String =
        withContext(Dispatchers.IO) {
            require(servidor.startsWith("https://")) { "Solo se lee la huella de una dirección https" }
            val captura = GestorCaptura()
            val contexto = SSLContext.getInstance("TLS")
            contexto.init(null, arrayOf(captura), null)
            val cliente = base.newBuilder()
                .sslSocketFactory(contexto.socketFactory, captura)
                .hostnameVerifier { _, _ -> true } // nada se confía todavía: solo se lee el certificado
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
            val peticion = Request.Builder().url(servidor.trimEnd('/') + "/api/v1/salud").get().build()
            cliente.newCall(peticion).execute().close()
            val hoja = captura.hoja ?: throw SSLPeerUnverifiedException("El servidor no presentó un certificado")
            huella(hoja)
        }
}

/** Acepta únicamente el certificado con la huella esperada, y solo mientras esté vigente. */
@SuppressLint("CustomX509TrustManager")
class GestorFijado(val esperada: String) : X509TrustManager {
    override fun checkServerTrusted(cadena: Array<out X509Certificate>?, tipo: String?) {
        val hoja = cadena?.firstOrNull() ?: throw CertificateException("El servidor no presentó un certificado")
        hoja.checkValidity()
        if (Confianza.normalizar(Confianza.huella(hoja)) != esperada) {
            throw CertificateException("La huella del servidor no coincide con la registrada")
        }
    }

    override fun checkClientTrusted(cadena: Array<out X509Certificate>?, tipo: String?) {
        throw CertificateException("Este gestor solo verifica servidores")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Solo para leer la huella antes de confiar: guarda el certificado y no se usa para enviar datos. */
@SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
private class GestorCaptura : X509TrustManager {
    @Volatile var hoja: X509Certificate? = null

    override fun checkServerTrusted(cadena: Array<out X509Certificate>?, tipo: String?) {
        hoja = cadena?.firstOrNull()
    }

    override fun checkClientTrusted(cadena: Array<out X509Certificate>?, tipo: String?) {
        throw CertificateException("Este gestor solo lee servidores")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
