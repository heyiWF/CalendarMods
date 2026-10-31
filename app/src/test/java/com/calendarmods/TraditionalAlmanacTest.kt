package com.calendarmods

import org.junit.Assert.*
import org.junit.Test
import com.tyme.solar.SolarDay

class TraditionalAlmanacTest {
    private fun day(value: String) = Almanacs.shared.day(PlainDate.parse(value))

    @Test fun referencePrintHasCalculatedCalendarAndZodiacFields() {
        val a = day("2025-04-03")
        assertEquals("初六日", a.lunarDay)
        assertEquals("乙巳年 三月大", a.lunarHeading)
        assertEquals("壬", a.stem); assertEquals("寅", a.branch)
        assertEquals("金箔金", a.sound); assertEquals("闭", a.duty)
        assertEquals("明日清明", a.termNotice)
        assertEquals("猪", a.sixHarmony); assertEquals("马狗", a.threeHarmony)
        assertEquals("07 19 31 43", a.editorial.zodiacNumbers)
        assertTrue(a.editorial.observances.contains("眼光娘娘"))
        assertEquals("卯巳", a.nobleBranches) // Common Tianyi edition, not print's unverified 丑卯未.
        assertTrue(a.editorial.zhuge.contains("青龙头"))
    }

    @Test fun newYearMappingChangesAtLunarNewYearRatherThanJanuaryFirst() {
        val before = TongShengRules.zodiacNumbers(PlainDate.of(2025, 1, 28), "蛇")
        val after = TongShengRules.zodiacNumbers(PlainDate.of(2025, 1, 29), "蛇")
        assertEquals(listOf(12, 24, 36, 48), before)
        assertEquals(listOf(1, 13, 25, 37, 49), after)
        assertEquals(before, TongShengRules.zodiacNumbers(PlainDate.of(2025, 1, 29), "蛇", TongShengRules.ZodiacYearBoundary.START_OF_SPRING))
    }

    @Test fun allColumnsRemainDeterministicAcrossDatesAndYearBoundaries() {
        listOf("1900-01-01", "2024-02-29", "2025-04-03", "2025-12-31", "2026-01-01", "2026-10-28", "2100-12-31").forEach { date ->
            val a = day(date)
            assertEquals(a, day(date))
            assertEquals(12, a.hours.size)
            assertEquals("子丑寅卯辰巳午未申酉戌亥", a.hours.joinToString("") { it.branch })
            assertEquals("23", a.hours.first().start); assertEquals("01", a.hours.first().end)
            assertEquals((1..9).toSet(), a.ninePalace.toSet())
            val solar = SolarDay.fromYmd(a.date.year, a.date.monthValue, a.date.dayOfMonth)
            assertEquals(solar.lunarDay.nineStar.index + 1, a.ninePalace[4])
            assertEquals(6, a.luckyZodiacs.distinct().size)
            assertEquals(6, a.editorial.luckyNumbers.distinct().size)
            assertTrue(a.editorial.luckyNumbers.all { it.toInt() in 1..49 })
            assertEquals(CalendarData.create(a.date).recommends, a.recommends)
            assertEquals(CalendarData.create(a.date).avoids, a.avoids)
            assertTrue(a.accessibility.contains("胎神"))
            assertTrue(a.accessibility.contains("岁时"))
            assertFalse(a.accessibility.contains("潮水"))
        }
    }

    @Test fun deityObservancesDoNotRepeatInLeapMonth() {
        assertTrue(DeityCalendar.on(3, 6, false).any { it.contains("眼光娘娘") })
        assertTrue(DeityCalendar.on(3, 6, true).isEmpty())
        assertTrue(DeityCalendar.on(2, 30, false).isEmpty())
    }

    @Test fun zhugeCyclesFollowMonthGroupAndResetAtFirstDay() {
        assertEquals("堂房", ZhugeTravel.name(1, 1))
        assertEquals("堂房", ZhugeTravel.name(4, 7))
        assertEquals("天盗", ZhugeTravel.name(2, 1))
        assertEquals("天仓", ZhugeTravel.name(5, 8))
        assertEquals("朱雀", ZhugeTravel.name(3, 1))
        assertEquals("青龙头", ZhugeTravel.name(3, 6))
        assertEquals("青龙头", ZhugeTravel.name(12, 30))
        assertEquals("青龙头", ZhugeTravel.name(6, 14))
    }

    @Test fun traditionalThemePreservesExistingIdsAndUsesDailyPaging() {
        assertEquals(CalendarTheme.HANGING.id, CalendarTheme.entries[6].id)
        assertEquals("traditional.v1", CalendarTheme.TRADITIONAL.renderer.wireId)
        assertFalse(CalendarTheme.TRADITIONAL.renderer.pagesByMonth)
        assertEquals(DayMotion.ALMANAC_PEEL, CalendarTheme.TRADITIONAL.renderer.dayMotion)
    }
}
