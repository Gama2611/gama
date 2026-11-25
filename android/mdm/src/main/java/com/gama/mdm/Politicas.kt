package com.gama.mdm

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings

/** Políticas que GAMA MDM aplica como propietario del dispositivo. */
object Politicas {
    /** Variantes de la app GAMA, en orden de preferencia: la de terminal es la del kiosco. */
    val APPS_GAMA = listOf("com.gama.suite.terminal", "com.gama.suite", "com.gama.suite.terminal.debug", "com.gama.suite.debug")

    fun admin(contexto: Context) = ComponentName(contexto, AdminReceptor::class.java)

    private fun dpm(contexto: Context): DevicePolicyManager =
        requireNotNull(contexto.getSystemService(DevicePolicyManager::class.java)) { "Sin servicio de administración" }

    fun esPropietario(contexto: Context): Boolean = dpm(contexto).isDeviceOwnerApp(contexto.packageName)

    fun instalada(contexto: Context, paquete: String): Boolean = version(contexto, paquete) != null

    @Suppress("DEPRECATION")
    fun version(contexto: Context, paquete: String): String? = try {
        val pm = contexto.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(paquete, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageInfo(paquete, 0)
        }
        info.versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /** La app GAMA instalada que se abre en el kiosco (la que pide el servidor, si está instalada). */
    fun appGama(contexto: Context, preferida: String?): String? =
        (listOfNotNull(preferida) + APPS_GAMA).distinct().firstOrNull { instalada(contexto, it) }

    /** Entrega a la app GAMA el servidor y la huella: queda conectada sin que nadie escriba nada. */
    fun configurarAppGama(contexto: Context, servidor: String, huella: String?) {
        if (!esPropietario(contexto)) return
        val datos = Bundle().apply {
            putString("servidor", servidor)
            putString("huella", huella.orEmpty())
        }
        for (paquete in APPS_GAMA) {
            runCatching { dpm(contexto).setApplicationRestrictions(admin(contexto), paquete, datos) }
        }
    }

    /** Restricciones permanentes del equipo administrado. */
    fun aplicarBase(contexto: Context) {
        if (!esPropietario(contexto)) return
        dpm(contexto).addUserRestriction(admin(contexto), UserManager.DISALLOW_ADD_USER)
    }

    /**
     * Modo kiosco: solo GAMA (y este MDM) pueden estar en pantalla, sin barra de estado ni bloqueo,
     * con la pantalla encendida mientras carga. La salida la ordena el servidor.
     */
    fun aplicarKiosco(contexto: Context, activo: Boolean, appGama: String?) {
        if (!esPropietario(contexto)) return
        val d = dpm(contexto)
        val a = admin(contexto)
        val alias = ComponentName(contexto.packageName, "${contexto.packageName}.Kiosco")
        if (activo) {
            d.setLockTaskPackages(a, listOfNotNull(contexto.packageName, appGama).toTypedArray())
            if (Build.VERSION.SDK_INT >= 28) {
                d.setLockTaskFeatures(
                    a,
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO,
                )
            }
            d.setStatusBarDisabled(a, true)
            d.setKeyguardDisabled(a, true)
            val siempre = BatteryManager.BATTERY_PLUGGED_AC or BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            d.setGlobalSetting(a, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, siempre.toString())
            d.addUserRestriction(a, UserManager.DISALLOW_USB_FILE_TRANSFER)
            d.addUserRestriction(a, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            contexto.packageManager.setComponentEnabledSetting(
                alias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP,
            )
            val inicio = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            d.addPersistentPreferredActivity(a, inicio, alias)
        } else {
            d.clearPackagePersistentPreferredActivities(a, contexto.packageName)
            contexto.packageManager.setComponentEnabledSetting(
                alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP,
            )
            d.setLockTaskPackages(a, emptyArray()) // sale del modo kiosco de inmediato
            d.setStatusBarDisabled(a, false)
            d.setKeyguardDisabled(a, false)
            d.setGlobalSetting(a, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
            d.clearUserRestriction(a, UserManager.DISALLOW_USB_FILE_TRANSFER)
            d.clearUserRestriction(a, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
        }
    }

    fun kioscoActivo(contexto: Context): Boolean {
        val estado = contexto.getSystemService(ActivityManager::class.java)?.lockTaskModeState
        return estado != null && estado != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun bloquear(contexto: Context) = dpm(contexto).lockNow()

    fun reiniciar(contexto: Context) = dpm(contexto).reboot(admin(contexto))

    /** Identificador estable del equipo para esta app (no requiere permisos especiales). */
    @SuppressLint("HardwareIds")
    fun identificador(contexto: Context): String? =
        Settings.Secure.getString(contexto.contentResolver, Settings.Secure.ANDROID_ID)
}
