package com.calendarmods

/** Day-by-day paging for the interactive month grid. */
internal class CalendarPaging(val origin: PlainDate) {
    private val first = PlainDate.of(1, 2, 1)
    private val last = PlainDate.of(9999, 11, 30)

    private fun distance(from: PlainDate, to: PlainDate): Int = (to.toEpochDay() - from.toEpochDay()).toInt()

    val home = distance(first, origin)
    val count = distance(first, last) + 1

    fun page(date: PlainDate, anchor: PlainDate = origin) = (home + distance(anchor, date)).coerceIn(0, count - 1)

    fun date(page: Int, anchor: PlainDate = origin): PlainDate {
        val offset = (page - home).toLong()
        // Clamp the calendar edges before date arithmetic; month grids need neighbours.
        val bounded = offset.coerceIn(distance(anchor, first).toLong(), distance(anchor, last).toLong())
        return anchor.plusDays(bounded).coerceIn(first, last)
    }
}
