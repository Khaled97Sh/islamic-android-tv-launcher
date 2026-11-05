package com.khaled.tvlauncher.widgets

import android.content.Context
import com.khaled.tvlauncher.R

/**
 * The ayah and zikr cards at the bottom can each be switched off from the "+" list, next to the widgets. They do not
 * count toward [MAX_WIDGETS] (they are not grid cards). Both are ON by default: what is stored is the list of cards the
 * user turned OFF, so a fresh install, or a card added in a later version, shows by default.
 */
val BOTTOM_CARDS = listOf(
    WidgetDef("ayah", "آية الآن", "آية قرآنية تتغيّر كل دقيقة", R.drawable.ic_w_ayah),
    WidgetDef("zikr", "ذكر الآن", "ذكر يتغيّر كل دقيقة", R.drawable.ic_w_zikr),
)

class BottomCardsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bottom_cards", Context.MODE_PRIVATE)

    /** Ids of the cards that are turned off. */
    fun hidden(): Set<String> {
        val known = BOTTOM_CARDS.map { it.id }.toSet()
        return (prefs.getString("hidden", "") ?: "").split(",").filter { it in known }.toSet()
    }

    fun setHidden(ids: Set<String>) = prefs.edit().putString("hidden", ids.joinToString(",")).apply()
}
