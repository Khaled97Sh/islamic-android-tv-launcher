package com.khaled.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppItem(val label: String, val packageName: String, val icon: ImageBitmap, val locked: Boolean = false, val youtube: Boolean = false)

/** The launcher shows exactly these apps, in this order (skipping any that aren't installed). */
class AppRepository(context: Context) {
    private val app = context.applicationContext

    private class Entry(val pkg: String, val label: String)

    private val prefs = app.getSharedPreferences("rail", Context.MODE_PRIVATE)

    // Fixed apps: YouTube then Pro TV always first; Settings and the stock-home icon always last. None can be removed.
    private val top get() = listOf(Entry(youtubePkg(), "YouTube"), Entry(PROTV, "Pro TV"))

    /** The YouTube TV app if installed, else the regular YouTube app (other devices). */
    private fun youtubePkg(): String =
        YOUTUBE_PKGS.firstOrNull { runCatching { app.packageManager.getApplicationInfo(it, 0) }.isSuccess } ?: YOUTUBE_PKGS.first()
    private val bottom = listOf(Entry("com.android.tv.settings", "الإعدادات"), Entry(STOCK, "الرئيسية الأصلية"))
    private val lockedPkgs get() = (top + bottom).map { it.pkg }.toSet()

    /** Packages the user added with "+", in the order they were added. */
    fun extras(): List<String> = prefs.getString("extra", "")!!.split(",").filter { it.isNotEmpty() }
    fun setExtras(list: List<String>) = prefs.edit().putString("extra", list.joinToString(",")).apply()

    private fun item(pkg: String, label: String?, locked: Boolean): AppItem? {
        val pm = app.packageManager
        return try {
            val icon = if (pkg == STOCK) houseIcon() else pm.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX)
            val name = label ?: pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            AppItem(name, pkg, icon.asImageBitmap(), locked, youtube = pkg in YOUTUBE_PKGS)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    /** What the rail shows: fixed top apps, the user's picks, fixed bottom apps. Blocking, run on IO. */
    fun load(): List<AppItem> =
        top.mapNotNull { item(it.pkg, it.label, true) } +
            extras().filter { it !in lockedPkgs }.mapNotNull { item(it, null, false) } +
            bottom.mapNotNull { item(it.pkg, it.label, true) }

    /** Every app that can be launched on the TV, for the picker (fixed ones last). Blocking, run on IO. */
    fun installed(): List<AppItem> {
        val pm = app.packageManager
        val pkgs = linkedSetOf<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach { pkgs += it.activityInfo.packageName }
        }
        pkgs.remove(app.packageName)
        val others = pkgs.filter { it !in lockedPkgs }.mapNotNull { item(it, null, false) }
            .sortedBy { it.label.lowercase() }
        // Fixed (unchangeable) apps go last.
        return others + top.mapNotNull { item(it.pkg, it.label, true) } + bottom.mapNotNull { item(it.pkg, it.label, true) }
    }

    /**
     * Opens a video straight in the YouTube app. A plain VIEW link is ignored by a YouTube app that is already
     * running (it just shows its home screen), but the Cast "LAUNCH" action it also handles always plays the video,
     * running or not. VIEW stays as the fallback.
     */
    fun openVideo(id: String) {
        val url = Uri.parse("https://www.youtube.com/watch?v=$id")
        val pkg = youtubePkg()
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            app.startActivity(Intent("com.google.android.gms.cast.tv.action.LAUNCH", url).setPackage(pkg).addFlags(flags))
        } catch (_: Exception) {
            try { app.startActivity(Intent(Intent.ACTION_VIEW, url).setPackage(pkg).addFlags(flags)) }
            catch (_: Exception) { runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(flags)) } }
        }
    }

    fun launch(packageName: String) {
        val ok = runCatching {
            if (packageName == STOCK) { openStockHome(); return@runCatching }
            val pm = app.packageManager
            val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
                ?: pm.getLaunchIntentForPackage(packageName)
                ?: if (packageName == "com.android.tv.settings") Intent(Settings.ACTION_SETTINGS) else error("no launch intent")
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { android.util.Log.w("TvLauncher", "launch $packageName failed: $it") }.isSuccess
        if (!ok) android.widget.Toast.makeText(app, "تعذّر فتح التطبيق", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * The stock Google TV home is switched off so that the Home button opens this launcher, and a switched-off
     * app can't be started. So: try to start it, and if that fails open its app-info page (where it can be enabled).
     */
    private fun openStockHome() {
        try {
            app.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(ComponentName(STOCK, STOCK_HOME)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // Switched off (the normal case): open its app-info page. If even that fails the caller shows a toast.
            app.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$STOCK")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** A simple white house outline on a transparent background. */
    private fun houseIcon(): Bitmap {
        val size = ICON_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = size * 0.07f
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        }
        val f = size.toFloat()
        val path = Path().apply {
            moveTo(f * 0.14f, f * 0.50f); lineTo(f * 0.50f, f * 0.18f); lineTo(f * 0.86f, f * 0.50f)   // roof
            moveTo(f * 0.24f, f * 0.44f); lineTo(f * 0.24f, f * 0.82f); lineTo(f * 0.76f, f * 0.82f); lineTo(f * 0.76f, f * 0.44f) // walls
            moveTo(f * 0.43f, f * 0.82f); lineTo(f * 0.43f, f * 0.58f); lineTo(f * 0.57f, f * 0.58f); lineTo(f * 0.57f, f * 0.82f) // door
        }
        c.drawPath(path, p)
        return bmp
    }

    private companion object {
        const val ICON_PX = 160
        val YOUTUBE_PKGS = listOf("com.google.android.youtube.tv", "com.google.android.youtube")
        const val PROTV = "com.prosoft.tv.launcher"
        const val STOCK = "com.google.android.apps.tv.launcherx"
        const val STOCK_HOME = "com.google.android.apps.tv.launcherx.home.HomeActivity"
    }
}
