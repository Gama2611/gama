package com.gama.suite.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gama.suite.ui.EstadoUi
import com.gama.suite.ui.tema.LocalTactil

/** Marco común de las pantallas de acceso: marca arriba, formulario centrado. */
@Composable
private fun MarcoAcceso(subtitulo: String, contenido: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "GAMA",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                subtitulo,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            contenido()
        }
    }
}

@Composable
private fun BotonPrincipal(texto: String, ocupado: Boolean, alPulsar: () -> Unit) {
    Button(
        onClick = alPulsar,
        enabled = !ocupado,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = LocalTactil.current),
    ) {
        if (ocupado) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(texto)
        }
    }
}

@Composable
private fun MensajeError(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Muestra la huella del certificado para compararla con la consola del servidor antes de confiar. */
@Composable
fun ConfirmarHuellaPantalla(estado: EstadoUi, alConfirmar: () -> Unit, alRechazar: () -> Unit) {
    val huella = estado.huellaPendiente ?: return
    MarcoAcceso("Verifique el servidor") {
        Text(
            "Compare esta huella con la que aparece en la consola del servidor (Conectar tabletas) " +
                "o en el archivo de acceso inicial. Deben ser idénticas.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(estado.servidor.orEmpty(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // En grupos de 8 bytes para leerla y compararla con facilidad.
            Text(
                huella.split(":").chunked(8).joinToString("\n") { it.joinToString(":") },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(14.dp),
            )
        }
        MensajeError(estado.error)
        BotonPrincipal("La huella coincide", estado.ocupado, alConfirmar)
        TextButton(onClick = alRechazar, modifier = Modifier.fillMaxWidth()) { Text("No coincide") }
    }
}

@Composable
fun ServidorPantalla(estado: EstadoUi, alConfirmar: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf(estado.servidor ?: "") }
    MarcoAcceso("Conectar con el servidor de su empresa") {
        Text(
            "Escriba la dirección del servidor GAMA. Se la entrega el administrador del sistema.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Dirección del servidor") },
            placeholder = { Text("https://192.168.1.50:8443") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        MensajeError(estado.error)
        BotonPrincipal("Conectar", estado.ocupado) { alConfirmar(url) }
    }
}

@Composable
fun LoginPantalla(estado: EstadoUi, alEntrar: (String, String) -> Unit, alCambiarServidor: () -> Unit) {
    var usuario by rememberSaveable { mutableStateOf(estado.usuarioIngresado) }
    var clave by rememberSaveable { mutableStateOf("") }
    MarcoAcceso("Iniciar sesión") {
        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text("Usuario") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = clave,
            onValueChange = { clave = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        MensajeError(estado.error)
        BotonPrincipal("Entrar", estado.ocupado) { alEntrar(usuario, clave) }
        TextButton(onClick = alCambiarServidor, modifier = Modifier.fillMaxWidth()) {
            Text("Servidor: ${estado.servidor ?: "-"} · Cambiar")
        }
    }
}

@Composable
fun CambioClavePantalla(estado: EstadoUi, alCambiar: (String, String, String) -> Unit, alSalir: () -> Unit) {
    var actual by rememberSaveable { mutableStateOf("") }
    var nueva by rememberSaveable { mutableStateOf("") }
    var confirmacion by rememberSaveable { mutableStateOf("") }
    MarcoAcceso("Cree una contraseña nueva") {
        Text(
            "Por seguridad debe cambiar la contraseña inicial. Use al menos 10 caracteres y combine letras con números o símbolos.",
            style = MaterialTheme.typography.bodyMedium,
        )
        CampoClave("Contraseña actual", actual) { actual = it }
        CampoClave("Contraseña nueva", nueva) { nueva = it }
        CampoClave("Confirme la contraseña nueva", confirmacion) { confirmacion = it }
        MensajeError(estado.error)
        BotonPrincipal("Guardar y entrar", estado.ocupado) { alCambiar(actual, nueva, confirmacion) }
        TextButton(onClick = alSalir, modifier = Modifier.fillMaxWidth()) { Text("Salir") }
    }
}

@Composable
private fun CampoClave(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
