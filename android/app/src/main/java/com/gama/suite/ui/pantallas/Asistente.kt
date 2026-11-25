package com.gama.suite.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import com.gama.suite.datos.AREAS_ASISTENTE
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.AyudaUso
import com.gama.suite.datos.HechoAsistente
import com.gama.suite.datos.LeccionUso
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.tema.Gama
import kotlinx.coroutines.launch
import java.util.Locale

private val NOMBRE_SEVERIDAD = mapOf(
    "critico" to "Urgente",
    "alerta" to "Para atender",
    "info" to "Para saber",
    "ok" to "Va bien",
)

private fun colorSeveridad(severidad: String): Color = when (severidad) {
    "critico" -> Gama.Critico
    "alerta" -> Gama.Alerta
    "ok" -> Gama.Ok
    else -> Gama.Primario
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsistentePantalla(api: ApiCliente) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pestana) {
            listOf("Resumen", "Aprender", "Preguntar").forEachIndexed { i, titulo ->
                Tab(selected = pestana == i, onClick = { pestana = i }, text = { Text(titulo) })
            }
        }
        when (pestana) {
            0 -> ResumenTab(api)
            1 -> AprenderTab(api)
            else -> PreguntarTab(api)
        }
    }
}

// --- Resumen --------------------------------------------------------------------
@Composable
private fun ResumenTab(api: ApiCliente) {
    var area by rememberSaveable { mutableStateOf("general") }
    var version by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AREAS_ASISTENTE.forEach { (clave, nombre) ->
                FilterChip(selected = area == clave, onClick = { area = clave }, label = { Text(nombre) })
            }
        }
        Cargar(clave = "$area-$version", cargar = { api.resumenAsistente(area) }) { r ->
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Qué está pasando", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.weight(1f))
                                Etiqueta(
                                    if (r.narrativa.fuente == "ia_verificada") "IA verificada" else "Datos exactos",
                                    Gama.Primario,
                                )
                            }
                            Text(r.narrativa.texto, style = MaterialTheme.typography.bodyMedium)
                            Text(r.narrativa.nota, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Actualizado: ${fechaHora(r.generadoEn)}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(r.hechos, key = { it.clave }) { TarjetaHecho(it) }
                if (r.areasSinPermiso.isNotEmpty()) {
                    item {
                        val nombres = r.areasSinPermiso.map { a -> AREAS_ASISTENTE.firstOrNull { it.first == a }?.second ?: a }
                        Text(
                            "Su rol no incluye: ${nombres.joinToString()}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    Button(onClick = { version++ }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar") }
                }
            }
        }
    }
}

private fun fechaHora(iso: String): String = iso.take(16).replace('T', ' ')

@Composable
private fun TarjetaHecho(h: HechoAsistente) {
    var abierto by rememberSaveable(h.clave) { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { abierto = !abierto },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(NOMBRE_SEVERIDAD[h.severidad] ?: h.severidad, colorSeveridad(h.severidad))
                Spacer(Modifier.width(8.dp))
                Text(h.titulo, fontWeight = FontWeight.SemiBold)
            }
            Text(h.texto, style = MaterialTheme.typography.bodyMedium)
            if (abierto) {
                Text("Qué significa", style = MaterialTheme.typography.labelLarge)
                Text(h.explicacion, style = MaterialTheme.typography.bodySmall)
                h.accion?.let {
                    Text("Qué hacer", style = MaterialTheme.typography.labelLarge)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                h.seccion?.let {
                    Text("Detalle en la pestaña «$it».", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Toque para ver qué significa y qué hacer", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// --- Aprender -------------------------------------------------------------------
@Composable
private fun AprenderTab(api: ApiCliente) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Cargar(clave = version, cargar = { api.guiaUso() }) { g ->
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Guía para ${g.usuario}", style = MaterialTheme.typography.titleMedium)
                        Text("Según su rol: ${g.roles.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            progress = { (g.avancePct / 100).toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                        )
                        Text("${String.format(Locale.getDefault(), "%.0f", g.avancePct)} % aprendido",
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(g.lecciones, key = { it.codigo }) { x ->
                    TarjetaLeccion(x) {
                        alcance.launch {
                            mensaje = try {
                                api.completarLeccion(x.codigo)
                                version++
                                "«${x.titulo}» marcada como aprendida"
                            } catch (e: ApiError) {
                                e.message
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TarjetaLeccion(x: LeccionUso, alCompletar: (() -> Unit)?) {
    var abierta by rememberSaveable(x.codigo) { mutableStateOf(false) }
    Card(
        Modifier
            .fillMaxWidth()
            .clickable { abierta = !abierta },
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Etiqueta(x.moduloNombre, Gama.Primario)
                Spacer(Modifier.weight(1f))
                if (x.completada) Etiqueta("Aprendida", Gama.Ok)
            }
            Text(x.titulo, fontWeight = FontWeight.SemiBold)
            Text(x.paraQue, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (abierta) {
                x.pasos.forEachIndexed { i, paso ->
                    Text("${i + 1}. $paso", style = MaterialTheme.typography.bodyMedium)
                }
                if (alCompletar != null && !x.completada) {
                    Button(onClick = alCompletar, modifier = Modifier.fillMaxWidth()) { Text("Ya lo hice") }
                }
            }
        }
    }
}

// --- Preguntar ------------------------------------------------------------------
@Composable
private fun PreguntarTab(api: ApiCliente) {
    val alcance = rememberCoroutineScope()
    var pregunta by rememberSaveable { mutableStateOf("") }
    var respuesta by remember { mutableStateOf<AyudaUso?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var buscando by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = pregunta,
                onValueChange = { pregunta = it },
                label = { Text("¿Cómo hago…?") },
                placeholder = { Text("Por ejemplo: reportar una falla") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                enabled = !buscando && pregunta.trim().length >= 2,
                onClick = {
                    buscando = true
                    error = null
                    alcance.launch {
                        try {
                            respuesta = api.ayudaUso(pregunta.trim())
                        } catch (e: ApiError) {
                            error = e.message
                        } finally {
                            buscando = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Preguntar") }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        respuesta?.let { r ->
            item { Text(r.respuesta, style = MaterialTheme.typography.bodyMedium) }
            items(r.lecciones, key = { "ayuda-${it.codigo}" }) { TarjetaLeccion(it, alCompletar = null) }
        }
    }
}
