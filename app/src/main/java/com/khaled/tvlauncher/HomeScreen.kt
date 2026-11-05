package com.khaled.tvlauncher

import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("hh:mm", Locale.forLanguageTag("ar-u-nu-latn"))
private val ampmFmt = DateTimeFormatter.ofPattern("a", Locale.forLanguageTag("ar-u-nu-latn"))
private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("ar-u-nu-latn"))
private val hijriFmt = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar-u-nu-latn")).withChronology(java.time.chrono.HijrahChronology.INSTANCE)

internal fun glow(radius: Float = 10f) = Shadow(Color.Black.copy(alpha = 0.75f), Offset(0f, 2f), radius)

@Composable
fun HomeScreen(vm: HomeViewModel) {
    val now by vm.now.collectAsStateWithLifecycle()
    val apps by vm.appList.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val ayah by vm.ayah.collectAsStateWithLifecycle()
    val zikr by vm.zikr.collectAsStateWithLifecycle()
    val ticker by vm.ticker.collectAsStateWithLifecycle()
    val bg by vm.bg.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val checked by vm.checked.collectAsStateWithLifecycle()
    val widgetIds by vm.widgets.collectAsStateWithLifecycle()
    val bottomHidden by vm.bottomHidden.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val chips = AutoChips.at(now)

    // YouTube preview window: shown while the YouTube tile (or the window itself) has focus.
    val ytTile = remember { FocusRequester() }
    val previewReq = remember { FocusRequester() }
    var railOnYt by remember { mutableStateOf(false) }
    var previewFocused by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    // The small delay bridges the instant while focus moves from the tile into the window (both flags false).
    LaunchedEffect(railOnYt, previewFocused) {
        if (railOnYt || previewFocused) showPreview = true else { delay(150); showPreview = false }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF05100C))) {
            val screenDensity = LocalDensity.current
            val screen = with(screenDensity) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
            // Responsive: the layout is designed for a 960 dp wide screen (this 55" 4K TV). On a screen that
            // reports more or fewer dp, every dp and sp is scaled by the same factor so it looks the same, just
            // bigger or smaller. Capped so a very odd size can't make things unreadably small or huge.
            val k = (maxWidth.value / DESIGN_WIDTH_DP).coerceIn(0.6f, 2.2f)
            val scaled = Density(screenDensity.density * k, screenDensity.fontScale)
            CompositionLocalProvider(LocalDensity provides scaled) {
            // Everything below is laid out in the scaled space (960 dp wide, like the design).
            BoxWithConstraints(Modifier.fillMaxSize()) {
            // The glass panels follow the photo: the previous one stays underneath while the new one fades in (same 1.6 s).
            var shown by remember { mutableStateOf<BackgroundImage?>(null) }
            var previous by remember { mutableStateOf<BackgroundImage?>(null) }
            val mix = remember { androidx.compose.animation.core.Animatable(1f) }
            LaunchedEffect(bg) {
                val nb = bg ?: return@LaunchedEffect
                if (UI_V2 && shown != null) {
                    previous = shown; shown = nb
                    mix.snapTo(0f); mix.animateTo(1f, tween(1600))
                    previous = null
                } else { shown = nb; previous = null; mix.snapTo(1f) }
            }
            CompositionLocalProvider(LocalGlass provides GlassEnv(shown ?: bg, screen, previous, { mix.value })) {
                // Photo, crossfading when it rotates. The scrim is light in the middle so the picture stays
                // clear, and only darkens at the top and bottom where the text sits.
                Crossfade(targetState = bg, animationSpec = tween(1600), label = "bg") { b ->
                    if (b != null) Image(b.full, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(0.46f), 0.28f to Color.Black.copy(0.05f),
                            0.55f to Color.Transparent, 1f to Color.Black.copy(0.66f)
                        )
                    )
                )

                // The news-ticker azkar bar is full width and flush with the bottom edge (no margins); when it shows, the screen's
                // content keeps clear of it by its height. Without the bar the bottom margin is the usual 12 dp.
                val barH = if (ticker != null) AZKAR_BAR_HEIGHT else 0.dp
                // One column: top bar, then the free middle area (the widget grid lives there and takes whatever height is
                // left, so it can never run into the bottom cards), then the ayah and zikr cards.
                Column(
                    Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = if (ticker != null) barH + 6.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopBar(
                        now.format(timeFmt), now.format(ampmFmt), now.format(dateFmt) + " ، " + now.format(hijriFmt), weather, chips,
                        Modifier.fillMaxWidth()
                    )

                    Box(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp, end = 104.dp)) {   // end = the left side (RTL): rail edge 88 dp + 40 dp
                        // Start = the right edge in this RTL root. With the bar showing the grid stays at the top; without it
                        // the grid is centred in the space between the clock and the ayah card.
                        WidgetGrid(
                            widgetIds, now, vm.widgetContent,
                            Modifier.align(if (ticker == null) Alignment.CenterStart else Alignment.TopStart).fillMaxWidth()
                                .then(if (ticker == null) Modifier.fillMaxHeight() else Modifier.fillMaxSize()),
                            centered = ticker == null
                        )
                    }

                    // Bottom: the ayah and zikr cards (v1 sizes); each can be switched off from the "+" list.
                    if (bottomHidden.size < BOTTOM_CARDS.size) Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Extra end (= left) padding keeps these cards clear of the app rail.
                        if ("ayah" !in bottomHidden) TextCard(Modifier.padding(end = 72.dp)) {
                            BasicText(
                                text = buildAnnotatedString {
                                    append("﴿ ${ayah.text} ﴾")
                                    withStyle(SpanStyle(color = Gold, fontSize = 13.sp)) { append("   ${digits(ayah.ref)}") }
                                },
                                modifier = Modifier.capLineBox(lineDp = 23.dp, fontSp = 17f * FACTOR_AMIRI_QURAN),
                                style = TextStyle(
                                    fontFamily = QuranFont, color = TextPrimary, fontSize = 17.sp, textAlign = TextAlign.Start, shadow = glow(8f),
                                    // Amiri Quran has a very tall ascent/descent: fix the line pitch (about 1.45x) and drop the font padding
                                    // so a one-line verse card is as tall as the zikr card below it.
                                    lineHeight = 23.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
                                )
                            )
                        }
                        if ("zikr" !in bottomHidden) TextCard(Modifier.padding(end = 72.dp)) {
                            BasicText(zikr, style = TextStyle(
                                fontFamily = UiFont, color = TextPrimary, fontSize = 17.sp, textAlign = TextAlign.Start, shadow = glow(8f),
                                lineHeight = 23.sp,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                lineHeightStyle = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
                            ))
                        }
                    }
                }

                ticker?.let { AzkarBar(it, onPassEnd = vm::nextTicker, Modifier.align(Alignment.BottomCenter)) }

                // Left: app card (declared last so it, and the focused name, draw above the bottom card).
                run {
                    AppRail(
                        apps, onLaunch = vm::launch, onAdd = vm::openPicker, pickerOpen = picker != null,
                        first = ytTile, onYouTubeFocus = { railOnYt = it },
                        onRightFromYouTube = { if (YT_SUGGESTIONS_ENABLED && videos.isNotEmpty() && showPreview) { runCatching { previewReq.requestFocus() }.isSuccess } else false },
                        // The rail is laid out left-to-right internally, so its margin is "start" (= physical left).
                        modifier = Modifier.align(Alignment.CenterEnd).padding(start = 24.dp)
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    AnimatedVisibility(
                        visible = YT_SUGGESTIONS_ENABLED && showPreview && videos.isNotEmpty() && picker == null,
                        enter = fadeIn(tween(160)), exit = fadeOut(tween(160)),
                        // Beside the rail (24dp margin + 64dp rail + gap), under the weather card, above the quote cards.
                        modifier = Modifier.align(Alignment.TopEnd).padding(start = 100.dp, top = 118.dp)
                    ) {
                        YouTubePreview(
                            videos, vm, previewReq, onFocusChange = { previewFocused = it },
                            onLeave = { runCatching { ytTile.requestFocus() } }
                        )
                    }
                }

                picker?.let { AppPicker(it, checked, vm::toggleApp, widgetIds, vm::toggleWidget, bottomHidden, vm::toggleBottom, vm::closePicker) }
            }
            }
            }
        }
    }
}

