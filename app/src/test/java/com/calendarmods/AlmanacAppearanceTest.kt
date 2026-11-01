package com.calendarmods

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AlmanacAppearanceTest {
    @Test fun scheduleHandlesMidnightBoundariesAndEmptyIntervals() {
        assertTrue(AlmanacNightPolicy.scheduled(1320, 1320, 420))
        assertTrue(AlmanacNightPolicy.scheduled(0, 1320, 420))
        assertTrue(AlmanacNightPolicy.scheduled(419, 1320, 420))
        assertFalse(AlmanacNightPolicy.scheduled(420, 1320, 420))
        assertFalse(AlmanacNightPolicy.scheduled(1319, 1320, 420))
        assertTrue(AlmanacNightPolicy.scheduled(720, 600, 900))
        assertFalse(AlmanacNightPolicy.scheduled(900, 600, 900))
        assertFalse(AlmanacNightPolicy.scheduled(600, 600, 600))
    }
    @Test fun ambientNeedsSustainedDarknessAndBrightnessWithHysteresis() {
        val gate = AmbientDarkGate()
        assertFalse(gate.update(5f, 0))
        assertFalse(gate.update(5f, 29_999))
        assertTrue(gate.update(5f, 30_000))
        assertTrue(gate.update(25f, 60_000)) // neutral band never oscillates
        assertTrue(gate.update(80f, 61_000))
        assertTrue(gate.update(80f, 70_999))
        assertFalse(gate.update(80f, 71_000))
    }
    @Test fun interruptedAndInvalidLightReadingsDoNotTriggerNight() {
        val gate = AmbientDarkGate()
        gate.update(0f, 0); gate.update(20f, 29_000)
        assertFalse(gate.update(0f, 30_000))
        assertFalse(gate.update(Float.NaN, 59_999))
        assertFalse(gate.update(0f, 60_000))
        assertFalse(gate.update(-1f, 100_000))
        assertFalse(gate.update(null, 200_000))
    }
    @Test fun manualScheduleAndAmbientCombineWithoutOverwritingUserSettings() {
        val base = ThemeStyle(scheduledDark = true, ambientDark = true)
        assertTrue(AlmanacNightPolicy.dark(base, 0, false))
        assertTrue(AlmanacNightPolicy.dark(base, 720, true))
        assertFalse(AlmanacNightPolicy.dark(base, 720, false))
        assertTrue(AlmanacNightPolicy.dark(base.copy(dark = true), 720, false))
        assertFalse(AlmanacNightPolicy.dark(ThemeStyle(), 0, true))
        AlmanacInk.entries.forEach { ink ->
            val palette = almanacPalette(CalendarTheme.CANTON, base.copy(ink = ink), 0, false)
            assertTrue(palette.dark)
            assertNotEquals(palette.ink, palette.paper)
        }
    }
    @Test fun allTraditionalThemesShareDateDrivenDefaultsAndHonorOverrides() {
        val weekday = CalendarData.create(LocalDate.of(2026, 11, 2)).copy(festivals = emptyList())
        val weekend = CalendarData.create(LocalDate.of(2026, 11, 28))
        val holiday = CalendarData.create(LocalDate.of(2026, 10, 1))
        assertFalse(almanacDefaultIsRed(weekday, ThemeStyle()))
        assertTrue(almanacDefaultIsRed(weekend, ThemeStyle()))
        assertTrue(almanacDefaultIsRed(holiday, ThemeStyle()))
        assertTrue(almanacDefaultIsRed(weekday, ThemeStyle(), specialEdition = true))
        assertFalse(almanacDefaultIsRed(weekday, ThemeStyle(festive = false), specialEdition = true))
        for (theme in CalendarTheme.entries.filter { it.renderer.isTongSheng }) {
            for (dark in listOf(false, true)) for (red in listOf(false, true)) {
                val expected = if (red) AlmanacInk.RED else AlmanacInk.GREEN
                val style = ThemeStyle(dark = dark)
                assertEquals(if (dark) expected.night else expected.light,
                    almanacPalette(theme, style, 720, false, red).ink)
                assertEquals(if (dark) AlmanacInk.BLUE.night else AlmanacInk.BLUE.light,
                    almanacPalette(theme, style.copy(ink = AlmanacInk.BLUE), 720, false, red).ink)
            }
        }
    }
    @Test fun festivalsUseLunarDatesIncludingThreeDistinctSpringDays() {
        val spring = LocalDate.of(2026, 2, 17)
        val titles = (0..2).map { festiveDay(spring.plusDays(it.toLong()))!!.title }
        assertEquals(3, titles.distinct().size)
        assertNull(festiveDay(spring.plusDays(3)))
        assertEquals(FestiveKind.MOON, festiveDay(LocalDate.of(2026, 9, 25))?.kind)
        assertEquals(FestiveKind.NATIONAL, festiveDay(LocalDate.of(2026, 10, 1))?.kind)
        assertEquals(FestiveKind.NEW_YEAR, festiveDay(LocalDate.of(2026, 1, 1))?.kind)
        assertEquals(FestiveKind.LANTERN, festiveDay(LocalDate.of(2026, 3, 3))?.kind)
        assertEquals(FestiveKind.DRAGON_BOAT, festiveDay(LocalDate.of(2026, 6, 19))?.kind)
    }
    @Test fun westernZodiacWrapsAtYearAndChangesOnBoundary() {
        assertEquals(0, WesternZodiac.index(LocalDate.of(2026, 1, 19)))
        assertEquals(1, WesternZodiac.index(LocalDate.of(2026, 1, 20)))
        assertEquals(11, WesternZodiac.index(LocalDate.of(2026, 12, 21)))
        assertEquals(0, WesternZodiac.index(LocalDate.of(2026, 12, 22)))
    }
}
