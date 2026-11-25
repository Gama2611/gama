package com.gama.suite.ui.pantallas

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.CodigoNuevo
import com.gama.suite.datos.CodigoRegistro
import com.gama.suite.datos.ComandoNuevo
import com.gama.suite.datos.DispositivoMdm
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.ui.BarraMensaje
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.DialogoTexto
import com.gama.suite.ui.Etiqueta
import com.gama.suite.ui.fechaCorta
import com.gama.suite.ui.tema.Gama
import kotlinx.coroutines.launch

@Composable
fun DispositivosPantalla(modulo: ModuloSesion, api: ApiCliente) {
    val alcance = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var registrando by remember { mutableStateOf(false) }
    var mensajePara by remember { mutableStateOf<DispositivoMdm?>(null) }
    val aprueba = modulo.puede("aprobar")

    fun ordenar(d: DispositivoMdm, tipo: String, texto: String? = null) {
        alcance.launch {
            mensaje = try {
                api.enviarComando(d.id, ComandoNuevo(tipo, texto))
                version++
                "Orden enviada a ${d.nombre}. Se aplica en el próximo contacto del equipo."
            } catch (e: ApiError) {
                e.message
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BarraMensaje(mensaje) { mensaje = null }
        Box(Modifier.fillMaxSize()) {
            Cargar(clave = version, cargar = { api.dispositivos() }) { lista ->
                if (lista.isEmpty()) {
                    Text(
                        "Aún no hay tabletas registradas." + if (aprueba) " Toque «Registrar tableta»." else "",
                        Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(lista, key = { it.id }) { d ->
                            TarjetaDispositivo(
                                d,
                                aprueba,
                                alOrdenar = { tipo -> ordenar(d, tipo) },
                                alMensaje = { mensajePara = d },
                            )
                        }
                    }
                }
            }
            if (aprueba) {
                ExtendedFloatingActionButton(
                    onClick = { registrando = true },
                    icon = { Icon(Icons.Outlined.QrCode2, null) },
                    text = { Text("Registrar tableta") },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (registrando) {
        RegistroDialogo(api, alCerrar = { registrando = false; version++ })
    }
    mensajePara?.let { d ->
        DialogoTexto("Mensaje para ${d.nombre}", "Mensaje", alCancelar = { mensajePara = null }) { texto ->
            mensajePara = null
            ordenar(d, "mensaje", texto)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaDispositivo(
    d: DispositivoMdm,
    aprueba: Boolean,
    alOrdenar: (String) -> Unit,
    alMensaje: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.nombre, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Etiqueta(if (d.enLinea) "En línea" else "Sin conexión", if (d.enLinea) Gama.Ok else Gama.Critico)
            }
            val equipo = listOfNotNull(d.fabricante, d.modelo, d.versionAndroid?.let { "Android $it" }).joinToString(" · ")
            if (equipo.isNotEmpty()) {
                Text(equipo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                d.bateriaPct?.let {
                    val color = if (it <= 15 && d.cargando != true) Gama.Critico else MaterialTheme.colorScheme.onSurface
                    Text("Batería $it %" + if (d.cargando == true) " · cargando" else "", color = color,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (d.kiosco) Etiqueta(if (d.kioscoActivo == false) "Kiosco pendiente" else "Kiosco", Gama.Primario)
            }
            Text(
                "Último contacto: " + (d.ultimoContacto?.let(::fechaCorta) ?: "nunca") +
                    if (d.comandosPendientes > 0) " · ${d.comandosPendientes} en camino" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (aprueba) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = alMensaje) { Text("Mensaje") }
                    OutlinedButton(onClick = { alOrdenar("bloquear") }) { Text("Bloquear") }
                    OutlinedButton(onClick = { alOrdenar("sincronizar") }) { Text("Sincronizar") }
                    OutlinedButton(onClick = { alOrdenar(if (d.kiosco) "kiosco_desactivar" else "kiosco_activar") }) {
                        Text(if (d.kiosco) "Salir de kiosco" else "Activar kiosco")
                    }
                    OutlinedButton(onClick = { alOrdenar("reiniciar") }) { Text("Reiniciar") }
                }
            }
        }
    }
}

@Composable
private fun RegistroDialogo(api: ApiCliente, alCerrar: () -> Unit) {
    val alcance = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var kiosco by remember { mutableStateOf(true) }
    var codigo by remember { mutableStateOf<CodigoRegistro?>(null) }
    var qr by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(if (codigo == null) "Registrar tableta" else "Escanee con GAMA MDM") },
        text = {
            val c = codigo
            if (c == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre de la tableta") },
                        placeholder = { Text("Ej.: Extrusora 3") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = kiosco, onCheckedChange = { kiosco = it })
                        Text("Modo kiosco (solo GAMA en pantalla)")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val bytes = qr
                    val imagen = remember(bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                    if (imagen != null) {
                        Image(
                            imagen.asImageBitmap(),
                            contentDescription = "Código QR de registro",
                            filterQuality = FilterQuality.None, // nítido: sin suavizar los módulos del QR
                            modifier = Modifier.size(240.dp),
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(c.codigoLegible, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                    Text("Para «${c.nombre}». Un solo uso; vence ${fechaCorta(c.venceEn)}.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (codigo == null) {
                TextButton(
                    enabled = !enviando && nombre.trim().length >= 2,
                    onClick = {
                        enviando = true
                        error = null
                        alcance.launch {
                            try {
                                val c = api.crearCodigoRegistro(CodigoNuevo(nombre.trim(), kiosco))
                                codigo = c
                                qr = api.qrRegistro(c.codigo)
                            } catch (e: ApiError) {
                                error = e.message
                            } finally {
                                enviando = false
                            }
                        }
                    },
                ) { Text("Crear código") }
            } else {
                TextButton(onClick = alCerrar) { Text("Listo") }
            }
        },
        dismissButton = { if (codigo == null) TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
