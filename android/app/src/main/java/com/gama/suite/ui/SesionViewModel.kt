package com.gama.suite.ui

import android.app.Application
import android.content.RestrictionsManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gama.comun.Confianza
import com.gama.suite.BuildConfig
import com.gama.suite.GamaApp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.Sesion
import com.gama.suite.datos.Tokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class Pantalla { CARGANDO, SERVIDOR, CONFIRMAR_HUELLA, LOGIN, CAMBIO_CLAVE, INICIO }

data class EstadoUi(
    val pantalla: Pantalla = Pantalla.CARGANDO,
    val servidor: String? = null,
    /** Huella leída del servidor que la persona debe confirmar antes de confiar en él. */
    val huellaPendiente: String? = null,
    /** El servidor lo fija el MDM (configuración administrada): no se puede cambiar desde la app. */
    val administrado: Boolean = false,
    val sesion: Sesion? = null,
    val usuarioIngresado: String = "",
    val ocupado: Boolean = false,
    val error: String? = null,
)

class SesionViewModel(app: Application) : AndroidViewModel(app) {
    private val almacen = (app as GamaApp).almacen
    private val _estado = MutableStateFlow(EstadoUi())
    val estado: StateFlow<EstadoUi> = _estado.asStateFlow()

    var api: ApiCliente? = null
        private set

    private val dispositivo = "${Build.MANUFACTURER} ${Build.MODEL}" + if (BuildConfig.TERMINAL) " (terminal)" else ""

    init {
        viewModelScope.launch {
            val administrada = configuracionAdministrada()
            if (administrada != null) {
                val (url, huella) = administrada
                if (url != almacen.servidor() || huella != almacen.huella()) {
                    almacen.guardarServidor(url, huella)
                    almacen.borrarTokens()
                }
                _estado.update { it.copy(administrado = true) }
            }
            val servidor = almacen.servidor()
            val huella = almacen.huella()
            // Un servidor https sin huella registrada no se puede verificar: se vuelve a conectar.
            if (servidor == null || (servidor.startsWith("https://") && huella == null && administrada == null)) {
                _estado.update { it.copy(pantalla = Pantalla.SERVIDOR, servidor = servidor) }
                return@launch
            }
            val cliente = crearCliente(servidor, almacen.tokens(), huella)
            if (!cliente.tieneSesion) {
                _estado.update { it.copy(pantalla = Pantalla.LOGIN, servidor = servidor) }
                return@launch
            }
            cargarSesion(servidor)
        }
    }

    /** Servidor y huella que entrega GAMA MDM como configuración administrada (si la hay). */
    private fun configuracionAdministrada(): Pair<String, String?>? {
        val gestor = getApplication<Application>().getSystemService(RestrictionsManager::class.java) ?: return null
        val datos = gestor.applicationRestrictions
        val servidor = datos.getString("servidor")?.takeIf { it.isNotBlank() } ?: return null
        val url = runCatching { ApiCliente.normalizarServidor(servidor) }.getOrNull() ?: return null
        val huella = datos.getString("huella")?.takeIf { Confianza.esValida(it) }?.let(Confianza::legible)
        return url to huella
    }

    private fun crearCliente(servidor: String, tokens: Pair<String, String>?, huella: String?): ApiCliente {
        val cliente = ApiCliente(servidor, tokens, huella = huella, alCambiarTokens = ::alCambiarTokens)
        api = cliente
        return cliente
    }

    // Llega desde un hilo de red; se persiste en una corrutina.
    private fun alCambiarTokens(tokens: Tokens?) {
        viewModelScope.launch {
            if (tokens == null) {
                almacen.borrarTokens()
                if (_estado.value.pantalla == Pantalla.INICIO) {
                    _estado.update {
                        it.copy(pantalla = Pantalla.LOGIN, sesion = null, error = "La sesión venció. Ingrese de nuevo.")
                    }
                }
            } else {
                almacen.guardarTokens(tokens.tokenAcceso, tokens.tokenRefresco)
            }
        }
    }

    private suspend fun cargarSesion(servidor: String) {
        val cliente = api ?: return
        try {
            val sesion = cliente.sesion()
            _estado.update {
                it.copy(
                    pantalla = if (sesion.usuario.debeCambiarClave) Pantalla.CAMBIO_CLAVE else Pantalla.INICIO,
                    servidor = servidor,
                    sesion = sesion,
                    usuarioIngresado = sesion.usuario.usuario,
                    ocupado = false,
                    error = null,
                )
            }
        } catch (e: ApiError) {
            _estado.update {
                it.copy(pantalla = Pantalla.LOGIN, servidor = servidor, sesion = null, ocupado = false, error = e.message)
            }
        }
    }

