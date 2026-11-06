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

    @Test fun twentyFourHourReadingAlwaysKeepsTwoDigits() {
        assertEquals("00:05", formatClock(true, 0, 5).text)
        assertEquals("09:05", formatClock(true, 9, 5).text)
        assertEquals("23:59", formatClock(true, 23, 59).text)
    }

    @Test fun twelveHourReadingPrintsTheMarkAndDropsTheLeadingZero() {
        assertEquals("上午9:05", formatClock(false, 9, 5).text)
        assertEquals("上午12:05", formatClock(false, 0, 5).text)
        assertEquals("上午11:59", formatClock(false, 11, 59).text)
        assertEquals("下午12:30", formatClock(false, 12, 30).text)
        assertEquals("下午11:59", formatClock(false, 23, 59).text)
        assertEquals("", formatClock(true, 9, 5).period)
    }
}
