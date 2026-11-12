package com.detox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/** Strict mode in the rule engine. 2026-11-13 is a Friday. */
class StrictModeTest {

    private val night = QuietWindow(
        1, "Night", QuietWindow.Kind.NIGHT, LocalTime.of(23, 30), LocalTime.of(7, 0), QuietWindow.EVERY_DAY,
    )
    private val morning = QuietWindow(
        2, "Morning", QuietWindow.Kind.MORNING, LocalTime.of(7, 0), LocalTime.of(9, 0), QuietWindow.EVERY_DAY,
    )
    private val windows = listOf(night, morning)

    private val instagram = "com.instagram.android"
    private val telegram = "org.telegram.messenger"
    private val apps = listOf(PausedApp("Instagram", instagram))

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test fun `strict mode blocks a paused app in the afternoon, with no end time`() {
        val decision = RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows, strictOn = true)
        assertTrue(decision is Decision.Block)
        decision as Decision.Block
        assertEquals(Reason.Strict, decision.reason)
        assertNull(decision.until)        // until you switch strict mode off
    }

    @Test fun `strict mode never blocks an app that is not paused`() =
        assertEquals(
            Decision.Allow,
            RuleEngine.decide(telegram, at("2026-11-13T15:00"), apps, windows, strictOn = true),
        )

    @Test fun `quiet time still wins in strict mode and reports its end time`() {
        val decision = RuleEngine.decide(instagram, at("2026-11-13T00:34"), apps, windows, strictOn = true)
        decision as Decision.Block
        assertTrue(decision.reason is Reason.QuietTime)
        assertEquals(at("2026-11-13T09:00"), decision.until)   // Night + Morning, back to back
    }

    @Test fun `strict mode off keeps the old behaviour`() =
        assertEquals(
            Decision.Allow,
            RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows, strictOn = false),
        )

    @Test fun `strict mode beats a daily limit that is not used up`() {
        val limits = LimitSettings(dailyLimitOn = true, dailyLimitMin = 45, dayStart = LocalTime.of(5, 0))
        val used = mapOf(instagram to 5 * 60_000L)    // only 5 of 45 minutes used
        val decision = RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows, limits, used, strictOn = true)
        assertEquals(Reason.Strict, (decision as Decision.Block).reason)
    }
}
