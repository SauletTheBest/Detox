package com.detox.app.domain

import com.detox.app.domain.UsageCalculator.Event
import com.detox.app.domain.UsageCalculator.Type
import org.junit.Assert.assertEquals
import org.junit.Test

/** Times are in minutes for readability; m(5) = 5 minutes after the period started. */
class UsageCalculatorTest {

    private val insta = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private fun m(minutes: Int) = minutes * 60_000L
    private fun ev(pkg: String, type: Type, minute: Int, activity: String = "Main") = Event(pkg, activity, type, m(minute))

    @Test fun `one simple session`() {
        val events = listOf(ev(insta, Type.RESUMED, 10), ev(insta, Type.PAUSED, 25))
        assertEquals(mapOf(insta to m(15)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }

    @Test fun `app still open right now counts up to now`() {
        val events = listOf(ev(insta, Type.RESUMED, 50))
        assertEquals(mapOf(insta to m(10)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }

    @Test fun `session that started before the day start counts only from the day start`() {
        // Opened at 04:50, closed at 05:10 -> only 05:00..05:10 belongs to "today".
        val events = listOf(ev(insta, Type.PAUSED, 10))
        assertEquals(mapOf(insta to m(10)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }

    @Test fun `screen off ends every open session`() {
        val events = listOf(ev(yt, Type.RESUMED, 0), Event("", "", Type.STOP_ALL, m(20)))
        assertEquals(mapOf(yt to m(20)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }

    @Test fun `two apps and two sessions are added up separately`() {
        val events = listOf(
            ev(insta, Type.RESUMED, 0), ev(insta, Type.PAUSED, 5),
            ev(yt, Type.RESUMED, 5), ev(yt, Type.PAUSED, 15),
            ev(insta, Type.RESUMED, 20), ev(insta, Type.PAUSED, 30),
        )
        assertEquals(mapOf(insta to m(15), yt to m(10)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }

    @Test fun `switching screens inside one app keeps counting`() {
        val events = listOf(
            ev(insta, Type.RESUMED, 0, "Feed"), ev(insta, Type.PAUSED, 5, "Feed"),
            ev(insta, Type.RESUMED, 5, "Reels"), ev(insta, Type.PAUSED, 12, "Reels"),
        )
        assertEquals(mapOf(insta to m(12)), UsageCalculator.foregroundMs(events, m(0), m(60)))
    }
}
