package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class MonthFlipTest {
    @Test fun theFreeEdgeOfTheSheetStaysUnderTheFinger() {
        val width = 1000f
        for (step in 0..10) {
            val progress = step / 10f
            val angle = Math.toRadians(MonthFlip.angle(progress).toDouble())
            assertEquals(width * (1f - progress), width * cos(angle).toFloat(), .5f)
        }
        assertEquals(0f, MonthFlip.angle(0f), 0f)
        assertEquals(90f, MonthFlip.angle(1f), .001f)
    }

    @Test fun progressIsBoundedByThePageAndIgnoresTheOtherDirection() {
        assertEquals(0f, MonthFlip.progress(-40f, 1000f), 0f)
        assertEquals(.25f, MonthFlip.progress(250f, 1000f), 0f)
        assertEquals(1f, MonthFlip.progress(1400f, 1000f), 0f)
        assertEquals(0f, MonthFlip.progress(250f, 0f), 0f)
    }

    @Test fun aCloseCameraStillLeavesTheEdgeUnderTheFinger() {
        val spread = .8f
        for (step in 0..9) {
            val progress = step / 10f
            val angle = Math.toRadians(MonthFlip.angle(progress, spread).toDouble())
            val projected = cos(angle) / (1 + spread * sin(angle))
            assertEquals(1.0 - progress, projected, .002)
        }
        // The fold stops just short of a right angle, where the sheet would collapse into the hinge.
        assertEquals(89.5f, MonthFlip.angle(1f, spread), .05f)
    }

    @Test fun aThirdOfThePageTurnsItAndAFlickCarriesAShortDrag() {
        assertTrue(MonthFlip.commits(.32f, 0f))
        assertFalse(MonthFlip.commits(.31f, 0f))
        assertTrue(MonthFlip.commits(.12f, MonthFlip.FLING))
        assertFalse(MonthFlip.commits(.12f, MonthFlip.FLING - 1f))
        // A jitter never turns the month, however fast it is delivered.
        assertFalse(MonthFlip.commits(.02f, 6000f))
        // Past the threshold the position decides, even when the sheet is thrown back.
        assertTrue(MonthFlip.commits(.5f, -6000f))
    }

    @Test fun aShortFlickFinishesSoonerThanAFullTurn() {
        assertEquals(340L, MonthFlip.settleMillis(0f, 1f))
        assertEquals(142L, MonthFlip.settleMillis(.9f, 1f))
        assertEquals(142L, MonthFlip.settleMillis(.1f, 0f))
    }
}
