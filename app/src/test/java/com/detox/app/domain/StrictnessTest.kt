package com.detox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

/** Which edits make a quiet time looser (-> countdown) and which only stricter (-> instant)? */
class StrictnessTest {

    private val night = QuietWindow(
        1, "Night", QuietWindow.Kind.NIGHT, LocalTime.of(23, 30), LocalTime.of(7, 0), QuietWindow.EVERY_DAY,
    )

    @Test fun `starting earlier is stricter`() =
        assertTrue(RuleEngine.isAtLeastAsStrict(night.copy(start = LocalTime.of(22, 0)), night))

    @Test fun `ending later is stricter`() =
        assertTrue(RuleEngine.isAtLeastAsStrict(night.copy(end = LocalTime.of(8, 0)), night))

    @Test fun `starting later is looser`() =
        assertFalse(RuleEngine.isAtLeastAsStrict(night.copy(start = LocalTime.of(0, 30)), night))

    @Test fun `removing Saturday is looser`() =
        assertFalse(RuleEngine.isAtLeastAsStrict(night.copy(days = QuietWindow.EVERY_DAY - DayOfWeek.SATURDAY), night))

    @Test fun `switching it off is looser`() =
        assertFalse(RuleEngine.isAtLeastAsStrict(night.copy(enabled = false), night))

    @Test fun `only renaming changes nothing`() =
        assertTrue(RuleEngine.isAtLeastAsStrict(night.copy(name = "Sleep"), night))

    @Test fun `editing a rule that was off is never a loosening`() =
        assertTrue(RuleEngine.isAtLeastAsStrict(night.copy(start = LocalTime.of(1, 0)), night.copy(enabled = false)))
}
