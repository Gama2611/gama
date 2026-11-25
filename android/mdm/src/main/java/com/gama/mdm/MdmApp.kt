package com.gama.mdm

import android.app.Application

class MdmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Registro(this).registrado) TrabajoLatido.programar(this)
    }
}
