package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceRecoveryBackoffTest {
    @Test fun persistentFailureKeepsRetryingAtABoundedRate() {
        val retry = FaceRecoveryBackoff()
        assertEquals(listOf(350L, 700L, 1400L, 2800L, 5600L, 11200L, 22400L, 30000L),
            List(8) { retry.nextDelayMillis() })
        repeat(10000) { assertEquals(30000L, retry.nextDelayMillis()) }
    }

    @Test fun briefRecoveryDoesNotResetBackoffButThirtySecondsOfFramesDoes() {
        val retry = FaceRecoveryBackoff()
        assertEquals(350L, retry.nextDelayMillis())
        retry.onFrame(100)
        retry.onFrame(30100 - 1)
        assertEquals(700L, retry.nextDelayMillis())
        retry.onFrame(40000)
        retry.onFrame(70000)
        assertEquals(350L, retry.nextDelayMillis())
    }
}
