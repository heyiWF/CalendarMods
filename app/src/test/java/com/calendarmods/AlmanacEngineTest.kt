package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class AlmanacEngineTest {
    @Test fun allFourRenderersShareOneCalculationAndBoundedLruCache() {
        var calls = 0
        val delegate = TymeAlmanacEngine()
        val engine = object : AlmanacEngine {
            override val profile = delegate.profile
            override fun calculate(date: PlainDate): AlmanacDay { calls++; return delegate.calculate(date) }
        }
        val repository = AlmanacRepository(engine, 2)
        val date = PlainDate.of(2025, 4, 3)
        val first = repository.day(date)
        val renderers = CalendarTheme.entries.filter { ThemeDataCapability.ALMANAC in it.renderer.dataCapabilities }
        assertEquals(4, renderers.size)
        renderers.forEach { assertSame(first, repository.day(date)) }
        assertEquals(1, calls)
        repository.day(date.plusDays(1)); repository.day(date) // retain the recently read date
        repository.day(date.plusDays(2)); assertSame(first, repository.day(date))
        repository.day(date.plusDays(1)); assertEquals(4, calls)
    }
    @Test fun editorialProviderCanChangeWithoutChangingCalendarOrRenderer() {
        val provider = object : AlmanacEditorialProvider {
            override val edition = "test-edition"
            override fun forDay(data: CalendarData) = AlmanacEditorial(fengShui = "替换文稿", zodiacNumbers = "01")
        }
        val date = PlainDate.of(2025, 4, 3)
        val engine = TymeAlmanacEngine(provider)
        val alternative = engine.calculate(date)
        val standard = Almanacs.shared.day(date)
        assertEquals(standard.lunarDay, alternative.lunarDay)
        assertEquals(standard.hours, alternative.hours)
        assertEquals("替换文稿", alternative.editorial.fengShui)
        assertEquals("test-edition", engine.profile.editorialEdition)
    }
}
