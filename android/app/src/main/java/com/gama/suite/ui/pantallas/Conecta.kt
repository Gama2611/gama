package com.gama.suite.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.Canal
import com.gama.suite.datos.Mensaje
import com.gama.suite.datos.MensajeNuevo
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.Persona
import com.gama.suite.datos.REACCIONES
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.fechaCorta
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.tema.LocalTactil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Sin MQTT todavía en la app: se consulta al servidor cada pocos segundos mientras la pantalla está abierta.
private const val REFRESCO_LISTA_MS = 15_000L
private const val REFRESCO_CHAT_MS = 6_000L

private fun iconoCanal(tipo: String): ImageVector = when (tipo) {
    "muro" -> Icons.Outlined.Forum
    "privado" -> Icons.Outlined.Lock
    "directo" -> Icons.Outlined.Person
    else -> Icons.Outlined.Tag
}

@Composable
fun ConectaPantalla(modulo: ModuloSesion, api: ApiCliente) {
    var canalId by rememberSaveable { mutableStateOf<Int?>(null) }
    var canalNombre by rememberSaveable { mutableStateOf("") }
    var canalTipo by rememberSaveable { mutableStateOf("") }

    val abierto = canalId
    if (abierto != null) {
        BackHandler { canalId = null }
        ChatCanal(abierto, canalNombre, canalTipo, modulo, api) { canalId = null }
        return
    }
    ListaCanales(modulo, api) { c ->
        canalNombre = c.nombre
        canalTipo = c.tipo
        canalId = c.id
    }
}

