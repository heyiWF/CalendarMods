package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSaverTest {
    @Test fun theIdleClockWaitsTwoMinutesAndOnlyOnTheFullscreenPage() {
        assertEquals(120_000L, ScreenSaver.WINDOW_MS)
        assertTrue(ScreenSaver.armed(immersive = true, enabled = true))
        assertFalse(ScreenSaver.armed(immersive = true, enabled = false))
        assertFalse(ScreenSaver.armed(immersive = false, enabled = true))
    }

    @Test fun itPrintsHoursAndMinutesWithoutThePeriodMark() {
        assertEquals("15:07", ScreenSaver.text(ClockFace("", "15", "07")))
        // The 12-hour reading keeps its digits and drops 上午 / 下午: only 时 and 分 are printed.
        assertEquals("9:05", ScreenSaver.text(ClockFace("上午", "9", "05")))
        assertEquals("12:30", ScreenSaver.text(ClockFace("下午", "12", "30")))
    }
}
