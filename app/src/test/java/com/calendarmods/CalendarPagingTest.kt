package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarPagingTest {
    @Test fun dailyPagingCrossesYearAndLeapDayInBothDirections() {
        val year = CalendarPaging(PlainDate.of(2026, 12, 31))
        assertEquals(PlainDate.of(2027, 1, 1), year.date(year.home + 1))
        val leap = CalendarPaging(PlainDate.of(2028, 2, 29))
        assertEquals(PlainDate.of(2028, 2, 28), leap.date(leap.home - 1))
        assertEquals(PlainDate.of(2028, 3, 1), leap.date(leap.home + 1))
        assertEquals(leap.home, leap.page(leap.origin))
    }

    @Test fun followingTodayReanchorsWithoutMovingThePager() {
        val pages = CalendarPaging(PlainDate.of(2026, 12, 31))
        val next = PlainDate.of(2027, 1, 1)
        assertEquals(next, pages.date(pages.home, next))
        assertEquals(pages.home, pages.page(next, next))
        assertEquals(next.minusDays(1), pages.date(pages.home - 1, next))
    }

    @Test fun boundaryPagesLeaveRoomForMonthGridNeighbours() {
        val pages = CalendarPaging(PlainDate.of(2028, 1, 31))
        assertEquals(1, pages.date(0).year)
        assertEquals(2, pages.date(0).monthValue)
        assertEquals(9999, pages.date(pages.count - 1).year)
        assertEquals(11, pages.date(pages.count - 1).monthValue)
    }

    @Test fun monthStepsLandOnTheFirstDayOfTheNeighbouringMonth() {
        val january = PlainDate.of(2026, 1, 31)
        assertEquals(PlainDate.of(2026, 2, 1), CalendarPaging.monthStep(january, 1))
        assertEquals(PlainDate.of(2025, 12, 1), CalendarPaging.monthStep(january, -1))
        assertEquals(PlainDate.of(2024, 3, 1), CalendarPaging.monthStep(PlainDate.of(2024, 2, 29), 1))
        assertEquals(PlainDate.of(2028, 1, 1), CalendarPaging.monthStep(PlainDate.of(2027, 12, 31), 1))
    }

    @Test fun monthStepsStopWhereASixWeekGridWouldLeaveTheRange() {
        assertNull(CalendarPaging.monthStep(PlainDate.of(1, 2, 1), -1))
        assertNull(CalendarPaging.monthStep(PlainDate.of(9999, 11, 30), 1))
        assertEquals(PlainDate.of(1, 3, 1), CalendarPaging.monthStep(PlainDate.of(1, 2, 1), 1))
        assertEquals(PlainDate.of(9999, 10, 1), CalendarPaging.monthStep(PlainDate.of(9999, 11, 30), -1))
    }
}
