package com.khaled.tvlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

/**
 * The app's text. It shadows Compose's BasicText inside this package so every text goes through [fit]:
 * the sizes in the code stay the original (v1) sp values, and [fit] makes the new fonts look and measure the same as
 * the TV's old default Arabic font did at those sizes:
 *  - width: measured with real shaped Arabic (HarfBuzz) over this app's ayat, azkar, duas and hadith, Plex draws
 *    about 3-9% wider than the old font and Amiri Quran about 5-6% narrower, so the size is scaled by 0.93-0.97
 *    (Plex, by weight) and 1.06 (Amiri); Latin digits in Plex are about 7-11% wider than Roboto, so 0.93;
 *  - line height: the old font had a 1.36 em line, Plex 1.49 em and Amiri 2.44 em, so a missing line height is set to
 *    1.36 x the original size. Cards therefore keep the size they had.
 * [RawText] is the unscaled original, used for the clock, date and top chips which keep their own v2 sizes.
 */
@Composable
fun BasicText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default,
    onTextLayout: (TextLayoutResult) -> Unit = {}, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1
) = androidx.compose.foundation.text.BasicText(
    text = text, modifier = modifier, style = fit(style, text), onTextLayout = onTextLayout,
    overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines
)

@Composable
fun BasicText(
    text: AnnotatedString, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default,
    onTextLayout: (TextLayoutResult) -> Unit = {}, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1
) = androidx.compose.foundation.text.BasicText(
    text = text, modifier = modifier, style = fit(style, text.text), onTextLayout = onTextLayout,
    overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines
)

/** Compose's BasicText without [fit]: the size in the style is the size drawn. */
@Composable
fun RawText(
    text: String, modifier: Modifier = Modifier, style: TextStyle = TextStyle.Default,
    onTextLayout: (TextLayoutResult) -> Unit = {}, overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true, maxLines: Int = Int.MAX_VALUE, minLines: Int = 1
) = androidx.compose.foundation.text.BasicText(
    text = text, modifier = modifier, style = style, onTextLayout = onTextLayout,
    overflow = overflow, softWrap = softWrap, maxLines = maxLines, minLines = minLines
)

/** Size factors (see above). */
const val FACTOR_PLEX_REGULAR = 0.96f
const val FACTOR_PLEX_LIGHT = 0.97f
const val FACTOR_PLEX_MEDIUM = 0.93f
const val FACTOR_PLEX_DIGITS = 0.93f
const val FACTOR_AMIRI_QURAN = 1.06f
/** Line pitch of the old default Arabic font, in em. */
const val V1_LINE_EM = 1.36f

internal fun fit(style: TextStyle, text: CharSequence): TextStyle {
    if (!UI_V2) return style
    val family = style.fontFamily
    if (family != UiFont && family != QuranFont) return style
    if (style.fontSize.isUnspecified) return style
    val sp = style.fontSize.value
    val hasArabic = text.any { it.code in 0x0600..0x06FF }
    val factor = when {
        family == QuranFont -> FACTOR_AMIRI_QURAN
        !hasArabic -> FACTOR_PLEX_DIGITS
        style.fontWeight == FontWeight.Medium || style.fontWeight == FontWeight.SemiBold -> FACTOR_PLEX_MEDIUM
        style.fontWeight == FontWeight.Light -> FACTOR_PLEX_LIGHT
        else -> FACTOR_PLEX_REGULAR
    }
    val line = if (style.lineHeight.isSpecified) style.lineHeight else (sp * V1_LINE_EM).sp
    return style.copy(fontSize = (sp * factor).sp, lineHeight = line)
}
