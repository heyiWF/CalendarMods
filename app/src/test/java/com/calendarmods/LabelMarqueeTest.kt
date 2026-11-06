package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelMarqueeTest {
    @Test fun twoCharactersNeverWalk() {
        assertFalse(LabelMarquee.scrolls(2, 60f, 43f))
        assertFalse(LabelMarquee.scrolls(2, 20f, 43f))
    }

    @Test fun threeCharactersAreSetWhole() {
        // 十一月 in a 47-unit cell: readable at 72% of the label size, so it never walks.
        val width = 65.4f
        val cell = 47.4f
        assertEquals(.72f, LabelMarquee.fitRatio(width, cell), .01f)
        assertFalse(LabelMarquee.scrolls(3, width, cell))
    }

    @Test fun longerNamesWalk() {
        // 冬至祭祖 in the same cell would have to shrink to 49%: too small, so it walks.
        assertEquals(.49f, LabelMarquee.fitRatio(4f * 24f, 47.4f), .01f)
        assertTrue(LabelMarquee.scrolls(4, 4f * 24f, 47.4f))
    }

    @Test fun aLabelThatFitsStaysStill() {
        assertFalse(LabelMarquee.scrolls(4, 40f, 43f))
        assertEquals(1f, LabelMarquee.fitRatio(40f, 43f), 0f)
        assertEquals(1f, LabelMarquee.progress(9_000L, 0f), 0f)
        assertEquals(Long.MAX_VALUE, LabelMarquee.delayMillis(9_000L, 0f))
    }

    @Test fun theWalkWaitsFirstThenMoves() {
        val overflow = 20f
        val walk = LabelMarquee.walkMillis(overflow)
        assertEquals(500L, walk)
        assertEquals(0f, LabelMarquee.progress(0L, overflow), 0f)
        assertEquals(0f, LabelMarquee.progress(LabelMarquee.PAUSE_MS, overflow), 0f)
        assertEquals(.5f, LabelMarquee.progress(LabelMarquee.PAUSE_MS + walk / 2, overflow), .01f)
        assertEquals(1f, LabelMarquee.progress(LabelMarquee.PAUSE_MS + walk, overflow), 0f)
    }

    @Test fun progressNeverOvershoots() {
        assertEquals(1f, LabelMarquee.progress(LabelMarquee.PAUSE_MS + 10_000L, 20f), 0f)
    }

    @Test fun oneWakeUpPerFrameWhileWalking() {
        val pause = LabelMarquee.PAUSE_MS
        assertEquals(LabelMarquee.PAUSE_MS + 1L, LabelMarquee.delayMillis(0L, 20f))
        assertEquals(LabelMarquee.FRAME_MS, LabelMarquee.delayMillis(pause + 100L, 20f))
        assertEquals(Long.MAX_VALUE, LabelMarquee.delayMillis(pause + 500L, 20f))
    }

    @Test fun walkMillisFollowsTheDistance() {
        assertTrue(LabelMarquee.walkMillis(40f) > LabelMarquee.walkMillis(20f))
        assertEquals(1L, LabelMarquee.walkMillis(.01f))
        assertEquals(0L, LabelMarquee.walkMillis(0f))
    }
}
