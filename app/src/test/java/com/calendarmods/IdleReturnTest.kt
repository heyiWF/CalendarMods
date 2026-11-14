package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class IdleReturnTest {
    private class MovableClock(var millis: Long) : CalendarClock {
        override fun now(): ClockSnapshot = ClockSnapshot(millis, TimeZone.getDefault())
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test fun theCountdownRunsOnlyForABrowsedPageInInteractiveMode() {
        assertTrue(IdleReturn.armed(immersive = true, interactive = true, browsed = true))
        // A page that already follows today has nothing to return to.
        assertFalse(IdleReturn.armed(immersive = true, interactive = true, browsed = false))
        // The feature rides on the interactive calendar instead of a switch of its own.
        assertFalse(IdleReturn.armed(immersive = true, interactive = false, browsed = true))
        assertFalse(IdleReturn.armed(immersive = false, interactive = true, browsed = true))
    }

    @Test fun aCountdownThatStraddlesMidnightLandsOnTheNewDay() {
        // 23:59:40 on the 14th: the user turns the page to the 15th and the countdown runs past
        // midnight. The monitor calls the 15th today before the countdown expires, so that is the
        // day the page lands on; stepping back to the 14th would undo the user's turn.
        val clock = MovableClock(at(2026, 11, 14, 23, 59, 40))
        val browsed = PlainDate.of(2026, 11, 15)
        assertEquals(PlainDate.of(2026, 11, 14), IdleReturn.target(PlainDate.of(2026, 11, 14), clock))
        clock.millis = at(2026, 11, 15, 0, 0, 20)
        assertEquals(browsed, IdleReturn.target(PlainDate.of(2026, 11, 15), clock))
    }

    @Test fun withoutALoadedDayTheClockDecides() {
        val clock = MovableClock(at(2026, 11, 14, 15, 30, 0))
        assertEquals(PlainDate.of(2026, 11, 14), IdleReturn.target(null, clock))
    }
}
