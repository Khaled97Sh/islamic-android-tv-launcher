package com.khaled.tvlauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant

/** Keeps exactly one exact alarm pending: the next prayer. The receiver re-arms it after firing. */
object AzanScheduler {
    private const val REQUEST_CODE = 4401

    fun scheduleNext(context: Context) {
        val app = context.applicationContext
        val next = PrayerTimeProvider.nextAfter(Instant.ofEpochMilli(TrustedClock.now()))
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            app,
            REQUEST_CODE,
            Intent(app, AzanReceiver::class.java).putExtra(AzanReceiver.EXTRA_PRAYER, next.nameAr),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Armed on the monotonic timer, not the wall clock: the TV's system clock can be wrong after a start without
        // internet. When the network later corrects it, TIME_SET re-arms this (BootReceiver).
        val at = TrustedClock.elapsedAt(next.at.toEpochMilli())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        } else {
            // Exact-alarm access not granted: still fire, just possibly a little late.
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
        }
    }
}
