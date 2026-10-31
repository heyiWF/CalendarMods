package com.calendarmods

/**
 * A zone-free calendar date.
 *
 * `java.time` is unavailable before API 26 and its desugared backport requires multidex below
 * API 21, so the compat branch keeps its own immutable date. The arithmetic follows the civil
 * calendar algorithms and never touches the default time zone.
 */
data class PlainDate(val year: Int, val monthValue: Int, val dayOfMonth: Int) : Comparable<PlainDate> {
    init {
        require(monthValue in 1..12) { "Invalid month $monthValue" }
        require(dayOfMonth in 1..daysInMonth(year, monthValue)) { "Invalid day $dayOfMonth" }
        require(year in 1..9999) { "Unsupported year $year" }
    }

    val lengthOfMonth: Int get() = daysInMonth(year, monthValue)

    /** ISO weekday: Monday is 1, Sunday is 7. */
    val dayOfWeek: Int get() = (((toEpochDay() + 3) % 7).toInt() + 7) % 7 + 1

    val dayOfYear: Int get() = (toEpochDay() - PlainDate(year, 1, 1).toEpochDay()).toInt() + 1

    val monthName: String get() = MONTHS[monthValue - 1]
    val monthShort: String get() = MONTHS_SHORT[monthValue - 1]
    val weekdayName: String get() = WEEKDAYS[dayOfWeek - 1]

    fun plusDays(days: Long): PlainDate = ofEpochDay(toEpochDay() + days)
    fun minusDays(days: Long): PlainDate = plusDays(-days)

    /** Keeps the preferred day across short months, e.g. Jan 31 -> Feb 28/29 -> Mar 31. */
    fun plusMonths(months: Long): PlainDate {
        val total = year.toLong() * 12 + (monthValue - 1) + months
        val targetYear = CompatMath.floorDiv(total, 12L).toInt()
        val targetMonth = CompatMath.floorMod(total, 12L).toInt() + 1
        return PlainDate(targetYear.coerceIn(1, 9999), targetMonth,
            dayOfMonth.coerceAtMost(daysInMonth(targetYear, targetMonth)))
    }

    fun toEpochDay(): Long = daysFromCivil(year, monthValue, dayOfMonth)

    fun startOfDayMillis(zone: java.util.TimeZone): Long {
        val calendar = java.util.Calendar.getInstance(zone)
        calendar.clear()
        calendar.set(year, monthValue - 1, dayOfMonth, 0, 0, 0)
        return calendar.timeInMillis
    }

    override fun compareTo(other: PlainDate): Int {
        if (year != other.year) return year - other.year
        if (monthValue != other.monthValue) return monthValue - other.monthValue
        return dayOfMonth - other.dayOfMonth
    }

    override fun toString(): String = "%04d-%02d-%02d".format(year, monthValue, dayOfMonth)

    companion object {
        private val MONTHS = arrayOf("January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December")
        private val MONTHS_SHORT = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        private val WEEKDAYS = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        fun of(year: Int, month: Int, day: Int) = PlainDate(year, month, day)

        /** Accepts the ISO form the calendar UI prints, e.g. 2026-02-17. */
        fun parse(text: String): PlainDate {
            val parts = text.split('-')
            require(parts.size == 3) { "Invalid date $text" }
            return PlainDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
        }

        fun ofEpochDay(epochDay: Long): PlainDate {
            // Howard Hinnant's civil_from_days, shifted to the Unix epoch.
            var z = epochDay + 719_468
            val era = CompatMath.floorDiv(z, 146_097L)
            val dayOfEra = z - era * 146_097
            val yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
            val year = yearOfEra + era * 400
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val mp = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * mp + 2) / 5 + 1
            val month = if (mp < 10) mp + 3 else mp - 9
            return PlainDate((if (month <= 2) year + 1 else year).toInt(), month.toInt(), day.toInt())
        }

        private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
            val y = (if (month <= 2) year - 1 else year).toLong()
            val era = CompatMath.floorDiv(y, 400L)
            val yearOfEra = y - era * 400
            val monthPrime = if (month > 2) month - 3 else month + 9
            val dayOfYear = (153 * monthPrime + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146_097 + dayOfEra - 719_468
        }

        /** ISO-8601 week number of the week-based year. */
        fun isoWeek(date: PlainDate): Int {
            val week = (date.dayOfYear - date.dayOfWeek + 10) / 7
            return when {
                week < 1 -> weeksInYear(date.year - 1)
                week > weeksInYear(date.year) -> 1
                else -> week
            }
        }

        private fun weeksInYear(year: Int): Int {
            val first = PlainDate(year, 1, 1).dayOfWeek
            return if (first == 4 || (isLeapYear(year) && first == 3)) 53 else 52
        }
    }
}

/** Year and month without a day, used for the month-grid cache key. */
data class PlainMonth(val year: Int, val month: Int) {
    companion object {
        fun from(date: PlainDate) = PlainMonth(date.year, date.monthValue)
    }
}
