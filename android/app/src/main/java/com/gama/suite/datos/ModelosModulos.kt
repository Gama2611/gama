package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Genérico ---------------------------------------------------------------
@Serializable
data class Pagina<T>(val total: Int, val elementos: List<T>)

// --- GAMA Activos -----------------------------------------------------------
@Serializable
data class CatalogoFalla(val id: Int, val tipo: String, val codigo: String, val nombre: String)

@Serializable
data class SolicitudMtto(
    val id: Int,
    val numero: String,
    @SerialName("equipo_id") val equipoId: Int,
    val equipo: String = "",
    val descripcion: String,
    val prioridad: Int,
    @SerialName("equipo_detenido") val equipoDetenido: Boolean,
    val estado: String,
    val solicitante: String = "",
    @SerialName("creada_en") val creadaEn: String,
    @SerialName("motivo_rechazo") val motivoRechazo: String? = null,
    @SerialName("orden_id") val ordenId: Int? = null,
)

@Serializable
data class SolicitudNueva(
    @SerialName("equipo_id") val equipoId: Int,
    val descripcion: String,
    val prioridad: Int,
    @SerialName("equipo_detenido") val equipoDetenido: Boolean,
)

@Serializable
data class AprobacionSolicitud(@SerialName("tecnico_id") val tecnicoId: Int? = null)

@Serializable
data class Motivo(val motivo: String)

@Serializable
data class NotaOrden(val nota: String? = null)

@Serializable
data class CierreOrden(
    @SerialName("modo_id") val modoId: Int,
    @SerialName("causa_id") val causaId: Int,
    @SerialName("accion_id") val accionId: Int,
    @SerialName("trabajo_realizado") val trabajoRealizado: String,
)

@Serializable
data class EventoOrden(
    val momento: String,
    @SerialName("de_estado") val deEstado: String? = null,
    @SerialName("a_estado") val aEstado: String,
    val nota: String? = null,
)

@Serializable
data class OrdenTrabajo(
    val id: Int,
    val numero: String,
    @SerialName("equipo_id") val equipoId: Int,
    val equipo: String = "",
    val tipo: String,
    val prioridad: Int,
    val descripcion: String,
    val estado: String,
    @SerialName("tecnico_id") val tecnicoId: Int? = null,
    val tecnico: String? = null,
    @SerialName("programada_para") val programadaPara: String? = null,
    @SerialName("creada_en") val creadaEn: String,
    @SerialName("inicio_real") val inicioReal: String? = null,
    @SerialName("fin_real") val finReal: String? = null,
    @SerialName("paro_desde") val paroDesde: String? = null,
    @SerialName("paro_hasta") val paroHasta: String? = null,
    @SerialName("trabajo_realizado") val trabajoRealizado: String? = null,
    @SerialName("motivo_anulacion") val motivoAnulacion: String? = null,
    val eventos: List<EventoOrden> = emptyList(),
)

@Serializable
data class IndicadoresMtto(
    val desde: String,
    val hasta: String,
    val equipos: Int,
    val fallas: Int,
    @SerialName("horas_paro") val horasParo: Double,
    @SerialName("mttr_horas") val mttrHoras: Double? = null,
    @SerialName("mtbf_horas") val mtbfHoras: Double? = null,
    @SerialName("disponibilidad_pct") val disponibilidadPct: Double? = null,
    @SerialName("ordenes_abiertas") val ordenesAbiertas: Int,
    @SerialName("preventivas_programadas") val preventivasProgramadas: Int,
    @SerialName("cumplimiento_preventivo_pct") val cumplimientoPreventivoPct: Double? = null,
    val definiciones: Map<String, String> = emptyMap(),
)

// --- GAMA Conecta -----------------------------------------------------------
@Serializable
data class Canal(
    val id: Int,
    val tipo: String,
    val nombre: String,
    val descripcion: String? = null,
    val miembros: Int,
    @SerialName("soy_miembro") val soyMiembro: Boolean,
    @SerialName("no_leidos") val noLeidos: Int,
    @SerialName("ultimo_mensaje_en") val ultimoMensajeEn: String? = null,
)

@Serializable
data class Tarjeta(val modulo: String, val entidad: String, val ref: String)

@Serializable
data class Mensaje(
    val id: Long,
    @SerialName("canal_id") val canalId: Int,
    @SerialName("autor_id") val autorId: Int,
    val autor: String,
    val texto: String,
    @SerialName("creado_en") val creadoEn: String,
    @SerialName("es_aviso") val esAviso: Boolean,
    val tarjeta: Tarjeta? = null,
    val reacciones: Map<String, Int> = emptyMap(),
    @SerialName("mis_reacciones") val misReacciones: List<String> = emptyList(),
    val comentarios: Int = 0,
    val oculto: Boolean = false,
    @SerialName("motivo_oculto") val motivoOculto: String? = null,
    @SerialName("aviso_confirmado_por_mi") val avisoConfirmadoPorMi: Boolean? = null,
)

@Serializable
data class MensajeNuevo(
    val texto: String,
    @SerialName("respuesta_a") val respuestaA: Long? = null,
    val aviso: Boolean = false,
)

@Serializable
data class ReaccionNueva(val emoji: String)

@Serializable
data class DirectoNuevo(@SerialName("usuario_id") val usuarioId: Int)

@Serializable
data class Persona(val id: Int, val nombre: String, val roles: List<String> = emptyList())

val REACCIONES = listOf("👍", "❤️", "👏", "✅", "⚠️", "💡")
