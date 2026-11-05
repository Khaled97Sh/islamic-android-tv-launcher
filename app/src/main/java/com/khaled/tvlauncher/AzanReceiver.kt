package com.khaled.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(EXTRA_PRAYER) ?: "الصلاة"
        // Re-arm the next prayer FIRST: whatever happens when starting the service below, later azans must survive.
        runCatching { AzanScheduler.scheduleNext(context) }
            .onFailure { android.util.Log.e("TvLauncher", "re-arm failed", it) }
        runCatching {
            context.startForegroundService(Intent(context, AzanService::class.java).putExtra(EXTRA_PRAYER, prayer))
        }.onFailure { android.util.Log.e("TvLauncher", "azan service could not start", it) }
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
    }
}
