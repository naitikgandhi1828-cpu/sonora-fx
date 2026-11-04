package com.sonora.fx

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.sonora.fx.engine.FxService
import com.sonora.fx.ui.CatchAllScreen
import com.sonora.fx.ui.EqualiserScreen
import com.sonora.fx.ui.HomeScreen
import com.sonora.fx.ui.KeepRunningScreen
import com.sonora.fx.ui.LogScreen
import com.sonora.fx.ui.SonoraFxTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    // Recomposition trigger: bumped whenever Shizuku/permission state changes.
    private var tick by mutableStateOf(0)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
    private val binderReceived = Shizuku.OnBinderReceivedListener { tick++ }
    private val binderDead = Shizuku.OnBinderDeadListener { tick++ }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        FxService.sync(this)
        setContent {
            SonoraFxTheme {
                var route by rememberSaveable { mutableStateOf("home") }
                val back = { route = "home" }
                BackHandler(enabled = route != "home") { back() }
                when (route) {
                    "eq" -> EqualiserScreen(onBack = back)
                    "catch" -> CatchAllScreen(refreshKey = tick, onRefresh = { tick++ }, onBack = back)
                    "keep" -> KeepRunningScreen(refreshKey = tick, onBack = back)
                    "log" -> LogScreen(onBack = back)
                    else -> HomeScreen(go = { route = it })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++ // re-check permissions when returning from Shizuku or Settings
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
        }
    }
}
