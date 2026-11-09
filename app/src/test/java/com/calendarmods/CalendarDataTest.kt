package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class CalendarDataTest {
    @Test fun printedDayNeverAddsALeadingZero() {
        for (day in 1..31) assertEquals(day.toString(), CalendarData.create(PlainDate.of(2026, 1, day)).day)
    }

    @Test fun resumeReusesDataAndMidnightReusesOnlyTheMonthGrid() {
        val date = PlainDate.of(2028, 2, 28)
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
        val date = PlainDate.of(2030, 1, 1)
        val first = CalendarData.create(date)
        (1L..9L).forEach { CalendarData.create(date.plusMonths(it)) }
        val reloaded = CalendarData.create(date)
        assertNotSame(first, reloaded)
        assertNotSame(first.cells, reloaded.cells)
        assertEquals(first, reloaded)
    }

    @Test fun pagingKeepsNeighbouringMonthGridsWarm() {
        val date = PlainDate.of(2031, 1, 1)
        val first = CalendarData.create(date)
        listOf(-1L, 1L, 2L).forEach { CalendarData.create(date.plusMonths(it)) }
        assertSame(first.cells, CalendarData.create(date.plusDays(1)).cells)
    }

    @Test fun simultaneousReadersShareTheSameComputedDay() {
        val workers = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val results = (1..8).map { workers.submit<CalendarData> { CalendarData.create(PlainDate.of(2029, 6, 1)) } }
                .map { it.get() }
            results.forEach { assertSame(results.first(), it) }
        } finally { workers.shutdownNow() }
    }

    @Test fun solarTermsOnlyLabelTheActualTermDate() {
        val qingming = cellOf(PlainDate.of(2024, 4, 4))
        val followingDay = cellOf(PlainDate.of(2024, 4, 5))
        assertTrue(qingming.festivals.contains("清明"))
        assertFalse(followingDay.festivals.contains("清明"))
    }

    @Test fun lunarAndSolarFestivalsOnTheSameDayAreBothRetainedInTheMonthGrid() {
        val date = PlainDate.of(2020, 10, 1)
        val cell = cellOf(date)
        assertEquals(listOf("中秋节", "国庆节"), cell.festivals)
    }

    @Test fun seasonalStartDatesMatchCalendarModsRules() {
        assertTrue(cellOf(PlainDate.of(2024, 12, 21)).festivals.containsAll(listOf("冬至", "一九")))
        assertFalse(cellOf(PlainDate.of(2024, 12, 22)).festivals.contains("一九"))
    }

    @Test fun actualAlmanacReplacesDecorativeSuitabilityText() {
        // Recorded from the same tyme4j 1.5.1 entry points the page prints.
        val data = CalendarData.create(PlainDate.of(2026, 10, 10))
        assertEquals(listOf("嫁娶", "求嗣", "纳采", "进人口", "纳财", "结网"), data.recommends.take(6))
        assertEquals(listOf("上梁", "作灶", "伐木", "出行"), data.avoids.take(4))
        assertEquals("嫁娶 · 求嗣", data.suitableText(2))
        assertEquals("上梁 · 作灶", data.avoidText(2))
        val missing = data.copy(recommends = emptyList(), avoids = emptyList())
        assertEquals("暂无", missing.suitableText(6))
        assertEquals("暂无", missing.avoidText(4))
    }

    @Test fun theClosingVerdictsKeepTheirOwnColumn() {
        // 2026-11-07 closes its 宜 list with 「馀事勿取」 and has an empty 忌 list: the phrase
        // stays with the recommendations, and the empty 忌 column falls back to 无.
        val today = CalendarData.create(PlainDate.of(2026, 11, 7))
        assertEquals(listOf("解除", "馀事勿取"), today.recommends)
        assertTrue(today.avoids.isEmpty())

        // A day that carries both verdicts keeps each in its own column.
        val both = CalendarData.create(PlainDate.of(2024, 1, 8))
        assertEquals(listOf("解除", "破屋", "坏垣", "馀事勿取"), both.recommends)
        assertEquals(listOf("诸事不宜"), both.avoids)

        // A phrase the source already placed under 忌 moves over to close the 宜 list.
        val inPlace = CalendarData.create(PlainDate.of(2024, 2, 14))
        assertEquals(listOf("祭祀", "解除", "治病", "破屋", "坏垣", "扫舍", "馀事勿取"), inPlace.recommends)
        assertTrue(inPlace.avoids.isEmpty())

        // The phrase closes the 宜 list after the source's own recommendations, leaving 忌 intact.
        val mixed = CalendarData.create(PlainDate.of(2024, 1, 4))
        assertEquals(listOf("祭祀", "平治道涂", "除服", "成服", "安葬", "馀事勿取"), mixed.recommends)
        assertEquals(listOf("嫁娶", "入宅", "纳采", "订盟", "掘井"), mixed.avoids)
    }

    @Test fun monthGridStartsOnMondayAndKeepsLeapDaysAcrossYearBoundaries() {
        for (year in 2000..2100) for (month in 1..12) {
            val target = PlainMonth(year, month)
            val cells = CalendarData.monthDates(target)
            assertEquals(42, cells.size)
            assertEquals(1, cells.first().dayOfWeek)
            assertEquals(PlainDate.daysInMonth(year, month), cells.count { PlainMonth.from(it) == target })
            cells.zipWithNext().forEach { (left, right) -> assertEquals(left.plusDays(1), right) }
        }
    }

    @Test fun lunarNewYearUsesTheActualLunarDate() {
        for (date in listOf(PlainDate.of(2024, 2, 10), PlainDate.of(2025, 1, 29), PlainDate.of(2026, 2, 17))) {
            val data = CalendarData.create(date)
            assertEquals("正月初一", data.lunar)
            assertEquals(1, data.cells.count { it.date == date })
        }
    }

    @Test fun headerRunsAreDerivedFromTheDate() {
        val data = CalendarData.create(PlainDate.of(2021, 1, 1))
        assertEquals("1", data.day)
        assertEquals("1月", data.monthLabel)
        assertEquals("星期五", data.weekdayChinese)
        assertEquals(31, data.cells.count { it.inMonth })
    }

    @Test fun firstLunarDayOfAMonthPrintsTheMonthName() {
        val newYear = PlainDate.of(2026, 2, 17)
        assertEquals("正月初一", CalendarData.create(newYear).lunar)
        assertEquals("正月", cellOf(newYear).lunar)
        assertEquals("初二", cellOf(newYear.plusDays(1)).lunar)
    }

    @Test fun theDateLineJoinsTheTermToTheLunarNameWithoutASpace() {
        val lidong = CalendarData.create(PlainDate.of(2026, 11, 7))
        assertEquals("立冬", lidong.solarTerm)
        assertEquals("九月廿九立冬", lidong.lunarHeadline)
        // The other 353 days keep the bare lunar name — no separator, no placeholder.
        val before = CalendarData.create(PlainDate.of(2026, 11, 6))
        assertEquals(null, before.solarTerm)
        assertEquals("九月廿八", before.lunarHeadline)
    }

    @Test fun aTermOutranksAFestivalOnTheSameDate() {
        val dongzhi = CalendarData.create(PlainDate.of(2026, 12, 22))
        assertEquals(listOf("冬至节"), dongzhi.festivals)
        assertEquals("十一月十四冬至", dongzhi.lunarHeadline)
    }

    @Test fun theFirstFestivalOfTheDayJoinsTheLunarName() {
        val nationalDay = CalendarData.create(PlainDate.of(2020, 10, 1))
        assertEquals(listOf("中秋节", "国庆节"), nationalDay.festivals)
        assertEquals("八月十五中秋节", nationalDay.lunarHeadline)
    }

    @Test fun seasonalMarkersRotateInTheGridAndStayOutOfTheDateLine() {
        val date = PlainDate.of(2024, 12, 21)
        assertEquals(listOf("冬至", "冬至节", "一九"), cellOf(date).festivals)
        val data = CalendarData.create(date)
        assertEquals(listOf("冬至节"), data.festivals)
        // 数九 is a stretch of the year, so it rotates in the grid and stays out of the date line.
        assertTrue(data.lunarHeadline.endsWith("冬至"))
    }

    @Test fun aThreeCharacterFestivalMakesTheLongestDateLineOfTheYear() {
        // 腊八节 sits on a fifth lunar day name, so the run reaches eight characters — the longest
        // the source produces in 2026-2028. Nine is the ceiling once a term or festival is joined
        // to 十一月三十, which is what the date line is measured against.
        val laba = CalendarData.create(PlainDate.of(2026, 1, 26))
        assertEquals("腊八节", laba.festivals.single())
        assertEquals("十二月初八腊八节", laba.lunarHeadline)
        assertEquals(8, laba.lunarHeadline.length)

        val chongyang = CalendarData.create(PlainDate.of(2026, 10, 18))
        assertEquals("九月初九重阳节", chongyang.lunarHeadline)
    }

    private fun cellOf(date: PlainDate): CalendarCell =
        CalendarData.create(date).cells.single { it.date == date }
}
