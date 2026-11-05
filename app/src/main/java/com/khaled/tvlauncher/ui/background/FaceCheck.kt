package com.khaled.tvlauncher.ui.background

import android.graphics.Bitmap
import android.media.FaceDetector

/** Last rule: a downloaded photo with a detectable face is thrown away ("no people in the photos"). */
object FaceCheck {
    /** Checked on a small copy; the platform detector needs RGB_565 and an even width. */
    fun hasFace(src: Bitmap): Boolean {
        val w = 640; val h = (src.height * 640L / src.width).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val rgb = small.copy(Bitmap.Config.RGB_565, false)
        if (small !== src) small.recycle()
        return try {
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val found = FaceDetector(rgb.width, rgb.height, MAX_FACES).findFaces(rgb, faces)
            (0 until found).any { (faces[it]?.confidence() ?: 0f) >= MIN_CONFIDENCE }
        } catch (_: Exception) { false } finally { rgb.recycle() }
    }
    private const val MAX_FACES = 4
    private const val MIN_CONFIDENCE = 0.4f
}
