package com.calendarmods

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class CalendarDataTest {
    @Test fun resumeReusesDataAndMidnightReusesOnlyTheMonthGrid() {
        val date = LocalDate.of(2028, 2, 28)
        val first = CalendarData.create(date)
        assertSame(first, CalendarData.create(date))
        val leapDay = CalendarData.create(date.plusDays(1))
        assertSame(first.cells, leapDay.cells)
        assertEquals("29", leapDay.day)
        assertNotEquals(first.today, leapDay.today)
        val march = CalendarData.create(date.plusDays(2))
        assertNotSame(leapDay.cells, march.cells)
        assertEquals(31, march.cells.count { it.inMonth })
        assertEquals(29, leapDay.cells.count { it.inMonth })
    }

    @Test fun dateCacheIsBoundedAndEvictionPreservesResults() {
        val date = LocalDate.of(2030, 1, 1)
        val first = CalendarData.create(date)
        (1L..9L).forEach { CalendarData.create(date.plusMonths(it)) }
        val reloaded = CalendarData.create(date)
        assertNotSame(first, reloaded)
        assertNotSame(first.cells, reloaded.cells)
        assertEquals(first, reloaded)
    }

    @Test fun pagingKeepsNeighbouringMonthGridsWarm() {
        val date = LocalDate.of(2031, 1, 1)
        val first = CalendarData.create(date)
        listOf(-1L, 1L, 2L).forEach { CalendarData.create(date.plusMonths(it)) }
        assertSame(first.cells, CalendarData.create(date.plusDays(1)).cells)
    }

    @Test fun simultaneousReadersShareTheSameComputedDay() {
        val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val results = (1..8).map { workers.submit<CalendarData> { CalendarData.create(LocalDate.of(2029, 6, 1)) } }
                .map { it.get() }
            results.forEach { assertSame(results.first(), it) }
        } finally { workers.shutdownNow() }
    }

    @Test fun solarTermsOnlyLabelTheActualTermDate() {
        val qingming = CalendarData.create(LocalDate.of(2024, 4, 4))
        val followingDay = CalendarData.create(LocalDate.of(2024, 4, 5))
        assertEquals("清明", qingming.solarTerm)
        assertTrue(qingming.festivals.contains("清明"))
        assertNull(followingDay.solarTerm)
        assertFalse(followingDay.festivals.any { it.contains("清明") })
        assertNull(CalendarData.create(LocalDate.of(2026, 10, 10)).solarTerm)
    }

    @Test fun lunarAndSolarFestivalsOnTheSameDayAreBothRetainedInTheMonthGrid() {
        val date = LocalDate.of(2020, 10, 1)
        val data = CalendarData.create(date)
        assertEquals(listOf("中秋节", "国庆节"), data.festivals)
        assertEquals(data.festivals, data.cells.single { it.date == date }.festivals)
    }

    @Test fun seasonalStartDatesMatchClockModsRules() {
        val first = CalendarData.create(LocalDate.of(2024, 12, 21))
        assertTrue(first.festivals.containsAll(listOf("冬至", "一九")))
        assertFalse(CalendarData.create(LocalDate.of(2024, 12, 22)).festivals.contains("一九"))
    }

    @Test fun actualAlmanacReplacesDecorativeSuitabilityText() {
        // Recorded from the same tyme4j 1.5.1 entry points used by ClockMods LunarAlmanac.
        val data = CalendarData.create(LocalDate.of(2026, 10, 10))
        assertEquals(listOf("嫁娶", "求嗣", "纳采", "进人口", "纳财", "结网"), data.recommends.take(6))
        assertEquals(listOf("上梁", "作灶", "伐木", "出行"), data.avoids.take(4))
        assertEquals("嫁娶 · 求嗣", data.suitableText(2))
        assertEquals("上梁 · 作灶", data.avoidText(2))
        val missing = data.copy(recommends = emptyList(), avoids = emptyList())
        assertEquals("暂无", missing.suitableText(6))
        assertEquals("暂无", missing.avoidText(4))
    }

    @Test fun monthGridKeepsLeapDaysAndSundayAlignmentAcrossYearBoundaries() {
        for (year in 2000..2100) for (month in 1..12) {
            val target = YearMonth.of(year, month)
            val cells = CalendarData.monthDates(target)
            assertEquals(42, cells.size)
            assertEquals(DayOfWeek.SUNDAY, cells.first().dayOfWeek)
            assertEquals(target.lengthOfMonth(), cells.count { YearMonth.from(it) == target })
            cells.zipWithNext().forEach { (left, right) -> assertEquals(left.plusDays(1), right) }
        }
    }
    @Test fun lunarNewYearUsesTheActualLunarDate() {
        for (date in listOf(LocalDate.of(2024, 2, 10), LocalDate.of(2025, 1, 29), LocalDate.of(2026, 2, 17))) {
            val data = CalendarData.create(date)
            assertEquals("正月初一", data.lunar)
            assertEquals(1, data.cells.count { it.date == date })
        }
    }
    @Test fun isoWeekAndRealMonthAreNotDerivedFromReferenceArtwork() {
        val data = CalendarData.create(LocalDate.of(2021, 1, 1))
        assertEquals(53, data.week)
        assertEquals("01", data.day)
        assertEquals("January", data.month)
        assertEquals("星期五", data.weekdayChinese)
        assertEquals(31, data.cells.count { it.inMonth })
    }
}
