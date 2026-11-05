package com.khaled.tvlauncher.widgets

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

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

