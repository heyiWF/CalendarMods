package com.calendarmods

import org.junit.Assert.assertEquals
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
    }
}
