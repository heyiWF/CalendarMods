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
        rejects { CalendarTheme.LIGHT.copy(backdrop = -1) }
        rejects { CalendarTheme.LIGHT.copy(paper = 0x1_0000_0000) }
        rejects { CalendarTheme.DARK.copy(ink = -2) }
    }

    @Test fun bothSheetsAreOpaqueAndDiffer() {
        for (scheme in listOf(CalendarTheme.DAILY.light, CalendarTheme.DAILY.dark)) {
            for (color in listOf(scheme.backdrop, scheme.paper, scheme.chrome)) {
                assertEquals(255, (color ushr 24) and 0xFF)
            }
        }
        assertNotEquals(CalendarTheme.DAILY.light.paper, CalendarTheme.DAILY.dark.paper)
        assertEquals(CalendarTheme.DAILY.dark, CalendarTheme.DAILY.scheme(dark = true))
        assertEquals(CalendarTheme.DAILY.light, CalendarTheme.DAILY.scheme(dark = false))
    }

    /** The dark sheet is a printing of the same page: ink still stands off the paper. */
    @Test fun inkStaysLegibleOnEitherSheet() {
        for (scheme in listOf(CalendarTheme.DAILY.light, CalendarTheme.DAILY.dark)) {
            assertTrue("ink is not distinct from paper", contrast(scheme.ink, scheme.paper) >= 7f)
            assertTrue("忌 ink is unreadable", contrast(scheme.accent, scheme.paper) >= 3f)
            assertTrue("宜 ink is unreadable", contrast(scheme.herb, scheme.paper) >= 3f)
            assertTrue("chip label is unreadable", contrast(scheme.onAccent, scheme.herb) >= 3f)
            assertTrue("chip label is unreadable", contrast(scheme.onAccent, scheme.accent) >= 3f)
        }
        assertTrue("the light sheet must be bright", luminance(CalendarTheme.DAILY.light.paper) > .7f)
        assertTrue("the dark sheet must be dark", luminance(CalendarTheme.DAILY.dark.paper) < .06f)
    }

    private fun luminance(color: Long): Float {
        fun channel(value: Long): Float {
            val c = (value and 0xFF) / 255f
            return if (c <= .03928f) c / 12.92f else Math.pow(((c + .055) / 1.055).toDouble(), 2.4).toFloat()
        }
        val r = channel(color ushr 16)
        val g = channel(color ushr 8)
        val b = channel(color)
        return .2126f * r + .7152f * g + .0722f * b
    }

    private fun contrast(a: Long, b: Long): Float {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + .05f) / (minOf(la, lb) + .05f)
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }
}
