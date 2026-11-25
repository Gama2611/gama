package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

// --- Departamentos sobre el motor común de registros ----------------------------------------
// La app no conoce los departamentos de antemano: los dibuja a partir de la declaración del servidor
// (campos, estados, acciones), así producción, calidad, SST, facturación, etc. comparten una sola pantalla.

@Serializable
data class OpcionCampo(val valor: String, val etiqueta: String)

@Serializable
data class CampoMeta(
    val nombre: String,
    val etiqueta: String,
    /** texto, texto_largo, entero, numero, fecha, fechahora, booleano, opcion, moneda, referencia, usuario, nodo */
    val tipo: String,
    val requerido: Boolean = false,
    val opciones: List<OpcionCampo> = emptyList(),
    val referencia: String? = null,
    val minimo: Double? = null,
    val maximo: Double? = null,
    val ayuda: String = "",
    @SerialName("solo_lectura") val soloLectura: Boolean = false,
    val defecto: JsonElement? = null,
)

@Serializable
data class EstadoMeta(val codigo: String, val etiqueta: String, val tono: String)

@Serializable
data class EntidadMeta(
    val codigo: String,
    val nombre: String,
    val plural: String,
    val prefijo: String,
    val ayuda: String = "",
    val columnas: List<String> = emptyList(),
    @SerialName("puede_crear") val puedeCrear: Boolean = false,
    @SerialName("editable_en") val editableEn: List<String> = emptyList(),
    val estados: List<EstadoMeta> = emptyList(),
    val campos: List<CampoMeta> = emptyList(),
)

@Serializable
data class ModuloMeta(
    val modulo: String,
    val nombre: String,
    @SerialName("tiene_indicadores") val tieneIndicadores: Boolean = false,
    val entidades: List<EntidadMeta> = emptyList(),
)

@Serializable
data class AccionRegistro(
    val accion: String,
    val etiqueta: String,
    @SerialName("requiere_nota") val requiereNota: Boolean = false,
)

@Serializable
data class EventoRegistro(
    val accion: String,
    @SerialName("de_estado") val deEstado: String? = null,
    @SerialName("a_estado") val aEstado: String,
    val nota: String? = null,
    val usuario: String,
    val fecha: String,
)

@Serializable
data class Registro(
    val id: Long,
    val modulo: String,
    val entidad: String,
    val numero: String,
    val estado: String,
    @SerialName("estado_etiqueta") val estadoEtiqueta: String,
    val tono: String,
    val titulo: String,
    val datos: JsonObject = JsonObject(emptyMap()),
    val etiquetas: Map<String, String> = emptyMap(),
    @SerialName("creado_en") val creadoEn: String,
    val editable: Boolean = false,
    val acciones: List<AccionRegistro> = emptyList(),
    val historial: List<EventoRegistro> = emptyList(),
)

@Serializable
data class PaginaRegistros(val total: Int, val elementos: List<Registro>)

@Serializable
data class OpcionRef(val id: Long, val etiqueta: String)

@Serializable
data class EntradaRegistro(val datos: JsonObject)

@Serializable
data class NotaRegistro(val nota: String? = null)

@Serializable
data class IndicadorArea(
    val clave: String,
    val nombre: String,
    val valor: Double? = null,
    val unidad: String = "",
    val definicion: String = "",
    val decimales: Int = 1,
    val meta: Double? = null,
    @SerialName("mayor_es_mejor") val mayorEsMejor: Boolean = true,
    /** ok, info, alerta, critico */
    val severidad: String = "info",
    val detalle: String = "",
)

/** Texto de un valor guardado, sin comillas de JSON. */
fun JsonElement?.comoTexto(): String = when (this) {
    null, JsonNull -> ""
    is JsonPrimitive -> contentOrNull.orEmpty()
    else -> toString()
}

fun JsonElement?.comoNumero(): Double? = (this as? JsonPrimitive)?.doubleOrNull

fun JsonElement?.comoBooleano(): Boolean = (this as? JsonPrimitive)?.booleanOrNull ?: false
