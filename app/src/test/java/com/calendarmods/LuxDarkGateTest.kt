package com.calendarmods

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxDarkGateTest {
    private val gate = LuxDarkGate()

    @Test fun aBriefReadingCannotFlipTheSheet() {
        assertFalse(gate.update(3f, 0L))
        assertFalse(gate.update(3f, 600L))
        assertTrue(gate.update(3f, 1_200L))
        // Once dark, a single bright sample is not enough either.
        assertTrue(gate.update(200f, 1_300L))
        assertFalse(gate.update(200f, 2_500L))
    }

    @Test fun theBandKeepsTheCurrentPrinting() {
        gate.reset(true)
        assertTrue("15 lx must not flip a dark page", gate.update(15f, 0L))
        assertTrue("20 lx must not flip a dark page", gate.update(20f, 5_000L))
        assertTrue("a bright sample only starts the change", gate.update(400f, 6_000L))
        assertFalse("the change lands once it holds", gate.update(400f, 7_200L))
        assertFalse("20 lx must not flip a light page", gate.update(20f, 9_000L))
        assertFalse("a dim sample only starts the change", gate.update(5f, 10_000L))
        assertTrue("the change lands once it holds", gate.update(5f, 11_200L))
    }

    @Test fun resetAndMissingReadingsKeepTheLastDecision() {
        gate.reset(true)
        assertTrue(gate.update(Float.NaN, 0L))
        assertTrue(gate.update(0f, 10L))
        assertTrue(gate.update(0f, 1_210L))
        gate.reset(false)
        assertFalse(gate.update(Float.NaN, 2_000L))
    }
}
