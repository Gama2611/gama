package com.gama.suite.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.CambioEtapa
import com.gama.suite.datos.ClienteVenta
import com.gama.suite.datos.ETAPAS_VENTA
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.NOMBRE_ETAPA
import com.gama.suite.datos.Oportunidad
import com.gama.suite.datos.OportunidadNueva
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.Selector
import com.gama.suite.ui.tema.Gama
import kotlinx.coroutines.launch
import java.util.Locale

private val MONEDAS = listOf("USD", "VES", "EUR")

private fun dinero(valor: Double?, moneda: String): String =
    valor?.let { String.format(Locale.getDefault(), "%,.2f %s", it, moneda) } ?: "—"

private fun colorEtapa(etapa: String) = when (etapa) {
    "ganada" -> Gama.Ok
    "perdida" -> Gama.Critico
    "negociacion" -> Gama.Alerta
    else -> Gama.Primario
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentasPantalla(modulo: ModuloSesion, api: ApiCliente) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var moneda by rememberSaveable { mutableStateOf("USD") }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pestana) {
            listOf("Embudo", "Oportunidades", "Clientes", "Facturación").forEachIndexed { i, titulo ->
                Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(titulo) })
            }
        }
        if (pestana < 3) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MONEDAS.forEach { m -> FilterChip(selected = moneda == m, onClick = { moneda = m }, label = { Text(m) }) }
            }
        }
        when (pestana) {
            0 -> EmbudoTab(api, moneda)
            1 -> OportunidadesTab(modulo, api, moneda)
            2 -> ValorClientesTab(api, moneda)
            // Facturas, líneas, cobros e indicadores de cuentas por cobrar (motor común de registros).
            else -> RegistrosPantalla("comercial", api)
        }
    }
}

