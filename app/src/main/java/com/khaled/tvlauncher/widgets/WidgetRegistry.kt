package com.khaled.tvlauncher.widgets

import android.content.Context
import androidx.annotation.DrawableRes
import com.khaled.tvlauncher.R

/** A widget the user can put on the home screen from the "+" picker. [icon] is the white icon drawn on the picker row's tile. */
data class WidgetDef(val id: String, val title: String, val subtitle: String, @DrawableRes val icon: Int)

/** At most this many widgets are on screen at once (a hidden grid of glass cards: columns of 2, filled column by column). */
const val MAX_WIDGETS = 6

/** To add a widget: define it here, then draw it in WidgetCards.kt's WidgetBody(). Order = order in the picker. The Friday and last-third chips are automatic (AutoChips), not widgets. */
val WIDGETS = listOf(
    WidgetDef("next_azan", "الأذان القادم", "عدّاد تنازلي حتى الصلاة التالية", R.drawable.ic_w_next_azan),
    WidgetDef("prayer_times", "أوقات الصلاة", "الصلوات الخمس اليوم والقادمة مميّزة", R.drawable.ic_w_prayer_times),
    WidgetDef("roznama", "الروزنامة", "صفحة اليوم: الميلادي والهجري والمناسبة القادمة", R.drawable.ic_w_roznama),
    WidgetDef("dua", "دعاء اليوم", "دعاء من حصن المسلم يتغيّر كل 6 ساعات", R.drawable.ic_w_dua),
    WidgetDef("asma", "أسماء الله الحسنى", "اسم جديد كل يوم", R.drawable.ic_w_asma),
    WidgetDef("sunnah", "سنّة اليوم", "حديث نبوي قصير يتغيّر كل 6 ساعات مع مصدره", R.drawable.ic_w_sunnah),
)

/** Which widgets are on the screen, in slot order (right to left, top row first). First run shows the first two. */
class WidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(): List<String> {
        val raw = prefs.getString("ids", null) ?: return listOf("next_azan", "roznama")
        val known = WIDGETS.map { it.id }.toSet()
        return raw.split(",").filter { it in known }.distinct().take(MAX_WIDGETS)
    }

    fun set(ids: List<String>) = prefs.edit().putString("ids", ids.joinToString(",")).apply()
}

