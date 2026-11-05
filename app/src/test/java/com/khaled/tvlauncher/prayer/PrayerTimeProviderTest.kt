package com.khaled.tvlauncher.prayer

import com.khaled.tvlauncher.time.DAMASCUS
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerTimeProviderTest {
    @Test fun fivePrayersInChronologicalOrder() {
        val times = PrayerTimeProvider.forDate(LocalDate.of(2026, 11, 4))
        assertEquals(listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء"), times.map { it.nameAr })
        assertTrue(times.zipWithNext().all { (a, b) -> a.at.isBefore(b.at) })
    }

    @Test fun dhuhrIsAroundMiddayDamascusTime() {
        val dhuhr = PrayerTimeProvider.forDate(LocalDate.of(2026, 11, 4))[1].at.atZone(DAMASCUS).toLocalTime()
        assertTrue("dhuhr was $dhuhr", dhuhr.hour in 11..12)
    }

    @Test fun matchesUmayyadMosqueTimetable() {
        // From the mosque's Mawaqit calendar for 5 Nov (fajr, dhuhr, asr, maghrib, isha).
        val actual = PrayerTimeProvider.forDate(LocalDate.of(2026, 11, 5))
            .map { it.at.atZone(DAMASCUS).toLocalTime().toString() }
        assertEquals(listOf("05:32", "12:19", "15:19", "17:41", "18:59"), actual)
    }

    @Test fun calendarCoversEveryDayOfALeapYear() {
        var d = LocalDate.of(2028, 1, 1)
        while (d.year == 2028) {
            assertTrue("missing $d", MosqueCalendar.timesFor(d) != null)
            d = d.plusDays(1)
        }
    }
}
