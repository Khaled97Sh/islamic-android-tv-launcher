package com.khaled.tvlauncher.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khaled.tvlauncher.prayer.PrayerTimeProvider
import com.khaled.tvlauncher.time.TrustedClock
import com.khaled.tvlauncher.time.clock12
import com.khaled.tvlauncher.ui.home.glow
import com.khaled.tvlauncher.ui.theme.BasicText
import com.khaled.tvlauncher.ui.theme.Gold
import com.khaled.tvlauncher.ui.theme.Soft
import com.khaled.tvlauncher.ui.theme.TextSecondary
import com.khaled.tvlauncher.ui.theme.UiFont
import com.khaled.tvlauncher.ui.theme.glass
import com.khaled.tvlauncher.widgets.Roznama
import com.khaled.tvlauncher.widgets.WidgetContent
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val CARD_W = 220.dp
/** One height for every card: the tallest widget (prayer times with its 5 rows) measured 137 dp on the TV and the user accepted it. */
private val CARD_H = 137.dp
private val GAP = 10.dp
private const val ROWS = 2
private const val MAX_COLUMNS = 3

private val weekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.forLanguageTag("ar-u-nu-latn"))
private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn"))
private val hijriLongFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn")).withChronology(java.time.chrono.HijrahChronology.INSTANCE)

/**
 * The hidden grid: up to [MAX_WIDGETS] glass cards, ALL the same size ([CARD_W] x [CARD_H]); each card's content is
 * scaled by [FitBox] to the largest size that shows all its data, so no card has empty space. Columns of 2, filled
 * column by column starting at the right edge (the root layout is RTL): slot 1 = top of column 1, slot 2 = under it,
 * slot 3 = top of column 2 ... At most 3 columns. The caller gives it the free area (it already stops short of the app
 * rail); the block keeps its natural size unless that area is too small, then it is scaled down uniformly (never below
 * 70%), so it can never touch the rail or the ayah card. [centered] puts it in the vertical middle of the area.
 */
