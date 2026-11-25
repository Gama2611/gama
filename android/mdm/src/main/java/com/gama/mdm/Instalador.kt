package com.gama.mdm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.IntentCompat
import java.io.File

/** Instalación silenciosa de APK (permitida al propietario del dispositivo). */
object Instalador {
    const val ACCION = "com.gama.mdm.INSTALACION"
    const val COMANDO = "comando"

    fun instalar(contexto: Context, apk: File, comandoId: Long) {
        val instalador = contexto.packageManager.packageInstaller
        val parametros = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sesionId = instalador.createSession(parametros)
        instalador.openSession(sesionId).use { sesion ->
            apk.inputStream().use { entrada ->
                sesion.openWrite("gama.apk", 0, apk.length()).use { salida ->
                    entrada.copyTo(salida)
                    sesion.fsync(salida)
                }
            }
            val aviso = Intent(contexto, InstalacionReceptor::class.java)
                .setAction(ACCION)
                .putExtra(COMANDO, comandoId)
            // MUTABLE: Android completa el intent con el estado de la instalación.
            val banderas = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pendiente = PendingIntent.getBroadcast(contexto, comandoId.toInt(), aviso, banderas)
            sesion.commit(pendiente.intentSender)
        }
    }
}

/** Recibe el resultado de la instalación y lo deja listo para informarlo al servidor. */
class InstalacionReceptor : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Instalador.ACCION) return
        val id = intent.getLongExtra(Instalador.COMANDO, -1L)
        val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val mensaje = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (estado == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Solo ocurre si el MDM no es propietario del dispositivo: Android pide confirmar a la persona.
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)?.let {
                contexto.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        if (id < 0) return
        val registro = Registro(contexto)
        registro.anotar(
            if (estado == PackageInstaller.STATUS_SUCCESS) {
                ResultadoComando(id, true, "Instalada")
            } else {
                ResultadoComando(id, false, mensaje ?: "La instalación falló (estado $estado)")
            },
        )
        TrabajoLatido.ahora(contexto)
    }
}
