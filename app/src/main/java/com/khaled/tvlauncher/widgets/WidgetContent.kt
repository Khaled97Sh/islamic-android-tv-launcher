package com.khaled.tvlauncher.widgets

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray

/** Offline content for the daily widgets (assets/asma.json, assets/duas.json; text copied from open datasets). */
class WidgetContent(context: Context) {
    class Asma(val number: Int, val name: String)
    class Dua(val text: String, val source: String)
    class Sunnah(val text: String, val topic: String, val source: String)

    val asma: List<Asma>
    val duas: List<Dua>
    val sunnah: List<Sunnah>

    init {
        val am = context.applicationContext.assets
        fun read(name: String) = runCatching { am.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrDefault("[]")
        val a = JSONArray(read("asma.json"))
        asma = List(a.length()) { a.getJSONObject(it).let { o -> Asma(o.getInt("n"), o.getString("a")) } }
        val d = JSONArray(read("duas.json"))
        duas = List(d.length()) { d.getJSONObject(it).let { o -> Dua(o.getString("t"), o.getString("c")) } }
            .filter { it.text.length <= MAX_DUA_CHARS }
        val s = JSONArray(read("sunnah.json"))
        sunnah = List(s.length()) { s.getJSONObject(it).let { o -> Sunnah(o.getString("t"), o.getString("k"), o.getString("s")) } }
    }

    /** The same item all day, a new one each day (Damascus date). */
    fun asmaFor(date: LocalDate): Asma? = asma.takeIf { it.isNotEmpty() }?.get(Math.floorMod(date.toEpochDay(), asma.size.toLong()).toInt())
    fun sunnahFor(date: LocalDate): Sunnah? = sunnah.takeIf { it.isNotEmpty() }?.get(Math.floorMod(date.toEpochDay(), sunnah.size.toLong()).toInt())
    fun duaFor(date: LocalDate): Dua? = duas.takeIf { it.isNotEmpty() }?.get(Math.floorMod(date.toEpochDay(), duas.size.toLong()).toInt())

    private companion object { const val MAX_DUA_CHARS = 110 }
}

