package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class ThemeLibraryTest {
    private val ids = CalendarTheme.entries.map { it.id }

    @Test fun defaultsInstallEveryPresetOnceInCatalogOrder() {
        assertEquals(ids, ThemeLibrary().order)
        assertEquals(11, ThemeLibrary().addedAt.size)
        assertEquals(ThemeLibrary(), ThemeLibrary().add(ids.first(), 123))
    }

    @Test fun removalRetainsFavoriteAndSchedulesStyleReset() {
        val removed = ThemeLibrary().favorite(ids[1]).remove(ids[1])
        assertTrue(ids[1] in removed.favorites)
        assertTrue(ids[1] in removed.resetStyles)
        assertFalse(ids[1] in removed.addedAt)
        val restored = removed.add(ids[1], 456)
        assertEquals(ids[1], restored.order.last())
        assertEquals(456L, restored.addedAt[ids[1]])
        assertFalse(ids[1] in restored.resetStyles)
        assertTrue(ids[1] in restored.favorites)
    }

    @Test fun favoriteBatchesSetValuesExplicitlyAndAreIdempotent() {
        val state = ThemeLibrary().favorite(ids[0]).favorite(ids[1])
        val values = mapOf(ids[0] to false, ids[1] to false, ids[2] to true)
        val next = state.setFavorites(values)
        assertEquals(setOf(ids[2]), next.favorites)
        assertEquals(next, next.setFavorites(values))
        assertEquals(state.order, next.order)
    }

    @Test fun manualOrderSurvivesNewAdditionsAndDuplicateAdds() {
        val reordered = ThemeLibrary().reorder(ids.reversed())
        assertEquals(reordered, reordered.add(ids[2], 300))
        assertEquals(ids.reversed() + "org.example.new", reordered.add("org.example.new", 400).order)
    }

    @Test fun rejectsLastRemovalAndInvalidPermutations() {
        val single = ids.drop(1).fold(ThemeLibrary()) { state, id -> state.remove(id) }
        assertTrue(runCatching { single.remove(ids.first()) }.isFailure)
        assertTrue(runCatching { ThemeLibrary().reorder(ids.drop(1)) }.isFailure)
        assertTrue(runCatching { ThemeLibrary().reorder(ids.dropLast(1) + ids.first()) }.isFailure)
    }

    @Test fun deletionChoosesNextThenPreviousAndReorderDoesNotChangeIdentity() {
        val state = ThemeLibrary()
        assertEquals(ids[3], state.selectionAfterRemoval(ids[2], ids[2]))
        assertEquals(ids[ids.lastIndex - 1], state.selectionAfterRemoval(ids.last(), ids.last()))
        assertEquals(ids[2], state.selectionAfterRemoval(ids[2], ids[1]))
    }

    @Test fun onlyAdjacentForegroundMidnightInSameZoneAnimates() {
        val zone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
        fun snapshot(): ClockSnapshot {
            val calendar = java.util.Calendar.getInstance(zone)
            calendar.clear()
            calendar.set(2026, 11, 31, 23, 59, 59)
            return ClockSnapshot(calendar.timeInMillis, zone)
        }
        val before = snapshot()
        assertTrue(naturalDateBoundary(before, ClockSnapshot(before.millis + 1_000, zone)))
        assertFalse(naturalDateBoundary(null, before))
        assertFalse(naturalDateBoundary(before, ClockSnapshot(before.millis + 86_400_000, zone)))
        assertFalse(naturalDateBoundary(before, ClockSnapshot(before.millis + 1_000,
            java.util.TimeZone.getTimeZone("Pacific/Kiritimati"))))
    }
}
