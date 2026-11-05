package com.khaled.tvlauncher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import java.io.File

/**
 * We are the system Home app, so this process stays alive. When the TV is powered on / woken from
 * standby, Android resumes whatever app was last open; bring the launcher to the front instead.
 * (Starting an activity from the background is allowed because the app holds the overlay permission.)
 */
class LauncherApp : Application() {
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!Settings.canDrawOverlays(context)) return
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        TrustedClock.init(this)
        MosqueCalendar.source = { MawaqitSync.days(this) }
        registerReceiver(screenOn, IntentFilter(Intent.ACTION_SCREEN_ON))
        // Leftovers from the removed "real home" experiment (an unused, never-authorized ADB key pair).
        File(filesDir, "adbkey").delete()
        File(filesDir, "adbkey.pub").delete()
    }
}
