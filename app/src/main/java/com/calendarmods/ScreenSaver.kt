package com.calendarmods

/**
 * When the idle clock takes over the fullscreen page, and what it prints.
 */
internal object ScreenSaver {
    /** How long the page has to stand untouched before the clock appears. */
    const val WINDOW_MS = 120_000L

    /** Whether the clock may appear: only on the fullscreen page, and only when it is switched on. */
    fun armed(immersive: Boolean, enabled: Boolean): Boolean = immersive && enabled

    /**
     * The reading the idle screen prints: hours and minutes. A 12-hour reading keeps its 上午 /
     * 下午 mark out of this string — the screen prints the mark on its own, over the reading's
     * left shoulder.
     */
    fun text(face: ClockFace): String = face.hour + ":" + face.minute

    /** The date line the idle screen prints under its reading: 11月21日 六. */
    data class DateLine(val month: String, val day: String, val weekday: String)

    /**
     * The date line for [date]. The weekday keeps one character — 星期六 prints as 六 — so the
     * line stays a short run under a reading that already fills the display.
     */
    fun dateLine(date: PlainDate): DateLine = DateLine(
        month = date.monthValue.toString(),
        day = date.dayOfMonth.toString(),
        weekday = WEEKDAYS[date.dayOfWeek - 1].toString(),
    )

    /** Monday-first, the order [PlainDate.dayOfWeek] uses. */
    private const val WEEKDAYS = "一二三四五六日"
}
