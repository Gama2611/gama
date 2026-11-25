package com.gama.suite.datos

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gama.comun.Cifrado
import kotlinx.coroutines.flow.first

private val Context.preferencias by preferencesDataStore(name = "gama")

/** Guarda la dirección del servidor, la huella de su certificado y los tokens (cifrados) en el dispositivo. */
class Almacen(private val contexto: Context) {
    private val claveServidor = stringPreferencesKey("servidor")
    private val claveHuella = stringPreferencesKey("huella")
    private val claveAcceso = stringPreferencesKey("acceso")
    private val claveRefresco = stringPreferencesKey("refresco")

    suspend fun servidor(): String? = contexto.preferencias.data.first()[claveServidor]

    suspend fun huella(): String? = contexto.preferencias.data.first()[claveHuella]

    suspend fun guardarServidor(url: String, huella: String?) {
        contexto.preferencias.edit {
            it[claveServidor] = url
            if (huella != null) it[claveHuella] = huella else it.remove(claveHuella)
        }
    }

    suspend fun tokens(): Pair<String, String>? {
        val datos = contexto.preferencias.data.first()
        val acceso = datos[claveAcceso]?.let(Cifrado::descifrar) ?: return null
        val refresco = datos[claveRefresco]?.let(Cifrado::descifrar) ?: return null
        return acceso to refresco
    }

    suspend fun guardarTokens(acceso: String, refresco: String) {
        contexto.preferencias.edit {
            it[claveAcceso] = Cifrado.cifrar(acceso)
            it[claveRefresco] = Cifrado.cifrar(refresco)
        }
    }

    suspend fun borrarTokens() {
        contexto.preferencias.edit {
            it.remove(claveAcceso)
            it.remove(claveRefresco)
        }
    }
}
