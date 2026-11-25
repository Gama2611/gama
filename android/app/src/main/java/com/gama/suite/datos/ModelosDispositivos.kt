package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- GAMA Dispositivos: tabletas administradas por GAMA MDM --------------------
@Serializable
data class DispositivoMdm(
    val id: Int,
    val nombre: String,
    val fabricante: String? = null,
    val modelo: String? = null,
    @SerialName("version_android") val versionAndroid: String? = null,
    @SerialName("version_app") val versionApp: String? = null,
    val kiosco: Boolean = true,
    @SerialName("kiosco_activo") val kioscoActivo: Boolean? = null,
    @SerialName("bateria_pct") val bateriaPct: Int? = null,
    val cargando: Boolean? = null,
    @SerialName("almacenamiento_libre_mb") val almacenamientoLibreMb: Int? = null,
    val ip: String? = null,
    @SerialName("ultimo_contacto") val ultimoContacto: String? = null,
    @SerialName("en_linea") val enLinea: Boolean = false,
    @SerialName("comandos_pendientes") val comandosPendientes: Int = 0,
)

@Serializable
data class ComandoNuevo(val tipo: String, val texto: String? = null, val archivo: String? = null)

@Serializable
data class ComandoMdm(val id: Int, val tipo: String, val estado: String)

@Serializable
data class CodigoNuevo(val nombre: String, val kiosco: Boolean = true)

@Serializable
data class CodigoRegistro(
    val codigo: String,
    @SerialName("codigo_legible") val codigoLegible: String,
    val nombre: String,
    @SerialName("vence_en") val venceEn: String,
    val servidor: String,
    val huella: String? = null,
)
