package com.khaled.tvlauncher.ui.background

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Extra background photos found and downloaded at run time while the TV is online, shown after the bundled ones.
 *
 * Every step is slow and gentle (Commons throttles fast clients): search a page of results, keep only those that pass
 * [OnlineRules], download the 3840 px version of one, check it really is 4K and has no face ([FaceCheck]), keep it in
 * `filesDir/bg_online`. Cached photos keep working offline. The cache holds at most [MAX_CACHE] photos; once a day the
 * oldest one is dropped so new ones keep coming. Progress through the search terms is remembered across restarts.
 */
class OnlineBackgrounds(private val context: Context) {
    private val dir = File(context.filesDir, "bg_online").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("online_bg", Context.MODE_PRIVATE)
    private val queue = ArrayDeque<Candidate>()

    private fun files() = dir.listFiles().orEmpty().filter { it.name.endsWith(".jpg") && it.length() > 0 }

    /** Names of the photos that are downloaded and ready. */
    fun cachedNames(): List<String> = files().map { it.name }.sorted()
    fun file(name: String) = File(dir, name)
    /** Remove a photo that turned out unreadable. */
    fun discard(name: String) { File(dir, name).delete() }

    private fun nameOf(title: String) = title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).removeSuffix(".jpg").removeSuffix(".JPG") + ".jpg"
    /** Bytes this feature downloaded today (search pages and photos), kept per calendar day. */
    private fun bytesToday(): Long {
        val day = System.currentTimeMillis() / DAY_MS
        return if (prefs.getLong("budget_day", -1) == day) prefs.getLong("budget_bytes", 0) else 0
    }
    private fun addBytes(n: Long) {
        val day = System.currentTimeMillis() / DAY_MS
        prefs.edit().putLong("budget_day", day).putLong("budget_bytes", bytesToday() + n).apply()
    }

    private fun rejected(): MutableSet<String> = prefs.getStringSet("rejected", emptySet())!!.toMutableSet()

    /** One unit of work, on an IO thread. Returns how long to wait before the next call. */
    fun syncStep(): Long {
        // Never on mobile data or a metered hotspot: no request at all, not even a search.
        if (!NetworkGuard.isUnmetered(context)) return NOT_UNMETERED_MS
        // A hard daily limit on what this feature may download, whatever else happens.
        if (bytesToday() >= DAILY_BUDGET_BYTES) return BUDGET_MS
        dir.listFiles().orEmpty().filter { it.name.endsWith(".part") }.forEach { it.delete() }
        val have = files()
        if (have.size >= MAX_CACHE) {
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("rotated_at", 0L) >= ROTATE_CACHE_MS) {
                have.minByOrNull { it.lastModified() }?.delete()
                prefs.edit().putLong("rotated_at", now).apply()
            }
            return IDLE_MS
        }
        if (queue.isEmpty() && !refill()) return RETRY_MS
        val next = queue.removeFirstOrNull() ?: return BETWEEN_MS
        return if (download(next)) BETWEEN_MS else SHORT_MS
    }

    /** Fetch the next page of search results into the queue. False = network problem. */
    private fun refill(): Boolean {
        var term = prefs.getInt("term", 0) % OnlineSearch.TERMS.size
        val offset = prefs.getInt("offset", 0)
        val page = OnlineSearch.fetch(OnlineSearch.TERMS[term], offset) ?: return false
        addBytes(page.bytes)
        val skip = rejected() + files().map { it.name }
        var kept = 0
        for (c in page.candidates) {
            val why = OnlineRules.reject(c)
            if (why == null && nameOf(c.title) !in skip) { queue.addLast(c); kept++ }
        }
        android.util.Log.i("TvLauncher", "online bg: '${OnlineSearch.TERMS[term]}' @$offset -> ${page.candidates.size} results, $kept pass the rules")
        if (page.nextOffset == null || offset >= MAX_OFFSET) { term++; prefs.edit().putInt("term", term).putInt("offset", 0).apply() }
        else prefs.edit().putInt("offset", page.nextOffset).apply()
        queue.shuffle()
        return true
    }

    private fun refuse(c: Candidate, why: String) {
        android.util.Log.i("TvLauncher", "online bg: refused '${c.title}': $why")
        prefs.edit().putStringSet("rejected", rejected().also { it.add(nameOf(c.title)) }.toSet()).apply()
    }

    private fun download(c: Candidate): Boolean {
        val tmp = File(dir, nameOf(c.title) + ".part")
        val conn = runCatching { URL(OnlineSearch.imageUrl(c.title)).openConnection() as HttpURLConnection }.getOrNull() ?: return false
        try {
            conn.connectTimeout = 15_000; conn.readTimeout = 40_000; conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", OnlineSearch.USER_AGENT)
            if (conn.responseCode != 200 || conn.url.protocol != "https" || conn.contentType?.startsWith("image/jpeg") != true) return false
            var total = 0L
            conn.inputStream.use { input -> tmp.outputStream().use { out ->
                val buf = ByteArray(16 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; total += n; if (total > MAX_BYTES) { tmp.delete(); refuse(c, "too large"); return false }; out.write(buf, 0, n) }
            } }
            addBytes(total)
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, b)
            if (b.outWidth < MIN_DECODED_WIDTH || b.outHeight < MIN_DECODED_HEIGHT) { tmp.delete(); refuse(c, "downloaded size ${b.outWidth}x${b.outHeight} is below 4K"); return false }
            val probe = BitmapFactory.decodeFile(tmp.path, BitmapFactory.Options().apply { inSampleSize = sampleFor(b.outWidth) * 2 })
            if (probe == null) { tmp.delete(); return false }
            val face = FaceCheck.hasFace(probe); probe.recycle()
            if (face) { tmp.delete(); refuse(c, "a face was found"); return false }
            return tmp.renameTo(File(dir, nameOf(c.title))).also { if (!it) tmp.delete() }
        } catch (_: Exception) { tmp.delete(); return false } finally { conn.disconnect() }
    }

    companion object {
        const val MAX_CACHE = 40
        const val MAX_OFFSET = 250
        const val MIN_DECODED_WIDTH = 3800
        const val MIN_DECODED_HEIGHT = 2000   // a 16:10 4K image is 3840x2400; a cropped one may be a little under 2160
        const val MAX_BYTES = 20L * 1024 * 1024
        const val BETWEEN_MS = 45_000L
        const val SHORT_MS = 5_000L
        const val RETRY_MS = 10L * 60_000L
        const val IDLE_MS = 6L * 60 * 60_000L
        const val ROTATE_CACHE_MS = 24L * 60 * 60_000L
        const val NOT_UNMETERED_MS = 5L * 60_000L
        const val BUDGET_MS = 60L * 60_000L
        const val DAY_MS = 24L * 60 * 60_000L
        /** At most this much per day (about 8 photos): the cache fills slowly and a bug can never run up a big bill. */
        const val DAILY_BUDGET_BYTES = 25L * 1024 * 1024
    }
}
