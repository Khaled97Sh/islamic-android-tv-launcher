package com.khaled.tvlauncher.azan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.widget.LinearLayout
import com.khaled.tvlauncher.ui.theme.GlassBackdrop
import com.khaled.tvlauncher.ui.theme.adaptiveDark

/**
 * The azan reminder as liquid glass, drawn with the same layers as the in-app cards (`Modifier.glass`): the slice of the
 * pre-blurred photo that lies behind the card, a smoke tint (darker over bright photos), a white veil, a diagonal sheen
 * and a light-catching gradient edge. Real window blur is switched off by this TV's compositor, so the frost is the photo
 * slice, exactly like the cards on the home screen. [leftPx] / [topPx] say where the card sits on the screen so the right
 * part of the photo is used.
 */
class AzanGlassCard(context: Context, private val screenW: Int, private val screenH: Int, private val topPx: Int, private val radiusPx: Float, private val strokePx: Float) :
    LinearLayout(context) {

    private val photo = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint()
    private val sheen = Paint()
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = strokePx }
    private val shape = Path()
    private val box = RectF()
    private var smoke = 0.40f
    private var lastW = -1
    private var lastH = -1

    init { setWillNotDraw(false) }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return super.dispatchDraw(canvas)
        if (width != lastW || height != lastH) rebuild(w, h)
        canvas.save()
        canvas.clipPath(shape)
        val bmp = GlassBackdrop.blurred
        if (bmp != null && !bmp.isRecycled) {
            val left = ((screenW - w) / 2f).coerceAtLeast(0f)
            val sx = bmp.width.toFloat() / screenW; val sy = bmp.height.toFloat() / screenH
            val src = sliceOf(bmp, left * sx, topPx * sy, w * sx, h * sy)
            canvas.drawBitmap(bmp, src, RectF(0f, 0f, w, h), photo)
        } else {
            canvas.drawColor(0xFF1B2623.toInt())          // no photo yet (just started): a dark frosted tone instead
        }
        fill.color = Color.argb((smoke * 255).toInt(), 0, 0, 0); canvas.drawRect(0f, 0f, w, h, fill)
        fill.color = Color.argb((0.07f * 255).toInt(), 255, 255, 255); canvas.drawRect(0f, 0f, w, h, fill)
        canvas.drawRect(0f, 0f, w, h, sheen)
        canvas.restore()
        super.dispatchDraw(canvas)                        // the text
        canvas.drawPath(shape, edge)
    }

    private fun rebuild(w: Float, h: Float) {
        lastW = width; lastH = height
        val half = strokePx / 2f
        box.set(half, half, w - half, h - half)
        shape.reset(); shape.addRoundRect(box, radiusPx, radiusPx, Path.Direction.CW)
        sheen.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0x29FFFFFF, 0x00FFFFFF, 0x0AFFFFFF), null, Shader.TileMode.CLAMP)
        edge.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0x99FFFFFF.toInt(), 0x0FFFFFFF, 0x47FFFFFF), null, Shader.TileMode.CLAMP)
        val bmp = GlassBackdrop.blurred
        smoke = if (bmp != null && !bmp.isRecycled) {
            val left = ((screenW - w) / 2f).coerceAtLeast(0f)
            val sx = bmp.width.toFloat() / screenW; val sy = bmp.height.toFloat() / screenH
            adaptiveDark(0.42f, meanLuminance(bmp, sliceOf(bmp, left * sx, topPx * sy, w * sx, h * sy)))
        } else 0.42f
    }

    private fun sliceOf(bmp: Bitmap, x: Float, y: Float, w: Float, h: Float): Rect {
        val x0 = x.toInt().coerceIn(0, bmp.width - 1); val y0 = y.toInt().coerceIn(0, bmp.height - 1)
        return Rect(x0, y0, (x + w).toInt().coerceIn(x0 + 1, bmp.width), (y + h).toInt().coerceIn(y0 + 1, bmp.height))
    }

    /** Mean relative luminance (0..1) of a part of the blurred photo, same idea as the in-app cards. */
    private fun meanLuminance(bmp: Bitmap, r: Rect): Float {
        var sum = 0f; var n = 0
        for (y in r.top until r.bottom) for (x in r.left until r.right) {
            val p = bmp.getPixel(x, y)
            fun lin(c: Int): Float { val v = c / 255f; return if (v <= 0.04045f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
            sum += 0.2126f * lin((p shr 16) and 0xFF) + 0.7152f * lin((p shr 8) and 0xFF) + 0.0722f * lin(p and 0xFF); n++
        }
        return if (n == 0) 0f else sum / n
    }
}
