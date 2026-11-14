package com.calendarmods

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * How a horizontal drag across the month grid turns the printed sheet.
 *
 * The turn is driven by the distance the finger travels, never by a timer: [progress] is the share
 * of the page that has been dragged, and [angle] is the fold that puts the free edge of the sheet
 * exactly where the finger left it. Nothing here touches the framework, so the gesture can be
 * checked off the device.
 */
internal object MonthFlip {
    /** A drag that has covered this share of the page turns it even without a fling. */
    const val COMMIT = .32f

    /** Pixels per second at which a shorter drag still carries the turn through. */
    const val FLING = 760f

    /** The furthest the sheet folds; a right angle would flatten it into the hinge. */
    private const val MAX_ANGLE = 89.5

    fun progress(distance: Float, width: Float): Float {
        if (width <= 0f || distance <= 0f) return 0f
        return min(distance / width, 1f)
    }

    /**
     * Fold angle in degrees that leaves the free edge of the sheet under the finger. [spread] is
     * the page width over the camera distance: a distant camera only scales the edge by cos, while
     * a close one foreshortens it further, so the angle is solved rather than derived.
     */
    fun angle(progress: Float, spread: Float = 0f): Float {
        val target = 1f - progress.coerceIn(0f, 1f)
        if (spread <= 0f) return Math.toDegrees(acos(target.toDouble())).toFloat()
        // The projected edge `cos(a) / (1 + spread * sin(a))` falls as the fold deepens.
        var low = 0.0
        var high = Math.toRadians(MAX_ANGLE)
        repeat(12) {
            val mid = (low + high) / 2
            if (cos(mid) / (1 + spread * sin(mid)) > target) low = mid else high = mid
        }
        return Math.toDegrees((low + high) / 2).toFloat()
    }

    /** Whether a released drag settles on the next month. [velocity] is positive while opening. */
    fun commits(progress: Float, velocity: Float): Boolean =
        progress >= COMMIT || (progress > .04f && velocity >= FLING)

    /** How long a released drag still needs, so a short flick finishes as quickly as a long one. */
    fun settleMillis(from: Float, to: Float): Long {
        val remaining = (to - from).let { if (it < 0f) -it else it }
        return (120 + 220 * remaining).toLong()
    }
}
