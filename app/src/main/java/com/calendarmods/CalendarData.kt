package com.calendarmods

import com.tyme.solar.SolarDay

data class CalendarCell(val date: PlainDate, val inMonth: Boolean, val lunar: String, val festivals: List<String>)

data class CalendarData(
    val today: PlainDate,
    val lunar: String,
    /** The solar term that starts on this very day, or null on the other 353 days. */
    val solarTerm: String?,
    /** Festivals of this day in the order the source lists them, lunar names first. */
    val festivals: List<String>,
    val recommends: List<String>,
    val avoids: List<String>,
    val cells: List<CalendarCell>,
) {
    /**
     * Right side of the date line: the lunar name with the day's solar term, or with the first
     * festival the source lists, joined without a space — `九月廿九立冬`. A solar term outranks a
     * festival, and the run is printed once, so the header never rotates.
     */
    val lunarHeadline get() = lunar + (solarTerm ?: festivals.firstOrNull()).orEmpty()

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
        private fun solarTerm(solar: SolarDay): String? =
            solar.termDay?.takeIf { it.dayIndex == 0 }?.solarTerm?.name

        /** Festivals of one day, in the order the source lists them: lunar names, then solar ones. */
        private fun festivals(solar: SolarDay): List<String> = buildList {
            solar.lunarDay.festival?.let { add(it.name) }
            solar.festival?.let { add(it.name) }
        }.distinct()

        /**
         * Everything the month grid rotates through: the term, the festivals, then the seasonal
         * markers (数九 / 三伏), which mark a stretch of the year rather than a festival day.
         */
        private fun marks(solar: SolarDay): List<String> = buildList {
            solarTerm(solar)?.let { add(it) }
            addAll(festivals(solar))
            solar.nineDay?.takeIf { it.dayIndex == 0 }?.let { add(it.nine.name) }
            solar.dogDay?.takeIf { it.dayIndex == 0 }?.let { add(it.dog.name) }
        }.distinct()

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
                if (lunar.day == 1) lunar.lunarMonth.name else lunar.name, marks(solar))
        }.also {
            months[month] = it
            if (months.size > 4) months.remove(months.keys.first())
        }

        /**
         * 「馀事勿取」 and 「诸事不宜」 close the source's lists with a day verdict — "take nothing
         * else" — instead of one concrete item. Each verdict keeps its own column: 「馀事勿取」
         * closes the 宜 list and 「诸事不宜」 closes the 忌 list, whichever column the source dropped
         * it in. A day that carries neither keeps both lists untouched.
         */
        private fun tabooColumns(recommends: List<String>, avoids: List<String>): Pair<List<String>, List<String>> {
            fun verdict(item: String) = item.endsWith("事勿取") || item == "诸事不宜"
            val all = recommends + avoids
            val closingYi = all.firstOrNull { it.endsWith("事勿取") }
            val closingJi = all.firstOrNull { it == "诸事不宜" }
            return recommends.filterNot { verdict(it) } + listOfNotNull(closingYi) to
                avoids.filterNot { verdict(it) } + listOfNotNull(closingJi)
        }

        private fun calculate(date: PlainDate): CalendarData {
            val solar = SolarDay.fromYmd(date.year, date.monthValue, date.dayOfMonth)
            val lunar = solar.lunarDay
            val (recommends, avoids) = tabooColumns(
                lunar.recommends.map { it.name }, lunar.avoids.map { it.name })
            return CalendarData(
                today = date,
                lunar = lunar.lunarMonth.name + lunar.name,
                solarTerm = solarTerm(solar),
                festivals = festivals(solar),
                recommends = recommends,
                avoids = avoids,
                cells = monthCells(PlainMonth.from(date)),
            )
        }
    }
}
