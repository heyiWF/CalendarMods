package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class CalendarClockTest {
    @Test fun midnightCrossesYearWithoutWaitingForPollingInterval() {
        assertEquals(250L, millisUntilNextDate(ZonedDateTime.parse("2026-12-31T23:59:59.750+08:00[Asia/Shanghai]")))
    }

    @Test fun leapDayRollsIntoMarch() {
        assertEquals(1_000L, millisUntilNextDate(ZonedDateTime.parse("2024-02-29T23:59:59+08:00[Asia/Shanghai]")))
    }

    @Test fun daylightSavingDaysDoNotAssumeTwentyFourHours() {
        assertEquals(23 * 3_600_000L, millisUntilNextDate(ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]")))
        assertEquals(25 * 3_600_000L, millisUntilNextDate(ZonedDateTime.parse("2026-11-01T00:00:00-04:00[America/New_York]")))
    }
}