/** The screen width (in dp) the layout was designed for: this 55" TV shows 1920 px at 2x density = 960 dp. */
private const val DESIGN_WIDTH_DP = 960f

/** Letters without the vowel marks: the real "length" of Arabic text (tashkeel roughly doubles the raw string). */
private fun plainLetters(t: String) = t.count { it.code !in 0x064B..0x065F && it.code != 0x0670 }

@Composable
private fun TopBar(time: String, ampm: String, date: String, weather: Weather?, chips: List<ChipInfo>, modifier: Modifier) {
    // Top-aligned: the clock and the chips both start exactly at the 24 dp top margin (centring pushed the chips lower).
    Row(modifier, verticalAlignment = Alignment.Top) {
        // Clock on the right (start in RTL); then the automatic chips; weather on the far left (end).
        // The clock font leaves about 16 dp of empty space above the digits; pull the block up by that much (and shrink its
        // layout height the same) so the top of the digits lines up with the top of the chips.
        Column(Modifier.weight(1f).layout { m, c ->
            val p = m.measure(c); val cut = 16.dp.roundToPx()
            layout(p.width, p.height - cut) { p.place(0, -cut) }
        }) {
            Row(verticalAlignment = Alignment.Bottom) {
                RawText(time, style = TextStyle(fontFamily = UiFont, color = TextPrimary, fontSize = if (UI_V2) 46.sp else 40.sp, fontWeight = FontWeight.Light, shadow = glow(16f)))
                Spacer(Modifier.width(6.dp))
                RawText(ampm, style = TextStyle(fontFamily = UiFont, color = TextSecondary, fontSize = if (UI_V2) 16.sp else 15.sp, shadow = glow()), modifier = Modifier.padding(bottom = 7.dp))
            }
            RawText(date, style = TextStyle(fontFamily = UiFont, color = TextSecondary, fontSize = if (UI_V2) 17.sp else 13.sp, shadow = glow()))
        }
        chips.forEach { c -> TopChip(c); Spacer(Modifier.width(8.dp)) }
        weather?.let { w ->
            TopCard {
                RawText("${w.tempC}°", style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Light, shadow = glow(8f)))
                Spacer(Modifier.width(8.dp))
                Column {
                    RawText(w.descriptionAr, style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 11.sp, shadow = glow(6f)))
                    RawText("دمشق", style = TextStyle(fontFamily = UiFont, color = Soft.copy(alpha = 0.85f), fontSize = 10.sp, shadow = glow(6f)))
                }
            }
        }
    }
}

