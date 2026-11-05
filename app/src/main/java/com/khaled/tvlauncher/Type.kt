package com.khaled.tvlauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The typography/colour system. UI_V2 = false brings back the old fonts, gold and strip behaviour from one place;
 * the complete old source also lives in tv-backup/ui-v1-src/ (old UI = that folder's src copied over app/src).
 */
const val UI_V2 = true

/** UI font: IBM Plex Sans Arabic (SIL OFL, licence in assets/licenses). Has a real Light for the clock and big numbers. */
val UiFont: FontFamily = if (UI_V2) FontFamily(
    Font(R.font.plex_arabic_light, FontWeight.Light),
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_medium, FontWeight.SemiBold),
) else FontFamily.Default

/** Quran font: Amiri Quran (SIL OFL), complete Quranic marks. Used for the ayah only. */
val QuranFont: FontFamily = if (UI_V2) FontFamily(Font(R.font.amiri_quran, FontWeight.Normal)) else FontFamily.Default

/** Colour tokens. Two text levels instead of five alpha steps; the gold is lighter so it holds up on bright photos. */
val TextPrimary = Color.White.copy(alpha = if (UI_V2) 0.97f else 1f)
internal val Soft = Color(0xFFEAF2EE)
internal val Gold = if (UI_V2) Color(0xFFFFE8A8) else Color(0xFFF2D58B)
val TextSecondary = Soft.copy(alpha = if (UI_V2) 0.9f else 0.85f)
val TextDisabled = Soft.copy(alpha = if (UI_V2) 0.68f else 0.5f)

/** One numeral system on screen: Latin digits (the clock and countdowns already use them). Quran references arrive with Arabic-Indic digits. */
const val NUMERALS_LATIN = true
fun digits(s: String): String =
    if (!NUMERALS_LATIN) s else buildString(s.length) { for (ch in s) append(if (ch in '٠'..'٩') '0' + (ch - '٠') else ch) }
