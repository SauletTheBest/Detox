package com.detox.app.data

import com.detox.app.data.db.DbCodec
import com.detox.app.domain.QuietWindow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime

class DbCodecTest {

    @Test fun `23-30 is stored as 1410 minutes and comes back the same`() {
        assertEquals(1410, DbCodec.minutesOf(LocalTime.of(23, 30)))
        assertEquals(LocalTime.of(23, 30), DbCodec.timeOf(1410))
    }

    @Test fun `every day is mask 127, weekdays 31, weekend 96`() {
        assertEquals(127, DbCodec.maskOf(QuietWindow.EVERY_DAY))
        assertEquals(31, DbCodec.maskOf(QuietWindow.WEEKDAYS))    // 1+2+4+8+16
        assertEquals(96, DbCodec.maskOf(QuietWindow.WEEKEND))     // 32+64
    }

    @Test fun `mask round trip keeps the same days`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY)
        assertEquals(days, DbCodec.daysOf(DbCodec.maskOf(days)))
    }

    @Test fun `night icon for evening starts, sun for daytime`() {
        assertEquals(QuietWindow.Kind.NIGHT, QuietWindow.Kind.forStart(LocalTime.of(23, 30)))
        assertEquals(QuietWindow.Kind.NIGHT, QuietWindow.Kind.forStart(LocalTime.of(0, 30)))
        assertEquals(QuietWindow.Kind.MORNING, QuietWindow.Kind.forStart(LocalTime.of(7, 0)))
    }
}
