package com.sistemasdeinformacion.proyecto

import android.app.Application
import androidx.work.Configuration
import com.sistemasdeinformacion.proyecto.di.AppContainer

class BitacoraApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
