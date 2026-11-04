package com.sonora.fx.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Turns the engine back on after a reboot or app update if it was on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> FxService.sync(context)
        }
    }
}
