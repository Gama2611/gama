package com.gama.suite.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.AccionRegistro
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.CampoMeta
import com.gama.suite.datos.EntidadMeta
import com.gama.suite.datos.IndicadorArea
import com.gama.suite.datos.ModuloMeta
import com.gama.suite.datos.OpcionRef
import com.gama.suite.datos.Registro
import com.gama.suite.datos.comoBooleano
import com.gama.suite.datos.comoNumero
import com.gama.suite.datos.comoTexto
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.DialogoTexto
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.Selector
import com.gama.suite.ui.fechaCorta
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.tema.LocalTactil
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ES = Locale("es", "VE")
private val MONEDAS_REGISTRO = listOf("USD", "VES", "EUR")
private val FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

/** Lo que muestra la pantalla de un departamento: la lista, un registro o el formulario. */
private sealed interface Vista {
    data object Lista : Vista
    data class Detalle(val entidad: EntidadMeta, val id: Long) : Vista
    data class Formulario(val entidad: EntidadMeta, val registro: Registro?) : Vista
}

/**
 * Pantalla común de todos los departamentos: Producción, Calidad, Mejora continua, Logística, Costos,
 * SST y Ambiente, Talento, Energía, Proyectos, Marketing, Gemelo digital y Facturación (en Ventas).
 * Se dibuja a partir de la declaración del servidor; los permisos los decide siempre el servidor.
 */
@Composable
fun RegistrosPantalla(codigoModulo: String, api: ApiCliente) {
    Cargar(clave = codigoModulo, cargar = { api.metaModulo(codigoModulo) }) { meta ->
        if (meta.entidades.isEmpty()) {
            Text("Este módulo no tiene registros para su rol.", Modifier.padding(24.dp))
        } else {
            ContenidoModulo(meta, api)
        }
    }
}

@Composable
private fun ContenidoModulo(meta: ModuloMeta, api: ApiCliente) {
    val pestanas = meta.entidades.map { it.plural } + if (meta.tieneIndicadores) listOf("Indicadores") else emptyList()
    var pestana by rememberSaveable(meta.modulo) { mutableIntStateOf(if (meta.tieneIndicadores) pestanas.lastIndex else 0) }
    var vista by remember(meta.modulo) { mutableStateOf<Vista>(Vista.Lista) }
    var version by remember { mutableIntStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = vista != Vista.Lista) { vista = Vista.Lista }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        when (val v = vista) {
            Vista.Lista -> {
                ScrollableTabRow(selectedTabIndex = pestana, edgePadding = 8.dp) {
                    pestanas.forEachIndexed { i, titulo ->
                        Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(titulo) })
                    }
                }
                val entidad = meta.entidades.getOrNull(pestana)
                if (entidad == null) {
                    IndicadoresContenido(meta.modulo, api)
                } else {
                    ListaRegistros(
                        meta.modulo, entidad, api, version,
                        alAbrir = { vista = Vista.Detalle(entidad, it.id) },
                        alCrear = { vista = Vista.Formulario(entidad, null) },
                    )
                }
            }
            is Vista.Detalle -> DetalleRegistro(
                meta.modulo, v.entidad, v.id, api, version,
                alEditar = { vista = Vista.Formulario(v.entidad, it) },
                alCambiar = { texto -> mensaje = texto; version++ },
            )
            is Vista.Formulario -> FormularioRegistro(
                meta.modulo, v.entidad, v.registro, api,
                alCancelar = { vista = if (v.registro == null) Vista.Lista else Vista.Detalle(v.entidad, v.registro.id) },
                alGuardar = { r ->
                    mensaje = "${r.numero} guardado"
                    version++
                    vista = Vista.Detalle(v.entidad, r.id)
                },
            )
        }
    }
}

