package com.gama.mdm

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gama.comun.Confianza
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Datos de un registro manual que esperan que la persona confirme la huella del servidor. */
private data class PorConfirmar(val servidor: String, val huella: String, val codigo: String)

private data class EstadoInicio(
    val propietario: Boolean = false,
    val registrado: Boolean = false,
    val nombre: String? = null,
    val servidor: String? = null,
    val huella: String? = null,
    val kiosco: Boolean = false,
    val kioscoActivo: Boolean = false,
    val app: String? = null,
    val ultimoContacto: Long = 0L,
    val ultimoError: String? = null,
    val ocupado: Boolean = false,
    val error: String? = null,
    val porConfirmar: PorConfirmar? = null,
)

class InicioActivity : ComponentActivity() {
    private var estado by mutableStateOf(EstadoInicio())
    private var ultimaApertura = 0L

    private val escaner = registerForActivityResult(ScanContract()) { r ->
        val texto = r.contents ?: return@registerForActivityResult
        val qr = leerQr(texto)
        if (qr == null) {
            estado = estado.copy(error = "Ese código no es un QR de registro de GAMA.")
        } else {
            registrar(qr.s, qr.h, qr.c)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaMdm {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Pantalla(
                        estado,
                        alEscanear = ::escanear,
                        alRegistrarManual = ::registrarManual,
                        alConfirmar = { p -> estado = estado.copy(porConfirmar = null); registrar(p.servidor, p.huella, p.codigo) },
                        alCancelar = { estado = estado.copy(porConfirmar = null) },
                        alAbrirGama = { estado.app?.let(::abrirGama) },
                        alSincronizar = ::sincronizarAhora,
                    )
                }
            }
        }
        // Mientras la pantalla está visible, contacto con el servidor cada «intervalo» (60 s por defecto).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    Sincronizador.sincronizar(this@InicioActivity)
                    refrescar()
                    delay(Registro(this@InicioActivity).intervaloSegundos * 1000L)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val reg = Registro(this)
        if (reg.registrado && reg.kiosco && Politicas.esPropietario(this)) {
            val app = Politicas.appGama(this, reg.paqueteApp)
            if (!Politicas.kioscoActivo(this)) {
                Politicas.aplicarKiosco(this, true, app)
                runCatching { startLockTask() }
            }
            // La app GAMA vuelve a primer plano; el margen evita un bucle si se cierra al abrir.
            if (app != null && SystemClock.elapsedRealtime() - ultimaApertura > 3_000) abrirGama(app)
        }
        refrescar()
    }

    private fun refrescar() {
        val reg = Registro(this)
        estado = estado.copy(
            propietario = Politicas.esPropietario(this),
            registrado = reg.registrado,
            nombre = reg.nombre,
            servidor = reg.servidor,
            huella = reg.huella,
            kiosco = reg.kiosco,
            kioscoActivo = Politicas.kioscoActivo(this),
            app = Politicas.appGama(this, reg.paqueteApp),
            ultimoContacto = reg.ultimoContacto,
            ultimoError = reg.ultimoError,
        )
    }

    private fun escanear() {
        escaner.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Escanee el código QR de la consola de GAMA")
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }

    private fun registrarManual(servidor: String, codigo: String) {
        var url = servidor.trim().trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        if (normalizarCodigo(codigo).length != 10) {
            estado = estado.copy(error = "El código tiene 10 caracteres, por ejemplo SSHF3-KBVW4.")
            return
        }
        if (!url.startsWith("https://")) {
            estado = estado.copy(error = "La conexión debe ser segura (https).")
            return
        }
        estado = estado.copy(ocupado = true, error = null)
        lifecycleScope.launch {
            estado = try {
                estado.copy(ocupado = false, porConfirmar = PorConfirmar(url, Confianza.leerHuella(url), codigo))
            } catch (e: IOException) {
                estado.copy(ocupado = false, error = "No se pudo conectar con $url.")
            }
        }
    }

    private fun registrar(servidor: String, huella: String?, codigo: String) {
        estado = estado.copy(ocupado = true, error = null)
        lifecycleScope.launch {
            try {
                val r = ClienteMdm(servidor, huella).registrar(
                    PeticionRegistro(
                        codigo = normalizarCodigo(codigo),
                        fabricante = Build.MANUFACTURER,
                        modelo = Build.MODEL,
                        versionAndroid = Build.VERSION.RELEASE,
                        identificador = Politicas.identificador(this@InicioActivity),
                        versionMdm = BuildConfig.VERSION_NAME,
                    ),
                )
                val ctx = this@InicioActivity
                Registro(ctx).guardar(servidor, huella, r)
                Politicas.aplicarBase(ctx)
                Politicas.configurarAppGama(ctx, servidor, huella)
                val app = Politicas.appGama(ctx, r.paqueteApp)
                Politicas.aplicarKiosco(ctx, r.kiosco, app)
                TrabajoLatido.programar(ctx)
                TrabajoLatido.ahora(ctx)
                estado = estado.copy(ocupado = false)
                refrescar()
                if (r.kiosco) {
                    runCatching { startLockTask() }
                    app?.let(::abrirGama)
                }
            } catch (e: ErrorMdm) {
                estado = estado.copy(ocupado = false, error = e.message)
            } catch (e: SerializationException) {
                estado = estado.copy(ocupado = false, error = "Respuesta inesperada: verifique que el servidor sea GAMA y esté actualizado.")
            }
        }
    }

    private fun sincronizarAhora() {
        estado = estado.copy(ocupado = true)
        lifecycleScope.launch {
            val r = Sincronizador.sincronizar(this@InicioActivity)
            estado = estado.copy(ocupado = false, error = (r as? Sincronizador.Resultado.Falla)?.mensaje)
            refrescar()
        }
    }

    private fun abrirGama(paquete: String) {
        val intent = packageManager.getLaunchIntentForPackage(paquete) ?: return
        ultimaApertura = SystemClock.elapsedRealtime()
        startActivity(intent)
    }
}

