package com.khaled.tvlauncher.home

import android.accessibilityservice.AccessibilityService
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.khaled.tvlauncher.MainActivity

/**
 * Optional fallback for TVs where the stock home (Google TV) cannot be switched off with adb.
 * When another home app comes to the front, this brings the launcher back. Off until the user
 * enables it in Settings > Accessibility. It reads nothing: it only looks at which package owns the window.
 */
class HomeCatcherService : AccessibilityService() {

    private var stockHomes: Set<String> = emptySet()
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        refreshStockHomes()
    }

    private fun refreshStockHomes() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        stockHomes = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .filter { it != packageName && it !in NEVER }
            .toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg in NEVER) return
        // Re-ask each time: a stock home can be switched back on after this service connected.
        if (pkg !in stockHomes) {
            refreshStockHomes()
            if (pkg !in stockHomes) return
        }
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1000) return
        lastLaunch = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onInterrupt() = Unit

    companion object {
        /**
         * Switches the service on every time the launcher is opened. Needs WRITE_SECURE_SETTINGS, which only
         * adb can grant (tools/install.sh does); without it this does nothing and the user enables it by hand.
         */
        fun ensureEnabled(context: Context) = runCatching {
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return@runCatching
            val me = ComponentName(context, HomeCatcherService::class.java).flattenToString()
            val cr = context.contentResolver
            val list = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            if (me in list.split(':')) return@runCatching
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, if (list.isEmpty()) me else "$list:$me")
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        }

        // Never treated as a stock home, even if they answer the HOME intent.
        private val NEVER = setOf("android", "com.android.settings", "com.android.tv.settings", "com.android.systemui")
    }
}
