package com.khaled.tvlauncher.prayer

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.khaled.tvlauncher.time.DAMASCUS
import java.time.Instant
import java.time.LocalDate

data class PrayerEntry(val nameAr: String, val at: Instant)

/**
 * Offline prayer times for Damascus, taken from the Umayyad Mosque timetable ([MosqueCalendar]),
 * with an adhan-java (Muslim World League) calculation as a fallback.
 */
object PrayerTimeProvider {
    private val damascus = Coordinates(33.5138, 36.2765)

    private fun params(): CalculationParameters {
        val p = CalculationMethod.MUSLIM_WORLD_LEAGUE.parameters
        p.madhab = Madhab.SHAFI
        // Fallback only (used if a date is missing from MosqueCalendar). Minutes.
        p.adjustments.fajr = 0
        p.adjustments.dhuhr = 0
        p.adjustments.asr = 0
        p.adjustments.maghrib = 0
        p.adjustments.isha = 0
        return p
    }

    private val names = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")

    /** The five daily prayers (sunrise excluded) for [date], in order. */
    fun forDate(date: LocalDate): List<PrayerEntry> {
        // Primary source: the Umayyad Mosque's own published timetable.
        MosqueCalendar.timesFor(date)?.let { times ->
            return names.zip(times) { n, t -> PrayerEntry(n, date.atTime(t).atZone(DAMASCUS).toInstant()) }
        }
        val t = PrayerTimes(damascus, DateComponents(date.year, date.monthValue, date.dayOfMonth), params())
        return listOf(
            PrayerEntry(names[0], t.fajr.toInstant()),
            PrayerEntry(names[1], t.dhuhr.toInstant()),
            PrayerEntry(names[2], t.asr.toInstant()),
            PrayerEntry(names[3], t.maghrib.toInstant()),
            PrayerEntry(names[4], t.isha.toInstant()),
        )
    }

    /** Sunrise for the roznama widget: the mosque's own timetable first, a calculation only if the date is missing. Sunset = the Maghrib entry. */
    fun sunrise(date: LocalDate): Instant =
        MosqueCalendar.shuruqFor(date)?.let { date.atTime(it).atZone(DAMASCUS).toInstant() } ?: calculatedSunrise(date)

    private fun calculatedSunrise(date: LocalDate): Instant = PrayerTimes(damascus, DateComponents(date.year, date.monthValue, date.dayOfMonth), params()).sunrise.toInstant()

    /** First prayer strictly after [now], looking at today then tomorrow (Damascus date). */
    fun nextAfter(now: Instant): PrayerEntry {
        val today = now.atZone(DAMASCUS).toLocalDate()
        return (forDate(today) + forDate(today.plusDays(1))).first { it.at.isAfter(now) }
    }
}
