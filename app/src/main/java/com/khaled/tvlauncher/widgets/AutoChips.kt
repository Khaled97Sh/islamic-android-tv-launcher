package com.khaled.tvlauncher.widgets

import com.khaled.tvlauncher.prayer.PrayerTimeProvider
import com.khaled.tvlauncher.time.clock12
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime

/** "h:mm" for a countdown, e.g. 1:55 or 0:45. */
fun hmClock(d: Duration): String = "%d:%02d".format(d.toHours(), d.toMinutes() % 60)

/** A small automatic card shown next to the weather (same size), only when it is relevant. */
class ChipInfo(val id: String, val big: String, val line1: String, val line2: String)

object AutoChips {
    /** The last-third chip appears this long before the last third starts, and stays until Fajr. */
    private val LEAD = Duration.ofHours(2)

    /** Thursday: a reminder; Friday: countdown to the Jumuah prayer (Dhuhr time of the mosque timetable), then a blessing. */
    fun friday(now: ZonedDateTime): ChipInfo? {
        val date = now.toLocalDate()
        return when (date.dayOfWeek) {
            DayOfWeek.THURSDAY -> ChipInfo("friday", "غداً", "يوم الجمعة", "تذكير بسورة الكهف")
            DayOfWeek.FRIDAY -> {
                val dhuhr = PrayerTimeProvider.forDate(date)[1].at
                val left = Duration.between(now.toInstant(), dhuhr)
                if (left.isNegative || left.isZero) ChipInfo("friday", "جمعة", "مباركة", "سورة الكهف")
                else ChipInfo("friday", hmClock(left), "حتى صلاة الجمعة", clock12(dhuhr))
            }
            else -> null
        }
    }

    fun lastThirdShown(now: Instant): Boolean {
        val w = NightThird.at(now)
        return !now.isBefore(w.start.minus(LEAD)) && now.isBefore(w.end)
    }

    fun lastThird(now: ZonedDateTime): ChipInfo? {
        val inst = now.toInstant()
        if (!lastThirdShown(inst)) return null
        val w = NightThird.at(inst)
        return if (inst.isBefore(w.start)) {
            ChipInfo("last_third", hmClock(Duration.between(inst, w.start)), "حتى الثلث الأخير", "يبدأ ${clock12(w.start)}")
        } else {
            ChipInfo("last_third", hmClock(Duration.between(inst, w.end)), "الثلث الأخير الآن", "حتى الفجر ${clock12(w.end)}")
        }
    }

    /** Chips in display order (next to the weather). */
    fun at(now: ZonedDateTime): List<ChipInfo> = listOfNotNull(friday(now), lastThird(now))
}

