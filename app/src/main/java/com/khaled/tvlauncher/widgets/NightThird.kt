package com.khaled.tvlauncher.widgets

import com.khaled.tvlauncher.prayer.PrayerTimeProvider
import com.khaled.tvlauncher.time.DAMASCUS
import java.time.Duration
import java.time.Instant

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

