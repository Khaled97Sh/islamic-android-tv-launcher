package com.khaled.tvlauncher.ui.background

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Searches Wikimedia Commons for 4K JPEGs. The size filter runs on the server; [OnlineRules] runs here. */
object OnlineSearch {
    private const val API = "https://commons.wikimedia.org/w/api.php"
    const val USER_AGENT = "IslamicTvLauncher/1.0 (https://github.com/Khaled97Sh/islamic-android-tv-launcher)"
    const val PAGE_SIZE = 50

    /** Search terms, used in turn. Each is combined with the 4K / JPEG filter. */
    val TERMS = listOf(
        "mosque dome", "mosque minaret", "mosque interior arches", "mosque courtyard", "mosque mihrab", "mosque sunset",
        "mosque night", "Alhambra", "Generalife", "Mezquita Córdoba", "Great Mosque Kairouan", "Umayyad Mosque",
        "Blue Mosque Istanbul", "Süleymaniye Mosque", "Selimiye Mosque", "Hassan II Mosque", "Sheikh Zayed Mosque",
        "Al-Aqsa", "Dome of the Rock", "Masjid al-Haram", "Masjid an-Nabawi", "Islamic geometric ornament", "muqarnas",
    )

    class Page(val candidates: List<Candidate>, val nextOffset: Int?)

    fun fetch(term: String, offset: Int): Page? {
        val query = "$term filetype:bitmap filew:>${OnlineRules.MIN_WIDTH - 1} fileh:>${OnlineRules.MIN_HEIGHT - 1}"
        val params = mapOf(
            "format" to "json", "action" to "query", "generator" to "search", "gsrnamespace" to "6",
            "gsrlimit" to "$PAGE_SIZE", "gsroffset" to "$offset", "gsrsearch" to query,
            "prop" to "imageinfo|categories", "cllimit" to "max", "iiprop" to "size|mime|extmetadata",
            "iiextmetadatafilter" to "ImageDescription",
        )
        val url = API + "?" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val body = getText(url) ?: return null
        return runCatching { parse(JSONObject(body)) }.getOrNull()
    }

    fun parse(root: JSONObject): Page {
        val pages = root.optJSONObject("query")?.optJSONObject("pages")
        val out = ArrayList<Candidate>()
        if (pages != null) for (key in pages.keys()) {
            val p = pages.getJSONObject(key)
            val ii = p.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val cats = p.optJSONArray("categories")?.let { a -> List(a.length()) { a.getJSONObject(it).getString("title").removePrefix("Category:") } }.orEmpty()
            val desc = ii.optJSONObject("extmetadata")?.optJSONObject("ImageDescription")?.optString("value").orEmpty().replace(Regex("<[^>]+>"), "")
            out += Candidate(p.getString("title").removePrefix("File:"), ii.optInt("width"), ii.optInt("height"), ii.optString("mime"), cats, desc.take(300))
        }
        val next = root.optJSONObject("continue")?.optInt("gsroffset", -1)?.takeIf { it >= 0 }
        return Page(out, next)
    }

    /** The 3840 px wide version of a file (Commons resizes on the server; never the 8K original). */
    fun imageUrl(title: String, width: Int = OnlineRules.MIN_WIDTH): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?width=$width"

    private fun getText(url: String): String? {
        val c = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            c.connectTimeout = 15_000; c.readTimeout = 30_000; c.setRequestProperty("User-Agent", USER_AGENT)
            if (c.responseCode != 200) null else c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) { null } finally { c.disconnect() }
    }
}
