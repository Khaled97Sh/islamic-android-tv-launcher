package com.khaled.tvlauncher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Weather(val tempC: Int, val descriptionAr: String)

/** Open-Meteo (no API key) for Damascus. Last good value is cached so the UI never waits on the network. */
class WeatherRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun cached(): Weather? {
        if (!prefs.contains("temp")) return null
        // An old reading is worse than none: hide it after 6 hours.
        if (TrustedClock.now() - prefs.getLong("at", 0L) > 6 * 60 * 60_000L) return null
        return Weather(prefs.getInt("temp", 0), prefs.getString("desc", "") ?: "")
    }

    /** Blocking call, run on an IO dispatcher. Returns null on any failure. */
    fun fetch(): Weather? = try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=33.5138&longitude=36.2765" +
                "&current=temperature_2m,weather_code&timezone=Asia%2FDamascus"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            val w = Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), describe(cur.getInt("weather_code")))
            prefs.edit().putInt("temp", w.tempC).putString("desc", w.descriptionAr).putLong("at", TrustedClock.now()).apply()
            w
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "صافٍ"
        1, 2 -> "غائم جزئياً"
        3 -> "غائم"
        45, 48 -> "ضباب"
        51, 53, 55, 56, 57 -> "رذاذ"
        61, 63, 65, 66, 67, 80, 81, 82 -> "مطر"
        71, 73, 75, 77, 85, 86 -> "ثلج"
        95, 96, 99 -> "عاصفة رعدية"
        else -> "—"
    }
}
