package com.gama.suite.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.produceState
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.ResumenAsistente
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.Sesion
import com.gama.suite.ui.iconoModulo
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.tema.LocalTactil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InicioPantalla(
    sesion: Sesion,
    alAbrir: (ModuloSesion) -> Unit,
    alRecargar: () -> Unit,
    alSalir: () -> Unit,
    alCambiarServidor: () -> Unit,
    /** Carga el resumen del Asistente; null si el rol no tiene el módulo. */
    alResumen: (suspend () -> ResumenAsistente)? = null,
    alAbrirAsistente: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(sesion.empresa, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    IconButton(onClick = alRecargar) { Icon(Icons.Outlined.Refresh, "Actualizar") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Cambiar de servidor") },
                                leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                                onClick = { menu = false; alCambiarServidor() },
                            )
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                                onClick = { menu = false; alSalir() },
                            )
                        }
                    }
                },
            )
        },
    ) { relleno ->
        val grupos = sesion.modulos.groupBy { it.grupo }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { Encabezado(sesion) }
            if (alResumen != null) {
                item(span = { GridItemSpan(maxLineSpan) }) { ResumenInicio(alResumen, alAbrirAsistente) }
            }
            if (sesion.modulos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Su rol todavía no tiene módulos asignados. Pida acceso al administrador del sistema.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            grupos.forEach { (grupo, modulos) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        grupo.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(modulos, key = { it.codigo }) { m -> TarjetaModulo(m) { alAbrir(m) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Encabezado(sesion: Sesion) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            DateFormat.getDateInstance(DateFormat.FULL).format(Date()).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text("Hola, ${sesion.usuario.nombre}", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sesion.usuario.roles.forEach { rol -> SuggestionChip(onClick = {}, label = { Text(rol.nombre) }) }
        }
        val lic = sesion.licencia
        val aviso = when {
            lic.modo == "desarrollo" -> "Modo desarrollo: todos los módulos habilitados sin licencia."
            lic.modo == "solo_nucleo" -> "Sin licencia: solo está disponible la administración."
            !lic.soporteVigente -> "El soporte venció el ${lic.soporteHasta}. La suite sigue funcionando."
            else -> null
        }
        if (aviso != null) {
            Text(aviso, style = MaterialTheme.typography.bodySmall, color = Gama.Alerta)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TarjetaModulo(modulo: ModuloSesion, alPulsar: () -> Unit) {
    Card(
        onClick = alPulsar,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalTactil.current * 2),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                    Icon(
                        iconoModulo(modulo.icono),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(modulo.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                modulo.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                nivelDeAcceso(modulo),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private sealed interface CargaResumen {
    data object Cargando : CargaResumen
    data class Listo(val resumen: ResumenAsistente) : CargaResumen
    data class Error(val mensaje: String) : CargaResumen
}

private fun colorSeveridad(severidad: String) = when (severidad) {
    "critico" -> Gama.Critico
    "alerta" -> Gama.Alerta
    "ok" -> Gama.Ok
    else -> Gama.Primario
}

/** Lo más importante del Asistente, arriba de todo: cada persona ve lo urgente de su área al entrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResumenInicio(cargar: suspend () -> ResumenAsistente, alAbrir: () -> Unit) {
    val carga by produceState<CargaResumen>(CargaResumen.Cargando) {
        value = try {
            CargaResumen.Listo(cargar())
        } catch (e: ApiError) {
            CargaResumen.Error(e.message ?: "No se pudo cargar el resumen")
        }
    }
    Card(
        onClick = alAbrir,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Qué está pasando", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Ver todo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            when (val c = carga) {
                CargaResumen.Cargando -> LinearProgressIndicator(Modifier.fillMaxWidth())
                is CargaResumen.Error -> Text(c.mensaje, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                is CargaResumen.Listo -> {
                    val hechos = c.resumen.hechos.filter { it.severidad != "ok" }.take(3)
                    if (hechos.isEmpty()) {
                        Text("Todo en orden en sus áreas.", style = MaterialTheme.typography.bodyMedium, color = Gama.Ok)
                    }
                    hechos.forEach { h ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .size(8.dp)
                                    .background(colorSeveridad(h.severidad), CircleShape),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(h.titulo, style = MaterialTheme.typography.labelLarge)
                                Text(h.texto, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun nivelDeAcceso(modulo: ModuloSesion): String = when {
    modulo.puede("administrar") -> "Administra"
    modulo.puede("aprobar") -> "Consulta, registra y aprueba".takeIf { modulo.puede("registrar") } ?: "Consulta y aprueba"
    modulo.puede("registrar") -> "Consulta y registra"
    else -> "Solo consulta"
}
