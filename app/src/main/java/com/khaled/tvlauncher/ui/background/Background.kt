package com.khaled.tvlauncher.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** A background photo plus a tiny pre-blurred copy used as the "frosted" layer behind glass panels. */
class BackgroundImage(val full: ImageBitmap, val blurred: ImageBitmap, val lum: FloatArray = FloatArray(0), val lumW: Int = 0, val lumH: Int = 0)

/**
 * Photos live in assets/bg as 3840x2160 JPEGs (4K is the most this TV's panel can show) and are
 * decoded at half size (1920x1080 = the resolution the UI is laid out for), so each one costs ~8 MB.
 */
class BackgroundLoader(private val context: Context) {
    private val names: List<String> = (context.assets.list("bg") ?: emptyArray())
        .filter { it.endsWith(".jpg") }.map { it.removeSuffix(".jpg") }.sorted()
    val count get() = names.size

    /** A bundled photo. Blocking, run on an IO dispatcher. */
    fun load(index: Int): BackgroundImage? {
        if (names.isEmpty()) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bmp = runCatching {
            context.assets.open("bg/${names[index % names.size]}.jpg").use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull() ?: return null
        return process(bmp)
    }

    /** A downloaded photo (any size >= 4K, any aspect): decoded near 1920 px wide and centre-cropped to 16:9. Blocking. */
    fun load(file: java.io.File): BackgroundImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth) }
        val raw = runCatching { BitmapFactory.decodeFile(file.path, opts) }.getOrNull() ?: return null
        val r = cropTo16by9(raw.width, raw.height)
        val bmp = if (r.width == raw.width && r.height == raw.height) raw
        else Bitmap.createBitmap(raw, r.left, r.top, r.width, r.height).also { raw.recycle() }
        return process(bmp)
    }

    private fun process(bmp: Bitmap): BackgroundImage {
        val small = Bitmap.createScaledBitmap(bmp, BLUR_W, BLUR_H, true)
        val blurred = boxBlur(small, radius = 4, passes = 3)
        val px = IntArray(BLUR_W * BLUR_H).also { blurred.getPixels(it, 0, BLUR_W, 0, 0, BLUR_W, BLUR_H) }
        val lum = FloatArray(px.size) { i ->
            val p = px[i]
            0.2126f * LIN[(p shr 16) and 0xFF] + 0.7152f * LIN[(p shr 8) and 0xFF] + 0.0722f * LIN[p and 0xFF]
        }
        return BackgroundImage(bmp.asImageBitmap(), blurred.asImageBitmap(), lum, BLUR_W, BLUR_H)
    }

    private fun boxBlur(src: Bitmap, radius: Int, passes: Int): Bitmap {
        val w = src.width
        val h = src.height
        var px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
        var tmp = IntArray(w * h)
        repeat(passes) {
            blurLine(px, tmp, w, h, radius, horizontal = true)
            blurLine(tmp, px, w, h, radius, horizontal = false)
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) src[line * w + j] else src[j * w + line]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                val out = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
            }
        }
    }

    private companion object {
        const val BLUR_W = 192
        const val BLUR_H = 108
        /** sRGB byte -> linear light, so the luminance is a real "relative luminance" (0 = black, 1 = white). */
        val LIN = FloatArray(256) { v -> val c = v / 255f; if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat() }
    }
}

/** Largest power-of-two sample size that keeps the decoded width at or above 1920 px (the UI's layout resolution). */
fun sampleFor(width: Int): Int { var s = 1; while (width / (s * 2) >= 1920) s *= 2; return s }

/** A crop window in pixels. */
data class Crop(val left: Int, val top: Int, val width: Int, val height: Int)

/** The centred 16:9 window inside a [w] x [h] image (the whole image when it already is 16:9). */
fun cropTo16by9(w: Int, h: Int): Crop {
    val targetH = (w * 9L / 16).toInt()
    return if (targetH <= h) Crop(0, (h - targetH) / 2, w, targetH)
    else { val targetW = (h * 16L / 9).toInt(); Crop((w - targetW) / 2, 0, targetW, h) }
}
