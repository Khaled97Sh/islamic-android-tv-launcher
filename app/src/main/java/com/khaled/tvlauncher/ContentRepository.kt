package com.khaled.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Ayah(val text: String, val ref: String)

/** Offline Arabic content, bundled in assets/ayat.json and assets/azkar.json. */
class ContentRepository(private val context: Context) {
    private val ayat: List<Ayah>
    val morning: List<String>
    val evening: List<String>
    val general: List<String>

    init {
        val am = context.applicationContext.assets
        fun read(name: String) = am.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

        val ayatJson = JSONArray(read("ayat.json"))
        ayat = List(ayatJson.length()) {
            val o = ayatJson.getJSONObject(it)
            Ayah(o.getString("t"), o.getString("r"))
        }
        val azkar = JSONObject(read("azkar.json"))
        fun list(key: String): List<String> {
            val a = azkar.getJSONArray(key)
            return List(a.length()) { a.getString(it) }
        }
        morning = list("morning")
        evening = list("evening")
        general = list("general")
    }

    /** Debuggable builds only: files/debug_quote.txt = "ayah|ref|zikr" pins the content, to test long text. */
    private fun debugOverride(): List<String>? {
        val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable) return null
        val f = java.io.File(context.filesDir, "debug_quote.txt")
        return if (f.exists()) f.readText().trim().split("|").takeIf { it.size == 3 } else null
    }

    /** Shuffle-bag: every item is shown once before any repeats, and never twice in a row across refills. */
    private class Bag<T>(private val items: List<T>) {
        private val queue = ArrayDeque<T>()
        private var last: T? = null
        fun next(): T {
            if (queue.isEmpty()) {
                val s = items.shuffled()
                queue.addAll(if (items.size > 1 && s.first() == last) s.drop(1) + s.first() else s)
            }
            return queue.removeFirst().also { last = it }
        }
    }

    private val ayahBag = Bag(ayat)
    private val zikrBag = Bag(general)

    fun randomAyah(): Ayah = debugOverride()?.let { Ayah(it[0], it[1]) } ?: ayahBag.next()
    fun randomZikr(): String = debugOverride()?.get(2) ?: zikrBag.next()

    /** A random sample of 12 items from the pool (a single line with all of them would be too heavy). */
    fun tickerText(mode: TickerMode): String? {
        val pool = when (mode) {
            TickerMode.MORNING -> morning
            TickerMode.EVENING -> evening
            TickerMode.NONE -> return null
        }
        if (!UI_V2) return pool.shuffled().take(12).joinToString("   ✦   ")
        // v2: skip very long items and cap one pass at about 1,500 letters, so a pass always finishes in ~3 minutes.
        fun letters(t: String) = t.count { it.code !in 0x064B..0x065F && it.code != 0x0670 }
        val short = pool.filter { letters(it) <= STRIP_MAX_ITEM }.ifEmpty { pool }
        val picked = ArrayList<String>(); var total = 0
        for (t in short.shuffled()) { if (picked.isNotEmpty() && total + letters(t) > STRIP_MAX_TOTAL) break; picked += t; total += letters(t) }
        return picked.joinToString("   ✦   ")
    }

    private companion object { const val STRIP_MAX_ITEM = 200; const val STRIP_MAX_TOTAL = 1500 }
}
