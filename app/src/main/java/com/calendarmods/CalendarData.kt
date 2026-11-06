package com.calendarmods

import com.tyme.solar.SolarDay

data class CalendarCell(val date: PlainDate, val inMonth: Boolean, val lunar: String, val festivals: List<String>)

data class CalendarData(
    val today: PlainDate,
    val lunar: String,
    val recommends: List<String>,
    val avoids: List<String>,
    val cells: List<CalendarCell>,
) {
    fun suitableText(limit: Int) = recommends.take(limit).joinToString(" · ").ifBlank { "暂无" }
    fun avoidText(limit: Int) = avoids.take(limit).joinToString(" · ").ifBlank { "暂无" }
    val day get() = today.dayOfMonth.toString()
    val monthLabel get() = "${today.monthValue}月"
    val weekdayChinese get() = "星期" + "一二三四五六日"[today.dayOfWeek - 1]

    companion object {
        // Dates are zone-independent once resolved by CalendarClock. Keep only a few
        // recent days/months so resume, rotation and midnight do not rebuild 42 cells.
        private val days = LinkedHashMap<PlainDate, CalendarData>(4, .75f, true)
        private val months = LinkedHashMap<PlainMonth, List<CalendarCell>>(2, .75f, true)

        // A term labels only its actual starting date, never every day in that two-week period.
        private fun festivals(solar: SolarDay): List<String> = buildSet {
            solar.termDay?.takeIf { it.dayIndex == 0 }?.let { add(it.solarTerm.name) }
            solar.lunarDay.festival?.let { add(it.name) }
            solar.festival?.let { add(it.name) }
            solar.nineDay?.takeIf { it.dayIndex == 0 }?.let { add(it.nine.name) }
            solar.dogDay?.takeIf { it.dayIndex == 0 }?.let { add(it.dog.name) }
        }.toList()

        /** Monday-first, six complete weeks: Saturday and Sunday close every printed row. */
        fun monthDates(month: PlainMonth): List<PlainDate> {
            val first = PlainDate(month.year, month.month, 1)
            val start = first.minusDays(((first.dayOfWeek - 1) % 7).toLong())
            return List(42) { start.plusDays(it.toLong()) }
        }

        @Synchronized
        fun create(date: PlainDate): CalendarData = days[date] ?: calculate(date).also {
            days[date] = it
            if (days.size > 8) days.remove(days.keys.first())
        }

        private fun monthCells(month: PlainMonth): List<CalendarCell> = months[month] ?: monthDates(month).map { date ->
            val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            val lunar = solar.lunarDay
            CalendarCell(date, PlainMonth.from(date) == month,
                if (lunar.day == 1) lunar.lunarMonth.name else lunar.name, festivals(solar))
        }.also {
            months[month] = it
            if (months.size > 4) months.remove(months.keys.first())
        }

        private fun calculate(date: PlainDate): CalendarData {
            val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            val lunar = solar.lunarDay
            return CalendarData(
                today = date,
                lunar = lunar.lunarMonth.name + lunar.name,
                recommends = lunar.recommends.map { it.name },
                avoids = lunar.avoids.map { it.name },
                cells = monthCells(PlainMonth.from(date)),
            )
        }
    }
}
