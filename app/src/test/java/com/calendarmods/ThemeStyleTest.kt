package com.calendarmods

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStyleTest {
    @Test fun fontScaleStaysInsideTheSliderRange() {
        assertEquals(ThemeStyle.MIN_FONT_SCALE, ThemeStyle.clampFontScale(-1f))
        assertEquals(ThemeStyle.MIN_FONT_SCALE, ThemeStyle.clampFontScale(0f))
        assertEquals(ThemeStyle.MAX_FONT_SCALE, ThemeStyle.clampFontScale(9f))
        assertEquals(1.2f, ThemeStyle.clampFontScale(1.2f))
        assertEquals(ThemeStyle.DEFAULT_FONT_SCALE, ThemeStyle.clampFontScale(Float.NaN))
    }

    @Test fun defaultStylePrintsAtOneToOne() {
        assertEquals(ThemeStyle.DEFAULT_FONT_SCALE, ThemeStyle().fontScale)
        assertEquals(true, ThemeStyle().pageAnimation)
        assertEquals(false, ThemeStyle().interactive)
        assertEquals(false, ThemeStyle().faceBrightness)
        assertEquals(false, ThemeStyle().screensaver)
        assertEquals(true, ThemeStyle().hour24)
        assertEquals(DarkMode.OFF, ThemeStyle().darkMode)
        assertEquals(ThemeStyle.DEFAULT_DARK_FROM, ThemeStyle().darkFrom)
        assertEquals(ThemeStyle.DEFAULT_DARK_TO, ThemeStyle().darkTo)
    }

    @Test fun nightWindowCrossesMidnight() {
        val style = ThemeStyle(darkMode = DarkMode.SCHEDULE)
        for (hour in listOf(19, 20, 23, 0, 3, 6)) assertTrue("$hour should be dark", style.scheduledDark(hour))
        for (hour in listOf(7, 12, 18)) assertFalse("$hour should stay light", style.scheduledDark(hour))
    }

    @Test fun nightWindowWithoutMidnightAndEmptyWindow() {
        val day = ThemeStyle(darkMode = DarkMode.SCHEDULE, darkFrom = 13, darkTo = 15)
        assertTrue(day.scheduledDark(13))
        assertTrue(day.scheduledDark(14))
        assertFalse(day.scheduledDark(15))
        assertFalse(day.scheduledDark(12))

        val empty = ThemeStyle(darkMode = DarkMode.SCHEDULE, darkFrom = 8, darkTo = 8)
        for (hour in 0..23) assertFalse("$hour should stay light", empty.scheduledDark(hour))
    }

    @Test fun storedHoursAndKeysStayInRange() {
        assertEquals(0, ThemeStyle.clampHour(-4))
        assertEquals(23, ThemeStyle.clampHour(31))
        assertEquals(DarkMode.ON, DarkMode.fromKey("ON"))
        assertEquals(DarkMode.SENSOR, DarkMode.fromKey("SENSOR"))
        assertEquals(DarkMode.OFF, DarkMode.fromKey("nonsense"))
        assertEquals(DarkMode.OFF, DarkMode.fromKey(null))
    }
}