/** The weather card's frame, shared with the automatic chips so they are exactly the same size and look. */
@Composable
private fun TopCard(content: @Composable () -> Unit) {
    Row(
        Modifier.glass(radius = 16.dp, dark = 0.36f).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** Friday / last-third chip: a big countdown (digits left-to-right) and two small lines, in the weather card's frame. */
@Composable
private fun TopChip(c: ChipInfo) {
    TopCard {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            RawText(c.big, style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Light, shadow = glow(8f)), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            RawText(c.line1, style = TextStyle(fontFamily = UiFont, color = Gold, fontSize = 11.sp, shadow = glow(6f)), maxLines = 1)
            RawText(c.line2, style = TextStyle(fontFamily = UiFont, color = Soft.copy(alpha = 0.85f), fontSize = 10.sp, shadow = glow(6f)), maxLines = 1)
        }
    }
}

/** Same glass card as the strip, but sized to its text. */
@Composable
private fun TextCard(modifier: Modifier, content: @Composable () -> Unit) {
    // The ayah and zikr cards share this: same padding, same radius, same one-line height (40 dp), text centred.
    Box(
        modifier.glass(radius = 16.dp, dark = 0.36f).heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) { content() }
}

/** Height of the azkar news-ticker bar. */
private val AZKAR_BAR_HEIGHT = 32.dp

/**
 * News-ticker azkar bar: full screen width, flush with the bottom edge, square, a dark smoke so it reads over any photo,
 * a light 1 dp line on top. The text measures itself so new text is picked exactly when a pass ends.
 */
@Composable
private fun AzkarBar(text: String, onPassEnd: () -> Unit, modifier: Modifier) {
    val speed = if (UI_V2) 40.dp else 60.dp
    var textPx by remember(text) { mutableStateOf(0) }
    var boxPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    LaunchedEffect(text, textPx, boxPx) {
        if (!UI_V2 || textPx <= 0 || boxPx <= 0) return@LaunchedEffect
        val perSec = with(density) { speed.toPx() }
        // One marquee cycle = text width + the gap the marquee leaves (a third of the box by default).
        val seconds = (textPx + boxPx / 3f) / perSec
        delay((seconds * 1000).toLong() + 1500L)
        onPassEnd()
    }
    Box(
        modifier.fillMaxWidth().height(AZKAR_BAR_HEIGHT)
            .background(Color(0xFF06130F).copy(alpha = 0.82f))
            .drawBehind { drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .onGloballyPositioned { boxPx = it.size.width },
        contentAlignment = Alignment.CenterStart
    ) {
        val styled = remember(text) {
            buildAnnotatedString {
                append(text)
                var k = text.indexOf('✦')
                while (k >= 0) { addStyle(SpanStyle(color = Color(0xFFF2D58B).copy(alpha = 0.7f)), k, k + 1); k = text.indexOf('✦', k + 1) }
            }
        }
        BasicText(
            styled, maxLines = 1, softWrap = false,
            onTextLayout = { textPx = it.size.width },
            style = TextStyle(fontFamily = UiFont, color = Color(0xFFFFF4D9), fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, shadow = glow(6f)),
            modifier = Modifier.fadeEnds(32.dp).padding(horizontal = 24.dp).basicMarquee(iterations = Int.MAX_VALUE, velocity = speed)
        )
    }
}

/** Fades the left and right [width] of the content to transparent so scrolling text never ends in a hard cut. */
private fun Modifier.fadeEnds(width: androidx.compose.ui.unit.Dp): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 2f)
        val f = w / size.width
        drawRect(
            brush = Brush.horizontalGradient(0f to Color.Transparent, f to Color.Black, 1f - f to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
        )
    }

/**
 * Rounded glass card of apps on the left. Shows up to 4 items; more scroll vertically inside the card.
 * The focused app's name is drawn here, outside the scroll area, so the scroll clip doesn't cut it off.
 */
@Composable
private fun AppRail(
    apps: List<AppItem>, onLaunch: (String) -> Unit, onAdd: () -> Unit, pickerOpen: Boolean,
    first: FocusRequester, onYouTubeFocus: (Boolean) -> Unit, onRightFromYouTube: () -> Boolean, modifier: Modifier
) {
    val addReq = remember { FocusRequester() }
    // Start on the first app (YouTube). Apps load asynchronously, so wait for them; only if the rail stays
    // empty fall back to "+".
    val hasApps = apps.isNotEmpty()
    LaunchedEffect(hasApps) {
        repeat(20) { i ->
            val ok = if (hasApps) runCatching { first.requestFocus() }.getOrDefault(false)
            else i >= 10 && runCatching { addReq.requestFocus() }.getOrDefault(false)
            if (ok) return@LaunchedEffect
            delay(100)
        }
    }
    var wasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pickerOpen) {
        if (!pickerOpen && wasOpen) {
            repeat(10) {
                if (runCatching { addReq.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                delay(50)
            }
        }
        wasOpen = pickerOpen
    }
    var outer by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var label by remember { mutableStateOf<String?>(null) }
    var labelY by remember { mutableStateOf(0f) }
    val onPos: (LayoutCoordinates) -> Unit = { c ->
        outer?.let { o -> if (o.isAttached && c.isAttached) labelY = o.localPositionOf(c, Offset.Zero).y + c.size.height / 2f }
    }
    // Laid out left-to-right: icon on the left, name to its right.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier.width(64.dp).onGloballyPositioned { outer = it }) {
            Box(Modifier.matchParentSize().glass(radius = 30.dp, dark = 0.30f))
            Column(
                Modifier.heightIn(max = 286.dp).clip(RoundedCornerShape(30.dp)).verticalScroll(rememberScrollState())
                    .padding(horizontal = 9.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                apps.forEachIndexed { i, app ->
                    RailItem(app.label, app.icon, if (i == 0) Modifier.focusRequester(first) else Modifier, enabled = !pickerOpen,
                        onFocus = { label = app.label; if (app.youtube) onYouTubeFocus(true) },
                        onBlur = { if (label == app.label) label = null; if (app.youtube) onYouTubeFocus(false) }, onPos = onPos,
                        onRight = if (app.youtube) onRightFromYouTube else null
                    ) { onLaunch(app.packageName) }
                }
                RailItem(ADD_LABEL, null, Modifier.focusRequester(addReq), enabled = !pickerOpen, onFocus = { label = ADD_LABEL }, onBlur = { if (label == ADD_LABEL) label = null }, onPos = onPos, onClick = onAdd)
            }
        }
    }
}

private const val ADD_LABEL = "إضافة تطبيق"

@Composable
private fun RailItem(
    label: String, icon: ImageBitmap?, modifier: Modifier, enabled: Boolean,
    onFocus: () -> Unit, onBlur: () -> Unit, onPos: (LayoutCoordinates) -> Unit, onRight: (() -> Boolean)? = null, onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() else onBlur() }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, tween(100), label = "scale")
    Box(
        modifier.size(46.dp)
            .onGloballyPositioned { if (focused) onPos(it) }
            .focusable(enabled = enabled, interactionSource = source)
            .onKeyEvent { e ->
                if (onRight != null && e.key == Key.DirectionRight && e.type == KeyEventType.KeyDown && onRight()) return@onKeyEvent true
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { onClick(); true } else ok
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.size(43.dp)
                .glass(radius = 14.dp, strength = if (focused) 2.6f else 1f, edge = if (focused) 1.7f else 0.7f, blur = false, dark = 0f),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Image(icon, label, Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)))
            else if (UI_V2) PlusIcon(Modifier.size(20.dp))
            else BasicText("+", style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Light, shadow = glow(8f)))
        }
    }
}

