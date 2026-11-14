package com.calendarmods

/** Day-by-day paging for the interactive month grid. */
internal class CalendarPaging(val origin: PlainDate) {
    private val first = FIRST
    private val last = LAST

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

    companion object {
        /** The supported range, wide enough that every printed month grid stays inside it. */
        private val FIRST = PlainDate.of(1, 2, 1)
        private val LAST = PlainDate.of(9999, 11, 30)

        /**
         * First day of the month [direction] away from [date], or null where the range ends. A
         * month is only offered when its own six-week grid stays inside [FIRST] .. [LAST].
         */
        fun monthStep(date: PlainDate, direction: Int): PlainDate? {
            val total = date.year.toLong() * 12 + (date.monthValue - 1) + direction
            val year = total / 12
            if (year < 1 || year > 9999) return null
            val target = PlainDate(year.toInt(), (total % 12).toInt() + 1, 1)
            return if (target < FIRST || target > LAST) null else target
        }
    }
}
