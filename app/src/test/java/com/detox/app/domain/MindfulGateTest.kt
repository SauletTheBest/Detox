package com.detox.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mindful pause: pause first, then a 10-minute grace after "Open", then pause again. */
class MindfulGateTest {

    private val youtube = "com.google.android.youtube"
    private val chess = "com.chess"
    private val minute = 60_000L
    private val start = 1_000_000L   // any "now" works: the gate only compares times

    @Test fun `first open needs a pause`() {
        val gate = MindfulGate()
        assertTrue(gate.needsPause(youtube, start))
    }

    @Test fun `after allow there is no pause for 10 minutes`() {
        val gate = MindfulGate()
        gate.allow(youtube, start)
        assertFalse(gate.needsPause(youtube, start + 1))
        assertFalse(gate.needsPause(youtube, start + 9 * minute))
    }

    @Test fun `after 10 minutes the pause is back`() {
        val gate = MindfulGate()
        gate.allow(youtube, start)
        assertTrue(gate.needsPause(youtube, start + 10 * minute))
        assertTrue(gate.needsPause(youtube, start + 60 * minute))
    }

    @Test fun `allowing one app does not let another through`() {
        val gate = MindfulGate()
        gate.allow(youtube, start)
        assertTrue(gate.needsPause(chess, start + 1))
    }

    @Test fun `allowing again starts a fresh 10 minutes`() {
        val gate = MindfulGate()
        gate.allow(youtube, start)
        gate.allow(youtube, start + 8 * minute)
        assertFalse(gate.needsPause(youtube, start + 15 * minute))
    }
}
