package com.khaled.tvlauncher.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.khaled.tvlauncher.apps.AppItem
import com.khaled.tvlauncher.apps.AppRepository
import com.khaled.tvlauncher.azan.AzanScheduler
import com.khaled.tvlauncher.content.Ayah
import com.khaled.tvlauncher.content.ContentRepository
import com.khaled.tvlauncher.content.TickerMode
import com.khaled.tvlauncher.content.TickerSchedule
import com.khaled.tvlauncher.prayer.MawaqitSync
import com.khaled.tvlauncher.prayer.SyncResult
import com.khaled.tvlauncher.time.DAMASCUS
import com.khaled.tvlauncher.time.TrustedClock
import com.khaled.tvlauncher.ui.background.BackgroundImage
import com.khaled.tvlauncher.ui.background.BackgroundLoader
import com.khaled.tvlauncher.ui.theme.UI_V2
import com.khaled.tvlauncher.weather.Weather
import com.khaled.tvlauncher.weather.WeatherRepository
import com.khaled.tvlauncher.widgets.BottomCardsStore
import com.khaled.tvlauncher.widgets.MAX_WIDGETS
import com.khaled.tvlauncher.widgets.WidgetContent
import com.khaled.tvlauncher.widgets.WidgetStore
import com.khaled.tvlauncher.youtube.Video
import com.khaled.tvlauncher.youtube.YT_SUGGESTIONS_ENABLED
import com.khaled.tvlauncher.youtube.YouTubeSuggestions
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Single state holder; all slow work happens on IO and results are published as immutable values. */
class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val apps = AppRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val content = ContentRepository(app)
    private val bgLoader = BackgroundLoader(app)
    private val yt = YouTubeSuggestions()

    /** A crash in any background loop must never take the launcher down (it is the TV's only Home): log it and stop that loop. */
    private val guard = kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("TvLauncher", "background loop failed", e) }

    private val _now = MutableStateFlow(java.time.Instant.ofEpochMilli(TrustedClock.now()).atZone(DAMASCUS))
    val now: StateFlow<ZonedDateTime> = _now.asStateFlow()

    private val _apps = MutableStateFlow<List<AppItem>>(emptyList())
    val appList: StateFlow<List<AppItem>> = _apps.asStateFlow()

    private val _weather = MutableStateFlow(weatherRepo.cached())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _ayah = MutableStateFlow(content.randomAyah())
    val ayah: StateFlow<Ayah> = _ayah.asStateFlow()

    private val _zikr = MutableStateFlow(content.randomZikr())
    val zikr: StateFlow<String> = _zikr.asStateFlow()

    private var tickerMode: TickerMode = TickerMode.NONE

    /** Called by the strip when it has finished a full pass: swap in a fresh pick. */
    fun nextTicker() { if (tickerMode != TickerMode.NONE) _ticker.value = content.tickerText(tickerMode) }

    private val _ticker = MutableStateFlow<String?>(null)
    /** Text for the sliding azkar strip, or null outside the morning/evening windows. */
    val ticker: StateFlow<String?> = _ticker.asStateFlow()

    private val _bg = MutableStateFlow<BackgroundImage?>(null)
    val bg: StateFlow<BackgroundImage?> = _bg.asStateFlow()

    private val widgetStore = WidgetStore(app)
    val widgetContent = WidgetContent(app)
    private val _widgets = MutableStateFlow(widgetStore.get())
    /** Ids of the widgets shown on the home screen, in slot order. */
    val widgets: StateFlow<List<String>> = _widgets.asStateFlow()

    fun toggleWidget(id: String) {
        val cur = _widgets.value.toMutableList()
        if (!cur.remove(id)) { if (cur.size >= MAX_WIDGETS) return; cur.add(id) }
        widgetStore.set(cur)
        _widgets.value = cur
    }

    private val bottomStore = BottomCardsStore(app)
    private val _bottomHidden = MutableStateFlow(bottomStore.hidden())
    /** Ids ("ayah", "zikr") of the bottom cards the user turned off; empty by default (both shown). */
    val bottomHidden: StateFlow<Set<String>> = _bottomHidden.asStateFlow()

    fun toggleBottom(id: String) {
        val cur = _bottomHidden.value.toMutableSet()
        if (!cur.remove(id)) cur.add(id)
        bottomStore.setHidden(cur)
        _bottomHidden.value = cur
    }

    private val _videos = MutableStateFlow<List<Video>>(emptyList())
    /** Suggested YouTube videos for the preview window (empty while offline). */
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    init {
        reloadApps()
        // Rotate the background photo every 30 s, walking a shuffled order (one decoded at a time).
        // While another app is in front nothing is shown, so no photo is decoded then.
        viewModelScope.launch(guard) {
            if (bgLoader.count == 0) return@launch
            var order = (0 until bgLoader.count).shuffled()
            var p = 0
            while (true) {
                if (!visible) { delay(2_000L); continue }
                if (p >= order.size) {
                    val last = order.last()
                    order = (0 until bgLoader.count).shuffled().let { if (it.size > 1 && it.first() == last) it.drop(1) + it.first() else it }
                    p = 0
                }
                withContext(Dispatchers.IO) { bgLoader.load(order[p++]) }?.let { _bg.value = it }
                delay(ROTATE_MS)
            }
        }
        // Clock + ticker window: the display has minute resolution, so tick on the minute boundary.
        viewModelScope.launch(guard) {
            var lastMode: TickerMode? = null
            var lastPick = 0L
            while (true) {
                val t = java.time.Instant.ofEpochMilli(TrustedClock.now()).atZone(DAMASCUS)
                TrustedClock.save()
                _now.value = t
                val mode = TickerSchedule.modeAt(t.toLocalTime())
                val nowMs = TrustedClock.now()
                // v2: new text is picked when a pass ends (nextTicker), not on a timer; v1 re-picked every 5 minutes.
                if (mode != lastMode || (!UI_V2 && nowMs - lastPick > 5 * 60_000L)) {
                    lastMode = mode
                    tickerMode = mode
                    lastPick = nowMs
                    _ticker.value = content.tickerText(mode)
                }
                val ms = TrustedClock.now()
                delay((60_000L - ms % 60_000L + 100L).coerceAtMost(60_000L))
            }
        }
        // Suggested videos: refreshed once every 24 hours and kept on disk (restarts reuse them). While offline the
        // old list stays and a refresh is retried every minute.
        if (YT_SUGGESTIONS_ENABLED) viewModelScope.launch(guard) {
            val prefs = app.getSharedPreferences("yt", Context.MODE_PRIVATE)
            var fetchedAt = prefs.getLong("at", 0L)
            runCatching {
                val a = org.json.JSONArray(prefs.getString("list", "[]"))
                _videos.value = List(a.length()) { k -> a.getJSONObject(k).let { Video(it.getString("i"), it.getString("t"), it.getString("c")) } }
            }
            while (true) {
                val now = TrustedClock.now()
                if (_videos.value.isEmpty() || now - fetchedAt >= DAY_MS) {
                    val list = withContext(Dispatchers.IO) { yt.fetch(now / DAY_MS) }
                    if (list.isEmpty()) { delay(60_000L); continue }
                    _videos.value = list
                    fetchedAt = now
                    val a = org.json.JSONArray()
                    list.forEach { a.put(org.json.JSONObject().put("i", it.id).put("t", it.title).put("c", it.channel)) }
                    prefs.edit().putLong("at", now).putString("list", a.toString()).apply()
                    android.util.Log.i("TvLauncher", "yt suggestions refreshed: ${list.size}")
                }
                delay((fetchedAt + DAY_MS - TrustedClock.now()).coerceAtLeast(60_000L))
            }
        }
        // Prayer timetable: re-downloaded from the mosque's Mawaqit page every 10 days (checked before use), retried
        // soon after a failure. After a successful update the next azan alarm is re-armed from the new data.
        viewModelScope.launch(guard) {
            delay(20_000L)   // let Wi-Fi come up after boot
            while (true) {
                var result: SyncResult? = null
                if (MawaqitSync.isDue(app)) {
                    result = withContext(Dispatchers.IO) { MawaqitSync.refresh(app) }
                    android.util.Log.i("TvLauncher", "mawaqit sync: ${result.message}")
                    if (result is SyncResult.Updated) runCatching { AzanScheduler.scheduleNext(app) }
                }
                // Network failure: try again in 10 min. Rejected / unreadable page: retrying soon would not help, wait 6 h.
                delay(MawaqitSync.waitAfter(result))
            }
        }
        // Weather every 30 min; the first fetch after boot often runs before Wi-Fi is up, so retry quickly until it works.
        viewModelScope.launch(guard) {
            while (true) {
                val w = withContext(Dispatchers.IO) { weatherRepo.fetch() }
                if (w != null) { _weather.value = w; delay(30 * 60_000L) }
                else { _weather.value = weatherRepo.cached(); delay(60_000L) }
            }
        }
        // New ayah + zikr every minute.
        viewModelScope.launch(guard) {
            while (true) {
                delay(QUOTE_MS)
                if (!visible) continue
                _ayah.value = content.randomAyah()
                _zikr.value = content.randomZikr()
            }
        }
    }

    suspend fun thumbnail(id: String): ImageBitmap? = withContext(Dispatchers.IO) { yt.thumbnail(id) }
    fun openVideo(id: String) = apps.openVideo(id)

    private val _picker = MutableStateFlow<List<AppItem>?>(null)
    /** All installed apps while the "+" picker is open, else null. */
    val picker: StateFlow<List<AppItem>?> = _picker.asStateFlow()

    private val _checked = MutableStateFlow<Set<String>>(emptySet())
    val checked: StateFlow<Set<String>> = _checked.asStateFlow()

    fun openPicker() {
        viewModelScope.launch(guard) {
            _checked.value = apps.extras().toSet()
            _picker.value = withContext(Dispatchers.IO) { apps.installed() }
        }
    }

    fun closePicker() { _picker.value = null }

    fun toggleApp(pkg: String) {
        val cur = apps.extras().toMutableList()
        if (!cur.remove(pkg)) cur.add(pkg)
        apps.setExtras(cur)
        _checked.value = cur.toSet()
        reloadApps()
    }

    /** True while the launcher is on screen. The heavy loops pause when another app (e.g. YouTube) is in front. */
    @Volatile var visible: Boolean = true

    fun reloadApps() {
        viewModelScope.launch { _apps.value = withContext(Dispatchers.IO) { apps.load() } }
    }

    fun launch(packageName: String) = apps.launch(packageName)

    private companion object {
        const val ROTATE_MS = 30_000L   // background photo
        const val QUOTE_MS = 60_000L    // ayah + zikr
        const val DAY_MS = 24 * 60 * 60_000L
    }
}