// --- Lista ------------------------------------------------------------------------------------
@Composable
private fun ListaRegistros(
    modulo: String,
    entidad: EntidadMeta,
    api: ApiCliente,
    version: Int,
    alAbrir: (Registro) -> Unit,
    alCrear: () -> Unit,
) {
    var estado by rememberSaveable(entidad.codigo) { mutableStateOf<String?>(null) }
    var buscar by rememberSaveable(entidad.codigo) { mutableStateOf("") }
    var consulta by rememberSaveable(entidad.codigo) { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = buscar,
                onValueChange = { buscar = it },
                placeholder = { Text("Buscar por número o texto") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = { consulta = buscar }) { Text("Buscar") }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (entidad.estados.size > 1) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = estado == null, onClick = { estado = null }, label = { Text("Todos") })
                    entidad.estados.forEach { e ->
                        FilterChip(selected = estado == e.codigo, onClick = { estado = e.codigo }, label = { Text(e.etiqueta) })
                    }
                }
            }
            Cargar(
                clave = "$modulo.${entidad.codigo}|$estado|$consulta|$version",
                cargar = { api.registros(modulo, entidad.codigo, estado, consulta) },
            ) { pagina ->
                if (pagina.elementos.isEmpty()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("No hay ${entidad.plural.lowercase(ES)} con este filtro.")
                        if (entidad.ayuda.isNotBlank()) {
                            Text(entidad.ayuda, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        item {
                            Text(
                                "${pagina.elementos.size} de ${pagina.total}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(pagina.elementos, key = { it.id }) { r ->
                            ListItem(
                                headlineContent = { Text("${r.numero} · ${r.titulo}", fontWeight = FontWeight.Medium) },
                                supportingContent = {
                                    Text(
                                        entidad.columnas.mapNotNull { col ->
                                            val c = entidad.campos.firstOrNull { it.nombre == col } ?: return@mapNotNull null
                                            valorLegible(c, r).takeIf { it.isNotBlank() }?.let { "${c.etiqueta}: $it" }
                                        }.joinToString(" · "),
                                    )
                                },
                                trailingContent = { Etiqueta(r.estadoEtiqueta, colorTono(r.tono)) },
                                modifier = Modifier
                                    .heightIn(min = LocalTactil.current)
                                    .clickable { alAbrir(r) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        if (entidad.puedeCrear) {
            ExtendedFloatingActionButton(
                onClick = alCrear,
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Nuevo registro") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

// --- Detalle ----------------------------------------------------------------------------------
@Composable
private fun DetalleRegistro(
    modulo: String,
    entidad: EntidadMeta,
    id: Long,
    api: ApiCliente,
    version: Int,
    alEditar: (Registro) -> Unit,
    alCambiar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var pidiendoNota by remember { mutableStateOf<AccionRegistro?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun ejecutar(bloque: suspend () -> Registro, exito: (Registro) -> String) {
        ocupado = true
        error = null
        alcance.launch {
            try {
                alCambiar(exito(bloque()))
            } catch (e: ApiError) {
                error = e.message
            } finally {
                ocupado = false
            }
        }
    }

    Cargar(clave = "$id|$version", cargar = { api.registro(modulo, entidad.codigo, id) }) { r ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entidad.nombre, style = MaterialTheme.typography.labelMedium)
                    Text("${r.numero} · ${r.titulo}", style = MaterialTheme.typography.titleLarge)
                }
                Etiqueta(r.estadoEtiqueta, colorTono(r.tono))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entidad.campos.forEach { c ->
                        val valor = valorLegible(c, r)
                        if (valor.isNotBlank()) {
                            Column {
                                Text(c.etiqueta, style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(valor, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            // Acciones: el servidor envía solo las que el rol puede hacer en el estado actual.
            if (r.acciones.isNotEmpty() || (r.editable && entidad.puedeCrear)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    r.acciones.forEach { a ->
                        Button(
                            enabled = !ocupado,
                            onClick = {
                                if (a.requiereNota) {
                                    pidiendoNota = a
                                } else {
                                    ejecutar({ api.accionRegistro(modulo, entidad.codigo, r.id, a.accion, null) }) {
                                        "${it.numero}: ${it.estadoEtiqueta}"
                                    }
                                }
                            },
                            modifier = Modifier.heightIn(min = LocalTactil.current),
                        ) { Text(a.etiqueta) }
                    }
                    if (r.editable && entidad.puedeCrear) {
                        OutlinedButton(
                            enabled = !ocupado,
                            onClick = { alEditar(r) },
                            modifier = Modifier.heightIn(min = LocalTactil.current),
                        ) { Text("Editar") }
                    }
                }
            }
            if (modulo == "twin" && entidad.codigo == "variable" && r.estado == "activo" &&
                r.datos["protocolo"].comoTexto() == "modbus_tcp" && entidad.puedeCrear
            ) {
                OutlinedButton(
                    enabled = !ocupado,
                    onClick = { ejecutar({ api.leerPlc(r.id) }) { "Lectura guardada: ${numeroLegible(it.datos["valor"].comoNumero(), 3)}" } },
                    modifier = Modifier.heightIn(min = LocalTactil.current),
                ) { Text("Leer PLC ahora") }
            }

            Text("Historial", style = MaterialTheme.typography.titleMedium)
            r.historial.asReversed().forEach { ev ->
                val estado = entidad.estados.firstOrNull { it.codigo == ev.aEstado }?.etiqueta ?: ev.aEstado
                Column {
                    Text("${fechaCorta(ev.fecha)} · ${ev.usuario}", style = MaterialTheme.typography.labelMedium)
                    Text(
                        if (ev.accion == "crear") "Registrado como $estado" else "Pasó a $estado",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ev.nota?.takeIf { it.isNotBlank() }?.let {
                        Text("Nota: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }

        pidiendoNota?.let { a ->
            DialogoTexto(
                titulo = a.etiqueta,
                etiqueta = "Motivo u observación",
                alCancelar = { pidiendoNota = null },
                alConfirmar = { nota ->
                    pidiendoNota = null
                    ejecutar({ api.accionRegistro(modulo, entidad.codigo, r.id, a.accion, nota) }) {
                        "${it.numero}: ${it.estadoEtiqueta}"
                    }
                },
            )
        }
    }
}

// --- Formulario dinámico ------------------------------------------------------------------------
@Composable
private fun FormularioRegistro(
    modulo: String,
    entidad: EntidadMeta,
    registro: Registro?,
    api: ApiCliente,
    alCancelar: () -> Unit,
    alGuardar: (Registro) -> Unit,
) {
    val campos = entidad.campos.filter { !it.soloLectura }
    val valores = remember(entidad.codigo, registro?.id) {
        mutableStateMapOf<String, String>().apply {
            campos.forEach { c ->
                val inicial = registro?.datos?.get(c.nombre) ?: c.defecto
                put(c.nombre, inicial.comoTexto())
            }
        }
    }
    val alcance = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    BackHandler { alCancelar() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (registro == null) "Nuevo: ${entidad.nombre.lowercase(ES)}" else "Editar ${registro.numero}",
            style = MaterialTheme.typography.titleLarge,
        )
        if (entidad.ayuda.isNotBlank()) {
            Text(entidad.ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        campos.forEach { c ->
            CampoEditable(c, valores[c.nombre].orEmpty(), api) { valores[c.nombre] = it }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = alCancelar, modifier = Modifier.heightIn(min = LocalTactil.current)) { Text("Cancelar") }
            Button(
                enabled = !ocupado,
                onClick = {
                    val faltan = campos.filter { it.requerido && valores[it.nombre].isNullOrBlank() && it.tipo != "booleano" }
                    if (faltan.isNotEmpty()) {
                        error = "Complete: " + faltan.joinToString { it.etiqueta }
                        return@Button
                    }
                    ocupado = true
                    error = null
                    alcance.launch {
                        try {
                            val datos = JsonObject(campos.associate { it.nombre to aJson(it, valores[it.nombre].orEmpty()) })
                            val r = if (registro == null) {
                                api.crearRegistro(modulo, entidad.codigo, datos)
                            } else {
                                api.editarRegistro(modulo, entidad.codigo, registro.id, datos)
                            }
                            alGuardar(r)
                        } catch (e: ApiError) {
                            error = e.message
                        } finally {
                            ocupado = false
                        }
                    }
                },
                modifier = Modifier.heightIn(min = LocalTactil.current),
            ) { Text("Guardar") }
        }
        Spacer(Modifier.heightIn(min = 48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoEditable(c: CampoMeta, valor: String, api: ApiCliente, alCambiar: (String) -> Unit) {
    val etiqueta = c.etiqueta + if (c.requerido) " *" else ""
    val ayuda: (@Composable () -> Unit)? = if (c.ayuda.isBlank()) null else ({ Text(c.ayuda) })
    when (c.tipo) {
        "texto_largo" -> OutlinedTextField(
            value = valor, onValueChange = alCambiar, label = { Text(etiqueta) }, supportingText = ayuda,
            minLines = 3, modifier = Modifier.fillMaxWidth(),
        )
        "entero", "numero" -> OutlinedTextField(
            value = valor,
            onValueChange = alCambiar,
            label = { Text(etiqueta) },
            supportingText = ayuda,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (c.tipo == "entero") KeyboardType.Number else KeyboardType.Decimal,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        "booleano" -> Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = LocalTactil.current),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.etiqueta)
                if (c.ayuda.isNotBlank()) Text(c.ayuda, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = valor == "true", onCheckedChange = { alCambiar(it.toString()) })
        }
        "opcion" -> Etiquetado(etiqueta, c.ayuda) {
            Selector(etiqueta, c.opciones, c.opciones.firstOrNull { it.valor == valor }, { it.etiqueta }) {
                alCambiar(it.valor)
            }
        }
        "moneda" -> Etiquetado(etiqueta, c.ayuda) {
            Selector(etiqueta, MONEDAS_REGISTRO, valor.ifBlank { null }, { it }) { alCambiar(it) }
        }
        "referencia", "usuario", "nodo" -> {
            var opciones by remember(c.nombre) { mutableStateOf<List<OpcionRef>?>(null) }
            var fallo by remember(c.nombre) { mutableStateOf<String?>(null) }
            LaunchedEffect(c.nombre) {
                try {
                    opciones = when (c.tipo) {
                        "usuario" -> api.opcionesUsuarios()
                        "nodo" -> api.opcionesNodos()
                        else -> api.opcionesReferencia(c.referencia.orEmpty())
                    }
                } catch (e: ApiError) {
                    fallo = e.message
                }
            }
            Etiquetado(etiqueta, fallo ?: c.ayuda) {
                val lista = opciones.orEmpty()
                Selector(
                    if (opciones == null) "Cargando…" else if (lista.isEmpty()) "Sin opciones registradas" else etiqueta,
                    lista,
                    lista.firstOrNull { it.id.toString() == valor },
                    { it.etiqueta },
                ) { alCambiar(it.id.toString()) }
            }
        }
        "fecha" -> SelectorFecha(etiqueta, c.ayuda, valor, conHora = false, alCambiar)
        "fechahora" -> SelectorFecha(etiqueta, c.ayuda, valor, conHora = true, alCambiar)
        else -> OutlinedTextField(
            value = valor, onValueChange = alCambiar, label = { Text(etiqueta) }, supportingText = ayuda,
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Etiquetado(etiqueta: String, ayuda: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium)
        contenido()
        if (ayuda.isNotBlank()) {
            Text(ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Elige una fecha (y la hora, si corresponde). Guarda ISO local: el servidor aplica la zona de la planta. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorFecha(etiqueta: String, ayuda: String, valor: String, conHora: Boolean, alCambiar: (String) -> Unit) {
    var paso by remember { mutableIntStateOf(0) } // 0 cerrado, 1 fecha, 2 hora
    var dia by remember { mutableStateOf<LocalDate?>(null) }
    val actual = leerFechaHora(valor)
    Etiquetado(etiqueta, ayuda) {
        OutlinedButton(
            onClick = { paso = 1 },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalTactil.current),
        ) {
            Text(
                when {
                    actual == null -> "Elegir fecha"
                    conHora -> actual.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    else -> actual.toLocalDate().format(FECHA)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (paso == 1) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = (actual?.toLocalDate() ?: LocalDate.now())
                .atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { paso = 0 },
            confirmButton = {
                TextButton(onClick = {
                    val ms = estado.selectedDateMillis
                    if (ms == null) {
                        paso = 0
                    } else {
                        val elegido = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        if (conHora) {
                            dia = elegido
                            paso = 2
                        } else {
                            alCambiar(elegido.toString())
                            paso = 0
                        }
                    }
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { paso = 0 }) { Text("Cancelar") } },
        ) { DatePicker(state = estado) }
    }
    if (paso == 2) {
        val ahora = actual?.toLocalTime() ?: LocalTime.now()
        val estado = rememberTimePickerState(initialHour = ahora.hour, initialMinute = ahora.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { paso = 0 },
            title = { Text("Hora") },
            text = { TimePicker(state = estado) },
            confirmButton = {
                TextButton(onClick = {
                    val d = dia ?: LocalDate.now()
                    alCambiar(LocalDateTime.of(d, LocalTime.of(estado.hour, estado.minute)).toString())
                    paso = 0
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { paso = 0 }) { Text("Cancelar") } },
        )
    }
}

// --- Indicadores ------------------------------------------------------------------------------------
@Composable
private fun IndicadoresContenido(modulo: String, api: ApiCliente) {
    var dias by rememberSaveable { mutableIntStateOf(30) }
    var abierto by remember { mutableStateOf<IndicadorArea?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(7, 30, 90, 365).forEach { d ->
                FilterChip(selected = dias == d, onClick = { dias = d }, label = { Text("$d días") })
            }
        }
        Cargar(clave = "$modulo|$dias", cargar = { api.indicadoresArea(modulo, dias) }) { lista ->
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lista, key = { it.clave }) { i ->
                    val color = colorSeveridad(i.severidad)
                    Card(
                        colors = CardDefaults.cardColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { abierto = i },
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(i.nombre, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    if (i.valor == null) "sin datos" else valorIndicador(i),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = if (i.valor == null) MaterialTheme.colorScheme.onSurfaceVariant else color,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                i.meta?.let {
                                    Text(
                                        "Meta ${if (i.mayorEsMejor) "≥" else "≤"} ${numeroLegible(it, i.decimales)} ${i.unidad}".trim(),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                if (i.detalle.isNotBlank()) Text(i.detalle, style = MaterialTheme.typography.bodySmall)
                            }
                            Etiqueta(NOMBRE_SEVERIDAD[i.severidad] ?: i.severidad, color)
                        }
                    }
                }
            }
        }
    }
    abierto?.let { i ->
        AlertDialog(
            onDismissRequest = { abierto = null },
            title = { Text(i.nombre) },
            text = { Text(i.definicion) },
            confirmButton = { TextButton(onClick = { abierto = null }) { Text("Entendido") } },
        )
    }
}

private val NOMBRE_SEVERIDAD = mapOf("ok" to "Cumple", "info" to "Informativo", "alerta" to "Atención", "critico" to "Crítico")

private fun colorSeveridad(s: String): Color = when (s) {
    "ok" -> Gama.Ok
    "alerta" -> Gama.Alerta
    "critico" -> Gama.Critico
    else -> Gama.Primario
}

private fun colorTono(t: String): Color = when (t) {
    "ok" -> Gama.Ok
    "alerta" -> Gama.Alerta
    "critico" -> Gama.Critico
    "info" -> Gama.Primario
    else -> Color(0xFF5F6B76)
}

// --- Formato -----------------------------------------------------------------------------------------
private fun numeroLegible(valor: Double?, decimales: Int): String =
    if (valor == null) "" else String.format(ES, "%,.${decimales.coerceIn(0, 6)}f", valor)

private fun valorIndicador(i: IndicadorArea): String {
    val n = numeroLegible(i.valor, i.decimales)
    return when {
        i.unidad.isBlank() -> n
        i.unidad == "%" -> "$n %"
        else -> "$n ${i.unidad}"
    }
}

private fun leerFechaHora(valor: String): LocalDateTime? {
    if (valor.isBlank()) return null
    return runCatching { OffsetDateTime.parse(valor).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(valor) }
        .recoverCatching { LocalDate.parse(valor).atStartOfDay() }
        .getOrNull()
}

/** Valor de un campo como lo lee una persona: nombres de referencias, opciones, fechas y números. */
private fun valorLegible(c: CampoMeta, r: Registro): String {
    r.etiquetas[c.nombre]?.let { return it }
    val v = r.datos[c.nombre]
    if (v == null || v is JsonNull) return ""
    return when (c.tipo) {
        "booleano" -> if (v.comoBooleano()) "Sí" else "No"
        "fecha" -> leerFechaHora(v.comoTexto())?.toLocalDate()?.format(FECHA) ?: v.comoTexto()
        "fechahora" -> fechaCorta(v.comoTexto())
        "numero" -> numeroLegible(v.comoNumero(), if ((v.comoNumero() ?: 0.0) % 1.0 == 0.0) 0 else 2)
        "entero" -> numeroLegible(v.comoNumero(), 0)
        "opcion" -> c.opciones.firstOrNull { it.valor == v.comoTexto() }?.etiqueta ?: v.comoTexto()
        else -> v.comoTexto()
    }
}

/** Convierte lo escrito al tipo del campo. Si no se puede, se envía tal cual y el servidor explica el error. */
private fun aJson(c: CampoMeta, texto: String): JsonElement {
    val t = texto.trim()
    if (t.isEmpty()) return JsonNull
    return when (c.tipo) {
        "entero", "referencia", "usuario", "nodo" -> t.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(t)
        "numero" -> t.replace(',', '.').toDoubleOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(t)
        "booleano" -> JsonPrimitive(t == "true")
        else -> JsonPrimitive(t)
    }
}
