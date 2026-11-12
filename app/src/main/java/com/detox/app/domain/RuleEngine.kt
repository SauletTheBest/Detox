package com.detox.app.domain

import java.time.LocalDateTime
import java.time.LocalTime

/** The answer to "may this app be open right now?" */
sealed interface Decision {
    data object Allow : Decision

    /**
     * Blocked because of [reason]; allowed again at [until].
     * until = null: there is no clock time — strict mode is on, and the app stays closed
     * until you switch strict mode off yourself.
     */
    data class Block(val app: PausedApp, val reason: Reason, val until: LocalDateTime?) : Decision
}

/** WHY an app is blocked (the block screen shows a different text for each). */
sealed interface Reason {
    data class QuietTime(val window: QuietWindow) : Reason
    data object Strict : Reason                         // strict mode: paused apps closed all day
    data class AppLimit(val minutes: Int) : Reason      // this app's own daily limit
    data class TotalLimit(val minutes: Int) : Reason    // shared budget of all paused apps
}

/**
 * The brain. Pure Kotlin: no Android, no clock of its own (you pass `now`),
 * so every rule can be unit-tested on the PC (see RuleEngineTest).
 */
object RuleEngine {

    /**
     * @param usedMs   milliseconds each paused app was on screen since the day started
     *                 (empty = we don't know / no limits in use).
     * @param strictOn strict mode: every paused app is blocked all day long.
     *                 Last parameter with a default, so older calls (and tests) don't change.
     */
    fun decide(
        packageName: String,
        now: LocalDateTime,
        apps: List<PausedApp>,
        windows: List<QuietWindow>,
        limits: LimitSettings = LimitSettings(),
        usedMs: Map<String, Long> = emptyMap(),
        strictOn: Boolean = false,
    ): Decision {
        val app = apps.firstOrNull { it.packageName == packageName } ?: return Decision.Allow

        // 1) Quiet time beats everything. It goes first because it has a real end time
        //    to show ("paused until 07:00"), which is more helpful than "until you switch it off".
        windows.firstOrNull { it.isActive(now) }?.let { window ->
            return Decision.Block(app, Reason.QuietTime(window), blockedUntil(now, windows))
        }

        // 2) Strict mode: paused apps are closed all day. No end time (null).
        if (strictOn) return Decision.Block(app, Reason.Strict, until = null)

        // 3) Daily limits: blocked until the next day starts (e.g. tomorrow 05:00).
        val nextDay = nextDayStart(now, limits.dayStart)
        app.dailyLimitMin?.let { limit ->
            if ((usedMs[packageName] ?: 0L) >= limit * MINUTE_MS) {
                return Decision.Block(app, Reason.AppLimit(limit), nextDay)
            }
        }
        if (limits.dailyLimitOn && totalUsedMs(apps, usedMs) >= limits.dailyLimitMin * MINUTE_MS) {
            return Decision.Block(app, Reason.TotalLimit(limits.dailyLimitMin), nextDay)
        }
        return Decision.Allow
    }

    /** How long this paused app may still stay open today (smallest of its own and the shared budget). null = no limit. */
    fun timeLeftMs(packageName: String, apps: List<PausedApp>, limits: LimitSettings, usedMs: Map<String, Long>): Long? {
        val app = apps.firstOrNull { it.packageName == packageName } ?: return null
        val own = app.dailyLimitMin?.let { it * MINUTE_MS - (usedMs[packageName] ?: 0L) }
        val shared = if (limits.dailyLimitOn) limits.dailyLimitMin * MINUTE_MS - totalUsedMs(apps, usedMs) else null
        return listOfNotNull(own, shared).minOrNull()?.coerceAtLeast(0L)
    }

    fun totalUsedMs(apps: List<PausedApp>, usedMs: Map<String, Long>): Long =
        apps.sumOf { usedMs[it.packageName] ?: 0L }

    /** When did "today" start? At 03:00 with dayStart 05:00 -> yesterday 05:00. */
    fun dayStartOf(now: LocalDateTime, dayStart: LocalTime): LocalDateTime {
        val todayStart = now.toLocalDate().atTime(dayStart)
        return if (now.isBefore(todayStart)) todayStart.minusDays(1) else todayStart
    }

    fun nextDayStart(now: LocalDateTime, dayStart: LocalTime): LocalDateTime =
        dayStartOf(now, dayStart).plusDays(1)

    /**
     * When does the quiet period end? Follows windows that touch each other:
     * Night 23:30–07:00 + Morning 07:00–09:00  ->  quiet until 09:00, not 07:00.
     */
    fun blockedUntil(now: LocalDateTime, windows: List<QuietWindow>): LocalDateTime {
        var t = now
        // Safety cap: windows that cover the whole day would otherwise chain forever.
        repeat(MAX_CHAIN) {
            val active = windows.filter { it.isActive(t) }
            if (active.isEmpty()) return t
            t = active.maxOf { it.activeEnd(t) }
        }
        return t
    }

    /**
     * For a paused app that is ALLOWED right now: when does the next quiet time start?
     * null = not a paused app, or no quiet time coming.
     */
    fun nextBlockStart(
        packageName: String,
        now: LocalDateTime,
        apps: List<PausedApp>,
        windows: List<QuietWindow>,
    ): LocalDateTime? {
        if (apps.none { it.packageName == packageName }) return null
        return windows.mapNotNull { it.nextStart(now) }.minOrNull()
    }

    /**
     * Is [new] at least as strict as [old]? True when every minute of a week that [old]
     * blocked is still blocked by [new]. Then saving an edit can't loosen anything and
     * needs no countdown. We simply test all 7 × 24 × 60 = 10,080 minutes: brute force,
     * but fast (a few milliseconds) and impossible to get subtly wrong.
     */
    fun isAtLeastAsStrict(new: QuietWindow, old: QuietWindow): Boolean {
        if (!old.enabled) return true    // the old rule protected nothing
        if (!new.enabled) return false
        val monday = LocalDateTime.of(2024, 1, 1, 0, 0)   // 2024-01-01 was a Monday
        for (minute in 0L until 7L * 24 * 60) {
            val t = monday.plusMinutes(minute)
            if (old.isActive(t) && !new.isActive(t)) return false
        }
        return true
    }

    private const val MAX_CHAIN = 20
    private const val MINUTE_MS = 60_000L
}
