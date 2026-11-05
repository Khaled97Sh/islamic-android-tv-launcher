package com.khaled.tvlauncher

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings

/**
 * The time the whole app uses. This TV has no battery-backed clock: after a cold start without internet its system
 * clock shows the firmware build date (2025-04-21) until the network sets it. So the system clock is only believed
 * when it is plausible, i.e. not earlier than the newest of: the last time this app saw a good clock, the moment the
 * app was installed, and the firmware build date. A wrong system clock is always older than that.
 *
 * When the system clock is not believable, time is continued from the last good moment with the monotonic timer
 * ([SystemClock.elapsedRealtime], keeps counting in standby) if the TV has not rebooted since, and otherwise starts from the
 * last time the app saw a good clock (the TV was off in between, so it can only be behind, never ahead).
 * As soon as the network corrects the system clock, it is trusted again and the app moves to it.
 */
object TrustedClock {
    @Volatile internal var systemMs: () -> Long = { System.currentTimeMillis() }
    @Volatile internal var elapsedMs: () -> Long = { SystemClock.elapsedRealtime() }

    private var floor = 0L
    private var anchorUtc = 0L
    private var anchorElapsed = 0L
    private var anchorBoot = -2
    private var bootNow = -1
    private var appContext: Context? = null

    @Synchronized fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val p = app.getSharedPreferences("clock", Context.MODE_PRIVATE)
        anchorUtc = p.getLong("utc", 0L); anchorElapsed = p.getLong("elapsed", 0L); anchorBoot = p.getInt("boot", -2)
        bootNow = runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
        val installed = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime }.getOrDefault(0L)
        floor = maxOf(anchorUtc, installed, Build.TIME)
    }

    /** Current UTC time in ms. */
    @Synchronized fun now(): Long {
        val s = systemMs(); val e = elapsedMs()
        if (s >= floor) {
            anchorUtc = s; anchorElapsed = e; anchorBoot = bootNow; floor = s
            return s
        }
        return if (anchorBoot == bootNow && e >= anchorElapsed) anchorUtc + (e - anchorElapsed) else floor
    }

    /** True when the time comes from the system clock, false when the app is carrying it forward itself. */
    @Synchronized fun isSystemClockGood(): Boolean = systemMs() >= floor

    /** Elapsed-realtime value at which the trusted time will be [targetUtc]; for alarms that must not depend on the wall clock. */
    fun elapsedAt(targetUtc: Long): Long = elapsedMs() + (targetUtc - now())

    /** Remember the last good time so a reboot without internet can start from it. */
    @Synchronized fun save() {
        val c = appContext ?: return
        now()
        c.getSharedPreferences("clock", Context.MODE_PRIVATE).edit()
            .putLong("utc", anchorUtc).putLong("elapsed", anchorElapsed).putInt("boot", anchorBoot).apply()
    }

    /** For tests. */
    @Synchronized internal fun setUp(floorMs: Long, utc: Long, elapsed: Long, boot: Int, bootNow: Int) {
        floor = floorMs; anchorUtc = utc; anchorElapsed = elapsed; anchorBoot = boot; this.bootNow = bootNow
    }
}
