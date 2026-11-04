package com.sonora.fx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sonora.fx.shizuku.ShizukuGrant
import com.sonora.fx.ui.CatchAllScreen
import com.sonora.fx.ui.SonoraFxTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    // Recomposition trigger: bumped whenever Shizuku/permission state changes.
    private var tick by mutableStateOf(0)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> tick++ }
    private val binderReceived = Shizuku.OnBinderReceivedListener { tick++ }
    private val binderDead = Shizuku.OnBinderDeadListener { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
        }
        setContent {
            SonoraFxTheme {
                // Reading tick here ties the screen to the state changes above.
                CatchAllScreen(refreshKey = tick, onRefresh = { tick++ })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++ // re-check DUMP status when returning from Shizuku or settings
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
