package com.khaled.tvlauncher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TickerScheduleTest {
    private fun mode(h: Int, m: Int = 0) = TickerSchedule.modeAt(LocalTime.of(h, m))

    @Test fun morningWindow() {
        assertEquals(TickerMode.NONE, mode(5, 59))
        assertEquals(TickerMode.MORNING, mode(6, 0))
        assertEquals(TickerMode.MORNING, mode(12, 59))
        assertEquals(TickerMode.NONE, mode(13, 0))
    }

    @Test fun eveningWindow() {
        assertEquals(TickerMode.NONE, mode(17, 59))
        assertEquals(TickerMode.EVENING, mode(18, 0))
        assertEquals(TickerMode.EVENING, mode(21, 59))
        assertEquals(TickerMode.EVENING, mode(22, 59))
        assertEquals(TickerMode.NONE, mode(23, 0))   // 11 pm at the latest
        assertEquals(TickerMode.NONE, mode(23, 59))
    }

    @Test fun middleOfTheNightIsNone() {
        assertEquals(TickerMode.NONE, mode(0, 0))
        assertEquals(TickerMode.NONE, mode(4, 59))
        assertEquals(TickerMode.NONE, mode(5, 59))
        assertEquals(TickerMode.NONE, mode(15, 30))
    }
}
