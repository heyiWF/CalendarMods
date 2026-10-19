package com.calendarmods

import com.tyme.solar.SolarDay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class CalendarCell(val date: LocalDate, val inMonth: Boolean, val lunar: String, val festivals: List<String>)

data class CalendarData(
    val today: LocalDate,
    val lunar: String,
    val stems: String,
    val solarTerm: String?,
    val festivals: List<String>,
    val recommends: List<String>,
    val avoids: List<String>,
    val cells: List<CalendarCell>,
) {
    fun suitableText(limit: Int) = recommends.take(limit).joinToString(" · ").ifBlank { "暂无" }
    fun avoidText(limit: Int) = avoids.take(limit).joinToString(" · ").ifBlank { "暂无" }
    val motto get() = DailyWords.motto(today)
    val heartWord get() = DailyWords.heartWord(today)
    val day get() = today.dayOfMonth.toString().padStart(2, '0')
    val month get() = today.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val monthShort get() = today.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val weekday get() = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    val weekdayChinese get() = "星期" + "一二三四五六日"[today.dayOfWeek.value - 1]
    val week get() = today.get(WeekFields.ISO.weekOfWeekBasedYear())
    val chineseMonth get() = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二")[today.monthValue - 1] + "月"

    companion object {
        // Dates are zone-independent once resolved by CalendarClock. Keep only a few
        // recent days/months so resume, rotation and midnight do not rebuild 42 cells.
        private val days = LinkedHashMap<LocalDate, CalendarData>(4, .75f, true)
        private val months = LinkedHashMap<YearMonth, List<CalendarCell>>(2, .75f, true)

        // Same rules/order as ClockMods LunarAlmanac.festivals(). A term labels only
        // its actual starting date, never every day in that two-week period.
        private fun festivals(solar: SolarDay): List<String> = buildSet {
            solar.termDay?.takeIf { it.dayIndex == 0 }?.let { add(it.solarTerm.name) }
            solar.lunarDay.festival?.let { add(it.name) }
            solar.festival?.let { add(it.name) }
            solar.nineDay?.takeIf { it.dayIndex == 0 }?.let { add(it.nine.name) }
            solar.dogDay?.takeIf { it.dayIndex == 0 }?.let { add(it.dog.name) }
        }.toList()

        // Sunday-first, six complete weeks: every theme uses the same date placement.
        fun monthDates(month: YearMonth): List<LocalDate> {
            val first = month.atDay(1)
            val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
            return List(42) { start.plusDays(it.toLong()) }
        }

        @Synchronized
        fun create(date: LocalDate): CalendarData = days[date] ?: calculate(date).also {
            days[date] = it
            if (days.size > 4) days.remove(days.keys.first())
        }

        private fun monthCells(month: YearMonth): List<CalendarCell> = months[month] ?: monthDates(month).map { date ->
            val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            val lunar = solar.lunarDay
            CalendarCell(date, YearMonth.from(date) == month,
                if (lunar.day == 1) lunar.lunarMonth.name else lunar.name, festivals(solar))
        }.also {
            months[month] = it
            if (months.size > 2) months.remove(months.keys.first())
        }

        private fun calculate(date: LocalDate): CalendarData {
            val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            val lunar = solar.lunarDay
            val cycle = solar.sixtyCycleDay
            return CalendarData(
                today = date,
                lunar = lunar.lunarMonth.name + lunar.name,
                stems = "${cycle.year.name}年  ${cycle.month.name}月  ${cycle.sixtyCycle.name}日",
                solarTerm = solar.termDay?.takeIf { it.dayIndex == 0 }?.solarTerm?.name,
                festivals = festivals(solar),
                recommends = lunar.recommends.map { it.name },
                avoids = lunar.avoids.map { it.name },
                cells = monthCells(YearMonth.from(date)),
            )
        }
    }
}
