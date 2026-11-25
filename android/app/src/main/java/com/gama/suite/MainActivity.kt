package com.gama.suite

import android.app.admin.DevicePolicyManager
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gama.suite.ui.Pantalla
import com.gama.suite.ui.SesionViewModel
import com.gama.suite.ui.pantallas.CambioClavePantalla
import com.gama.suite.ui.pantallas.ConfirmarHuellaPantalla
import com.gama.suite.ui.pantallas.InicioPantalla
import com.gama.suite.ui.pantallas.LoginPantalla
import com.gama.suite.ui.pantallas.ModuloPantalla
import com.gama.suite.ui.pantallas.ServidorPantalla
import com.gama.suite.ui.tema.TemaGama

class MainActivity : ComponentActivity() {
    private val vm: SesionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.TERMINAL) prepararTerminal()
        setContent {
            TemaGama(terminal = BuildConfig.TERMINAL) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Aplicacion(vm)
                }
            }
        }
    }

    /** Terminal de planta: horizontal, pantalla siempre encendida y, si el MDM lo autoriza, modo kiosco. */
    private fun prepararTerminal() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val dpm = getSystemService(DevicePolicyManager::class.java)
        // Solo con autorización del administrador del dispositivo (GAMA Dispositivos);
        // sin ella Android mostraría un aviso de fijación al usuario.
        if (dpm != null && dpm.isLockTaskPermitted(packageName)) startLockTask()
    }
}

@Composable
private fun Aplicacion(vm: SesionViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var moduloAbierto by rememberSaveable { mutableStateOf<String?>(null) }

    when (estado.pantalla) {
        Pantalla.CARGANDO -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        Pantalla.SERVIDOR -> ServidorPantalla(estado, vm::configurarServidor)
        Pantalla.CONFIRMAR_HUELLA -> ConfirmarHuellaPantalla(estado, vm::confirmarHuella, vm::rechazarHuella)
        Pantalla.LOGIN -> LoginPantalla(estado, vm::entrar, vm::cambiarServidor)
        Pantalla.CAMBIO_CLAVE -> CambioClavePantalla(estado, vm::cambiarClave, vm::salir)
        Pantalla.INICIO -> {
            val sesion = estado.sesion ?: return
            val api = vm.api ?: return
            val modulo = sesion.modulos.firstOrNull { it.codigo == moduloAbierto }
            if (modulo == null) {
                InicioPantalla(
                    sesion = sesion,
                    alAbrir = { moduloAbierto = it.codigo },
                    alRecargar = vm::recargar,
                    alSalir = { moduloAbierto = null; vm.salir() },
                    alCambiarServidor = { moduloAbierto = null; vm.cambiarServidor() },
                    alResumen = if (sesion.modulos.any { it.codigo == "asistente" }) {
                        suspend { api.resumenAsistente("general") }
                    } else {
                        null
                    },
                    alAbrirAsistente = { moduloAbierto = "asistente" },
                )
            } else {
                ModuloPantalla(modulo, api) { moduloAbierto = null }
            }
        }
    }
}
