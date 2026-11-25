package com.gama.suite

import android.app.Application
import com.gama.suite.datos.Almacen

class GamaApp : Application() {
    val almacen: Almacen by lazy { Almacen(this) }
}
