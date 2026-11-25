package com.gama.suite.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.DesempenoProveedor
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.PoliticaArticulo
import com.gama.suite.datos.RequisicionNueva
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.tema.Gama
import kotlinx.coroutines.launch
import java.util.Locale

private fun numero(valor: Double?, decimales: Int = 1): String =
    valor?.let { String.format(Locale.getDefault(), "%,.${decimales}f", it) } ?: "—"

private fun colorOtif(otif: Double?): Color {
    val v = otif ?: return Gama.Primario
    return if (v >= 95) Gama.Ok else if (v >= 85) Gama.Alerta else Gama.Critico
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprasPantalla(modulo: ModuloSesion, api: ApiCliente) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pestana) {
            listOf("Reponer", "Proveedores").forEachIndexed { i, titulo ->
                Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(titulo) })
            }
        }
        if (pestana == 0) ReponerTab(modulo, api) else ProveedoresTab(api)
    }
}

// --- Reponer ---------------------------------------------------------------------
@Composable
private fun ReponerTab(modulo: ModuloSesion, api: ApiCliente) {
    var version by remember { mutableIntStateOf(0) }
    var pidiendo by remember { mutableStateOf<PoliticaArticulo?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Cargar(clave = version, cargar = { api.reponer() }) { r ->
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        if (r.reponer.isEmpty()) {
                            "Ningún artículo bajo su punto de reorden (${r.articulosRevisados} revisados)."
                        } else {
                            "${r.reponer.size} de ${r.articulosRevisados} artículos deben reponerse"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(r.reponer, key = { it.articuloId }) { p ->
                    TarjetaReponer(p, puedePedir = modulo.puede("registrar")) { pidiendo = p }
                }
                item {
                    Text(r.metodo, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    pidiendo?.let { p ->
        RequisicionDialogo(api, p, alCerrar = { pidiendo = null }) { numeroReq ->
            pidiendo = null
            version++
            mensaje = "Requisición $numeroReq creada para ${p.codigo}"
        }
    }
}

@Composable
private fun TarjetaReponer(p: PoliticaArticulo, puedePedir: Boolean, alPedir: () -> Unit) {
    val seguridad = p.stockSeguridad
    val critico = seguridad != null && p.stock <= seguridad
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.codigo, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Etiqueta(if (critico) "Bajo stock de seguridad" else "Bajo punto de reorden",
                    if (critico) Gama.Critico else Gama.Alerta)
            }
            Text(p.nombre, style = MaterialTheme.typography.bodyMedium)
            val rop = p.puntoReorden ?: 0.0
            if (rop > 0) {
                LinearProgressIndicator(
                    progress = { (p.posicion / rop).coerceIn(0.0, 1.0).toFloat() },
                    color = if (critico) Gama.Critico else Gama.Alerta,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
            }
            Text(
                "Existencia ${numero(p.stock)} + en tránsito ${numero(p.enTransito)} = ${numero(p.posicion)} ${p.unidad} · " +
                    "punto de reorden ${numero(p.puntoReorden)} · seguridad ${numero(p.stockSeguridad)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Demanda ${p.patron.clase} (${numero(p.demandaDiaria, 2)} ${p.unidad}/día) · " +
                    "entrega ${numero(p.tiempoEntregaDias)} días (${p.fuenteTiempoEntrega})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            p.advertencias.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Gama.Alerta)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sugerido: ${numero(p.cantidadSugerida)} ${p.unidad}", fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                if (puedePedir) OutlinedButton(onClick = alPedir) { Text("Pedir") }
            }
        }
    }
}

@Composable
private fun RequisicionDialogo(api: ApiCliente, p: PoliticaArticulo, alCerrar: () -> Unit, alCreada: (String) -> Unit) {
    val alcance = rememberCoroutineScope()
    var cantidad by remember { mutableStateOf(p.cantidadSugerida?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "") }
    var motivo by remember { mutableStateOf("Reposición: posición bajo el punto de reorden") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    val valor = cantidad.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Requisición de ${p.codigo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(cantidad, { cantidad = it }, label = { Text("Cantidad (${p.unidad})") }, singleLine = true)
                OutlinedTextField(motivo, { motivo = it }, label = { Text("Motivo") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !enviando && valor != null && valor > 0 && motivo.trim().length >= 3,
                onClick = {
                    val v = valor ?: 0.0
                    enviando = true
                    alcance.launch {
                        try {
                            val r = api.crearRequisicion(
                                RequisicionNueva(p.articuloId, String.format(Locale.ROOT, "%.3f", v), motivo.trim()),
                            )
                            alCreada(r.numero)
                        } catch (e: ApiError) {
                            error = e.message
                        } finally {
                            enviando = false
                        }
                    }
                },
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// --- Proveedores -----------------------------------------------------------------
@Composable
private fun ProveedoresTab(api: ApiCliente) {
    var dias by rememberSaveable { mutableIntStateOf(365) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(90 to "90 días", 180 to "6 meses", 365 to "12 meses").forEach { (d, nombre) ->
                FilterChip(selected = dias == d, onClick = { dias = d }, label = { Text(nombre) })
            }
        }
        Cargar(clave = dias, cargar = { api.desempenoProveedores(dias) }) { d ->
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (d.proveedores.isEmpty()) {
                    item { Text("Sin órdenes de compra en este período.", Modifier.padding(12.dp)) }
                }
                items(d.proveedores, key = { it.proveedorId }) { TarjetaProveedor(it) }
                item {
                    Text(d.metodo, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TarjetaProveedor(p: DesempenoProveedor) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.nombre, fontWeight = FontWeight.SemiBold)
                    Text("${p.lineasEvaluadas} entregas evaluadas", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(p.otifPct?.let { "${numero(it)} %" } ?: "—", style = MaterialTheme.typography.headlineSmall,
                        color = colorOtif(p.otifPct))
                    Text("OTIF", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                "A tiempo ${numero(p.aTiempoPct)} % · completas ${numero(p.completasPct)} % · rechazo ${numero(p.rechazoPct, 2)} %",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Entrega real ${numero(p.tiempoEntregaMedio)} ± ${numero(p.tiempoEntregaDesviacion)} días · " +
                    "índice de precio ${numero(p.indicePrecio, 3)} (1,000 = el más barato)",
                style = MaterialTheme.typography.bodySmall,
            )
            if (p.gasto.isNotEmpty()) {
                Text(
                    "Gasto: " + p.gasto.entries.joinToString(" · ") { (m, v) -> "${numero(v, 2)} $m" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
