package com.gama.suite.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.CatalogoFalla
import com.gama.suite.datos.CierreOrden
import com.gama.suite.datos.Confiabilidad
import com.gama.suite.datos.IndicadoresMtto
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.Nodo
import com.gama.suite.datos.OrdenTrabajo
import com.gama.suite.datos.SolicitudMtto
import com.gama.suite.datos.SolicitudNueva
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.DialogoTexto
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.NOMBRE_PRIORIDAD
import com.gama.suite.ui.Selector
import com.gama.suite.ui.colorPrioridad
import com.gama.suite.ui.fechaCorta
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.tema.LocalTactil
import kotlinx.coroutines.launch

private val NOMBRE_ESTADO = mapOf(
    "abierta" to "Abierta", "rechazada" to "Rechazada", "convertida" to "En orden",
    "planificada" to "Planificada", "asignada" to "Asignada", "en_ejecucion" to "En ejecución",
    "pausada" to "Pausada", "completada" to "Completada", "cerrada" to "Cerrada", "anulada" to "Anulada",
)

private fun colorEstado(estado: String) = when (estado) {
    "abierta", "planificada", "asignada" -> Gama.Primario
    "en_ejecucion" -> Gama.Alerta
    "pausada", "rechazada", "anulada" -> Gama.Critico
    else -> Gama.Ok
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivosPantalla(modulo: ModuloSesion, api: ApiCliente) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var ordenAbierta by rememberSaveable { mutableStateOf<Int?>(null) }

    val abierta = ordenAbierta
    if (abierta != null) {
        BackHandler { ordenAbierta = null }
        DetalleOrden(abierta, modulo, api) { ordenAbierta = null }
        return
    }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pestana) {
            listOf("Solicitudes", "Órdenes", "Indicadores", "Confiabilidad").forEachIndexed { i, titulo ->
                Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(titulo) })
            }
        }
        when (pestana) {
            0 -> SolicitudesTab(modulo, api)
            1 -> OrdenesTab(api) { ordenAbierta = it }
            2 -> IndicadoresTab(api)
            else -> ConfiabilidadTab(api)
        }
    }
}

// --- Solicitudes --------------------------------------------------------------
@Composable
private fun SolicitudesTab(modulo: ModuloSesion, api: ApiCliente) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var creando by remember { mutableStateOf(false) }
    var rechazando by remember { mutableStateOf<SolicitudMtto?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Box(Modifier.fillMaxSize()) {
            Cargar(clave = version, cargar = { api.solicitudes() }) { pagina ->
                if (pagina.elementos.isEmpty()) {
                    Text("No hay solicitudes.", Modifier.padding(24.dp))
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp)) {
                        items(pagina.elementos, key = { it.id }) { x ->
                            TarjetaSolicitud(
                                x = x,
                                puedeAprobar = modulo.puede("aprobar"),
                                alAprobar = {
                                    alcance.launch {
                                        mensaje = try {
                                            val o = api.aprobarSolicitud(x.id)
                                            version++
                                            "${x.numero} aprobada: se creó la orden ${o.numero}"
                                        } catch (e: ApiError) {
                                            e.message
                                        }
                                    }
                                },
                                alRechazar = { rechazando = x },
                            )
                        }
                    }
                }
            }
            if (modulo.puede("registrar")) {
                ExtendedFloatingActionButton(
                    onClick = { creando = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Nueva solicitud") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (creando) {
        NuevaSolicitudDialogo(api, alCerrar = { creando = false }) { numero ->
            creando = false
            version++
            mensaje = "Solicitud $numero enviada a mantenimiento"
        }
    }
    rechazando?.let { x ->
        DialogoTexto("Rechazar ${x.numero}", "Motivo del rechazo", alCancelar = { rechazando = null }) { motivo ->
            rechazando = null
            alcance.launch {
                mensaje = try {
                    api.rechazarSolicitud(x.id, motivo)
                    version++
                    "${x.numero} rechazada"
                } catch (e: ApiError) {
                    e.message
                }
            }
        }
    }
}

@Composable
private fun TarjetaSolicitud(x: SolicitudMtto, puedeAprobar: Boolean, alAprobar: () -> Unit, alRechazar: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(x.numero, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Etiqueta(NOMBRE_ESTADO[x.estado] ?: x.estado, colorEstado(x.estado))
                Spacer(Modifier.weight(1f))
                Etiqueta(NOMBRE_PRIORIDAD[x.prioridad] ?: "P${x.prioridad}", colorPrioridad(x.prioridad))
            }
            Text(x.equipo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(x.descripcion, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                "${x.solicitante} · ${fechaCorta(x.creadaEn)}" + if (x.equipoDetenido) " · EQUIPO DETENIDO" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (x.equipoDetenido) Gama.Critico else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            x.motivoRechazo?.let { Text("Motivo: $it", style = MaterialTheme.typography.bodySmall, color = Gama.Critico) }
            if (puedeAprobar && x.estado == "abierta") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = alAprobar, modifier = Modifier.heightIn(min = LocalTactil.current)) {
                        Text("Aprobar")
                    }
                    OutlinedButton(onClick = alRechazar, modifier = Modifier.heightIn(min = LocalTactil.current)) {
                        Text("Rechazar")
                    }
                }
            }
        }
    }
}

