package com.gama.suite.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiError
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.tema.LocalTactil
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Carga datos de la API con estados de carga, error y reintento.
 * Cambiar [clave] vuelve a cargar (por ejemplo, después de guardar algo).
 */
@Composable
fun <T> Cargar(clave: Any? = null, cargar: suspend () -> T, contenido: @Composable (T) -> Unit) {
    var intento by remember { mutableIntStateOf(0) }
    var datos by remember { mutableStateOf<T?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(intento, clave) {
        error = null
        try {
            datos = cargar()
        } catch (e: ApiError) {
            error = e.message
        }
    }
    val actual = datos
    when {
        error != null && actual == null -> Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { intento++ }) { Text("Reintentar") }
        }
        actual == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> contenido(actual)
    }
}

private val FORMATO_CORTO = DateTimeFormatter.ofPattern("dd/MM HH:mm")

/** "2026-11-21T22:10:00.123+00:00" → "21/11 18:10" en la hora del teléfono. */
fun fechaCorta(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(FORMATO_CORTO)
    } catch (e: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

val NOMBRE_PRIORIDAD = mapOf(1 to "Urgente", 2 to "Alta", 3 to "Normal", 4 to "Baja")

fun colorPrioridad(p: Int): Color = when (p) {
    1 -> Gama.Critico
    2 -> Gama.Alerta
    else -> Gama.Ok
}

/** Etiqueta de color, por ejemplo para estados y prioridades. */
@Composable
fun Etiqueta(texto: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(
            texto,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Selector simple: un botón que abre un menú con las opciones. */
@Composable
fun <T> Selector(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T?,
    texto: (T) -> String,
    alElegir: (T) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { abierto = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = LocalTactil.current),
        ) {
            Text(
                seleccion?.let(texto) ?: etiqueta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            opciones.forEach { o ->
                DropdownMenuItem(text = { Text(texto(o)) }, onClick = { abierto = false; alElegir(o) })
            }
        }
    }
}

/** Diálogo que pide un texto obligatorio (motivo de rechazo, de pausa, etc.). */
@Composable
fun DialogoTexto(titulo: String, etiqueta: String, alCancelar: () -> Unit, alConfirmar: (String) -> Unit) {
    var texto by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text(etiqueta) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { alConfirmar(texto.trim()) }, enabled = texto.trim().length >= 3) { Text("Aceptar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/** Franja de aviso arriba de la pantalla, para confirmar acciones o mostrar errores. */
@Composable
fun BarraMensaje(texto: String?, alCerrar: () -> Unit) {
    if (texto == null) return
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(texto, modifier = Modifier.align(Alignment.CenterStart).padding(end = 72.dp))
            TextButton(onClick = alCerrar, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text("OK", color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
