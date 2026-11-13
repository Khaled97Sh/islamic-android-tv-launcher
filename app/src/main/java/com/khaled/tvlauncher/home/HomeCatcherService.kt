package com.khaled.tvlauncher.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
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
        if (pkg !in stockHomes) return
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 1000) return
        lastLaunch = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        // Never treated as a stock home, even if they answer the HOME intent.
        val NEVER = setOf("android", "com.android.settings", "com.android.tv.settings", "com.android.systemui")
    }
}
