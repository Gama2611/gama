package com.gama.mdm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Contenido del QR que muestra la consola: {"gama":1,"s":servidor,"h":huella,"c":código}. */
@Serializable
data class QrRegistro(val gama: Int, val s: String, val h: String? = null, val c: String)

@Serializable
data class PeticionRegistro(
    val codigo: String,
    val fabricante: String?,
    val modelo: String?,
    @SerialName("version_android") val versionAndroid: String?,
    val identificador: String?,
    @SerialName("version_mdm") val versionMdm: String?,
)

@Serializable
data class RespuestaRegistro(
    @SerialName("dispositivo_id") val dispositivoId: Int,
    val token: String,
    val nombre: String,
    val kiosco: Boolean,
    @SerialName("paquete_app") val paqueteApp: String,
    @SerialName("intervalo_segundos") val intervaloSegundos: Int = 60,
)

@Serializable
data class ResultadoComando(val id: Long, val exito: Boolean, val detalle: String? = null)

@Serializable
data class Latido(
    @SerialName("bateria_pct") val bateriaPct: Int?,
    val cargando: Boolean?,
    @SerialName("almacenamiento_libre_mb") val almacenamientoLibreMb: Int?,
    @SerialName("kiosco_activo") val kioscoActivo: Boolean,
    @SerialName("propietario_dispositivo") val propietarioDispositivo: Boolean,
    @SerialName("version_android") val versionAndroid: String,
    @SerialName("version_mdm") val versionMdm: String,
    @SerialName("version_app") val versionApp: String?,
    val resultados: List<ResultadoComando>,
)

@Serializable
data class ComandoEquipo(val id: Long, val tipo: String, val datos: JsonObject? = null)

@Serializable
data class RespuestaLatido(
    val nombre: String,
    val kiosco: Boolean,
    @SerialName("paquete_app") val paqueteApp: String,
    @SerialName("intervalo_segundos") val intervaloSegundos: Int = 60,
    val comandos: List<ComandoEquipo> = emptyList(),
)
