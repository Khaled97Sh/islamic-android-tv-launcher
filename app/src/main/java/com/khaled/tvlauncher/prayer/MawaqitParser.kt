package com.khaled.tvlauncher.prayer

import org.json.JSONObject

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

