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
}
