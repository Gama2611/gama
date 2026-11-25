package com.gama.mdm

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Latido en segundo plano: Android lo ejecuta como máximo cada 15 minutos, también con la pantalla apagada. */
class TrabajoLatido(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result = when (Sincronizador.sincronizar(applicationContext)) {
        is Sincronizador.Resultado.Falla -> Result.retry()
        else -> Result.success()
    }

    companion object {
        private val conRed = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun programar(contexto: Context) {
            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(
                "gama-latido",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<TrabajoLatido>(15, TimeUnit.MINUTES).setConstraints(conRed).build(),
            )
        }

        fun ahora(contexto: Context) {
            WorkManager.getInstance(contexto).enqueueUniqueWork(
                "gama-latido-ahora",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TrabajoLatido>().setConstraints(conRed).build(),
            )
        }
    }
}
