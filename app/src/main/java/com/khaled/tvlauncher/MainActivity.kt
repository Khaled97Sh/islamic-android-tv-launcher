package com.khaled.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.khaled.tvlauncher.azan.AzanScheduler
import com.khaled.tvlauncher.home.HomeCatcherService
import com.khaled.tvlauncher.ui.home.HomeScreen
import com.khaled.tvlauncher.ui.home.HomeViewModel

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = vm.reloadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No ambient screensaver while the launcher is showing: keep the screen awake, so the idle
        // timer never starts the dream. Other apps and a real power-off are unaffected.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            // A launcher has nowhere to go "back" to.
            BackHandler(enabled = true) {}
            HomeScreen(vm)
        }
        HomeCatcherService.ensureEnabled(this)
        runCatching { AzanScheduler.scheduleNext(this) }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageChanges, filter)
        vm.visible = true
        vm.reloadApps()
    }

    override fun onStop() {
        vm.visible = false
        unregisterReceiver(packageChanges)
        super.onStop()
    }
}