/** Full-screen chooser for which installed apps appear in the rail. Fixed apps are shown ticked and can't be changed. */
@Composable
private fun AppPicker(
    items: List<AppItem>, checked: Set<String>, onToggle: (String) -> Unit,
    widgetIds: List<String>, onToggleWidget: (String) -> Unit,
    bottomHidden: Set<String>, onToggleBottom: (String) -> Unit, onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val requester = remember { FocusRequester() }
    LaunchedEffect(items) {
        repeat(15) {
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(100)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)).padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(640.dp).fillMaxHeight().glass(radius = 30.dp, dark = 0.5f).padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            BasicText("التطبيقات والأدوات", style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium, shadow = glow(8f)))
            BasicText("زر الرجوع للإغلاق", style = TextStyle(fontFamily = UiFont, color = Soft.copy(alpha = 0.75f), fontSize = 13.sp, shadow = glow(6f)))
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.clip(RoundedCornerShape(18.dp)), contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Widgets first (few, and the newer feature), then the installed apps.
                // The count covers every row under this header: the grid widgets plus the two bottom cards (ayah, zikr).
                val shown = widgetIds.size + (BOTTOM_CARDS.size - bottomHidden.size)
                item(key = "h_widgets") { SectionHeader("الأدوات على الشاشة  ($shown من ${MAX_WIDGETS + BOTTOM_CARDS.size})") }
                itemsIndexed(WIDGETS, key = { _, w -> "w_" + w.id }) { i, w ->
                    val on = w.id in widgetIds
                    val full = !on && widgetIds.size >= MAX_WIDGETS
                    WidgetPickerRow(w, on, full, if (i == 0) Modifier.focusRequester(requester) else Modifier) { onToggleWidget(w.id) }
                }
                // The ayah and zikr cards: same kind of row, on by default, not counted in the 6-widget limit.
                itemsIndexed(BOTTOM_CARDS, key = { _, c -> "b_" + c.id }) { _, c ->
                    WidgetPickerRow(c, c.id !in bottomHidden, false, Modifier) { onToggleBottom(c.id) }
                }
                item(key = "h_apps") { SectionHeader("التطبيقات في القائمة") }
                itemsIndexed(items, key = { _, it -> it.packageName }) { _, app ->
                    PickerRow(app, app.locked || app.packageName in checked, Modifier) {
                        if (!app.locked) onToggle(app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(app: AppItem, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (focused) 0.22f else 0.06f))
            .focusable(interactionSource = source)
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { onClick(); true } else ok
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(app.icon, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(14.dp))
        BasicText(app.label, maxLines = 1, style = TextStyle(fontFamily = UiFont, color = if (app.locked) Soft.copy(alpha = 0.6f) else Color.White, fontSize = 18.sp), modifier = Modifier.weight(1f))
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp))
                .background(if (on) Gold.copy(alpha = if (app.locked) 0.45f else 0.95f) else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { if (on) { if (UI_V2) TickIcon(Modifier.size(18.dp)) else BasicText("✓", style = TextStyle(fontFamily = UiFont, color = Color(0xFF1B1B1B), fontSize = 18.sp, fontWeight = FontWeight.Bold)) } }
    }
}

