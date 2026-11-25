package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Confiabilidad (GAMA Ciencia sobre el historial de Activos) ------------------
@Serializable
data class Reemplazo(
    val intervalo: Double? = null,
    @SerialName("costo_por_hora_optimo") val costoPorHoraOptimo: Double? = null,
    @SerialName("costo_por_hora_correctivo") val costoPorHoraCorrectivo: Double? = null,
    @SerialName("ahorro_pct") val ahorroPct: Double? = null,
    val recomendacion: String = "",
)

@Serializable
data class WeibullResultado(
    val metodo: String = "",
    val beta: Double? = null,
    val eta: Double? = null,
    @SerialName("beta_ic90") val betaIc90: List<Double?> = emptyList(),
    @SerialName("eta_ic90") val etaIc90: List<Double?> = emptyList(),
    val mttf: Double? = null,
    val b10: Double? = null,
    val fallas: Int = 0,
    val censuras: Int = 0,
    val interpretacion: String = "",
    @SerialName("reemplazo_optimo") val reemplazoOptimo: Reemplazo? = null,
)

@Serializable
data class Tendencia(
    val metodo: String = "",
    val beta: Double? = null,
    @SerialName("mtbf_instantaneo_horas") val mtbfInstantaneoHoras: Double? = null,
    @SerialName("laplace_p") val laplaceP: Double? = null,
    val conclusion: String = "",
)

@Serializable
data class Confiabilidad(
    val equipo: String,
    @SerialName("fallas_registradas") val fallasRegistradas: Int,
    val advertencias: List<String> = emptyList(),
    val tendencia: Tendencia? = null,
    val weibull: WeibullResultado? = null,
)

// --- GAMA Ventas --------------------------------------------------------------------
@Serializable
data class ClienteVenta(
    val id: Int,
    val codigo: String,
    val nombre: String,
    val sector: String? = null,
)

@Serializable
data class Oportunidad(
    val id: Int,
    val numero: String,
    @SerialName("cliente_id") val clienteId: Int,
    val cliente: String = "",
    val titulo: String,
    // Los importes llegan como texto decimal exacto ("1500.00").
    @SerialName("monto_estimado") val montoEstimado: String,
    val moneda: String,
    val etapa: String,
    @SerialName("cierre_estimado") val cierreEstimado: String? = null,
    @SerialName("motivo_perdida") val motivoPerdida: String? = null,
)

@Serializable
data class OportunidadNueva(
    @SerialName("cliente_id") val clienteId: Int,
    val titulo: String,
    @SerialName("monto_estimado") val montoEstimado: String,
    val moneda: String,
)

@Serializable
data class CambioEtapa(
    val etapa: String,
    @SerialName("motivo_perdida") val motivoPerdida: String? = null,
    val competidor: String? = null,
)

@Serializable
data class EtapaEmbudo(
    val etapa: String,
    val oportunidades: Int,
    val monto: Double,
    val probabilidad: Double,
    @SerialName("cerradas_historicas") val cerradasHistoricas: Int,
    @SerialName("fuente_probabilidad") val fuenteProbabilidad: String,
    @SerialName("monto_ponderado") val montoPonderado: Double,
)

@Serializable
data class Embudo(
    val moneda: String,
    val metodo: String,
    val etapas: List<EtapaEmbudo>,
    @SerialName("total_ponderado") val totalPonderado: Double,
)

@Serializable
data class ClienteValor(
    @SerialName("cliente_id") val clienteId: Int,
    val nombre: String,
    val compras: Int,
    @SerialName("dias_desde_ultima_compra") val diasDesdeUltimaCompra: Int,
    @SerialName("monto_total") val montoTotal: Double,
    val segmento: String? = null,
    @SerialName("prob_activo") val probActivo: Double? = null,
    @SerialName("compras_esperadas") val comprasEsperadas: Double? = null,
    val clv: Double? = null,
)

@Serializable
data class ValorClientes(
    val moneda: String,
    @SerialName("clientes_con_compras") val clientesConCompras: Int,
    val advertencias: List<String> = emptyList(),
    val metodo: String? = null,
    val clientes: List<ClienteValor> = emptyList(),
)

val ETAPAS_VENTA = listOf("prospecto", "calificada", "propuesta", "negociacion", "ganada", "perdida")
val NOMBRE_ETAPA = mapOf(
    "prospecto" to "Prospecto", "calificada" to "Calificada", "propuesta" to "Propuesta",
    "negociacion" to "Negociación", "ganada" to "Ganada", "perdida" to "Perdida",
)
