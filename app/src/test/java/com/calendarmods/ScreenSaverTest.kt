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

    @Test fun theDateLineKeepsOneWeekdayCharacter() {
        val saturday = ScreenSaver.dateLine(PlainDate.of(2026, 11, 21))
        assertEquals("11", saturday.month)
        assertEquals("21", saturday.day)
        // 星期六 prints as 六 alone, not 星期六.
        assertEquals("六", saturday.weekday)

        assertEquals("一", ScreenSaver.dateLine(PlainDate.of(2026, 11, 16)).weekday)
        assertEquals("日", ScreenSaver.dateLine(PlainDate.of(2026, 11, 22)).weekday)
        val december = ScreenSaver.dateLine(PlainDate.of(2026, 12, 1))
        assertEquals("12", december.month)
        assertEquals("1", december.day)
    }
}