/**
 * Medium glass window with one suggested YouTube video. Reached with Right from the YouTube tile; Up/Down switch
 * the suggestion, OK opens it in YouTube, Left/Back returns to the tile.
 */
@Composable
private fun YouTubePreview(videos: List<Video>, vm: HomeViewModel, requester: FocusRequester, onFocusChange: (Boolean) -> Unit, onLeave: () -> Unit) {
    var idx by remember { mutableIntStateOf(0) }
    val n = videos.size
    val i = idx.coerceIn(0, n - 1)
    val v = videos[i]
    var thumb by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(v.id) { thumb = null; thumb = vm.thumbnail(v.id) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChange(focused) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.width(272.dp).glass(radius = 22.dp, dark = 0.42f)
            .then(if (focused) Modifier.border(2.dp, Gold.copy(alpha = 0.95f), shape) else Modifier)
            .focusRequester(requester).focusable(interactionSource = source)
            .onKeyEvent { e ->
                when (e.key) {
                    Key.DirectionDown -> { if (e.type == KeyEventType.KeyDown) idx = (i + 1) % n; true }
                    Key.DirectionUp -> { if (e.type == KeyEventType.KeyDown) idx = (i - 1 + n) % n; true }
                    Key.DirectionLeft, Key.Back -> { if (e.type == KeyEventType.KeyUp || e.key == Key.DirectionLeft && e.type == KeyEventType.KeyDown) onLeave(); true }
                    Key.DirectionRight -> true
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { if (e.type == KeyEventType.KeyUp) vm.openVideo(v.id); true }
                    else -> false
                }
            }
            .padding(9.dp)
    ) {
        val img = thumb
        if (img != null) {
            Image(img, v.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(15.dp)).background(Color.White.copy(alpha = 0.10f)))
        }
        // Arabic text: right-to-left inside this left-to-right region.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(top = 8.dp, bottom = 2.dp)) {
                BasicText(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, shadow = glow(8f)))
                Spacer(Modifier.height(3.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(v.channel, maxLines = 1, style = TextStyle(fontFamily = UiFont, color = Gold, fontSize = 12.sp, shadow = glow(6f)), modifier = Modifier.weight(1f))
                    if (focused && UI_V2) { UpDownIcon(Modifier.size(width = 9.dp, height = 14.dp)); Spacer(Modifier.width(8.dp)) }
                    BasicText(if (focused && !UI_V2) "\u2066${i + 1} / $n\u2069   ▲ ▼" else "\u2066${i + 1} / $n\u2069", style = TextStyle(fontFamily = UiFont, color = if (UI_V2) TextSecondary else Soft.copy(alpha = 0.8f), fontSize = 11.sp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    BasicText(
        text, style = TextStyle(fontFamily = UiFont, color = Gold, fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = glow(6f)),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp, start = 4.dp)
    )
}

/** A widget row: letter badge, title + one-line description, tick. Greyed out (still focusable) when the 4 slots are taken. */
@Composable
private fun WidgetPickerRow(w: WidgetDef, on: Boolean, full: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = if (focused) 0.22f else 0.06f))
            .focusable(interactionSource = source)
            .onKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (ok && e.type == KeyEventType.KeyUp) { onClick(); true } else ok
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = if (full) 0.18f else 0.35f)), contentAlignment = Alignment.Center) {
            BasicText(w.glyph, style = TextStyle(fontFamily = UiFont, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(w.title, maxLines = 1, style = TextStyle(fontFamily = UiFont, color = if (full) Soft.copy(alpha = 0.5f) else Color.White, fontSize = 18.sp))
            BasicText(if (full) "الحد الأقصى $MAX_WIDGETS أدوات، أزل واحدة أولاً" else w.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = UiFont, color = Soft.copy(alpha = if (full) 0.5f else 0.7f), fontSize = 12.sp))
        }
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (on) Gold.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) { if (on) { if (UI_V2) TickIcon(Modifier.size(18.dp)) else BasicText("✓", style = TextStyle(fontFamily = UiFont, color = Color(0xFF1B1B1B), fontSize = 18.sp, fontWeight = FontWeight.Bold)) } }
    }
}


