package com.gama.mdm

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * Un ciclo de sincronización: envía el estado y los resultados pendientes, recibe comandos y los ejecuta.
 * Lo llaman el trabajo periódico (cada 15 min) y la pantalla de inicio (cada minuto, mientras está visible).
 */
object Sincronizador {
    private val candado = Mutex()

    sealed interface Resultado {
        data object SinRegistro : Resultado
        data object Ok : Resultado
        data object Retirado : Resultado
        data class Falla(val mensaje: String) : Resultado
    }

    suspend fun sincronizar(contexto: Context): Resultado = candado.withLock {
        val ctx = contexto.applicationContext
        val reg = Registro(ctx)
        val servidor = reg.servidor
        val token = reg.token
        if (!reg.registrado || servidor == null || token == null) return@withLock Resultado.SinRegistro
        val cliente = ClienteMdm(servidor, reg.huella)
        val pendientes = reg.pendientes()
        val respuesta = try {
            cliente.latido(token, estado(ctx, reg, pendientes))
        } catch (e: ErrorMdm) {
            if (e.codigo == 401) {
                // El administrador retiró el equipo: se libera el kiosco para no dejarlo inutilizable.
                Politicas.aplicarKiosco(ctx, false, null)
                reg.olvidar()
                return@withLock Resultado.Retirado
            }
            reg.ultimoError = e.message
            return@withLock Resultado.Falla(e.message ?: "Error")
        }
        reg.descartar(pendientes.map { it.id })
        reg.actualizar(respuesta)
        reg.ultimoContacto = System.currentTimeMillis()
        reg.ultimoError = null

        // Lo que el servidor dice manda: kiosco, app y configuración se reaplican en cada contacto.
        val app = Politicas.appGama(ctx, respuesta.paqueteApp)
        Politicas.configurarAppGama(ctx, servidor, reg.huella)
        if (respuesta.kiosco != reg.kiosco || (respuesta.kiosco && !Politicas.kioscoActivo(ctx))) {
            reg.kiosco = respuesta.kiosco
            Politicas.aplicarKiosco(ctx, respuesta.kiosco, app)
            if (respuesta.kiosco) abrirInicio(ctx)
        }

        for (c in respuesta.comandos) ejecutar(ctx, reg, cliente, token, c)
        Resultado.Ok
    }

    private fun estado(ctx: Context, reg: Registro, pendientes: List<ResultadoComando>): Latido {
        val bateria = ctx.getSystemService(BatteryManager::class.java)
        val libre = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes / (1024 * 1024) }.getOrNull()
        return Latido(
            bateriaPct = bateria?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 },
            cargando = bateria?.isCharging,
            almacenamientoLibreMb = libre?.toInt(),
            kioscoActivo = Politicas.kioscoActivo(ctx),
            propietarioDispositivo = Politicas.esPropietario(ctx),
            versionAndroid = Build.VERSION.RELEASE,
            versionMdm = BuildConfig.VERSION_NAME,
            versionApp = Politicas.appGama(ctx, reg.paqueteApp)?.let { Politicas.version(ctx, it) },
            resultados = pendientes,
        )
    }

    private suspend fun ejecutar(ctx: Context, reg: Registro, cliente: ClienteMdm, token: String, c: ComandoEquipo) {
        val resultado: ResultadoComando? = try {
            when (c.tipo) {
                "bloquear" -> {
                    Politicas.bloquear(ctx)
                    ResultadoComando(c.id, true)
                }
                "reiniciar" -> {
                    // Se informa antes de reiniciar: después ya no habría oportunidad.
                    reg.anotar(ResultadoComando(c.id, true, "Reiniciando"))
                    runCatching { cliente.latido(token, estado(ctx, reg, reg.pendientes())) }
                        .onSuccess { reg.descartar(listOf(c.id)) }
                    Politicas.reiniciar(ctx)
                    null
                }
                "mensaje" -> {
                    val texto = c.datos?.get("texto")?.jsonPrimitive?.contentOrNull.orEmpty()
                    val de = c.datos?.get("de")?.jsonPrimitive?.contentOrNull.orEmpty()
                    ctx.startActivity(
                        Intent(ctx, MensajeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            .putExtra(MensajeActivity.TEXTO, texto)
                            .putExtra(MensajeActivity.DE, de),
                    )
                    ResultadoComando(c.id, true, "Mostrado")
                }
                "kiosco_activar", "kiosco_desactivar" -> {
                    val activo = c.tipo == "kiosco_activar"
                    reg.kiosco = activo
                    Politicas.aplicarKiosco(ctx, activo, Politicas.appGama(ctx, reg.paqueteApp))
                    if (activo) abrirInicio(ctx)
                    ResultadoComando(c.id, true)
                }
                "actualizar_app" -> {
                    actualizar(ctx, cliente, token, c)
                    null // el resultado llega cuando Android termina de instalar (InstalacionReceptor)
                }
                "sincronizar" -> ResultadoComando(c.id, true)
                else -> ResultadoComando(c.id, false, "Comando desconocido: ${c.tipo}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultadoComando(c.id, false, e.message?.take(500) ?: e.javaClass.simpleName)
        }
        resultado?.let(reg::anotar)
    }

    private suspend fun actualizar(ctx: Context, cliente: ClienteMdm, token: String, c: ComandoEquipo) {
        val datos = c.datos ?: error("La orden no trae datos del APK")
        val archivo = datos["archivo"]?.jsonPrimitive?.contentOrNull ?: error("Falta el nombre del APK")
        val esperado = datos["sha256"]?.jsonPrimitive?.contentOrNull ?: error("Falta la huella SHA-256 del APK")
        val bytes = datos["bytes"]?.jsonPrimitive?.longOrNull
        require(Regex("^[A-Za-z0-9._-]{1,80}\\.apk$").matches(archivo)) { "Nombre de APK no válido" }
        val destino = File(ctx.cacheDir, "actualizacion-${c.id}.apk")
        try {
            cliente.descargar(token, archivo, destino)
            if (bytes != null && destino.length() != bytes) error("Descarga incompleta")
            if (!sha256(destino).equals(esperado, ignoreCase = true)) error("El APK no coincide con la huella del servidor")
            Instalador.instalar(ctx, destino, c.id)
        } finally {
            destino.delete()
        }
    }

    private fun abrirInicio(ctx: Context) {
        ctx.startActivity(Intent(ctx, InicioActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