@Composable
private fun NuevaSolicitudDialogo(api: ApiCliente, alCerrar: () -> Unit, alCreada: (String) -> Unit) {
    val alcance = rememberCoroutineScope()
    var equipos by remember { mutableStateOf<List<Nodo>>(emptyList()) }
    var equipo by remember { mutableStateOf<Nodo?>(null) }
    var descripcion by remember { mutableStateOf("") }
    var prioridad by remember { mutableIntStateOf(3) }
    var detenido by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            equipos = api.jerarquia().filter { it.nivel == "equipo" || it.nivel == "componente" }
        } catch (e: ApiError) {
            error = e.message
        }
    }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nueva solicitud") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Selector("Elija el equipo", equipos, equipo, { "${it.codigo} · ${it.nombre}" }) { equipo = it }
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("¿Qué está pasando?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Prioridad", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { p ->
                        FilterChip(
                            selected = prioridad == p,
                            onClick = { prioridad = p },
                            label = { Text(NOMBRE_PRIORIDAD[p] ?: "$p") },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { detenido = !detenido },
                ) {
                    Checkbox(checked = detenido, onCheckedChange = { detenido = it })
                    Text("El equipo está detenido")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !enviando,
                onClick = {
                    val e = equipo
                    if (e == null) {
                        error = "Elija el equipo."
                    } else if (descripcion.trim().length < 5) {
                        error = "Describa el problema (al menos 5 caracteres)."
                    } else {
                        enviando = true
                        alcance.launch {
                            try {
                                val creada = api.crearSolicitud(SolicitudNueva(e.id, descripcion.trim(), prioridad, detenido))
                                alCreada(creada.numero)
                            } catch (ex: ApiError) {
                                error = ex.message
                                enviando = false
                            }
                        }
                    }
                },
            ) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// --- Órdenes ------------------------------------------------------------------
@Composable
private fun OrdenesTab(api: ApiCliente, alAbrir: (Int) -> Unit) {
    var filtro by rememberSaveable { mutableStateOf("mias") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mias" to "Mis órdenes", "abiertas" to "Abiertas", "todas" to "Todas").forEach { (clave, nombre) ->
                FilterChip(selected = filtro == clave, onClick = { filtro = clave }, label = { Text(nombre) })
            }
        }
        Cargar(clave = filtro, cargar = { api.ordenes(filtro) }) { pagina ->
            if (pagina.elementos.isEmpty()) {
                Text("No hay órdenes en esta vista.", Modifier.padding(24.dp))
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    items(pagina.elementos, key = { it.id }) { o -> FilaOrden(o) { alAbrir(o.id) } }
                }
            }
        }
    }
}

