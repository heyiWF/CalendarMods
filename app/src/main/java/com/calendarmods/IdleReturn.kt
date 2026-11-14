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
     * The date the page lands on: the day the app is calling today, or the wall clock when no day
     * is loaded. It is read when the countdown expires and never remembered from when it started,
     * so a countdown that runs past midnight lands on the new day instead of the one it left.
     */
    fun target(today: PlainDate?, clock: CalendarClock): PlainDate =
        today ?: clock.now().localDate()
}
