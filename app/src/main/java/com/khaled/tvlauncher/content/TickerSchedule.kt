package com.khaled.tvlauncher.content

import java.time.LocalTime

enum class TickerMode { MORNING, EVENING, NONE }

object TickerSchedule {
    private val morningStart = LocalTime.of(6, 0)
    private val morningEnd = LocalTime.of(13, 0)
    private val eveningStart = LocalTime.of(18, 0)
    private val eveningEnd = LocalTime.of(23, 0)

    /** 06:00-13:00 morning azkar, 18:00-23:00 evening azkar (11 pm at the latest). End times are exclusive. */
    fun modeAt(time: LocalTime): TickerMode = when {
        !time.isBefore(morningStart) && time.isBefore(morningEnd) -> TickerMode.MORNING
        !time.isBefore(eveningStart) && time.isBefore(eveningEnd) -> TickerMode.EVENING
        else -> TickerMode.NONE
    }
}