// --- Interfaz -------------------------------------------------------------------------------
@Composable
private fun Pantalla(
    e: EstadoInicio,
    alEscanear: () -> Unit,
    alRegistrarManual: (String, String) -> Unit,
    alConfirmar: (PorConfirmar) -> Unit,
    alCancelar: () -> Unit,
    alAbrirGama: () -> Unit,
    alSincronizar: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Encabezado(e.nombre)
            when {
                !e.propietario -> PasoPropietario()
                !e.registrado -> PasoRegistro(e, alEscanear, alRegistrarManual)
                else -> EstadoEquipo(e, alAbrirGama, alSincronizar)
            }
            e.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    e.porConfirmar?.let { p ->
        AlertDialog(
            onDismissRequest = alCancelar,
            title = { Text("Verifique el servidor") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Compare esta huella con la de la consola del servidor. Deben ser idénticas.")
                    Text(p.servidor, color = MaterialTheme.colorScheme.primary)
                    Text(
                        p.huella.split(":").chunked(8).joinToString("\n") { it.joinToString(":") },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { alConfirmar(p) }) { Text("La huella coincide") } },
            dismissButton = { TextButton(onClick = alCancelar) { Text("No coincide") } },
        )
    }
}

@Composable
private fun Encabezado(nombre: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
            Icon(
                Icons.Outlined.AdminPanelSettings,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .padding(10.dp)
                    .size(28.dp),
            )
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text("GAMA MDM", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                nombre ?: "Administración de tabletas de planta",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tarjeta(titulo: String, contenido: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

@Composable
private fun PasoPropietario() {
    Tarjeta("Falta un paso") {
        Text("Para administrar la tableta, GAMA MDM debe ser el propietario del dispositivo. Se hace una sola vez, con la tableta recién restablecida y sin cuentas de Google:")
        Text("1. Active la depuración USB y conecte la tableta a una computadora.")
        Text("2. Ejecute este comando:")
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
            Text(
                "adb shell dpm set-device-owner com.gama.mdm/.AdminReceptor",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        Text("3. Vuelva a abrir GAMA MDM y registre la tableta con el código QR de la consola.")
    }
}

@Composable
private fun PasoRegistro(e: EstadoInicio, alEscanear: () -> Unit, alRegistrarManual: (String, String) -> Unit) {
    var servidor by rememberSaveable { mutableStateOf("") }
    var codigo by rememberSaveable { mutableStateOf("") }
    Tarjeta("Registrar esta tableta") {
        Text("En la consola del servidor, abra Dispositivos › Registrar tableta y escanee el código.")
        Button(
            onClick = alEscanear,
            enabled = !e.ocupado,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
            Text("Escanear código QR", Modifier.padding(start = 8.dp))
        }
        HorizontalDivider()
        Text("¿Sin cámara? Escriba los datos:", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            servidor, { servidor = it },
            label = { Text("Dirección del servidor") },
            placeholder = { Text("https://192.168.1.50:8443") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            codigo, { codigo = it },
            label = { Text("Código de registro") },
            placeholder = { Text("SSHF3-KBVW4") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { alRegistrarManual(servidor, codigo) },
            enabled = !e.ocupado && servidor.isNotBlank() && codigo.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (e.ocupado) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Registrar")
        }
    }
}

@Composable
private fun EstadoEquipo(e: EstadoInicio, alAbrirGama: () -> Unit, alSincronizar: () -> Unit) {
    Tarjeta("Estado") {
        Dato("Servidor", e.servidor ?: "—")
        Dato("Último contacto", if (e.ultimoContacto > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.ultimoContacto)) else "Aún no")
        Dato("Modo kiosco", if (!e.kiosco) "Desactivado" else if (e.kioscoActivo) "Activo" else "Activándose")
        Dato("App GAMA", e.app ?: "No instalada: el administrador puede instalarla desde la consola")
        e.huella?.let { Dato("Huella del servidor", it.take(23) + "…") }
        e.ultimoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    Button(
        onClick = alAbrirGama,
        enabled = e.app != null,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) { Text("Abrir GAMA") }
    OutlinedButton(onClick = alSincronizar, enabled = !e.ocupado, modifier = Modifier.fillMaxWidth()) {
        if (e.ocupado) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Sincronizar ahora")
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String) {
    Column {
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.bodyMedium)
    }
}
