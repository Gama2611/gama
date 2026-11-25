package com.gama.mdm

import com.gama.comun.Confianza
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class ErrorMdm(val codigo: Int, mensaje: String) : Exception(mensaje)

/** Cliente del servidor GAMA para el MDM. Con huella, solo habla con ese certificado. */
class ClienteMdm(servidor: String, huella: String?) {
    private val base = servidor.trimEnd('/') + "/api/v1/dispositivos"
    private val tipoJson = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .let { b -> if (huella != null) Confianza.fijar(b, huella) else b }
        .build()

    suspend fun registrar(p: PeticionRegistro): RespuestaRegistro =
        post("/registrar", null, JsonMdm.encodeToString(PeticionRegistro.serializer(), p), RespuestaRegistro.serializer())

    suspend fun latido(token: String, l: Latido): RespuestaLatido =
        post("/latido", token, JsonMdm.encodeToString(Latido.serializer(), l), RespuestaLatido.serializer())

    /** Descarga un APK del servidor a [destino]. */
    suspend fun descargar(token: String, archivo: String, destino: File) = withContext(Dispatchers.IO) {
        val peticion = Request.Builder().url("$base/apk/$archivo").header("Authorization", "Dispositivo $token").get().build()
        ejecutar(peticion) { r ->
            val cuerpo = r.body ?: throw ErrorMdm(r.code, "Respuesta vacía")
            destino.outputStream().use { salida -> cuerpo.byteStream().use { it.copyTo(salida) } }
        }
    }

    private suspend fun <T> post(ruta: String, token: String?, json: String, s: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val b = Request.Builder().url(base + ruta).post(json.toRequestBody(tipoJson))
            if (token != null) b.header("Authorization", "Dispositivo $token")
            ejecutar(b.build()) { r -> JsonMdm.decodeFromString(s, r.body?.string().orEmpty()) }
        }

    private fun <T> ejecutar(peticion: Request, leer: (okhttp3.Response) -> T): T {
        val respuesta = try {
            http.newCall(peticion).execute()
        } catch (e: SSLException) {
            throw ErrorMdm(0, "El certificado del servidor no coincide con el registrado")
        } catch (e: IOException) {
            throw ErrorMdm(0, "Sin conexión con el servidor")
        }
        return respuesta.use { r ->
            if (!r.isSuccessful) {
                val detalle = r.body?.string().orEmpty()
                val mensaje = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(detalle)?.groupValues?.get(1)
                throw ErrorMdm(r.code, mensaje ?: "Error del servidor (${r.code})")
            }
            leer(r)
        }
    }
}