    fun limpiarError() = _estado.update { it.copy(error = null) }

    fun configurarServidor(entrada: String) {
        val url = try {
            ApiCliente.normalizarServidor(entrada)
        } catch (e: IllegalArgumentException) {
            _estado.update { it.copy(error = "La dirección no es válida. Ejemplo: https://192.168.1.50:8443") }
            return
        }
        _estado.update { it.copy(ocupado = true, error = null) }
        viewModelScope.launch {
            if (url.startsWith("https://")) {
                // Certificado propio de la planta: se lee su huella y la persona la confirma contra la consola.
                val huella = try {
                    Confianza.leerHuella(url)
                } catch (e: IOException) {
                    _estado.update { it.copy(ocupado = false, error = "El servidor $url no responde.") }
                    return@launch
                }
                _estado.update {
                    it.copy(pantalla = Pantalla.CONFIRMAR_HUELLA, servidor = url, huellaPendiente = huella, ocupado = false)
                }
            } else {
                conectar(url, null)
            }
        }
    }

    /** La persona confirmó que la huella coincide con la que muestra la consola del servidor. */
    fun confirmarHuella() {
        val url = _estado.value.servidor ?: return
        val huella = _estado.value.huellaPendiente ?: return
        _estado.update { it.copy(ocupado = true, error = null) }
        viewModelScope.launch { conectar(url, huella) }
    }

    fun rechazarHuella() {
        _estado.update {
            it.copy(
                pantalla = Pantalla.SERVIDOR,
                huellaPendiente = null,
                error = "No se conectó. Si la huella no coincide, alguien podría estar suplantando al servidor: avise a sistemas.",
            )
        }
    }

    private suspend fun conectar(url: String, huella: String?) {
        val cliente = crearCliente(url, null, huella)
        if (!cliente.salud()) {
            _estado.update { it.copy(ocupado = false, error = "El servidor $url no responde.") }
            return
        }
        almacen.guardarServidor(url, huella)
        almacen.borrarTokens()
        _estado.update { it.copy(pantalla = Pantalla.LOGIN, servidor = url, huellaPendiente = null, ocupado = false) }
    }

    fun entrar(usuario: String, clave: String) {
        val cliente = api ?: return
        val servidor = _estado.value.servidor ?: return
        if (usuario.isBlank() || clave.isEmpty()) {
            _estado.update { it.copy(error = "Escriba su usuario y su contraseña.") }
            return
        }
        _estado.update { it.copy(ocupado = true, error = null, usuarioIngresado = usuario.trim()) }
        viewModelScope.launch {
            try {
                cliente.login(usuario.trim(), clave, dispositivo)
                cargarSesion(servidor)
            } catch (e: ApiError) {
                _estado.update { it.copy(ocupado = false, error = e.message) }
            }
        }
    }

    fun cambiarClave(actual: String, nueva: String, confirmacion: String) {
        val cliente = api ?: return
        val servidor = _estado.value.servidor ?: return
        if (nueva != confirmacion) {
            _estado.update { it.copy(error = "La confirmación no coincide con la nueva contraseña.") }
            return
        }
        _estado.update { it.copy(ocupado = true, error = null) }
        viewModelScope.launch {
            try {
                cliente.cambiarClave(actual, nueva)
                // El servidor cierra las sesiones al cambiar la clave: se vuelve a entrar con la nueva.
                cliente.login(_estado.value.usuarioIngresado, nueva, dispositivo)
                cargarSesion(servidor)
            } catch (e: ApiError) {
                _estado.update { it.copy(ocupado = false, error = e.message) }
            }
        }
    }

    fun recargar() {
        val servidor = _estado.value.servidor ?: return
        viewModelScope.launch { cargarSesion(servidor) }
    }

    // Se cambia la pantalla antes de cerrar la sesión en el servidor, para que el aviso
    // de "sesión vencida" no aparezca cuando el usuario sale por su cuenta.
    fun salir() {
        _estado.update { it.copy(pantalla = Pantalla.LOGIN, sesion = null, error = null) }
        viewModelScope.launch { api?.salir() }
    }

    fun cambiarServidor() {
        if (_estado.value.administrado) {
            _estado.update { it.copy(error = "El servidor de esta tableta lo administra GAMA MDM.") }
            return
        }
        _estado.update { EstadoUi(pantalla = Pantalla.SERVIDOR, servidor = it.servidor) }
        viewModelScope.launch { api?.salir() }
    }
}