/** Drawn icons (the UI fonts have no "+", tick or arrow glyphs worth relying on, so they are shapes). */
@Composable
private fun PlusIcon(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val sw = size.minDimension * 0.10f; val c = center; val r = size.minDimension / 2f
        drawLine(Color.White, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun TickIcon(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height; val sw = w * 0.14f; val ink = Color(0xFF1B1B1B)
        drawLine(ink, Offset(w * 0.16f, h * 0.54f), Offset(w * 0.42f, h * 0.78f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(ink, Offset(w * 0.42f, h * 0.78f), Offset(w * 0.86f, h * 0.24f), strokeWidth = sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** Small up/down arrows for the YouTube preview hint. */
@Composable
private fun UpDownIcon(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height; val c = Soft.copy(alpha = 0.9f)
        val up = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.5f, 0f); lineTo(w, h * 0.42f); lineTo(0f, h * 0.42f); close() }
        val dn = androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.5f, h); lineTo(w, h * 0.58f); lineTo(0f, h * 0.58f); close() }
        drawPath(up, c); drawPath(dn, c)
    }
}


/**
 * Amiri Quran's own line is 2.45 em tall (a very tall ascent and descent for its stacked Quranic marks), which makes a
 * one-line verse card much taller than the zikr card. Compose ignores a smaller lineHeight for a single line when the
 * paragraph is trimmed, so the height is capped here instead: the text block is [lineDp] per line and the glyphs are
 * centred on their baseline (the ink sits about 0.29 em above it), so nothing is cut off and the space above and below
 * the verse is equal. Extra lines add one [lineDp] each.
 */
private fun Modifier.capLineBox(lineDp: androidx.compose.ui.unit.Dp, fontSp: Float): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    val line = lineDp.roundToPx()
    val natural = p.height
    val base = p[androidx.compose.ui.layout.FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: (natural * 0.74f).roundToInt()
    // number of lines from the natural height: the font's line is about 2.45 em
    val fontPx = fontSp.sp.roundToPx()
    val perLine = (fontPx * 2.449f).roundToInt().coerceAtLeast(1)
    val lines = ((natural + perLine / 2) / perLine).coerceAtLeast(1)
    val h = lines * line
    val ink = (0.29f * fontPx).roundToInt()
    val y = (line / 2f + ink - base).roundToInt()
    layout(p.width, h) { p.place(0, y) }
}
