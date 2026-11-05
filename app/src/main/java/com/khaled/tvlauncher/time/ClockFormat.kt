package com.khaled.tvlauncher.time

import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** 12-hour clock text such as "05:32 صباحاً" ([suffix] false gives just "05:32"). */
fun clock12(t: LocalTime, suffix: Boolean = true): String {
    val h = t.hour % 12
    val base = "%02d:%02d".format(if (h == 0) 12 else h, t.minute)
    return if (suffix) base + if (t.hour < 12) " صباحاً" else " مساءً" else base
}

fun clock12(at: Instant, suffix: Boolean = true): String = clock12(ZonedDateTime.ofInstant(at, DAMASCUS).toLocalTime(), suffix)
