package com.khaled.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
import kotlin.math.abs

/** One day of the mosque timetable: fajr, shuruq (sunrise), dhuhr, asr, maghrib, isha, in minutes after midnight. */
class DayTimes(val m: IntArray) {
    init { require(m.size == 6) }
    fun time(i: Int): LocalTime = LocalTime.of(m[i] / 60, m[i] % 60)
    override fun equals(other: Any?) = other is DayTimes && m.contentEquals(other.m)
    override fun hashCode() = m.contentHashCode()
}

/** The outcome of checking a timetable: the usable data plus a record of everything that was repaired. */
class Checked(val days: Map<String, DayTimes>, val repairs: List<String>, val ok: Boolean, val reason: String?)

/**
 * Checks a full-year timetable before it is trusted. Mawaqit publishes whatever the mosque types in, and the mosque's
 * own table has had typos (e.g. an Asr of 14:15 instead of 16:15). A cell is wrong when it is outside the plausible
 * range for that prayer or far from the median of its 30 neighbouring days (this also catches runs of bad days);
 * a wrong cell is rebuilt by interpolating the nearest good days on both sides. If too many cells are wrong the
 * whole table is rejected (format change / garbage) so good data is never replaced by bad.
 */
object TimetableCheck {
    private val NAMES = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    /** Plausible Damascus ranges in minutes (fajr 03:00-06:30 ... isha 17:30-22:00). Wide on purpose: a sanity net, not a model. */
    private val RANGE = listOf(180..390, 270..480, 690..780, 870..1020, 990..1230, 1050..1320)
    private const val WINDOW = 15
    private const val MAX_DEVIATION = 8        // minutes from the neighbours' median
    private const val MAX_BAD_FRACTION = 0.02  // more than 2% of cells wrong => reject the whole table
    private const val MAX_DAY_STEP = 4         // after repair no prayer may move more than this between two days

    fun key(month: Int, day: Int) = "$month-$day"

    /** Every day of a leap year, in calendar order. */
    private val KEYS: List<String> = run {
        val len = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        (1..12).flatMap { m -> (1..len[m - 1]).map { d -> key(m, d) } }
    }

    fun check(input: Map<String, DayTimes>): Checked {
        if (KEYS.any { it !in input }) return Checked(emptyMap(), emptyList(), false, "missing days")
        val n = KEYS.size
        val cols = Array(6) { c -> IntArray(n) { input.getValue(KEYS[it]).m[c] } }
        val bad = Array(6) { BooleanArray(n) }
        var badCount = 0
        for (c in 0 until 6) for (i in 0 until n) {
            val v = cols[c][i]
            val win = IntArray(2 * WINDOW) { k -> cols[c][Math.floorMod(i + (k - WINDOW).let { if (it >= 0) it + 1 else it }, n)] }.sorted()
            val median = (win[win.size / 2 - 1] + win[win.size / 2]) / 2.0
            if (v !in RANGE[c] || abs(v - median) > MAX_DEVIATION) { bad[c][i] = true; badCount++ }
        }
        if (badCount > n * 6 * MAX_BAD_FRACTION) return Checked(emptyMap(), emptyList(), false, "$badCount wrong cells")

        val fixed = Array(6) { cols[it].copyOf() }
        val repairs = mutableListOf<String>()
        for (c in 0 until 6) for (i in 0 until n) if (bad[c][i]) {
            var p = 1; while (bad[c][Math.floorMod(i - p, n)] && p < n) p++
            var q = 1; while (bad[c][Math.floorMod(i + q, n)] && q < n) q++
            val a = cols[c][Math.floorMod(i - p, n)]; val b = cols[c][Math.floorMod(i + q, n)]
            fixed[c][i] = Math.floor(a + (b - a) * p.toDouble() / (p + q) + 0.5).toInt()
            repairs += "${KEYS[i]} ${NAMES[c]} ${fmt(cols[c][i])} -> ${fmt(fixed[c][i])}"
        }
        for (c in 0 until 6) for (i in 0 until n) if (abs(fixed[c][i] - fixed[c][(i + 1) % n]) > MAX_DAY_STEP) {
            return Checked(emptyMap(), emptyList(), false, "unstable ${NAMES[c]} at ${KEYS[i]}")
        }
        for (i in 0 until n) for (c in 0 until 5) if (fixed[c][i] >= fixed[c + 1][i]) {
            return Checked(emptyMap(), emptyList(), false, "order at ${KEYS[i]}")
        }
        return Checked(KEYS.indices.associate { i -> KEYS[i] to DayTimes(IntArray(6) { c -> fixed[c][i] }) }, repairs, true, null)
    }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}

/** Reads the Mawaqit page of the Umayyad Mosque. Pure parsing, no Android, so it is unit-tested with a saved page. */
object MawaqitParser {
    /** The `confData` object embedded in the page, or null. */
    fun confData(html: String): JSONObject? {
        val i = html.indexOf("confData")
        if (i < 0) return null
        val start = html.indexOf('{', i)
        if (start < 0) return null
        var depth = 0; var inStr = false; var esc = false
        for (j in start until html.length) {
            val ch = html[j]
            if (inStr) { if (esc) esc = false else if (ch == '\\') esc = true else if (ch == '"') inStr = false; continue }
            when (ch) {
                '"' -> inStr = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return runCatching { JSONObject(html.substring(start, j + 1)) }.getOrNull() }
            }
        }
        return null
    }

    /** Parse the 12-month `calendar`; null when anything about its shape is unexpected. */
    fun calendar(conf: JSONObject): Map<String, DayTimes>? = runCatching {
        val cal = conf.getJSONArray("calendar")
        if (cal.length() != 12) return null
        val out = HashMap<String, DayTimes>()
        for (mi in 0 until 12) {
            val month = cal.getJSONObject(mi)
            for (day in month.keys()) {
                val arr = month.getJSONArray(day)
                if (arr.length() != 6) return null
                out[TimetableCheck.key(mi + 1, day.toInt())] = DayTimes(IntArray(6) { c ->
                    val t = arr.getString(c); require(Regex("\\d\\d:\\d\\d").matches(t)); t.substring(0, 2).toInt() * 60 + t.substring(3).toInt()
                })
            }
        }
        out
    }.getOrNull()

    /** Quick identity check so a redirect to some other page can never be accepted as the timetable. */
    fun isUmayyad(conf: JSONObject): Boolean =
        conf.optString("timezone") == "Asia/Damascus" && conf.optString("name").contains("الأموي")

    fun parse(html: String): Map<String, DayTimes>? {
        val conf = confData(html) ?: return null
        if (!isUmayyad(conf)) return null
        return calendar(conf)
    }
}

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
