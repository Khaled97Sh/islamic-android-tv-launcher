package com.khaled.tvlauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Xml
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Master switch for the YouTube suggestion window (hidden for now; flip to true to bring it back). */
const val YT_SUGGESTIONS_ENABLED = false

data class Video(val id: String, val title: String, val channel: String)

/**
 * "Suggested for you" videos for the YouTube preview window. YouTube gives personal recommendations only to its
 * own app, so the content comes from a fixed list of channels: each channel's public RSS feed (no account, no API
 * key), Shorts dropped, newest first, channels interleaved. Blocking calls, run on an IO dispatcher.
 */
class YouTubeSuggestions {
    private class Channel(val id: String, val name: String)

    // Starting list, meant to be reviewed/edited by the user.
    private val channels = listOf(
        Channel("UCmMcOjsVehVlEOteyrhjI2Q", "مشاري العفاسي"),
        Channel("UCvwDvEh9i7Ge8bs_tw6nrrA", "محمد راتب النابلسي"),
        Channel("UCKUOmGXE9Ytlc2EzpGqimtw", "عمر عبد الكافي"),
        Channel("UCpPZKwayH_pWPF20Ls0hgVw", "نبيل العوضي"),
        Channel("UCfimgqx1TcWuLkgcrm2Rkdw", "ماهر المعيقلي"),
        Channel("UCf5vl3vN4zU2NSdcL-3Z5Nw", "ياسر الدوسري"),
        Channel("UCoD5-Dk95rFMtk5iCXqak6w", "عبد الباسط عبد الصمد"),
    )

    private val thumbs = LruCache<String, ImageBitmap>(6)

    /** [PER_CHANNEL] videos per channel picked from its recent ones (the pick depends on [daySeed], so it changes daily), interleaved. Empty when offline. */
    suspend fun fetch(daySeed: Long): List<Video> = coroutineScope {
        val lists = channels.map { c ->
            async {
                runCatching { feed(c) }
                    .onFailure { android.util.Log.w("TvLauncher", "yt feed ${c.name}: $it") }
                    .getOrDefault(emptyList()).shuffled(kotlin.random.Random(daySeed + c.id.hashCode())).take(PER_CHANNEL)
            }
        }.awaitAll()
        buildList {
            for (i in 0 until PER_CHANNEL) lists.forEach { l -> l.getOrNull(i)?.let(::add) }
        }
    }

    private fun feed(c: Channel): List<Video> {
        val conn = URL("https://www.youtube.com/feeds/videos.xml?channel_id=${c.id}").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        return try { parse(conn.inputStream, c.name) } finally { conn.disconnect() }
    }

    private fun parse(stream: InputStream, channel: String): List<Video> {
        val p = Xml.newPullParser()
        // Feed tags are prefixed ("yt:videoId", "media:title"); without this the parser reports the bare local name.
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(stream, "UTF-8")
        val out = mutableListOf<Video>()
        var inEntry = false; var id = ""; var title = ""; var shorts = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "entry" -> { inEntry = true; id = ""; title = ""; shorts = false }
                    "yt:videoId" -> if (inEntry) id = p.nextText()
                    "title" -> if (inEntry && title.isEmpty()) title = p.nextText().trim()
                    "link" -> if (inEntry && p.getAttributeValue(null, "rel") == "alternate" &&
                        p.getAttributeValue(null, "href").orEmpty().contains("/shorts/")) shorts = true
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "entry") {
                if (inEntry && id.isNotEmpty() && title.isNotEmpty() && !shorts) out += Video(id, title, channel)
                inEntry = false
            }
            ev = p.next()
        }
        return out
    }

    /** 16:9 thumbnail, or null when it can't be loaded. Cached (last few). */
    fun thumbnail(id: String): ImageBitmap? {
        thumbs.get(id)?.let { return it }
        val bmp = download("https://i.ytimg.com/vi/$id/maxresdefault.jpg", crop = false)
            ?: download("https://i.ytimg.com/vi/$id/hqdefault.jpg", crop = true)
            ?: return null
        return bmp.asImageBitmap().also { thumbs.put(id, it) }
    }

    private fun download(url: String, crop: Boolean): Bitmap? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000; conn.readTimeout = 8000
        if (conn.responseCode != 200) { conn.disconnect(); null } else {
            val bytes = conn.inputStream.use { it.readBytes() }
            val opts = BitmapFactory.Options().apply { inSampleSize = if (crop) 1 else 2 }   // maxres 1280 -> 640 wide
            val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            // hqdefault is 4:3 with black bars above and below the 16:9 picture: cut them off.
            if (crop && raw != null) {
                val h = raw.width * 9 / 16
                Bitmap.createBitmap(raw, 0, (raw.height - h) / 2, raw.width, h)
            } else raw
        }
    } catch (_: Exception) { null }

    private companion object { const val PER_CHANNEL = 5 }
}
