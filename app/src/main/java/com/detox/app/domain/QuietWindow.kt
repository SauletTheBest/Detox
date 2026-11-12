package com.detox.app.domain

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A "quiet time": paused apps are blocked between [start] and [end] on [days].
 *
 * Plain Kotlin, no Android inside -> we can unit-test it on the PC (see QuietWindowTest).
 *
 * [days] are the days on which the window STARTS. So "Night, Mon–Fri, 23:30–07:00"
 * also covers Saturday 00:00–07:00, because that is still Friday's night.
 */
data class QuietWindow(
    val id: Int,
    val name: String,
    val kind: Kind,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
) {
    /** Only decides the icon (moon / sun). */
    enum class Kind {
        NIGHT, MORNING;

        companion object {
            /** Moon for evening/night starts (18:00–04:59), sun for daytime starts. */
            fun forStart(start: LocalTime): Kind =
                if (start.hour >= 18 || start.hour < 5) NIGHT else MORNING
        }
    }

    /** 23:30–07:00 goes over midnight; 07:00–09:00 does not. */
    val crossesMidnight: Boolean get() = end <= start

    fun isActive(now: LocalDateTime): Boolean {
        if (!enabled) return false
        val time = now.toLocalTime()
        val today = now.dayOfWeek
        return if (!crossesMidnight) {
            today in days && time >= start && time < end
        } else {
            // Evening part: 23:30..24:00, belongs to TODAY.
            val eveningPart = today in days && time >= start
            // After-midnight part: 00:00..07:00, belongs to YESTERDAY's window.
            val afterMidnightPart = today.minus(1) in days && time < end
            eveningPart || afterMidnightPart
        }
    }

    /** Only valid while active: the moment this window ends (tomorrow 07:00 if we are in the 23:30 part). */
    fun activeEnd(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        return if (crossesMidnight && now.toLocalTime() >= start) today.plusDays(1).atTime(end)
        else today.atTime(end)
    }

    /** When does this window start next (strictly after [now])? null if never. */
    fun nextStart(now: LocalDateTime): LocalDateTime? {
        if (!enabled || days.isEmpty()) return null
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = date.atTime(start)
            if (date.dayOfWeek in days && candidate.isAfter(now)) return candidate
        }
        return null
    }

    fun daysLabel(): String = when {
        days.size == 7 -> "Every day"
        days == WEEKDAYS -> "Mon – Fri"
        days == WEEKEND -> "Weekends"
        days.isEmpty() -> "No days"
        else -> DayOfWeek.values()
            .filter { it in days }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }

    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.values().toSet()

        /** Starting values until editing + saving arrives (database step). */
        val defaults = listOf(
            QuietWindow(1, "Night", Kind.NIGHT, LocalTime.of(23, 30), LocalTime.of(7, 0), EVERY_DAY),
            QuietWindow(2, "Morning", Kind.MORNING, LocalTime.of(7, 0), LocalTime.of(9, 0), EVERY_DAY),
        )
    }
}
