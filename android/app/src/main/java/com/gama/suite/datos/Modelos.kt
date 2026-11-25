package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Contratos de la API /api/v1. Deben coincidir con servidor/gama/esquemas.py.

@Serializable
data class LoginPeticion(
    val usuario: String,
    val clave: String,
    val dispositivo: String? = null,
)

@Serializable
data class RefrescoPeticion(@SerialName("token_refresco") val tokenRefresco: String)

@Serializable
data class CambioClavePeticion(
    @SerialName("clave_actual") val claveActual: String,
    @SerialName("clave_nueva") val claveNueva: String,
)

@Serializable
data class Tokens(
    @SerialName("token_acceso") val tokenAcceso: String,
    @SerialName("token_refresco") val tokenRefresco: String,
    @SerialName("expira_en_segundos") val expiraEnSegundos: Int,
    @SerialName("debe_cambiar_clave") val debeCambiarClave: Boolean = false,
)

@Serializable
data class RolBreve(val id: Int, val codigo: String, val nombre: String)

@Serializable
data class UsuarioSesion(
    val id: Int,
    val usuario: String,
    val nombre: String,
    val correo: String? = null,
    val activo: Boolean,
    @SerialName("debe_cambiar_clave") val debeCambiarClave: Boolean,
    @SerialName("ultimo_acceso") val ultimoAcceso: String? = null,
    val roles: List<RolBreve>,
)

@Serializable
data class ModuloSesion(
    val codigo: String,
    val nombre: String,
    val descripcion: String,
    val icono: String,
    val grupo: String,
    val secciones: List<String>,
    val acciones: List<String>,
) {
    fun puede(accion: String): Boolean = accion in acciones
}

@Serializable
data class LicenciaSesion(
    val cliente: String,
    val modo: String,
    @SerialName("soporte_hasta") val soporteHasta: String? = null,
    @SerialName("soporte_vigente") val soporteVigente: Boolean,
)

@Serializable
data class Sesion(
    val usuario: UsuarioSesion,
    val empresa: String,
    val modulos: List<ModuloSesion>,
    val licencia: LicenciaSesion,
)

@Serializable
data class Nodo(
    val id: Int,
    @SerialName("padre_id") val padreId: Int? = null,
    val nivel: String,
    val codigo: String,
    val nombre: String,
    val descripcion: String? = null,
    val criticidad: String? = null,
    val activo: Boolean,
)

@Serializable
data class RegistroAuditoria(
    val id: Long,
    val momento: String,
    val usuario: String? = null,
    val modulo: String,
    val accion: String,
    val entidad: String? = null,
    @SerialName("entidad_id") val entidadId: String? = null,
)

@Serializable
data class PaginaAuditoria(val total: Int, val elementos: List<RegistroAuditoria>)
