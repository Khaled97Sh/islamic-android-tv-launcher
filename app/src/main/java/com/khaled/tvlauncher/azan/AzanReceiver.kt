package com.khaled.tvlauncher.azan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.khaled.tvlauncher.time.TrustedClock

class AzanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prayer = intent.getStringExtra(EXTRA_PRAYER) ?: "الصلاة"
        // Re-arm the next prayer FIRST: whatever happens when starting the service below, later azans must survive.
        runCatching { AzanScheduler.scheduleNext(context) }
            .onFailure { android.util.Log.e("TvLauncher", "re-arm failed", it) }
        if (!AzanSettings.isEnabled(context)) return   // user turned the azan off
        // Fast power-on / standby can hold the alarm until the TV wakes: never play an adhan for a prayer that is already past.
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        if (at > 0 && TrustedClock.now() - at > MAX_LATE_MS) {
            android.util.Log.w("TvLauncher", "azan skipped: fired ${(TrustedClock.now() - at) / 1000}s late")
            return
        }
        runCatching {
            context.startForegroundService(Intent(context, AzanService::class.java).putExtra(EXTRA_PRAYER, prayer))
        }.onFailure { android.util.Log.e("TvLauncher", "azan service could not start", it) }
    }

    companion object {
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_AT = "at"
        private const val MAX_LATE_MS = 2 * 60_000L
    }
}
