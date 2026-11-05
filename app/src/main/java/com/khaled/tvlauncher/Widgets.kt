package com.khaled.tvlauncher

import android.content.Context
import org.json.JSONArray
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** A widget the user can put on the home screen from the "+" picker. [glyph] is the letter shown in the picker row. */
data class WidgetDef(val id: String, val title: String, val subtitle: String, val glyph: String)

/** At most this many widgets are on screen at once (a hidden grid of glass cards: columns of 2, filled column by column). */
const val MAX_WIDGETS = 6

/** To add a widget: define it here, then draw it in WidgetCards.kt's WidgetBody(). Order = order in the picker. The Friday and last-third chips are automatic (AutoChips), not widgets. */
val WIDGETS = listOf(
    WidgetDef("next_azan", "الأذان القادم", "عدّاد تنازلي حتى الصلاة التالية", "أ"),
    WidgetDef("prayer_times", "أوقات الصلاة", "الصلوات الخمس اليوم والقادمة مميّزة", "ص"),
    WidgetDef("roznama", "الروزنامة", "صفحة اليوم: الميلادي والهجري والمناسبة القادمة", "ر"),
    WidgetDef("dua", "دعاء اليوم", "دعاء من حصن المسلم يتغيّر كل يوم", "د"),
    WidgetDef("asma", "أسماء الله الحسنى", "اسم جديد كل يوم", "ح"),
    WidgetDef("sunnah", "سنّة اليوم", "حديث نبوي قصير كل يوم مع مصدره", "س"),
)

/** Which widgets are on the screen, in slot order (right to left, top row first). First run shows the first two. */
class WidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun get(): List<String> {
        val raw = prefs.getString("ids", null) ?: return listOf("next_azan", "roznama")
        val known = WIDGETS.map { it.id }.toSet()
        return raw.split(",").filter { it in known }.distinct().take(MAX_WIDGETS)
    }

    fun set(ids: List<String>) = prefs.edit().putString("ids", ids.joinToString(",")).apply()
}

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

data class Occasion(val name: String, val days: Int) {
    /** "عيد الفطر بعد 12 يوماً" */
    fun text(): String = when (days) {
        0 -> "$name اليوم"
        1 -> "$name غداً"
        2 -> "$name بعد يومين"
        in 3..10 -> "$name بعد $days أيام"
        else -> "$name بعد $days يوماً"
    }
}

/** Facts for the roznama (day-page) widget. Hijri dates follow the Umm al-Qura calendar, which can differ from a local moon sighting by a day. */
object Roznama {
    private class Event(val month: Int, val day: Int, val name: String)

    private val events = listOf(
        Event(1, 1, "رأس السنة الهجرية"),
        Event(1, 10, "يوم عاشوراء"),
        Event(9, 1, "شهر رمضان"),
        Event(10, 1, "عيد الفطر"),
        Event(12, 9, "يوم عرفة"),
        Event(12, 10, "عيد الأضحى"),
    )

    /** The next occasion on or after [today]. Always found within one Hijri year. */
    fun nextOccasion(today: LocalDate): Occasion {
        for (i in 0..400) {
            val h = HijrahDate.from(today.plusDays(i.toLong()))
            val m = h.get(ChronoField.MONTH_OF_YEAR)
            val d = h.get(ChronoField.DAY_OF_MONTH)
            events.firstOrNull { it.month == m && it.day == d }?.let { return Occasion(it.name, i) }
        }
        return Occasion(events.first().name, 0)
    }
}

/** The last third of the night, counted from Maghrib to Fajr (the common reckoning). */
object NightThird {
    class Window(val start: Instant, val end: Instant)

    fun lastThird(maghrib: Instant, fajr: Instant): Window {
        val night = Duration.between(maghrib, fajr)
        return Window(maghrib.plus(night.multipliedBy(2).dividedBy(3)), fajr)
    }

    /** The window of the night that is current or coming next at [now] (Damascus). */
    fun at(now: Instant): Window {
        val today = now.atZone(DAMASCUS).toLocalDate()
        val fajrToday = PrayerTimeProvider.forDate(today)[0].at
        return if (now.isBefore(fajrToday)) {
            lastThird(PrayerTimeProvider.forDate(today.minusDays(1))[3].at, fajrToday)
        } else {
            lastThird(PrayerTimeProvider.forDate(today)[3].at, PrayerTimeProvider.forDate(today.plusDays(1))[0].at)
        }
    }
}

/** "h:mm" for a countdown, e.g. 1:55 or 0:45. */
fun hmClock(d: Duration): String = "%d:%02d".format(d.toHours(), d.toMinutes() % 60)

/** A small automatic card shown next to the weather (same size), only when it is relevant. */
class ChipInfo(val id: String, val big: String, val line1: String, val line2: String)

object AutoChips {
    /** The last-third chip appears this long before the last third starts, and stays until Fajr. */
    private val LEAD = Duration.ofHours(2)

    /** Thursday: a reminder; Friday: countdown to the Jumuah prayer (Dhuhr time of the mosque timetable), then a blessing. */
    fun friday(now: ZonedDateTime): ChipInfo? {
        val date = now.toLocalDate()
        return when (date.dayOfWeek) {
            DayOfWeek.THURSDAY -> ChipInfo("friday", "غداً", "يوم الجمعة", "تذكير بسورة الكهف")
            DayOfWeek.FRIDAY -> {
                val dhuhr = PrayerTimeProvider.forDate(date)[1].at
                val left = Duration.between(now.toInstant(), dhuhr)
                if (left.isNegative || left.isZero) ChipInfo("friday", "جمعة", "مباركة", "سورة الكهف")
                else ChipInfo("friday", hmClock(left), "حتى صلاة الجمعة", clock12(dhuhr))
            }
            else -> null
        }
    }

    fun lastThirdShown(now: Instant): Boolean {
        val w = NightThird.at(now)
        return !now.isBefore(w.start.minus(LEAD)) && now.isBefore(w.end)
    }

    fun lastThird(now: ZonedDateTime): ChipInfo? {
        val inst = now.toInstant()
        if (!lastThirdShown(inst)) return null
        val w = NightThird.at(inst)
        return if (inst.isBefore(w.start)) {
            ChipInfo("last_third", hmClock(Duration.between(inst, w.start)), "حتى الثلث الأخير", "يبدأ ${clock12(w.start)}")
        } else {
            ChipInfo("last_third", hmClock(Duration.between(inst, w.end)), "الثلث الأخير الآن", "حتى الفجر ${clock12(w.end)}")
        }
    }

    /** Chips in display order (next to the weather). */
    fun at(now: ZonedDateTime): List<ChipInfo> = listOfNotNull(friday(now), lastThird(now))
}

/** 12-hour clock text such as "05:32 صباحاً" ([suffix] false gives just "05:32"). */
fun clock12(t: LocalTime, suffix: Boolean = true): String {
    val h = t.hour % 12
    val base = "%02d:%02d".format(if (h == 0) 12 else h, t.minute)
    return if (suffix) base + if (t.hour < 12) " صباحاً" else " مساءً" else base
}

fun clock12(at: Instant, suffix: Boolean = true): String = clock12(ZonedDateTime.ofInstant(at, DAMASCUS).toLocalTime(), suffix)
