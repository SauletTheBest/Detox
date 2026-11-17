package com.detox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Stats rules: one try per burst, nights that cross midnight, and the calm-night streak. */
class StatsTest {

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    // ---- TryDeduper ----

    @Test fun `a burst of blocks is one try`() {
        val d = TryDeduper()
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 0))       // splash
        assertFalse(d.isNewTry(ig, TryReason.QUIET, 800))    // main screen
        assertFalse(d.isNewTry(ig, TryReason.QUIET, 1_500))  // another window
    }

    @Test fun `trying again after a pause is a new try`() {
        val d = TryDeduper()
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 0))
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 60_000))
    }

    @Test fun `the 5 seconds slide with the burst`() {
        val d = TryDeduper()
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 0))
        assertFalse(d.isNewTry(ig, TryReason.QUIET, 4_000))
        assertFalse(d.isNewTry(ig, TryReason.QUIET, 8_000))  // 4 s after the LAST one: same burst
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 13_000))
    }

    @Test fun `different apps or reasons are different tries`() {
        val d = TryDeduper()
        assertTrue(d.isNewTry(ig, TryReason.QUIET, 0))
        assertTrue(d.isNewTry(yt, TryReason.QUIET, 100))
        assertTrue(d.isNewTry(ig, TryReason.REELS, 200))
    }

    // ---- Nights ----

    @Test fun `a night that crosses midnight is one night`() {
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(monday, NightStats.nightOf(LocalDateTime.of(2026, 11, 16, 23, 45)))
        assertEquals(monday, NightStats.nightOf(LocalDateTime.of(2026, 11, 17, 2, 0)))
        assertEquals(monday, NightStats.nightOf(LocalDateTime.of(2026, 11, 17, 6, 30)))
        // Tuesday evening already belongs to Tuesday night
        assertEquals(monday.plusDays(1), NightStats.nightOf(LocalDateTime.of(2026, 11, 17, 23, 31)))
    }

    @Test fun `last night is the one that started yesterday evening`() {
        val monday = LocalDate.of(2026, 11, 16)
        assertEquals(monday, NightStats.lastNight(LocalDateTime.of(2026, 11, 17, 10, 0)))
        assertEquals(monday, NightStats.lastNight(LocalDateTime.of(2026, 11, 17, 23, 50)))
    }

    @Test fun `calm streak counts back until a night with tries`() {
        val last = LocalDate.of(2026, 11, 16)
        val tries = setOf(last.minusDays(3))                  // Friday night: you tried
        val first = last.minusDays(30)
        assertEquals(3, NightStats.calmStreak(last, tries, first))  // Mon, Sun, Sat
    }

    @Test fun `calm streak is zero after a night with tries`() {
        val last = LocalDate.of(2026, 11, 16)
        assertEquals(0, NightStats.calmStreak(last, setOf(last), last.minusDays(30)))
    }

    @Test fun `calm streak never goes back before stats started`() {
        val last = LocalDate.of(2026, 11, 16)
        assertEquals(2, NightStats.calmStreak(last, emptySet(), firstNight = last.minusDays(1)))
        assertEquals(0, NightStats.calmStreak(last, emptySet(), firstNight = last.plusDays(1)))
    }

    @Test fun `block reasons map to stored reasons`() {
        val night = QuietWindow.defaults.first()
        assertEquals(TryReason.QUIET, Reason.QuietTime(night).toTryReason())
        assertEquals(night.kind, Reason.QuietTime(night).windowKind())
        assertEquals(TryReason.STRICT, Reason.Strict.toTryReason())
        assertEquals(null, Reason.Strict.windowKind())
        assertEquals(TryReason.APP_LIMIT, Reason.AppLimit(30).toTryReason())
        assertEquals(TryReason.TOTAL_LIMIT, Reason.TotalLimit(45).toTryReason())
    }
}
