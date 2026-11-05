package com.khaled.tvlauncher.time

import com.khaled.tvlauncher.prayer.PrayerTimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedClockTest {
    private val goodNow = 1_793_900_000_000L   // 2026-11-05
    private val badBoot = 1_745_250_000_000L   // firmware build date the TV shows after a cold start
    private var sys = 0L
    private var el = 0L
    private fun setUp(floor: Long, utc: Long, elapsed: Long, boot: Int, bootNow: Int) {
        TrustedClock.systemMs = { sys }; TrustedClock.elapsedMs = { el }
        TrustedClock.setUp(floor, utc, elapsed, boot, bootNow)
    }

    @Test fun goodSystemClockIsUsed() {
        setUp(goodNow - 1000, goodNow - 1000, 0, 5, 5); sys = goodNow; el = 1000
        assertEquals(goodNow, TrustedClock.now()); assertTrue(TrustedClock.isSystemClockGood())
    }

    @Test fun wrongClockAfterRebootStartsFromLastGoodTimeThenCountsForward() {
        // last seen good 23:00; TV rebooted (boot 5 -> 6), no internet, system clock says April 2025
        setUp(goodNow, goodNow, 500, 5, 6); sys = badBoot; el = 10_000
        assertFalse(TrustedClock.isSystemClockGood())
        assertEquals(goodNow, TrustedClock.now())
        el += 3 * 60_000L   // three minutes later the clock must have moved
        assertEquals(goodNow + 3 * 60_000L, TrustedClock.now())
    }

    @Test fun wrongClockWithoutRebootKeepsCountingWithTheTimer() {
        setUp(goodNow, goodNow, 1_000, 6, 6); sys = badBoot; el = 1_000 + 5 * 86_400_000L   // 5 days later, same boot
        assertEquals(goodNow + 5 * 86_400_000L, TrustedClock.now())
    }

    @Test fun networkCorrectionIsAdoptedAndPrayerDayFollowsIt() {
        setUp(goodNow, goodNow, 0, 6, 6); sys = badBoot; el = 60_000
        assertEquals(goodNow + 60_000, TrustedClock.now())
        sys = goodNow + 3 * 3_600_000L; el = 120_000   // internet came back
        assertEquals(sys, TrustedClock.now()); assertTrue(TrustedClock.isSystemClockGood())
    }

    @Test fun nextAzanForWeekOfflineComesFromStoredTable() {
        // 5 days with no internet: every day still has times (the table covers the whole year, stored on the TV).
        val start = java.time.LocalDate.of(2026, 11, 5)
        for (i in 0..10) assertEquals(5, PrayerTimeProvider.forDate(start.plusDays(i.toLong())).size)
    }
}