// --- Embudo ----------------------------------------------------------------------
@Composable
private fun EmbudoTab(api: ApiCliente, moneda: String) {
    Cargar(clave = moneda, cargar = { api.embudo(moneda) }) { e ->
        val maximo = (e.etapas.maxOfOrNull { it.monto } ?: 0.0).coerceAtLeast(1.0)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ventas esperadas del embudo", style = MaterialTheme.typography.labelMedium)
                    Text(dinero(e.totalPonderado, e.moneda), style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            e.etapas.forEach { et ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(NOMBRE_ETAPA[et.etapa] ?: et.etapa, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("${et.oportunidades} · ${dinero(et.monto, e.moneda)}", style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { (et.monto / maximo).toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                    )
                    Text(
                        "Probabilidad de ganar ${String.format(Locale.getDefault(), "%.0f", et.probabilidad * 100)} % " +
                            "(${et.fuenteProbabilidad}, ${et.cerradasHistoricas} cerradas) · esperado ${dinero(et.montoPonderado, e.moneda)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(e.metodo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// --- Oportunidades -----------------------------------------------------------------
@Composable
private fun OportunidadesTab(modulo: ModuloSesion, api: ApiCliente, moneda: String) {
    val alcance = rememberCoroutineScope()
    var filtro by rememberSaveable { mutableStateOf("abiertas") }
    var version by remember { mutableIntStateOf(0) }
    var creando by remember { mutableStateOf(false) }
    var moviendo by remember { mutableStateOf<Oportunidad?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mias" to "Mías", "abiertas" to "Abiertas", "todas" to "Todas").forEach { (clave, nombre) ->
                FilterChip(selected = filtro == clave, onClick = { filtro = clave }, label = { Text(nombre) })
            }
        }
        Box(Modifier.fillMaxSize()) {
            Cargar(clave = "$filtro-$version", cargar = { api.oportunidades(filtro) }) { pagina ->
                val lista = pagina.elementos.filter { it.moneda == moneda }
                if (lista.isEmpty()) {
                    Text("No hay oportunidades en $moneda en esta vista.", Modifier.padding(24.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp)) {
                        items(lista, key = { it.id }) { o ->
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                                    .clickable(enabled = modulo.puede("registrar")) { moviendo = o },
                            ) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(o.numero, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        Etiqueta(NOMBRE_ETAPA[o.etapa] ?: o.etapa, colorEtapa(o.etapa))
                                        Spacer(Modifier.weight(1f))
                                        Text(dinero(o.montoEstimado.toDoubleOrNull(), o.moneda), fontWeight = FontWeight.Medium)
                                    }
                                    Text(o.cliente, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(o.titulo, style = MaterialTheme.typography.bodyMedium)
                                    o.motivoPerdida?.let {
                                        Text("Motivo: $it", style = MaterialTheme.typography.bodySmall, color = Gama.Critico)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (modulo.puede("registrar")) {
                ExtendedFloatingActionButton(
                    onClick = { creando = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Nueva oportunidad") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (creando) {
        NuevaOportunidadDialogo(api, moneda, alCerrar = { creando = false }) { numero ->
            creando = false
            version++
            mensaje = "Oportunidad $numero creada"
        }
    }
    moviendo?.let { o ->
        MoverEtapaDialogo(o, alCerrar = { moviendo = null }) { cambio ->
            moviendo = null
            alcance.launch {
                mensaje = try {
                    val r = api.cambiarEtapa(o.id, cambio)
                    version++
                    "${r.numero} ahora está en ${NOMBRE_ETAPA[r.etapa] ?: r.etapa}"
                } catch (e: ApiError) {
                    e.message
                }
            }
        }
    }
}

@Composable
private fun NuevaOportunidadDialogo(api: ApiCliente, moneda: String, alCerrar: () -> Unit, alCreada: (String) -> Unit) {
    val alcance = rememberCoroutineScope()
    var clientes by remember { mutableStateOf<List<ClienteVenta>>(emptyList()) }
    var cliente by remember { mutableStateOf<ClienteVenta?>(null) }
    var titulo by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            clientes = api.clientesVenta()
        } catch (e: ApiError) {
            error = e.message
        }
    }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nueva oportunidad en $moneda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Selector("Elija el cliente", clientes, cliente, { "${it.codigo} · ${it.nombre}" }) { cliente = it }
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("¿Qué se va a vender?") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Monto estimado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !enviando, onClick = {
                val c = cliente
                val m = monto.toBigDecimalOrNull()
                if (c == null) {
                    error = "Elija el cliente."
                } else if (titulo.trim().length < 3) {
                    error = "Describa la oportunidad."
                } else if (m == null || m.signum() < 0) {
                    error = "Escriba un monto válido."
                } else {
                    enviando = true
                    val importe = m.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                    alcance.launch {
                        try {
                            val o = api.crearOportunidad(OportunidadNueva(c.id, titulo.trim(), importe, moneda))
                            alCreada(o.numero)
                        } catch (ex: ApiError) {
                            error = ex.message
                            enviando = false
                        }
                    }
                }
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun MoverEtapaDialogo(o: Oportunidad, alCerrar: () -> Unit, alConfirmar: (CambioEtapa) -> Unit) {
    var etapa by remember { mutableStateOf<String?>(null) }
    var motivo by remember { mutableStateOf("") }
    var competidor by remember { mutableStateOf("") }
    val cerrada = o.etapa == "ganada" || o.etapa == "perdida"
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("${o.numero} · mover de etapa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (cerrada) {
                    Text("Esta oportunidad ya está cerrada como ${NOMBRE_ETAPA[o.etapa]}.")
                } else {
                    Selector("Nueva etapa", ETAPAS_VENTA.filter { it != o.etapa }, etapa, { NOMBRE_ETAPA[it] ?: it }) { etapa = it }
                    if (etapa == "perdida") {
                        OutlinedTextField(value = motivo, onValueChange = { motivo = it },
                            label = { Text("Motivo de la pérdida (obligatorio)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = competidor, onValueChange = { competidor = it },
                            label = { Text("Competidor que ganó (opcional)") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            val e = etapa
            TextButton(
                enabled = !cerrada && e != null && (e != "perdida" || motivo.trim().length >= 3),
                onClick = {
                    if (e != null) {
                        alConfirmar(
                            CambioEtapa(
                                etapa = e,
                                motivoPerdida = motivo.trim().takeIf { e == "perdida" && it.isNotEmpty() },
                                competidor = competidor.trim().takeIf { e == "perdida" && it.isNotEmpty() },
                            ),
                        )
                    }
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// --- Valor de clientes -------------------------------------------------------------
@Composable
private fun ValorClientesTab(api: ApiCliente, moneda: String) {
    Cargar(clave = moneda, cargar = { api.valorClientes(moneda) }) { v ->
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            item {
                Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${v.clientesConCompras} clientes con compras en ${v.moneda}", fontWeight = FontWeight.SemiBold)
                    v.metodo?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    v.advertencias.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Gama.Alerta) }
                }
            }
            items(v.clientes, key = { it.clienteId }) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.nombre, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            c.segmento?.let { Etiqueta(it, if (it == "En riesgo" || it == "Hibernando") Gama.Critico else Gama.Primario) }
                        }
                        Text(
                            "${c.compras} compras · última hace ${c.diasDesdeUltimaCompra} días · total ${dinero(c.montoTotal, v.moneda)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (c.clv != null) {
                            Text(
                                "Valor próximos 12 meses: ${dinero(c.clv, v.moneda)} · probabilidad de seguir activo " +
                                    String.format(Locale.getDefault(), "%.0f %%", (c.probActivo ?: 0.0) * 100),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
