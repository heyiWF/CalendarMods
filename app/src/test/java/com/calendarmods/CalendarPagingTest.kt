package com.calendarmods

import org.junit.Assert.assertEquals
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
}