@Composable
private fun FilaOrden(o: OrdenTrabajo, alPulsar: () -> Unit) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(onClick = alPulsar),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.numero, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Etiqueta(NOMBRE_ESTADO[o.estado] ?: o.estado, colorEstado(o.estado))
                Spacer(Modifier.weight(1f))
                Etiqueta(NOMBRE_PRIORIDAD[o.prioridad] ?: "P${o.prioridad}", colorPrioridad(o.prioridad))
            }
            Text("${o.equipo} · ${o.tipo.replaceFirstChar { it.uppercase() }}", fontWeight = FontWeight.Medium)
            Text(o.descripcion, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(o.tecnico ?: "Sin asignar", o.programadaPara?.let { "Programada $it" }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetalleOrden(id: Int, modulo: ModuloSesion, api: ApiCliente, alVolver: () -> Unit) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var pausando by remember { mutableStateOf(false) }
    var completando by remember { mutableStateOf(false) }

    fun ejecutar(exito: String, accion: suspend () -> Unit) {
        alcance.launch {
            mensaje = try {
                accion()
                version++
                exito
            } catch (e: ApiError) {
                e.message
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            TextButton(onClick = alVolver) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, null)
                Spacer(Modifier.width(6.dp))
                Text("Órdenes")
            }
        }
        BarraMensaje(mensaje) { mensaje = null }
        Cargar(clave = version, cargar = { api.orden(id) }) { o ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.numero, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(10.dp))
                    Etiqueta(NOMBRE_ESTADO[o.estado] ?: o.estado, colorEstado(o.estado))
                }
                Text("${o.equipo} · ${o.tipo.replaceFirstChar { it.uppercase() }} · ${NOMBRE_PRIORIDAD[o.prioridad]}",
                    fontWeight = FontWeight.Medium)
                Text(o.descripcion)
                Dato("Técnico", o.tecnico ?: "Sin asignar")
                o.programadaPara?.let { Dato("Programada", it) }
                o.inicioReal?.let { Dato("Inicio", fechaCorta(it)) }
                o.finReal?.let { Dato("Fin", fechaCorta(it)) }
                if (o.paroDesde != null) Dato("Equipo detenido", "${fechaCorta(o.paroDesde)} → ${fechaCorta(o.paroHasta) .ifEmpty { "sigue detenido" }}")
                o.trabajoRealizado?.let { Dato("Trabajo realizado", it) }

                val puedeRegistrar = modulo.puede("registrar")
                val tactil = Modifier
                    .fillMaxWidth()
                    .heightIn(min = LocalTactil.current)
                when (o.estado) {
                    "planificada", "asignada" -> if (puedeRegistrar) {
                        Button(onClick = { ejecutar("Orden iniciada") { api.accionOrden(o.id, "iniciar") } }, modifier = tactil) {
                            Text("Iniciar trabajo")
                        }
                    }
                    "en_ejecucion" -> {
                        Button(onClick = { completando = true }, modifier = tactil) { Text("Completar") }
                        OutlinedButton(onClick = { pausando = true }, modifier = tactil) { Text("Pausar") }
                    }
                    "pausada" -> {
                        Button(onClick = { ejecutar("Trabajo reanudado") { api.accionOrden(o.id, "reanudar") } }, modifier = tactil) {
                            Text("Reanudar")
                        }
                        OutlinedButton(onClick = { completando = true }, modifier = tactil) { Text("Completar") }
                    }
                    "completada" -> if (modulo.puede("aprobar")) {
                        Button(onClick = { ejecutar("Orden verificada y cerrada") { api.accionOrden(o.id, "cerrar") } }, modifier = tactil) {
                            Text("Verificar y cerrar")
                        }
                    }
                }

                if (o.eventos.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Historial", style = MaterialTheme.typography.titleSmall)
                    o.eventos.forEach { e ->
                        Text(
                            "${fechaCorta(e.momento)} · ${NOMBRE_ESTADO[e.aEstado] ?: e.aEstado}" + (e.nota?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    if (pausando) {
        DialogoTexto("Pausar la orden", "Motivo (por ejemplo: esperando repuesto)", alCancelar = { pausando = false }) { motivo ->
            pausando = false
            ejecutar("Orden en pausa") { api.pausarOrden(id, motivo) }
        }
    }
    if (completando) {
        CompletarDialogo(api, alCerrar = { completando = false }) { cierre ->
            completando = false
            ejecutar("Orden completada. Falta la verificación del responsable.") { api.completarOrden(id, cierre) }
        }
    }
}

@Composable
private fun Dato(nombre: String, valor: String) {
    Column {
        Text(nombre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompletarDialogo(api: ApiCliente, alCerrar: () -> Unit, alConfirmar: (CierreOrden) -> Unit) {
    var catalogo by remember { mutableStateOf<List<CatalogoFalla>>(emptyList()) }
    var modo by remember { mutableStateOf<CatalogoFalla?>(null) }
    var causa by remember { mutableStateOf<CatalogoFalla?>(null) }
    var accion by remember { mutableStateOf<CatalogoFalla?>(null) }
    var trabajo by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            catalogo = api.catalogosFalla()
        } catch (e: ApiError) {
            error = e.message
        }
    }
    fun de(tipo: String) = catalogo.filter { it.tipo == tipo }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Completar orden") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estos datos alimentan el historial de fallas y los indicadores.", style = MaterialTheme.typography.bodySmall)
                Selector("¿Cómo falló? (modo)", de("modo"), modo, { it.nombre }) { modo = it }
                Selector("¿Por qué falló? (causa)", de("causa"), causa, { it.nombre }) { causa = it }
                Selector("¿Qué se hizo? (acción)", de("accion"), accion, { it.nombre }) { accion = it }
                OutlinedTextField(
                    value = trabajo,
                    onValueChange = { trabajo = it },
                    label = { Text("Trabajo realizado") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val m = modo
                val c = causa
                val a = accion
                if (m == null || c == null || a == null || trabajo.trim().length < 5) {
                    error = "Complete modo, causa, acción y el trabajo realizado."
                } else {
                    alConfirmar(CierreOrden(m.id, c.id, a.id, trabajo.trim()))
                }
            }) { Text("Completar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

// --- Indicadores --------------------------------------------------------------
@Composable
private fun IndicadoresTab(api: ApiCliente) {
    Cargar(cargar = { api.indicadoresMtto() }) { ind -> PanelIndicadores(ind) }
}

private fun formato(valor: Double?, unidad: String): String =
    valor?.let { String.format(java.util.Locale.getDefault(), "%.1f %s", it, unidad).trim() } ?: "Sin fallas"

@Composable
private fun PanelIndicadores(ind: IndicadoresMtto) {
    val tarjetas = listOf(
        Triple("Disponibilidad", formato(ind.disponibilidadPct, "%"), "disponibilidad_pct"),
        Triple("MTBF", formato(ind.mtbfHoras, "h"), "mtbf_horas"),
        Triple("MTTR", formato(ind.mttrHoras, "h"), "mttr_horas"),
        Triple("Fallas", "${ind.fallas}", "fallas"),
        Triple("Órdenes abiertas", "${ind.ordenesAbiertas}", ""),
        Triple(
            "Cumplimiento preventivo",
            ind.cumplimientoPreventivoPct?.let { formato(it, "%") } ?: "Sin programadas",
            "cumplimiento_preventivo_pct",
        ),
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Período: ${ind.desde} a ${ind.hasta} · ${ind.equipos} equipos", style = MaterialTheme.typography.labelLarge)
        }
        tarjetas.forEach { (titulo, valor, _) ->
            item {
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(valor, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text("Cómo se calculan", style = MaterialTheme.typography.titleSmall)
                tarjetas.mapNotNull { (titulo, _, clave) -> ind.definiciones[clave]?.let { "$titulo: $it" } }
                    .plus(listOfNotNull(ind.definiciones["nota"]))
                    .forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// --- Confiabilidad (GAMA Ciencia) ------------------------------------------------
private fun num(v: Double?, decimales: Int = 1, unidad: String = ""): String =
    v?.let { String.format(java.util.Locale.getDefault(), "%,.${decimales}f", it) + unidad } ?: "—"

@Composable
private fun ConfiabilidadTab(api: ApiCliente) {
    val alcance = rememberCoroutineScope()
    var equipos by remember { mutableStateOf<List<Nodo>>(emptyList()) }
    var equipo by remember { mutableStateOf<Nodo?>(null) }
    var costoPreventivo by rememberSaveable { mutableStateOf("") }
    var costoFalla by rememberSaveable { mutableStateOf("") }
    var resultado by remember { mutableStateOf<Confiabilidad?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var calculando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            equipos = api.jerarquia().filter { it.nivel == "equipo" }
        } catch (e: ApiError) {
            error = e.message
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Análisis de vida del equipo con su historial de fallas: distribución de Weibull, tendencia y cada cuánto conviene el reemplazo preventivo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Selector("Elija el equipo", equipos, equipo, { "${it.codigo} · ${it.nombre}" }) { equipo = it; resultado = null }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = costoPreventivo,
                onValueChange = { costoPreventivo = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Costo preventivo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = costoFalla,
                onValueChange = { costoFalla = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Costo de una falla") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Los costos son opcionales; con ellos se calcula el intervalo óptimo de reemplazo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            enabled = equipo != null && !calculando,
            onClick = {
                val e = equipo
                if (e != null) {
                    calculando = true
                    error = null
                    alcance.launch {
                        try {
                            resultado = api.confiabilidad(e.id, costoPreventivo.toDoubleOrNull(), costoFalla.toDoubleOrNull())
                        } catch (ex: ApiError) {
                            error = ex.message
                        } finally {
                            calculando = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalTactil.current),
        ) { Text(if (calculando) "Calculando…" else "Analizar") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        resultado?.let { r -> ResultadoConfiabilidad(r) }
    }
}

@Composable
private fun ResultadoConfiabilidad(r: Confiabilidad) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("${r.equipo} · ${r.fallasRegistradas} fallas registradas", style = MaterialTheme.typography.titleSmall)
        r.advertencias.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = Gama.Alerta) }
        r.weibull?.let { w ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Weibull", style = MaterialTheme.typography.titleSmall)
                    Dato("β (forma)", "${num(w.beta, 2)}  ·  IC 90 %: ${num(w.betaIc90.getOrNull(0), 2)} a ${num(w.betaIc90.getOrNull(1), 2)}")
                    Dato("η (vida característica)", num(w.eta, 0, " h"))
                    Dato("Vida media (MTTF)", num(w.mttf, 0, " h"))
                    Dato("B10 (falla el 10 %)", num(w.b10, 0, " h"))
                    Text(w.interpretacion, style = MaterialTheme.typography.bodyMedium)
                    w.reemplazoOptimo?.let { ro ->
                        HorizontalDivider()
                        Text("Reemplazo preventivo", style = MaterialTheme.typography.titleSmall)
                        Text(ro.recomendacion, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        if (ro.intervalo != null) {
                            Dato("Intervalo óptimo", num(ro.intervalo, 0, " h de operación"))
                            Dato("Ahorro frente a esperar la falla", num(ro.ahorroPct, 1, " %"))
                        }
                    }
                    Text(w.metodo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        r.tendencia?.let { t ->
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tendencia", style = MaterialTheme.typography.titleSmall)
                    Text(t.conclusion, style = MaterialTheme.typography.bodyMedium)
                    Dato("β de Crow-AMSAA", num(t.beta, 2))
                    Dato("MTBF actual", num(t.mtbfInstantaneoHoras, 0, " h"))
                    Dato("Prueba de Laplace (p)", num(t.laplaceP, 3))
                    Text(t.metodo, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
