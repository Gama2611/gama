package com.gama.mdm

import android.content.Context
import com.gama.comun.Cifrado
import kotlinx.serialization.builtins.ListSerializer

/** Estado del equipo en GAMA MDM. El token del dispositivo se guarda cifrado con el Android Keystore. */
class Registro(contexto: Context) {
    private val prefs = contexto.applicationContext.getSharedPreferences("gama_mdm", Context.MODE_PRIVATE)

    val registrado: Boolean get() = prefs.contains(TOKEN) && servidor != null
    val servidor: String? get() = prefs.getString(SERVIDOR, null)
    val huella: String? get() = prefs.getString(HUELLA, null)
    val token: String? get() = prefs.getString(TOKEN, null)?.let(Cifrado::descifrar)
    val nombre: String? get() = prefs.getString(NOMBRE, null)
    val paqueteApp: String? get() = prefs.getString(PAQUETE, null)

    var kiosco: Boolean
        get() = prefs.getBoolean(KIOSCO, false)
        set(v) = prefs.edit().putBoolean(KIOSCO, v).apply()

    var intervaloSegundos: Int
        get() = prefs.getInt(INTERVALO, 60).coerceIn(30, 900)
        set(v) = prefs.edit().putInt(INTERVALO, v).apply()

    var ultimoContacto: Long
        get() = prefs.getLong(ULTIMO, 0L)
        set(v) = prefs.edit().putLong(ULTIMO, v).apply()

    var ultimoError: String?
        get() = prefs.getString(ERROR, null)
        set(v) = prefs.edit().putString(ERROR, v).apply()

    fun guardar(servidor: String, huella: String?, r: RespuestaRegistro) {
        prefs.edit()
            .putString(SERVIDOR, servidor)
            .putString(HUELLA, huella)
            .putString(TOKEN, Cifrado.cifrar(r.token))
            .putString(NOMBRE, r.nombre)
            .putString(PAQUETE, r.paqueteApp)
            .putBoolean(KIOSCO, r.kiosco)
            .putInt(INTERVALO, r.intervaloSegundos)
            .remove(RESULTADOS)
            .apply()
    }

    fun actualizar(r: RespuestaLatido) {
        prefs.edit()
            .putString(NOMBRE, r.nombre)
            .putString(PAQUETE, r.paqueteApp)
            .putInt(INTERVALO, r.intervaloSegundos)
            .apply()
    }

    /** Resultados de comandos que aún no se informaron al servidor (sobreviven a reinicios). */
    @Synchronized
    fun pendientes(): List<ResultadoComando> = prefs.getString(RESULTADOS, null)
        ?.let { runCatching { JsonMdm.decodeFromString(ListSerializer(ResultadoComando.serializer()), it) }.getOrNull() }
        .orEmpty()

    @Synchronized
    fun anotar(r: ResultadoComando) {
        val lista = (pendientes().filterNot { it.id == r.id } + r).takeLast(50)
        prefs.edit().putString(RESULTADOS, JsonMdm.encodeToString(ListSerializer(ResultadoComando.serializer()), lista)).commit()
    }

    @Synchronized
    fun descartar(informados: Collection<Long>) {
        val lista = pendientes().filterNot { it.id in informados }
        prefs.edit().putString(RESULTADOS, JsonMdm.encodeToString(ListSerializer(ResultadoComando.serializer()), lista)).commit()
    }

    /** El servidor retiró el equipo: se borra todo lo que lo identificaba. */
    fun olvidar() {
        prefs.edit().clear().commit()
    }

    private companion object {
        const val SERVIDOR = "servidor"
        const val HUELLA = "huella"
        const val TOKEN = "token"
        const val NOMBRE = "nombre"
        const val PAQUETE = "paquete_app"
        const val KIOSCO = "kiosco"
        const val INTERVALO = "intervalo"
        const val ULTIMO = "ultimo_contacto"
        const val ERROR = "ultimo_error"
        const val RESULTADOS = "resultados"
    }
}
