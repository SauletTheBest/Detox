package com.detox.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Runs on the PC (no phone needed):  gradlew testDebugUnitTest
 * 2026-11-13 is a Friday, 2026-11-14 Saturday, 2026-11-16 Monday.
 */
class QuietWindowTest {

    private val nightWeekdays = QuietWindow(
        id = 1, name = "Night", kind = QuietWindow.Kind.NIGHT,
        start = LocalTime.of(23, 30), end = LocalTime.of(7, 0),
        days = QuietWindow.WEEKDAYS,
    )

    private val morningWeekdays = QuietWindow(
        id = 2, name = "Morning", kind = QuietWindow.Kind.MORNING,
        start = LocalTime.of(7, 0), end = LocalTime.of(9, 0),
        days = QuietWindow.WEEKDAYS,
    )

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test fun `night is active in the evening part`() =
        assertTrue(nightWeekdays.isActive(at("2026-11-13T23:45")))           // Fri 23:45

    @Test fun `Friday night continues after midnight into Saturday`() =
        assertTrue(nightWeekdays.isActive(at("2026-11-14T00:34")))           // Sat 00:34

    @Test fun `Sunday night is not on, so Monday 00-34 is free`() =
        assertFalse(nightWeekdays.isActive(at("2026-11-16T00:34")))          // Mon 00:34

    @Test fun `end time is not included`() =
        assertFalse(nightWeekdays.isActive(at("2026-11-14T07:00")))          // Sat 07:00

    @Test fun `morning window works on a weekday and not on weekend`() {
        assertTrue(morningWeekdays.isActive(at("2026-11-13T08:00")))         // Fri 08:00
        assertFalse(morningWeekdays.isActive(at("2026-11-14T08:00")))        // Sat 08:00
    }

    @Test fun `disabled window is never active`() =
        assertFalse(nightWeekdays.copy(enabled = false).isActive(at("2026-11-13T23:45")))

    @Test fun `next start skips the weekend`() =
        assertEquals(at("2026-11-16T23:30"), nightWeekdays.nextStart(at("2026-11-14T12:00")))

    @Test fun `next start is later today when it has not started yet`() =
        assertEquals(at("2026-11-13T23:30"), nightWeekdays.nextStart(at("2026-11-13T18:00")))

    @Test fun `no days means no next start`() =
        assertNull(nightWeekdays.copy(days = emptySet()).nextStart(at("2026-11-13T18:00")))
}
