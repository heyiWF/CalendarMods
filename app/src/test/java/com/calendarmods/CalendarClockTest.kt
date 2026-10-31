package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarClockTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int,
        millis: Int, zone: String): ClockSnapshot {
        val timeZone = TimeZone.getTimeZone(zone)
        val calendar = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
            set(Calendar.MILLISECOND, millis)
        }
        return ClockSnapshot(calendar.timeInMillis, timeZone)
    }

    @Test fun midnightCrossesYearWithoutWaitingForPollingInterval() {
        assertEquals(250L, millisUntilNextDate(at(2026, 12, 31, 23, 59, 59, 750, "Asia/Shanghai")))
    }

    @Test fun leapDayRollsIntoMarch() {
        assertEquals(1_000L, millisUntilNextDate(at(2024, 2, 29, 23, 59, 59, 0, "Asia/Shanghai")))
    }

    @Test fun daylightSavingDaysDoNotAssumeTwentyFourHours() {
        assertEquals(23 * 3_600_000L, millisUntilNextDate(at(2026, 3, 8, 0, 0, 0, 0, "America/New_York")))
        assertEquals(25 * 3_600_000L, millisUntilNextDate(at(2026, 11, 1, 0, 0, 0, 0, "America/New_York")))
    }

    @Test fun naturalBoundaryNeedsTheSameZoneAndAConsecutiveDate() {
        val before = at(2026, 12, 31, 23, 59, 40, 0, "Asia/Shanghai")
        val after = at(2027, 1, 1, 0, 0, 3, 0, "Asia/Shanghai")
        assertEquals(true, naturalDateBoundary(before, after))
        assertEquals(false, naturalDateBoundary(before, at(2027, 1, 1, 0, 0, 3, 0, "Asia/Tokyo")))
        assertEquals(false, naturalDateBoundary(at(2026, 12, 30, 23, 59, 40, 0, "Asia/Shanghai"), after))
    }
}
