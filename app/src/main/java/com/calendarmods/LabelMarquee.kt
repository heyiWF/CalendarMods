package com.calendarmods

/**
 * How a cell label that is wider than its cell is brought into view. Two characters are always set
 * whole; three are set whole by shrinking them a little; only a name that would have to shrink
 * below [MIN_FIT] walks sideways. Nothing is ever cut off with an ellipsis. Every cell shares one
 * timeline, so a row scrolls in phase.
 */
internal object LabelMarquee {
    /** Labels up to this many characters are set whole, shrinking only if they overflow. */
    const val STATIC_CHARS = 2

    /** Below this share of the label size a name is too small to read, so it walks instead. */
    const val MIN_FIT = .62f

    /** A beat on the first characters before the walk starts. */
    const val PAUSE_MS = 1_000L

    /** Design units per second: a 360-unit page moves about 11% of its width per second. */
    const val SPEED = 40f

    const val FRAME_MS = 16L

    /**
     * A label is set whole while it is up to [STATIC_CHARS] characters or still readable when
     * shrunk to the cell; a longer name that would fall below [MIN_FIT] walks.
     */
    fun scrolls(chars: Int, width: Float, cellWidth: Float): Boolean =
        chars > STATIC_CHARS && width > cellWidth && cellWidth / width < MIN_FIT

    /** Share of the label size that sets the whole name inside the cell. */
    fun fitRatio(width: Float, cellWidth: Float): Float =
        if (width <= 0f || cellWidth >= width) 1f else cellWidth / width

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
