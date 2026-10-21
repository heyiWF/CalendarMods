package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarPagingTest {
    @Test fun dailyPagingCrossesYearAndLeapDayInBothDirections() {
        val year = CalendarPaging(LocalDate.of(2026, 12, 31), false)
        assertEquals(LocalDate.of(2027, 1, 1), year.date(year.home + 1))
        val leap = CalendarPaging(LocalDate.of(2028, 2, 29), false)
        assertEquals(LocalDate.of(2028, 2, 28), leap.date(leap.home - 1))
        assertEquals(LocalDate.of(2028, 3, 1), leap.date(leap.home + 1))
        assertEquals(leap.home, leap.page(leap.origin))
    }

    @Test fun monthlyPagingPreservesPreferredDayAfterShortMonths() {
        val pages = CalendarPaging(LocalDate.of(2028, 1, 31), true)
        assertEquals(LocalDate.of(2028, 2, 29), pages.date(pages.home + 1))
        assertEquals(LocalDate.of(2028, 3, 31), pages.date(pages.home + 2))
        assertEquals(LocalDate.of(2027, 12, 31), pages.date(pages.home - 1))
        assertEquals(pages.home + 2, pages.page(LocalDate.of(2028, 3, 15)))
    }

    @Test fun followingTodayReanchorsWithoutMovingThePager() {
        val pages = CalendarPaging(LocalDate.of(2026, 12, 31), false)
        val next = LocalDate.of(2027, 1, 1)
        assertEquals(next, pages.date(pages.home, next))
        assertEquals(pages.home, pages.page(next, next))
        assertEquals(next.minusDays(1), pages.date(pages.home - 1, next))
    }

    @Test fun boundaryPagesLeaveRoomForMonthGridNeighbours() {
        for (monthly in listOf(false, true)) {
            val pages = CalendarPaging(LocalDate.of(2028, 1, 31), monthly)
            assertEquals(1, pages.date(0).year)
            assertEquals(2, pages.date(0).monthValue)
            assertEquals(9999, pages.date(pages.count - 1).year)
            assertEquals(11, pages.date(pages.count - 1).monthValue)
        }
    }
}
