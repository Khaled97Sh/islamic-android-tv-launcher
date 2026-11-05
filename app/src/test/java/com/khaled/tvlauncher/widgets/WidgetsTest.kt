package com.khaled.tvlauncher.widgets

import com.khaled.tvlauncher.prayer.PrayerTimeProvider
import com.khaled.tvlauncher.time.DAMASCUS
import com.khaled.tvlauncher.time.clock12
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetsTest {
    @Test fun clock12Formats() {
        assertEquals("05:32 صباحاً", clock12(LocalTime.of(5, 32)))
        assertEquals("12:00 مساءً", clock12(LocalTime.of(12, 0)))
        assertEquals("12:05 صباحاً", clock12(LocalTime.of(0, 5)))
        assertEquals("05:41", clock12(LocalTime.of(17, 41), suffix = false))
    }

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, DAMASCUS)

    @Test fun fridayChipOnlyThursdayAndFriday() {
        assertEquals(null, AutoChips.friday(at(2026, 11, 4, 12)))            // Wednesday
        assertEquals("غداً", AutoChips.friday(at(2026, 11, 5, 12))!!.big)     // Thursday
        assertEquals(null, AutoChips.friday(at(2026, 11, 7, 12)))            // Saturday
        assertEquals(null, AutoChips.friday(at(2026, 11, 8, 12)))            // Sunday
    }

    @Test fun fridayCountsDownToDhuhrThenBlesses() {
        val before = AutoChips.friday(at(2026, 11, 6, 8, 0))!!               // Friday morning
        assertEquals("حتى صلاة الجمعة", before.line1)
        assertTrue(before.big.matches(Regex("\\d+:\\d\\d")))
        assertEquals("جمعة", AutoChips.friday(at(2026, 11, 6, 15, 0))!!.big)  // after Dhuhr
    }

    @Test fun lastThirdIsTheLastThirdOfTheNight() {
        val maghrib = Instant.parse("2026-11-05T14:41:00Z")
        val fajr = Instant.parse("2026-11-06T02:33:00Z")                 // night = 11h52m
        val w = NightThird.lastThird(maghrib, fajr)
        assertEquals(fajr, w.end)
        assertEquals(237L, java.time.Duration.between(w.start, fajr).toMinutes())   // a third of 11h52m = 3h57m = 237 min
        assertTrue(w.start.isAfter(maghrib) && w.start.isBefore(fajr))
    }

    @Test fun lastThirdChipAppearsTwoHoursBeforeAndEndsAtFajr() {
        val day = java.time.LocalDate.of(2026, 11, 5)
        val w = NightThird.lastThird(PrayerTimeProvider.forDate(day)[3].at, PrayerTimeProvider.forDate(day.plusDays(1))[0].at)
        assertFalse(AutoChips.lastThirdShown(w.start.minusSeconds(2 * 3600 + 60)))   // 2h01m before: hidden
        assertTrue(AutoChips.lastThirdShown(w.start.minusSeconds(2 * 3600 - 60)))    // 1h59m before: shown
        assertTrue(AutoChips.lastThirdShown(w.start.plusSeconds(600)))               // inside the last third
        assertFalse(AutoChips.lastThirdShown(w.end.plusSeconds(60)))                 // after Fajr: hidden
    }

    @Test fun occasionWording() {
        assertEquals("عيد الفطر اليوم", Occasion("عيد الفطر", 0).text())
        assertEquals("عيد الفطر غداً", Occasion("عيد الفطر", 1).text())
        assertEquals("عيد الفطر بعد يومين", Occasion("عيد الفطر", 2).text())
        assertEquals("عيد الفطر بعد 5 أيام", Occasion("عيد الفطر", 5).text())
        assertEquals("عيد الفطر بعد 12 يوماً", Occasion("عيد الفطر", 12).text())
    }

    @Test fun nextOccasionIsRamadanBeforeItsFirstDay() {
        // 1 Ramadan 1448 AH falls in February 2027 (Umm al-Qura), so from November 2026 the next event is Ramadan.
        val o = Roznama.nextOccasion(LocalDate.of(2026, 11, 5))
        assertEquals("شهر رمضان", o.name)
        assertTrue(o.days in 80..110)
    }
}