@Composable
fun WidgetGrid(ids: List<String>, now: ZonedDateTime, content: WidgetContent, modifier: Modifier, centered: Boolean = false) {
    if (ids.isEmpty()) return
    WidgetFlow(modifier, centered) {
        ids.forEach { id ->
            Box(Modifier.glass(radius = 22.dp, dark = 0.36f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Reading widgets (prayer times, dua, sunnah) are set at fixed, hand-tuned sizes: they may only shrink to fit,
                // never grow (growing made them too big for the eye with Plex Arabic). The others still grow to fill.
                val fixedSize = id == "prayer_times" || id == "dua" || id == "sunnah"
                FitBox(Modifier.fillMaxSize(), maxFit = if (fixedSize) 1f else MAX_FIT, fill = id == "dua" || id == "sunnah") { WidgetBody(id, now, content) }
            }
        }
    }
}

/**
 * Fit-to-box: shows its content at the LARGEST scale (between [MIN_FIT] and [MAX_FIT]) at which everything still fits
 * the box, centred vertically so the top and bottom insets are equal. It does this for any content, so short text
 * grows to fill the card and long text (a long dua) shrinks until all of it is visible, nothing is cut. The scale is
 * found with intrinsic heights at the narrower layout width (width / scale), then the content is measured once and drawn
 * scaled (glyphs are redrawn at the larger size, not stretched).
 */
@Composable
private fun FitBox(modifier: Modifier, relax: Float = 1f, maxFit: Float = MAX_FIT, fill: Boolean = false, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val m = measurables.firstOrNull() ?: return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        val w = constraints.maxWidth; val h = constraints.maxHeight
        fun fits(k: Float) = m.maxIntrinsicHeight((w / k).roundToInt()) * k <= h
        var k = if (fits(maxFit)) maxFit else if (!fits(MIN_FIT)) MIN_FIT else {
            var lo = MIN_FIT; var hi = maxFit
            repeat(8) { val mid = (lo + hi) / 2f; if (fits(mid)) lo = mid else hi = mid }
            lo
        }
        // [relax] < 1 keeps the text a little smaller than the biggest size that fits (long reading text looks calmer).
        k = (k * relax).coerceAtLeast(MIN_FIT)
        val innerW = (w / k).roundToInt()
        // [fill]: give the content the card's full inner height (at the chosen scale) so a Column with SpaceBetween can pin
        // its first line to the top edge and its last line to the bottom edge, with no empty band above the title.
        val innerH = (h / k).roundToInt()
        val p = m.measure(
            if (fill) Constraints(minWidth = innerW, maxWidth = innerW, minHeight = innerH, maxHeight = innerH)
            else Constraints(minWidth = innerW, maxWidth = innerW, minHeight = 0, maxHeight = Constraints.Infinity)
        )
        layout(w, h) {
            p.placeWithLayer(0, ((h - p.height * k) / 2f).roundToInt()) {
                scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

private const val MIN_FIT = 0.85f
private const val MAX_FIT = 1.7f

@Composable
private fun WidgetFlow(modifier: Modifier, centered: Boolean, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val cw = CARD_W.roundToPx(); val gap = GAP.roundToPx()
        val availW = constraints.maxWidth; val availH = constraints.maxHeight
        val ch = CARD_H.roundToPx()
        val cards = measurables.map { it.measure(Constraints.fixed(cw, ch)) }
        val cols = minOf(MAX_COLUMNS, (cards.size + ROWS - 1) / ROWS)
        val rows = minOf(ROWS, cards.size)
        val blockW = cols * cw + (cols - 1) * gap
        val blockH = rows * ch + (rows - 1) * gap
        val scale = minOf(1f, if (blockW > 0) availW.toFloat() / blockW else 1f, if (blockH > 0 && availH > 0) availH.toFloat() / blockH else 1f).coerceAtLeast(0.7f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        val left = if (rtl) availW - blockW * scale else 0f
        val top = if (centered) ((availH - blockH * scale) / 2f).coerceAtLeast(0f) else 0f
        layout(availW, availH) {
            cards.forEachIndexed { k, p ->
                val col = k / ROWS; val row = k % ROWS
                val xIn = if (rtl) blockW - (col + 1) * cw - col * gap else col * (cw + gap)
                p.placeWithLayer((left + xIn * scale).roundToInt(), (top + row * (ch + gap) * scale).roundToInt()) {
                    scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

@Composable
private fun WidgetBody(id: String, now: ZonedDateTime, content: WidgetContent) = when (id) {
    "next_azan" -> NextAzanWidget()
    "prayer_times" -> PrayerTimesWidget(now)
    "roznama" -> RoznamaWidget(now)
    "dua" -> DuaWidget(now, content)
    "asma" -> AsmaWidget(now, content)
    "sunnah" -> SunnahWidget(now, content)
    else -> Unit
}

// ---------- shared bits ----------

private fun txt(color: Color, size: Int, weight: FontWeight? = null, align: TextAlign = TextAlign.Start, line: Int = 0) =
    TextStyle(fontFamily = UiFont, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align, shadow = glow(8f), lineHeight = if (line > 0) line.sp else TextStyle.Default.lineHeight)

@Composable private fun Label(text: String, modifier: Modifier = Modifier) =
    BasicText(text, style = txt(TextSecondary, 12), maxLines = 1, modifier = modifier)

@Composable private fun Small(text: String, modifier: Modifier = Modifier) =
    BasicText(text, style = txt(TextSecondary, 13), maxLines = 1, modifier = modifier)

/** Big number with a small unit/seconds suffix after it. Digits are laid out left-to-right even in the RTL root. */
@Composable private fun BigNumber(big: String, small: String, modifier: Modifier = Modifier) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicText(
            buildAnnotatedString {
                append(big)
                if (small.isNotEmpty()) withStyle(SpanStyle(fontSize = 16.sp, color = Gold)) { append(if (small.startsWith(":")) small else " $small") }
            },
            style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Light, shadow = glow(14f)),
            maxLines = 1, modifier = modifier
        )
    }

/** The current time in ms, refreshed every [stepMs] (aligned to the step). */
@Composable private fun rememberNowMs(stepMs: Long): Long {
    var t by remember { mutableLongStateOf(TrustedClock.now()) }
    LaunchedEffect(stepMs) { while (true) { t = TrustedClock.now(); delay(stepMs - t % stepMs) } }
    return t
}

// ---------- 1. next azan ----------

@Composable
private fun NextAzanWidget() {
    var next by remember { mutableStateOf(PrayerTimeProvider.nextAfter(Instant.ofEpochMilli(TrustedClock.now()))) }
    var nowMs by remember { mutableLongStateOf(TrustedClock.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val t = TrustedClock.now()
            nowMs = t
            if (!next.at.isAfter(Instant.ofEpochMilli(t))) next = PrayerTimeProvider.nextAfter(Instant.ofEpochMilli(t))
            delay(1000 - t % 1000)
        }
    }
    val rem = (next.at.toEpochMilli() - nowMs).coerceAtLeast(0)
    val h = rem / 3_600_000L; val m = rem / 60_000L % 60; val s = rem / 1000L % 60
    val (big, small) = if (h >= 1) "$h:%02d".format(m) to ":%02d".format(s) else "%02d:%02d".format(m, s) to ""
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(next.nameAr, style = txt(Gold, 20), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Label("الأذان القادم", Modifier.padding(bottom = 3.dp))
        }
        BigNumber(big, small)
        Small(clock12(next.at))
    }
}

// ---------- 2. prayer times ----------

@Composable
private fun PrayerTimesWidget(now: ZonedDateTime) {
    val today = now.toLocalDate()
    val list = remember(today) { PrayerTimeProvider.forDate(today) }
    val nextIdx = list.indexOfFirst { it.at.isAfter(now.toInstant()) }.let { if (it < 0) 0 else it }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.forEachIndexed { i, p ->
            val hot = i == nextIdx
            Row(
                Modifier.fillMaxWidth().then(if (hot) Modifier.background(Color.White.copy(alpha = 0.16f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(p.nameAr, style = txt(if (hot) Gold else Color.White, 13), modifier = Modifier.weight(1f))
                BasicText(clock12(p.at), style = txt(if (hot) Gold else Soft, 13))
            }
        }
    }
}

// ---------- 3. roznama: tear-off day page ----------

@Composable
private fun RoznamaWidget(now: ZonedDateTime) {
    val date = now.toLocalDate()
    val sunrise = remember(date) { PrayerTimeProvider.sunrise(date) }
    val sunset = remember(date) { PrayerTimeProvider.forDate(date)[3].at }
    val occasion = remember(date) { Roznama.nextOccasion(date) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("${now.format(weekdayFmt)}  ·  ${now.format(monthFmt)}", style = txt(Gold, 12, align = TextAlign.Center), maxLines = 1)
        BasicText(
            now.dayOfMonth.toString(),
            style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, shadow = glow(14f))
        )
        BasicText(now.format(hijriLongFmt), style = txt(Color.White, 12, align = TextAlign.Center), maxLines = 1)
        Box(Modifier.fillMaxWidth(0.5f).height(1.dp).background(Color.White.copy(alpha = 0.28f)))
        BasicText("الشروق ${clock12(sunrise, false)} ص   ·   الغروب ${clock12(sunset, false)} م", style = txt(TextSecondary, 10, align = TextAlign.Center), maxLines = 1)
        BasicText(occasion.text(), style = txt(Gold, 11, align = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------- 4. daily dua ----------

@Composable
private fun DuaWidget(now: ZonedDateTime, content: WidgetContent) {
    val dua = remember(now.toLocalDate()) { content.duaFor(now.toLocalDate()) } ?: return
    // Title on the very top edge, caption on the bottom edge, the dua text centred between them (FitBox fill = true).
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText(dua.source, style = txt(Gold, 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            BasicText(dua.text, style = txt(Color.White, 14, line = 21))
        }
        Label("دعاء اليوم  ·  حصن المسلم")
    }
}

// ---------- 5. asma al-husna ----------

@Composable
private fun AsmaWidget(now: ZonedDateTime, content: WidgetContent) {
    val a = remember(now.toLocalDate()) { content.asmaFor(now.toLocalDate()) } ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label("من أسماء الله الحسنى")
        BasicText(a.name, style = TextStyle(fontFamily = UiFont, color = Gold, fontSize = 38.sp, textAlign = TextAlign.Center, shadow = glow(12f)), maxLines = 1)
        Small("${a.number} / 99")
    }
}

// ---------- 6. sunnah of the day ----------

@Composable
private fun SunnahWidget(now: ZonedDateTime, content: WidgetContent) {
    val h = remember(now.toLocalDate()) { content.sunnahFor(now.toLocalDate()) } ?: return
    // Long sayings get a smaller font so they still fit the card without being cut off.
    val plain = h.text.count { it.code !in 0x064B..0x065F && it.code != 0x0670 }
    val size = if (plain > 95) 13 else 14
    // Title on the very top edge, source on the bottom edge, the saying centred between them (FitBox fill = true).
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText("سنّة اليوم  ·  ${h.topic}", style = txt(Gold, 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            BasicText("\u00ab ${h.text} \u00bb", style = txt(Color.White, size, line = (size * 1.5f).toInt()))
        }
        Label(h.source)
    }
}
