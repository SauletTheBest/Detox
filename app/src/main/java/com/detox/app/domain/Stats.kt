package com.detox.app.domain

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Stats, part 1: the rules, in pure Kotlin (tested on the PC in StatsTest).
 *
 * A "try" = you opened something and Detox stopped you (block screen), slowed you down
 * (mindful pause) or closed Reels/Shorts. Every try becomes one row in the block_event table.
 */
enum class TryReason(val label: String) {
    QUIET("Quiet time"),
    STRICT("Strict mode"),
    APP_LIMIT("App limit"),
    TOTAL_LIMIT("Daily limit"),
    MINDFUL("Mindful pause"),
    REELS("Reels & Shorts"),
}

/** The bouncer's block reason -> what we store. Quiet times also keep their kind (night / morning). */
fun Reason.toTryReason(): TryReason = when (this) {
    is Reason.QuietTime -> TryReason.QUIET
    Reason.Strict -> TryReason.STRICT
    is Reason.AppLimit -> TryReason.APP_LIMIT
    is Reason.TotalLimit -> TryReason.TOTAL_LIMIT
}

fun Reason.windowKind(): QuietWindow.Kind? = (this as? Reason.QuietTime)?.window?.kind

/**
 * One open of Instagram can make Android report 2–3 windows in a second (splash -> main),
 * and the bouncer blocks each of them. Counting those as 3 tries would make the stats lie.
 * So: the same app + reason again within 5 seconds is the SAME try.
 * In memory only (like MindfulGate): after a restart the worst case is one extra count.
 */
class TryDeduper(private val windowMs: Long = WINDOW_MS) {

    private val lastSeen = HashMap<String, Long>()   // "pkg|REASON" -> last time we saw it (ms)

    /** @return true = a new try (record it); false = part of the try we already recorded. */
    @Synchronized
    fun isNewTry(packageName: String, reason: TryReason, nowMs: Long): Boolean {
        val key = "$packageName|$reason"
        val last = lastSeen[key]
        lastSeen[key] = nowMs              // a burst keeps sliding: splash, main, main... = one try
        return last == null || nowMs - last >= windowMs
    }

    companion object {
        const val WINDOW_MS = 5_000L
    }
}

/** The ONE deduper shared by the bouncer and the Reels guard (both run in Detox's process). */
object Tries {
    val deduper = TryDeduper()
}

/**
 * Nights. A night crosses midnight (23:30 -> 07:00), so it can't belong to one calendar day.
 * Trick: shift every time back by 12 hours. Then a whole night lands on ONE date:
 *   Mon 23:45 - 12 h = Mon 11:45 -> "Monday night"
 *   Tue 06:30 - 12 h = Mon 18:30 -> "Monday night"   (same night, same date)
 * The SQL in StatsDao uses the same shift, so Kotlin and the database agree.
 */
object NightStats {

    const val NIGHT_SHIFT_HOURS = 12L

    fun nightOf(time: LocalDateTime): LocalDate = time.minusHours(NIGHT_SHIFT_HOURS).toLocalDate()

    /** "Last night" = the night that started yesterday evening (at 02:00 that's the one going on now). */
    fun lastNight(now: LocalDateTime): LocalDate = now.toLocalDate().minusDays(1)

    /**
     * Calm nights in a row: count back from [lastNight] while a night had no night-time tries.
     * Stops at [firstNight] (the first night Detox was recording), otherwise a fresh install
     * would claim "500 calm nights".
     */
    fun calmStreak(lastNight: LocalDate, nightsWithTries: Set<LocalDate>, firstNight: LocalDate): Int {
        var night = lastNight
        var streak = 0
        while (!night.isBefore(firstNight) && night !in nightsWithTries) {
            streak++
            night = night.minusDays(1)
        }
        return streak
    }

    /** Stats started at [sinceMs]-time -> the first FULL night we saw is the one starting that evening. */
    fun firstNight(since: LocalDateTime): LocalDate = since.toLocalDate()
}

// ---- What the Stats screen shows (filled by StatsRepository) ----------------------------

/** One bar of the 7-day chart. pausedMinutes = null when Usage access is off. */
data class DayStat(val day: LocalDate, val tries: Int, val pausedMinutes: Int?)

data class AppStat(val packageName: String, val label: String, val tries: Int)

data class StatsSnapshot(
    val days: List<DayStat>,            // last 7 Detox-days, oldest first, today last
    val todayTries: Int,
    val todayPausedMinutes: Int?,       // minutes in paused apps today; null = no Usage access
    val lastNightTries: Int,
    val calmNights: Int,
    val topApps: List<AppStat>,         // last 7 days, most tries first
    val byReason: List<Pair<TryReason, Int>>, // last 7 days, biggest first
)
