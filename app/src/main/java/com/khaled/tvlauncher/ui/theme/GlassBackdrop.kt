package com.khaled.tvlauncher.ui.theme

import android.graphics.Bitmap

/**
 * The newest pre-blurred background photo, shared with things that are drawn outside Compose (the azan reminder card is a
 * plain window added by a Service). Tiny (192x108), so keeping one reference costs nothing. Null until the first photo loads.
 */
object GlassBackdrop {
    @Volatile var blurred: Bitmap? = null
}