@Composable
private fun ListaCanales(modulo: ModuloSesion, api: ApiCliente, alAbrir: (Canal) -> Unit) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var eligiendo by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESCO_LISTA_MS)
            version++
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Box(Modifier.fillMaxSize()) {
            Cargar(clave = version, cargar = { api.canales() }) { canales ->
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(canales, key = { it.id }) { c ->
                        ListItem(
                            leadingContent = { Icon(iconoCanal(c.tipo), null, tint = MaterialTheme.colorScheme.primary) },
                            headlineContent = {
                                Text(c.nombre, fontWeight = if (c.noLeidos > 0) FontWeight.Bold else FontWeight.Normal)
                            },
                            supportingContent = {
                                Text(
                                    c.descripcion ?: when (c.tipo) {
                                        "directo" -> "Mensaje directo"
                                        else -> "${c.miembros} miembros"
                                    },
                                )
                            },
                            trailingContent = if (c.noLeidos > 0) {
                                { Badge { Text("${c.noLeidos}") } }
                            } else {
                                null
                            },
                            modifier = Modifier
                                .heightIn(min = LocalTactil.current)
                                .clickable { alAbrir(c) },
                        )
                        HorizontalDivider()
                    }
                }
            }
            if (modulo.puede("registrar")) {
                ExtendedFloatingActionButton(
                    onClick = { eligiendo = true },
                    icon = { Icon(Icons.Outlined.Person, null) },
                    text = { Text("Mensaje directo") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (eligiendo) {
        PersonasDialogo(api, alCerrar = { eligiendo = false }) { persona ->
            eligiendo = false
            alcance.launch {
                try {
                    alAbrir(api.abrirDirecto(persona.id))
                } catch (e: ApiError) {
                    mensaje = e.message
                }
            }
        }
    }
}

@Composable
private fun PersonasDialogo(api: ApiCliente, alCerrar: () -> Unit, alElegir: (Persona) -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("¿Con quién quiere hablar?") },
        text = {
            Box(Modifier.heightIn(max = 420.dp)) {
                Cargar(cargar = { api.personas() }) { personas ->
                    LazyColumn {
                        items(personas, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.nombre) },
                                supportingContent = { Text(p.roles.joinToString()) },
                                modifier = Modifier.clickable { alElegir(p) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun ChatCanal(
    canalId: Int,
    nombre: String,
    tipo: String,
    modulo: ModuloSesion,
    api: ApiCliente,
    alVolver: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var texto by rememberSaveable { mutableStateOf("") }
    var aviso by remember { mutableStateOf(false) }
    var enviando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var comentando by remember { mutableStateOf<Mensaje?>(null) }
    val puedeAvisar = modulo.puede("aprobar") && tipo != "directo"

    LaunchedEffect(canalId) {
        while (true) {
            delay(REFRESCO_CHAT_MS)
            version++
        }
    }

    fun accion(trabajo: suspend () -> Unit) {
        alcance.launch {
            try {
                trabajo()
                version++
            } catch (e: ApiError) {
                mensaje = e.message
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            Icon(iconoCanal(tipo), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(nombre, style = MaterialTheme.typography.titleMedium)
        }
        BarraMensaje(mensaje) { mensaje = null }
        Box(Modifier.weight(1f)) {
            Cargar(clave = version, cargar = { api.mensajes(canalId) }) { mensajes ->
                if (mensajes.isEmpty()) {
                    Text("Todavía no hay mensajes. Escriba el primero.", Modifier.padding(24.dp))
                } else {
                    // Lista invertida: el mensaje más reciente queda abajo, junto al teclado.
                    LazyColumn(reverseLayout = true, contentPadding = PaddingValues(12.dp)) {
                        items(mensajes, key = { it.id }) { m ->
                            TarjetaMensaje(
                                m = m,
                                alReaccionar = { emoji -> accion { api.reaccionar(m.id, emoji) } },
                                alConfirmar = { accion { api.confirmarAviso(m.id) } },
                                alComentar = { comentando = m },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        if (modulo.puede("registrar")) {
            Column(Modifier.padding(8.dp)) {
                if (puedeAvisar) {
                    FilterChip(
                        selected = aviso,
                        onClick = { aviso = !aviso },
                        label = { Text("Aviso oficial (pide confirmación de lectura)") },
                        leadingIcon = { Icon(Icons.Outlined.Campaign, null) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        placeholder = { Text("Escriba un mensaje") },
                        maxLines = 5,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = texto.isNotBlank() && !enviando,
                        onClick = {
                            val contenido = texto.trim()
                            enviando = true
                            alcance.launch {
                                try {
                                    api.publicar(canalId, MensajeNuevo(contenido, aviso = aviso))
                                    texto = ""
                                    aviso = false
                                    version++
                                } catch (e: ApiError) {
                                    mensaje = e.message
                                } finally {
                                    enviando = false
                                }
                            }
                        },
                    ) { Icon(Icons.AutoMirrored.Outlined.Send, "Enviar") }
                }
            }
        }
    }

    comentando?.let { m ->
        ComentariosDialogo(canalId, m, modulo.puede("registrar"), api) {
            comentando = null
            version++
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaMensaje(
    m: Mensaje,
    alReaccionar: (String) -> Unit,
    alConfirmar: () -> Unit,
    alComentar: () -> Unit,
) {
    Card(
        colors = if (m.esAviso) {
            CardDefaults.cardColors(containerColor = Gama.Acento.copy(alpha = 0.12f))
        } else {
            CardDefaults.cardColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.autor, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(fechaCorta(m.creadoEn), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (m.esAviso) {
                    Spacer(Modifier.weight(1f))
                    Etiqueta("AVISO OFICIAL", Gama.Alerta)
                }
            }
            if (m.oculto) {
                Text(
                    "Publicación ocultada por moderación" + (m.motivoOculto?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ContenidoMensaje(m, alReaccionar, alConfirmar, alComentar)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoMensaje(
    m: Mensaje,
    alReaccionar: (String) -> Unit,
    alConfirmar: () -> Unit,
    alComentar: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(m.texto, style = MaterialTheme.typography.bodyLarge)
            m.tarjeta?.let { t ->
                AssistChip(onClick = {}, label = { Text("${t.modulo.uppercase()} · ${t.entidad} ${t.ref}") })
            }
            if (m.esAviso) {
                if (m.avisoConfirmadoPorMi == true) {
                    Text("Usted confirmó la lectura", style = MaterialTheme.typography.labelMedium, color = Gama.Ok)
                } else {
                    Button(onClick = alConfirmar, modifier = Modifier.heightIn(min = LocalTactil.current)) {
                        Text("Leí y entendí")
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                REACCIONES.forEach { emoji ->
                    val n = m.reacciones[emoji] ?: 0
                    FilterChip(
                        selected = emoji in m.misReacciones,
                        onClick = { alReaccionar(emoji) },
                        label = { Text(if (n > 0) "$emoji $n" else emoji) },
                    )
                }
                TextButton(onClick = alComentar) {
                    Text(if (m.comentarios > 0) "Comentarios (${m.comentarios})" else "Comentar")
                }
            }
    }
}

@Composable
private fun ComentariosDialogo(canalId: Int, publicacion: Mensaje, puedeEscribir: Boolean, api: ApiCliente, alCerrar: () -> Unit) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var texto by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Comentarios") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(publicacion.texto, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                HorizontalDivider()
                Box(Modifier.heightIn(max = 320.dp)) {
                    Cargar(clave = version, cargar = { api.comentarios(publicacion.id) }) { lista ->
                        if (lista.isEmpty()) {
                            Text("Sin comentarios todavía.")
                        } else {
                            LazyColumn {
                                items(lista, key = { it.id }) { c ->
                                    Column(Modifier.padding(vertical = 4.dp)) {
                                        Text("${c.autor} · ${fechaCorta(c.creadoEn)}", style = MaterialTheme.typography.labelMedium)
                                        Text(if (c.oculto) "Comentario ocultado por moderación" else c.texto)
                                    }
                                }
                            }
                        }
                    }
                }
                if (puedeEscribir) {
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        placeholder = { Text("Escriba un comentario") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (puedeEscribir) {
                TextButton(enabled = texto.isNotBlank(), onClick = {
                    val contenido = texto.trim()
                    alcance.launch {
                        try {
                            api.publicar(canalId, MensajeNuevo(contenido, respuestaA = publicacion.id))
                            texto = ""
                            version++
                        } catch (e: ApiError) {
                            error = e.message
                        }
                    }
                }) { Text("Comentar") }
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
