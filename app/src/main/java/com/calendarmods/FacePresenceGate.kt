package com.calendarmods

/** An arrival wakes immediately; brief occlusions never flash the screen dark. */
internal class FacePresenceGate(private val holdMillis: Long = 8_000L) {
    private var lastSeen = 0L
    fun reset(now: Long) { lastSeen = now }
    fun update(facePresent: Boolean, now: Long): Boolean {
        if (facePresent) lastSeen = now
        return now - lastSeen < holdMillis
    }
}
