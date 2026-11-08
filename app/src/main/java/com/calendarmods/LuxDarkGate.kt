package com.calendarmods

/**
 * Turns ambient lux readings into a "the room is dark" decision.
 *
 * Two thresholds keep the page from flickering around dusk, and a change only counts once the new
 * reading has held for [confirmMillis], so a hand or a cloud passing over the sensor cannot
 * repaint the page.
 */
internal class LuxDarkGate(
    private val darkBelow: Float = 10f,
    private val lightAbove: Float = 25f,
    private val confirmMillis: Long = 1_200L,
) {
    private var dark = false
    private var pending: Boolean? = null
    private var pendingSince = 0L

    /** Starts from a known state, e.g. the current printing when the sensor is switched on. */
    fun reset(value: Boolean) {
        dark = value
        pending = null
    }

    fun update(lux: Float, now: Long): Boolean {
        if (lux.isNaN() || lux.isInfinite() || lux < 0f) { pending = null; return dark }
        val wanted = when {
            lux <= darkBelow -> true
            lux >= lightAbove -> false
            // Inside the band the page keeps what it has, which is what stops the flicker.
            else -> { pending = null; return dark }
        }
        if (wanted == dark) { pending = null; return dark }
        if (pending != wanted) {
            pending = wanted
            pendingSince = now
            return dark
        }
        if (now - pendingSince >= confirmMillis) {
            dark = wanted
            pending = null
        }
        return dark
    }
}
