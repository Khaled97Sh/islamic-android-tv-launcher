package com.khaled.tvlauncher.azan

import android.content.Context
import com.khaled.tvlauncher.widgets.WidgetDef

/** The row shown in the "+" list (a tick box like the widgets) that switches the whole azan alarm on or off. */
val AZAN_ALERT = WidgetDef("azan_alert", "تنبيه الأذان", "صوت الأذان وبطاقة التذكير عند كل صلاة", "ت")

/**
 * User switch for the azan alarm. ON by default (what is stored is the user's choice, so a fresh install or an update keeps
 * the azan working). Turning it off cancels the pending alarm, turning it on arms the next prayer straight away.
 */
object AzanSettings {
    private const val KEY = "enabled"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("azan", Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY, true)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY, on).apply()
        if (on) AzanScheduler.scheduleNext(context) else AzanScheduler.cancel(context)
    }
}
