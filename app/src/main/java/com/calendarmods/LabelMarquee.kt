package com.calendarmods

/**
 * Timing for a cell label that is wider than the cell. Two characters are always set whole; a
 * longer name waits a beat and then walks left until its last character is in view, so nothing is
 * ever cut off with an ellipsis. Every cell shares one timeline, so a row scrolls in phase.
 */
internal object LabelMarquee {
    /** Labels up to this many characters never walk; a rare overflow is shrunk to fit instead. */
    const val STATIC_CHARS = 2

    /** A beat on the first characters before the walk starts. */
    const val PAUSE_MS = 1_000L

    /** Design units per second: a 360-unit page moves about 11% of its width per second. */
    const val SPEED = 40f

    const val FRAME_MS = 16L

    /** A label walks only when it is longer than [STATIC_CHARS] and does not fit its cell. */
    fun scrolls(chars: Int, width: Float, cellWidth: Float): Boolean =
        chars > STATIC_CHARS && width > cellWidth

    /** Time the walk needs to bring the last character into view. */
    fun walkMillis(overflow: Float): Long =
        if (overflow <= 0f) 0L else (overflow / SPEED * 1000f).toLong().coerceAtLeast(1L)

    /** How far along the walk is: 0 while it waits, 1 once the whole label has been shown. */
    fun progress(elapsed: Long, overflow: Float): Float {
        if (overflow <= 0f) return 1f
        if (elapsed <= PAUSE_MS) return 0f
        return ((elapsed - PAUSE_MS).toFloat() / walkMillis(overflow)).coerceIn(0f, 1f)
    }

    /** Milliseconds until the label can look different again; [Long.MAX_VALUE] once settled. */
    fun delayMillis(elapsed: Long, overflow: Float): Long = when {
        overflow <= 0f -> Long.MAX_VALUE
        elapsed <= PAUSE_MS -> PAUSE_MS - elapsed + 1L
        elapsed - PAUSE_MS < walkMillis(overflow) -> FRAME_MS
        else -> Long.MAX_VALUE
    }
}
