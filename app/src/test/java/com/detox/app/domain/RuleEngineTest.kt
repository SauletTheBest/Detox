package com.detox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/** 2026-11-13 is a Friday. */
class RuleEngineTest {

    private val night = QuietWindow(
        1, "Night", QuietWindow.Kind.NIGHT, LocalTime.of(23, 30), LocalTime.of(7, 0), QuietWindow.EVERY_DAY,
    )
    private val morning = QuietWindow(
        2, "Morning", QuietWindow.Kind.MORNING, LocalTime.of(7, 0), LocalTime.of(9, 0), QuietWindow.EVERY_DAY,
    )
    private val windows = listOf(night, morning)

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val telegram = "org.telegram.messenger"
    private val apps = listOf(
        PausedApp("Instagram", instagram),
        PausedApp("YouTube", youtube, dailyLimitMin = 20),   // YouTube has its own 20-min limit
    )
    private val limitOn = LimitSettings(dailyLimitOn = true, dailyLimitMin = 45, dayStart = LocalTime.of(5, 0))

    private fun at(text: String) = LocalDateTime.parse(text)
    private fun min(m: Int) = m * 60_000L

    // ---- quiet times ------------------------------------------------------

    @Test fun `paused app at night is blocked until the morning window ends`() {
        val decision = RuleEngine.decide(instagram, at("2026-11-13T00:34"), apps, windows)
        assertTrue(decision is Decision.Block)
        decision as Decision.Block
        assertEquals("Night", (decision.reason as Reason.QuietTime).window.name)
        assertEquals(at("2026-11-13T09:00"), decision.until)   // Night then Morning, back to back
    }

    @Test fun `apps that are not paused are never blocked`() =
        assertEquals(Decision.Allow, RuleEngine.decide(telegram, at("2026-11-13T00:34"), apps, windows))

    @Test fun `paused app in the afternoon is allowed`() =
        assertEquals(Decision.Allow, RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows))

    @Test fun `timer for a paused app points to tonight 23-30`() =
        assertEquals(at("2026-11-13T23:30"), RuleEngine.nextBlockStart(instagram, at("2026-11-13T15:00"), apps, windows))

    @Test fun `no timer for apps that are not paused`() =
        assertNull(RuleEngine.nextBlockStart(telegram, at("2026-11-13T15:00"), apps, windows))

    @Test fun `with only the night window, quiet ends at 07-00 next day`() =
        assertEquals(at("2026-11-14T07:00"), RuleEngine.blockedUntil(at("2026-11-13T23:45"), listOf(night)))

    @Test fun `round-the-clock window does not loop forever`() {
        val always = QuietWindow(
            3, "Always", QuietWindow.Kind.NIGHT, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, QuietWindow.EVERY_DAY,
        )
        val now = at("2026-11-13T12:00")
        assertTrue(RuleEngine.blockedUntil(now, listOf(always)).isAfter(now))
    }

    // ---- daily limits -----------------------------------------------------

    @Test fun `shared budget used up - blocked until tomorrow 05-00`() {
        val used = mapOf(instagram to min(30), youtube to min(15))          // 45 of 45
        val decision = RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows, limitOn, used)
        assertEquals(Decision.Block(apps[0], Reason.TotalLimit(45), at("2026-11-14T05:00")), decision)
    }

    @Test fun `shared budget not used up - allowed`() {
        val used = mapOf(instagram to min(30), youtube to min(10))          // 40 of 45
        assertEquals(Decision.Allow, RuleEngine.decide(instagram, at("2026-11-13T15:00"), apps, windows, limitOn, used))
    }

    @Test fun `own app limit blocks even when the shared limit is off`() {
        val used = mapOf(youtube to min(20))
        val decision = RuleEngine.decide(youtube, at("2026-11-13T15:00"), apps, windows, LimitSettings(), used)
        assertEquals(Reason.AppLimit(20), (decision as Decision.Block).reason)
    }

    @Test fun `quiet time wins over limits`() {
        val used = mapOf(instagram to min(60))
        val decision = RuleEngine.decide(instagram, at("2026-11-13T23:45"), apps, windows, limitOn, used)
        assertTrue((decision as Decision.Block).reason is Reason.QuietTime)
    }

    @Test fun `time left is the smaller of own limit and shared budget`() {
        val used = mapOf(instagram to min(30), youtube to min(5))           // shared: 10 left, own YouTube: 15 left
        assertEquals(min(10), RuleEngine.timeLeftMs(youtube, apps, limitOn, used))
    }

    @Test fun `no limits means no time-left timer`() =
        assertNull(RuleEngine.timeLeftMs(instagram, apps, LimitSettings(), emptyMap()))

    @Test fun `at 03-00 the day still started yesterday at 05-00`() =
        assertEquals(at("2026-11-12T05:00"), RuleEngine.dayStartOf(at("2026-11-13T03:00"), LocalTime.of(5, 0)))

    @Test fun `at 05-00 sharp a new day starts`() =
        assertEquals(at("2026-11-13T05:00"), RuleEngine.dayStartOf(at("2026-11-13T05:00"), LocalTime.of(5, 0)))
}
