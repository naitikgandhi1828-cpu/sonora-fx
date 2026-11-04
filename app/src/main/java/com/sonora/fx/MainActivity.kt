package com.sonora.fx

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.sonora.fx.engine.FxService
import com.sonora.fx.ui.CatchAllScreen
import com.sonora.fx.ui.EffectsScreen
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
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Effects") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Catch Every App") })
                    }
                    when (tab) {
                        0 -> EffectsScreen()
                        // Reading tick here ties the screen to the state changes above.
                        else -> CatchAllScreen(refreshKey = tick, onRefresh = { tick++ })
                    }
                }
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
