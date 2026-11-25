package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- GAMA Asistente: resúmenes verificados por área y guía de uso por rol ------
@Serializable
data class HechoAsistente(
    val clave: String,
    val area: String,
    val severidad: String,
    val titulo: String,
    val texto: String,
    val explicacion: String = "",
    val accion: String? = null,
    val seccion: String? = null,
)

@Serializable
data class NarrativaAsistente(
    val texto: String = "",
    /** "plantilla" (exacta por construcción) o "ia_verificada". */
    val fuente: String = "plantilla",
    val nota: String = "",
)

@Serializable
data class ResumenAsistente(
    val area: String,
    @SerialName("generado_en") val generadoEn: String,
    @SerialName("areas_incluidas") val areasIncluidas: List<String> = emptyList(),
    @SerialName("areas_sin_permiso") val areasSinPermiso: List<String> = emptyList(),
    val narrativa: NarrativaAsistente = NarrativaAsistente(),
    val hechos: List<HechoAsistente> = emptyList(),
)

@Serializable
data class LeccionUso(
    val codigo: String,
    val modulo: String,
    @SerialName("modulo_nombre") val moduloNombre: String,
    val titulo: String,
    @SerialName("para_que") val paraQue: String,
    val pasos: List<String> = emptyList(),
    val completada: Boolean = false,
)

@Serializable
data class GuiaUso(
    val usuario: String,
    val roles: List<String> = emptyList(),
    @SerialName("avance_pct") val avancePct: Double = 0.0,
    val lecciones: List<LeccionUso> = emptyList(),
)

@Serializable
data class AyudaUso(
    val pregunta: String,
    val respuesta: String,
    val lecciones: List<LeccionUso> = emptyList(),
)

/** Áreas que el servidor sabe resumir y su nombre visible. */
val AREAS_ASISTENTE = listOf(
    "general" to "General",
    "activos" to "Mantenimiento",
    "compras" to "Compras",
    "ventas" to "Ventas",
    "planta" to "Producción",
    "calidad" to "Calidad",
    "mejora" to "Mejora continua",
    "logistica" to "Logística",
    "costos" to "Costos",
    "sst" to "SST y Ambiente",
    "talento" to "Talento",
    "energia" to "Energía",
    "proyectos" to "Proyectos",
    "marketing" to "Marketing",
    "twin" to "Gemelo digital",
)
