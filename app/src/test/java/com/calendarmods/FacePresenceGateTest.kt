package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class FacePresenceGateTest {
    @Test fun startupAbsenceArrivalAndOcclusion() {
        val gate = FacePresenceGate()
        gate.reset(100)
        assertTrue(gate.update(false, 100))
        assertTrue(gate.update(false, 8099))
        assertFalse(gate.update(false, 8100))
        assertTrue(gate.update(true, 9000))
        assertTrue(gate.update(false, 16999))
        assertFalse(gate.update(false, 17000))
        assertTrue(gate.update(true, 18000))
        gate.reset(20000)
        assertTrue(gate.update(false, 20000))
    }
}
