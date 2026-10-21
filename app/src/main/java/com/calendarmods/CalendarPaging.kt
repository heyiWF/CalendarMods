package com.calendarmods

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal val ThemeRenderer.pagesByMonth: Boolean
    get() = this == ThemeRenderer.TEDDY || this == ThemeRenderer.PHOTO

/** Keep the preferred day across short months (Jan 31 -> Feb 28 -> Mar 31). */
internal class CalendarPaging(val origin: LocalDate, val monthly: Boolean) {
    private val first = LocalDate.of(1, 2, 1)
    private val last = LocalDate.of(9999, 11, 30)
    private fun distance(from: LocalDate, to: LocalDate): Int =
        if (monthly) ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(to)).toInt()
        else ChronoUnit.DAYS.between(from, to).toInt()
    val home = distance(first, origin)
    val count = distance(first, last) + 1
    fun page(date: LocalDate, anchor: LocalDate = origin) = (home + distance(anchor, date)).coerceIn(0, count - 1)
    fun date(page: Int, anchor: LocalDate = origin): LocalDate {
        val offset = (page - home).toLong()
        // Clamp the proleptic calendar edges before date arithmetic; month grids need neighbours.
        val bounded = offset.coerceIn(distance(anchor, first).toLong(), distance(anchor, last).toLong())
        return (if (monthly) anchor.plusMonths(bounded) else anchor.plusDays(bounded)).coerceIn(first, last)
    }
}
