package com.khaled.tvlauncher

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * What glass panels need to fake "backdrop blur": the blurred photo and the full-screen size it spans. While the photo
 * changes, [previous] is still drawn underneath and [mix] (0..1, read lazily during drawing) fades the new one in over
 * the same 1.6 s as the photo itself, so a panel never changes backdrop before the picture behind it has.
 */
class GlassEnv(val current: BackgroundImage?, val screen: IntSize, val previous: BackgroundImage? = null, val mix: () -> Float = { 1f }) {
    val blurred: ImageBitmap? get() = current?.blurred
}

val LocalGlass = compositionLocalOf { GlassEnv(null, IntSize.Zero) }

/**
 * Darker smoke where the photo behind a panel is bright, so light text keeps its contrast. [lum] is the mean relative
 * luminance (0..1) of the blurred photo under the panel: dark photos (about 0.1) keep the panel's own tint, a pale one
 * (about 0.5) gets about +0.14, and the tint never goes above 0.62.
 */
fun adaptiveDark(base: Float, lum: Float): Float {
    if (base <= 0f) return base
    val extra = ((lum - 0.35f) * 0.9f).coerceIn(0f, 0.26f)
    return (base + extra).coerceAtMost(maxOf(base, 0.62f))
}

/** Mean luminance of the part of [img] that lies under a panel at [origin] with [size] (screen pixels). */
private fun panelLum(img: BackgroundImage, origin: Offset, size: IntSize, screen: IntSize): Float {
    if (img.lum.isEmpty() || screen.width <= 0 || screen.height <= 0 || size.width <= 0) return 0f
    val sx = img.lumW.toFloat() / screen.width; val sy = img.lumH.toFloat() / screen.height
    val x0 = (origin.x * sx).toInt().coerceIn(0, img.lumW - 1); val y0 = (origin.y * sy).toInt().coerceIn(0, img.lumH - 1)
    val x1 = ((origin.x + size.width) * sx).toInt().coerceIn(x0 + 1, img.lumW); val y1 = ((origin.y + size.height) * sy).toInt().coerceIn(y0 + 1, img.lumH)
    var sum = 0f; var n = 0
    for (y in y0 until y1) for (x in x0 until x1) { sum += img.lum[y * img.lumW + x]; n++ }
    return if (n == 0) 0f else sum / n
}

/**
 * Liquid-glass panel. Draws only the part of the pre-blurred photo that sits behind this panel
 * (so the cost is the panel's own size, not a full-screen image), then a dark smoked tint so white
 * text stays readable on any photo, a faint white veil, a diagonal sheen and a light-catching edge.
 *
 * [blur] = false skips the photo slice (used for the small dock bubbles, which sit on a glass bar that
 * already carries it); [dark] is how much smoke to add.
 */
fun Modifier.glass(
    radius: Dp = 28.dp,
    strength: Float = 1f,
    edge: Float = 1f,
    blur: Boolean = true,
    dark: Float = 0.30f,
): Modifier = composed {
    val env = LocalGlass.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    // Mean brightness under this panel, for the current and the previous photo: measured once per photo / position / size, never per frame.
    val lumCur = remember(env.current, origin, panelSize) { env.current?.let { panelLum(it, origin, panelSize, env.screen) } ?: 0f }
    val lumPrev = remember(env.previous, origin, panelSize) { env.previous?.let { panelLum(it, origin, panelSize, env.screen) } ?: lumCur }
    val shape = RoundedCornerShape(radius)
    onGloballyPositioned { origin = it.positionInRoot(); panelSize = it.size }
        .clip(shape)
        .drawBehind {
            val mix = if (UI_V2) env.mix().coerceIn(0f, 1f) else 1f
            fun slice(bmp: ImageBitmap, alpha: Float) {
                val sx = bmp.width.toFloat() / env.screen.width
                val sy = bmp.height.toFloat() / env.screen.height
                val x = (origin.x * sx).roundToInt().coerceIn(0, bmp.width - 1)
                val y = (origin.y * sy).roundToInt().coerceIn(0, bmp.height - 1)
                val w = (size.width * sx).roundToInt().coerceIn(1, bmp.width - x)
                val h = (size.height * sy).roundToInt().coerceIn(1, bmp.height - y)
                drawImage(
                    bmp, alpha = alpha,
                    srcOffset = IntOffset(x, y), srcSize = IntSize(w, h),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    filterQuality = FilterQuality.Low
                )
            }
            val prev = env.previous?.blurred
            if (blur && env.screen.width > 0) {
                // The old photo stays underneath until the new one has faded in.
                if (UI_V2 && prev != null && mix < 1f) slice(prev, 1f)
                env.blurred?.let { slice(it, if (UI_V2 && prev != null) mix else 1f) }
            }
            val smoke = if (UI_V2 && blur) adaptiveDark(dark, lumPrev + (lumCur - lumPrev) * mix) else dark
            if (smoke > 0f) drawRect(Color.Black.copy(alpha = smoke))
            drawRect(Color.White.copy(alpha = 0.07f * strength))
            drawRect(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f * strength), Color.Transparent, Color.White.copy(alpha = 0.04f * strength)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
        }
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.60f * edge), Color.White.copy(alpha = 0.06f * edge), Color.White.copy(alpha = 0.28f * edge))),
            shape
        )
}
