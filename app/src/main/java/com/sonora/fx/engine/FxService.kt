package com.sonora.fx.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.IBinder
import com.sonora.fx.diag.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.sonora.fx.FxApplication
import com.sonora.fx.MainActivity

/**
 * Foreground service that keeps the engine alive while Sonora FX is on, and
 * listens for players announcing their audio sessions.
 */
class FxService : Service() {

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
            if (pkg == packageName) return
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> FxEngine.openSession(session, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> FxEngine.closeSession(session)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sonora FX running", NotificationManager.IMPORTANCE_LOW)
        )
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        ContextCompat.registerReceiver(
            this, sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        FxEngine.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!FxApplication.settings.enabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionReceiver) }
        FxEngine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(com.sonora.fx.R.drawable.ic_stat_fx)
            .setContentTitle("Sonora FX is on")
            .setContentText("Effects are applied to your music")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "SonoraFX.Service"
        private const val CHANNEL = "fx.running"
        private const val NOTIF_ID = 1

        /** Starts or stops the service to match the master switch. */
        fun sync(context: Context) {
            val i = Intent(context, FxService::class.java)
            try {
                if (FxApplication.settings.enabled) ContextCompat.startForegroundService(context, i)
                else context.stopService(i)
            } catch (t: Throwable) {
                Log.w(TAG, "sync failed", t)
            }
        }
    }
}
