package com.sonora.fx

import android.app.Application
import com.sonora.fx.perdevice.PerDeviceEq
import com.sonora.fx.settings.FxSettings

class FxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        app = this
        settings = FxSettings(this)
        // Separate EQ per output device (speaker, wired, each Bluetooth device).
        PerDeviceEq.start(this)
    }

    companion object {
        lateinit var app: FxApplication
            private set
        lateinit var settings: FxSettings
            private set
    }
}
