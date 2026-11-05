package com.khaled.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableCheckTest {
    private fun page(): String = javaClass.getResource("/mawaqit_umayyad_page.html")!!.readText()

    /** The table exactly as the mosque publishes it (it contains the mosque's own typos). */
    private val published get() = MawaqitParser.parse(page())!!

    private fun t(day: String, col: Int, m: Map<String, DayTimes>) = m.getValue(day).m[col]
    private fun hm(h: Int, mi: Int) = h * 60 + mi

    @Test fun parsesTheRealMawaqitPage() {
        val m = published
        assertEquals(366, m.size)
        assertEquals(hm(5, 32), t("11-5", 0, m))   // fajr, 5 Nov
        assertEquals(hm(6, 57), t("11-5", 1, m))   // shuruq
        assertEquals(hm(17, 41), t("11-5", 4, m))  // maghrib
    }

    @Test fun thePublishedTableReallyHasTheMosquesTypos() {
        val m = published
        assertEquals(hm(7, 7), t("5-8", 0, m))     // fajr 07:07 instead of 04:07
        assertEquals(hm(14, 15), t("5-23", 3, m))  // asr 14:15 instead of 16:15
        assertEquals(hm(15, 37), t("5-25", 4, m))  // maghrib 15:37 instead of 19:37
    }

    @Test fun checkRepairsEveryTypoIncludingTheNineDayAsrRun() {
        val c = TimetableCheck.check(published)
        assertTrue(c.reason, c.ok)
        assertEquals(11, c.repairs.size)
        assertEquals(hm(4, 7), t("5-8", 0, c.days))
        for (d in 23..31) assertTrue("asr 5-$d", t("5-$d", 3, c.days) in hm(16, 14)..hm(16, 17))
        assertEquals(hm(19, 38), t("5-25", 4, c.days))
    }

    @Test fun repairedTableHasNoJumpsAndStaysInOrder() {
        val days = TimetableCheck.check(published).days
        val keys = days.keys.toList()
        for (c in 0 until 6) for (d in 1..12) {
            val len = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[d - 1]
            for (day in 1..len) {
                val a = days.getValue("$d-$day"); val nd = if (day == len) "${d % 12 + 1}-1" else "$d-${day + 1}"
                assertTrue("$d-$day col $c", kotlin.math.abs(a.m[c] - days.getValue(nd).m[c]) <= 4)
            }
        }
        assertTrue(days.values.all { v -> (0 until 5).all { v.m[it] < v.m[it + 1] } })
        assertEquals(366, keys.size)
    }

    @Test fun theBundledTableIsAlreadyClean() {
        val c = TimetableCheck.check(MosqueCalendar.bundled)
        assertTrue(c.reason, c.ok)
        assertEquals("the app's own copy must need no repairs", emptyList<String>(), c.repairs)
    }

    @Test fun bundledMatchesTheRepairedLiveData() {
        assertEquals(TimetableCheck.check(published).days, MosqueCalendar.bundled)
    }

    @Test fun rejectsATableWithTooManyWrongCells() {
        val broken = published.mapValues { (k, v) -> if (k.startsWith("3-")) DayTimes(v.m.copyOf().also { it[3] += 120 }) else v }
        assertFalse(TimetableCheck.check(broken).ok)
    }

    @Test fun rejectsAMissingDay() {
        assertFalse(TimetableCheck.check(published - "2-29").ok)
    }

    @Test fun anOutOfRangeValueIsAlwaysRepaired() {
        val m = published.toMutableMap(); val v = m.getValue("8-15").m.copyOf(); v[3] = hm(23, 0); m["8-15"] = DayTimes(v)
        val c = TimetableCheck.check(m)
        assertTrue(c.ok)
        assertTrue(t("8-15", 3, c.days) in hm(16, 0)..hm(17, 30))
    }

    @Test fun aWholeTableThatIsOffByAnHourIsRejectedAsImplausible() {
        // Syria has no summer/winter time (permanent +03), so a real update never moves everything by an hour.
        // If a download ever looks like that it is a wrong table, and the range net must stop it.
        val shifted = published.mapValues { (_, v) -> DayTimes(IntArray(6) { v.m[it] + 60 }) }
        assertFalse(TimetableCheck.check(shifted).ok)
    }

    @Test fun damascusIsPlusThreeAllYear() {
        for (m in 1..12) {
            val off = java.time.ZonedDateTime.of(2026, m, 15, 12, 0, 0, 0, DAMASCUS).offset.totalSeconds
            assertEquals("month $m", 3 * 3600, off)
        }
    }

    @Test fun rejectsPagesThatAreNotTheTimetable() {
        assertNull(MawaqitParser.parse("<html>no data</html>"))
        // The page escapes the slash ("Asia\\/Damascus"); make sure the replacement really changes the page.
        val otherZone = page().replace("Asia\\/Damascus", "Europe\\/Paris")
        assertTrue(otherZone != page())
        assertNull(MawaqitParser.parse(otherZone))
        val otherMosque = page().replace("\\u0627\\u0644\\u0623\\u0645\\u0648\\u064a", "other")
        assertTrue(otherMosque != page())
        assertNull(MawaqitParser.parse(otherMosque))
        val noCalendar = page().replace("\"calendar\":", "\"calendar_x\":")
        assertTrue(noCalendar != page())
        assertNull(MawaqitParser.parse(noCalendar))
        assertNotNull(MawaqitParser.parse(page()))
    }

    @Test fun truncatedPageIsRejectedNotPartiallyParsed() {
        assertNull(MawaqitParser.parse(page().substring(0, page().length / 2)))
    }
}

class MawaqitRetryTest {
    @Test fun networkFailuresRetrySoonButRejectedPagesWaitHours() {
        assertEquals(10L * 60_000L, MawaqitSync.waitAfter(SyncResult.NetworkFailure("x")))
        assertEquals(6L * 60 * 60_000L, MawaqitSync.waitAfter(SyncResult.Rejected("x")))
        assertEquals(6L * 60 * 60_000L, MawaqitSync.waitAfter(SyncResult.Updated("x")))
        assertEquals(6L * 60 * 60_000L, MawaqitSync.waitAfter(null))
    }
}
