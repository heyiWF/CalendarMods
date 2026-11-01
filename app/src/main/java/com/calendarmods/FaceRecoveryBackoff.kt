package com.calendarmods

/** Bounded retry frequency, without a retry limit that disables an unattended calendar. */
internal class FaceRecoveryBackoff {
    private var failures = 0
    private var stableSince: Long? = null

    fun nextDelayMillis(): Long {
        stableSince = null
        val delay = minOf(30_000L, 350L shl failures.coerceAtMost(7))
        failures = (failures + 1).coerceAtMost(8)
        return delay
    }

    fun onFrame(now: Long) {
        val start = stableSince
        if (start == null) stableSince = now
        else if (now - start >= 30_000L) failures = 0
    }
}
