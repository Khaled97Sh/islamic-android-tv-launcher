package com.khaled.tvlauncher.prayer

import android.content.Context
import com.khaled.tvlauncher.time.TrustedClock
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/** What a sync attempt ended with, so the caller can pick the right wait before the next attempt. */
sealed class SyncResult(val message: String) {
    /** New, checked data was stored. */
    class Updated(message: String) : SyncResult(message)
    /** The network failed (offline, timeout, HTTP error): worth trying again soon. */
    class NetworkFailure(message: String) : SyncResult(message)
    /** A page arrived but is not usable (changed format, rejected as implausible): trying again soon would not help. */
    class Rejected(message: String) : SyncResult(message)
}

/**
 * Keeps the prayer timetable correct: the checked copy shipped in the app is the offline default, and every
 * [REFRESH_DAYS] days the Mawaqit page of the Umayyad Mosque is downloaded again. A download only replaces the stored
 * timetable after it has passed [TimetableCheck]; on any failure the previous data stays in use.
 */
object MawaqitSync {
    const val URL_PAGE = "https://mawaqit.net/ar/m/ljm-lmwy-dmshq-dmshq-00000-syria"
    const val REFRESH_DAYS = 10L
    /** Wait before retrying after a network failure / after a rejected or unreadable page. */
    const val RETRY_NETWORK_MS = 10L * 60_000L
    const val RETRY_REJECTED_MS = 6L * 60 * 60_000L
    private const val MAX_BYTES = 2_000_000
    private const val PREFS = "mawaqit"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Where the page comes from; replaced in tests. */
    @Volatile var downloader: () -> String = ::download

    @Volatile private var live: Map<String, DayTimes>? = null

    /** The timetable currently in use: downloaded data if there is any, else the app's own checked copy. */
    fun days(context: Context): Map<String, DayTimes> {
        live?.let { return it }
        return (load(context) ?: MosqueCalendar.bundled).also { live = it }
    }

    fun lastSyncMillis(context: Context): Long = prefs(context).getLong("at", 0L)
    /** The outcome of the latest attempt (also shown in the log), e.g. "ok, 366 days, repaired 11: ...". */
    fun lastResult(context: Context): String = prefs(context).getString("last", "never") ?: "never"

    fun isDue(context: Context, now: Long = TrustedClock.now()): Boolean =
        lastSyncMillis(context).let { at -> at > now || now - at >= REFRESH_DAYS * DAY_MS }

    /** Download, check and store. Blocking, run on an IO thread. */
    fun refresh(context: Context): SyncResult {
        val html = try { downloader() } catch (e: Exception) { return remember(context, SyncResult.NetworkFailure("download failed: ${e.javaClass.simpleName}")) }
        val parsed = MawaqitParser.parse(html) ?: return remember(context, SyncResult.Rejected("unexpected page format, kept old data"))
        val checked = TimetableCheck.check(parsed)
        if (!checked.ok) return remember(context, SyncResult.Rejected("rejected (${checked.reason}), kept old data"))
        store(context, checked.days)
        live = checked.days
        val note = if (checked.repairs.isEmpty()) "no repairs" else "repaired ${checked.repairs.size}: ${checked.repairs.joinToString("; ")}"
        return remember(context, SyncResult.Updated("ok, 366 days, $note"))
    }

    /** How long to wait before the loop looks at the sync again. */
    fun waitAfter(result: SyncResult?): Long = when (result) {
        is SyncResult.NetworkFailure -> RETRY_NETWORK_MS
        is SyncResult.Rejected -> RETRY_REJECTED_MS
        else -> 6L * 60 * 60_000L
    }

    private fun remember(c: Context, r: SyncResult): SyncResult {
        prefs(c).edit().putString("last", r.message.take(400)).apply()
        return r
    }

    private fun download(): String {
        val conn = URL(URL_PAGE).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000; conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) tcl-tv-launcher")
        try {
            require(conn.responseCode == 200) { "http ${conn.responseCode}" }
            require(conn.url.protocol == "https") { "not https" }
            val buf = java.io.ByteArrayOutputStream()
            conn.inputStream.use { s -> val b = ByteArray(8192); while (true) { val r = s.read(b); if (r < 0) break; buf.write(b, 0, r); require(buf.size() <= MAX_BYTES) { "too large" } } }
            return buf.toString("UTF-8")
        } finally { conn.disconnect() }
    }

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun store(c: Context, days: Map<String, DayTimes>) {
        val o = JSONObject(); for ((k, v) in days) o.put(k, JSONArray(v.m.toList()))
        prefs(c).edit().putString("table", o.toString()).putLong("at", TrustedClock.now()).apply()
    }

    private fun load(c: Context): Map<String, DayTimes>? = runCatching {
        val o = JSONObject(prefs(c).getString("table", null) ?: return null)
        val map = o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> DayTimes(IntArray(6) { a.getInt(it) }) } }
        TimetableCheck.check(map).takeIf { it.ok }?.days   // never trust stored data blindly either
    }.getOrNull()

    /** For tests: forget the in-memory copy so the next [days] reads storage again. */
    internal fun resetForTest() { live = null }
}
