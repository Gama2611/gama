package com.gama.mdm

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receptor de administrador del dispositivo. Se hace propietario con:
 *  adb shell dpm set-device-owner com.gama.mdm/.AdminReceptor */
class AdminReceptor : DeviceAdminReceiver()

/** Tras reiniciar o actualizar el MDM se reprograma el latido y se sincroniza de inmediato. */
class ArranqueReceptor : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Registro(contexto).registrado) return
        TrabajoLatido.programar(contexto)
        TrabajoLatido.ahora(contexto)
    }
}
