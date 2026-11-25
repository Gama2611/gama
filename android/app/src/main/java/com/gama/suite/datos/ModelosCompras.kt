package com.gama.suite.datos

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- GAMA Compras: reposición científica y desempeño de proveedores ------------
@Serializable
data class PatronDemanda(
    val clase: String = "",
    val adi: Double? = null,
    val cv2: Double? = null,
    val metodo: String = "",
)

@Serializable
data class PoliticaArticulo(
    @SerialName("articulo_id") val articuloId: Int,
    val codigo: String,
    val nombre: String,
    val unidad: String,
    val stock: Double = 0.0,
    @SerialName("en_transito") val enTransito: Double = 0.0,
    val posicion: Double = 0.0,
    @SerialName("demanda_diaria") val demandaDiaria: Double? = null,
    val patron: PatronDemanda = PatronDemanda(),
    @SerialName("tiempo_entrega_dias") val tiempoEntregaDias: Double = 0.0,
    @SerialName("fuente_tiempo_entrega") val fuenteTiempoEntrega: String = "",
    @SerialName("stock_seguridad") val stockSeguridad: Double? = null,
    @SerialName("punto_reorden") val puntoReorden: Double? = null,
    @SerialName("lote_economico") val loteEconomico: Double? = null,
    val reponer: Boolean = false,
    @SerialName("cantidad_sugerida") val cantidadSugerida: Double? = null,
    val advertencias: List<String> = emptyList(),
)

@Serializable
data class Reposicion(
    val metodo: String = "",
    @SerialName("articulos_revisados") val articulosRevisados: Int = 0,
    val reponer: List<PoliticaArticulo> = emptyList(),
)

@Serializable
data class DesempenoProveedor(
    @SerialName("proveedor_id") val proveedorId: Int,
    val codigo: String,
    val nombre: String,
    @SerialName("lineas_evaluadas") val lineasEvaluadas: Int = 0,
    @SerialName("otif_pct") val otifPct: Double? = null,
    @SerialName("a_tiempo_pct") val aTiempoPct: Double? = null,
    @SerialName("completas_pct") val completasPct: Double? = null,
    @SerialName("rechazo_pct") val rechazoPct: Double? = null,
    @SerialName("tiempo_entrega_medio_dias") val tiempoEntregaMedio: Double? = null,
    @SerialName("tiempo_entrega_desviacion_dias") val tiempoEntregaDesviacion: Double? = null,
    @SerialName("indice_precio") val indicePrecio: Double? = null,
    val gasto: Map<String, Double> = emptyMap(),
)

@Serializable
data class DesempenoProveedores(
    val metodo: String = "",
    val dias: Int = 365,
    val proveedores: List<DesempenoProveedor> = emptyList(),
)

/** Las cantidades viajan como texto decimal, igual que las devuelve el servidor. */
@Serializable
data class RequisicionNueva(
    @SerialName("articulo_id") val articuloId: Int,
    val cantidad: String,
    val motivo: String,
)

@Serializable
data class Requisicion(
    val id: Int,
    val numero: String,
    @SerialName("articulo_id") val articuloId: Int,
    val cantidad: String,
    val estado: String,
)
