package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class ThemeContractTest {
    @Test fun bundledThemeHasOneStableIdentity() {
        assertEquals(1, CalendarTheme.entries.size)
        assertEquals(CalendarTheme.DAILY, CalendarTheme.entries.single())
        assertEquals("com.calendarmods.daily", CalendarTheme.DAILY.id)
        assertTrue(CalendarTheme.DAILY.title.isNotBlank())
    }

    @Test fun malformedContractsAreRejected() {
        rejects { CalendarTheme.DAILY.copy(id = "../escape") }
        rejects { CalendarTheme.DAILY.copy(title = "\n") }
        rejects { CalendarTheme.DAILY.copy(title = "x".repeat(81)) }
        rejects { ThemeSurfaces(-1) }
        rejects { ThemeSurfaces(0xFF000000, 0x1_0000_0000) }
    }

    @Test fun surfacesKeepOpaqueBackdropAndPaper() {
        val surfaces = CalendarTheme.DAILY.surfaces
        assertEquals(255, (surfaces.backdrop ushr 24) and 0xFF)
        assertEquals(255, (surfaces.paper ushr 24) and 0xFF)
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }
}
