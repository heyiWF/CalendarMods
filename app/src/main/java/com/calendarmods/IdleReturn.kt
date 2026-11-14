package com.calendarmods

/**
 * The silent return to today: a page browsed away from today goes back after a minute without a
 * touch, and nothing on screen counts the minute down.
 */
internal object IdleReturn {
    /** How long a browsed page waits for a touch before it goes back to today. */
    const val WINDOW_MS = 60_000L

    /** Whether the countdown runs: another date is on screen and the page accepts touches. */
    fun armed(immersive: Boolean, interactive: Boolean, browsed: Boolean): Boolean =
        immersive && interactive && browsed

    /**
     * Read the wall clock at expiry. The day's asynchronously loaded data may still belong to
     * yesterday when the midnight notification and the idle callback arrive together.
     */
    fun target(clock: CalendarClock): PlainDate = clock.now().localDate()
}
