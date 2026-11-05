package com.khaled.tvlauncher.prayer

import java.time.LocalTime
import kotlin.math.abs

/** One day of the mosque timetable: fajr, shuruq (sunrise), dhuhr, asr, maghrib, isha, in minutes after midnight. */
class DayTimes(val m: IntArray) {
    init { require(m.size == 6) }
    fun time(i: Int): LocalTime = LocalTime.of(m[i] / 60, m[i] % 60)
    override fun equals(other: Any?) = other is DayTimes && m.contentEquals(other.m)
    override fun hashCode() = m.contentHashCode()
}

/** The outcome of checking a timetable: the usable data plus a record of everything that was repaired. */
class Checked(val days: Map<String, DayTimes>, val repairs: List<String>, val ok: Boolean, val reason: String?)

/**
 * Checks a full-year timetable before it is trusted. Mawaqit publishes whatever the mosque types in, and the mosque's
 * own table has had typos (e.g. an Asr of 14:15 instead of 16:15). A cell is wrong when it is outside the plausible
 * range for that prayer or far from the median of its 30 neighbouring days (this also catches runs of bad days);
 * a wrong cell is rebuilt by interpolating the nearest good days on both sides. If too many cells are wrong the
 * whole table is rejected (format change / garbage) so good data is never replaced by bad.
 */
object TimetableCheck {
    private val NAMES = listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء")
    /** Plausible Damascus ranges in minutes (fajr 03:00-06:30 ... isha 17:30-22:00). Wide on purpose: a sanity net, not a model. */
    private val RANGE = listOf(180..390, 270..480, 690..780, 870..1020, 990..1230, 1050..1320)
    private const val WINDOW = 15
    private const val MAX_DEVIATION = 8        // minutes from the neighbours' median
    private const val MAX_BAD_FRACTION = 0.02  // more than 2% of cells wrong => reject the whole table
    private const val MAX_DAY_STEP = 4         // after repair no prayer may move more than this between two days

    fun key(month: Int, day: Int) = "$month-$day"

    /** Every day of a leap year, in calendar order. */
    private val KEYS: List<String> = run {
        val len = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        (1..12).flatMap { m -> (1..len[m - 1]).map { d -> key(m, d) } }
    }

    fun check(input: Map<String, DayTimes>): Checked {
        if (KEYS.any { it !in input }) return Checked(emptyMap(), emptyList(), false, "missing days")
        val n = KEYS.size
        val cols = Array(6) { c -> IntArray(n) { input.getValue(KEYS[it]).m[c] } }
        val bad = Array(6) { BooleanArray(n) }
        var badCount = 0
        for (c in 0 until 6) for (i in 0 until n) {
            val v = cols[c][i]
            val win = IntArray(2 * WINDOW) { k -> cols[c][Math.floorMod(i + (k - WINDOW).let { if (it >= 0) it + 1 else it }, n)] }.sorted()
            val median = (win[win.size / 2 - 1] + win[win.size / 2]) / 2.0
            if (v !in RANGE[c] || abs(v - median) > MAX_DEVIATION) { bad[c][i] = true; badCount++ }
        }
        if (badCount > n * 6 * MAX_BAD_FRACTION) return Checked(emptyMap(), emptyList(), false, "$badCount wrong cells")

        val fixed = Array(6) { cols[it].copyOf() }
        val repairs = mutableListOf<String>()
        for (c in 0 until 6) for (i in 0 until n) if (bad[c][i]) {
            var p = 1; while (bad[c][Math.floorMod(i - p, n)] && p < n) p++
            var q = 1; while (bad[c][Math.floorMod(i + q, n)] && q < n) q++
            val a = cols[c][Math.floorMod(i - p, n)]; val b = cols[c][Math.floorMod(i + q, n)]
            fixed[c][i] = Math.floor(a + (b - a) * p.toDouble() / (p + q) + 0.5).toInt()
            repairs += "${KEYS[i]} ${NAMES[c]} ${fmt(cols[c][i])} -> ${fmt(fixed[c][i])}"
        }
        for (c in 0 until 6) for (i in 0 until n) if (abs(fixed[c][i] - fixed[c][(i + 1) % n]) > MAX_DAY_STEP) {
            return Checked(emptyMap(), emptyList(), false, "unstable ${NAMES[c]} at ${KEYS[i]}")
        }
        for (i in 0 until n) for (c in 0 until 5) if (fixed[c][i] >= fixed[c + 1][i]) {
            return Checked(emptyMap(), emptyList(), false, "order at ${KEYS[i]}")
        }
        return Checked(KEYS.indices.associate { i -> KEYS[i] to DayTimes(IntArray(6) { c -> fixed[c][i] }) }, repairs, true, null)
    }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
}

