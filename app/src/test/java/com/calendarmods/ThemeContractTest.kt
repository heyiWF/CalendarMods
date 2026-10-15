package com.calendarmods

import org.junit.Assert.*
import org.junit.Test

class ThemeContractTest {
    private val custom = CalendarTheme.TEDDY.copy(id = "org.example.winter", title = "Winter", author = "Example")

    @Test fun builtInsHaveUniqueStableIdsAndRendererContracts() {
        assertEquals(7, CalendarTheme.entries.map { it.id }.toSet().size)
        assertEquals(ThemeRenderer.entries.toSet(), CalendarTheme.entries.map { it.renderer }.toSet())
        CalendarTheme.entries.forEach { theme ->
            assertEquals(theme.renderer.image != null, theme.settings.isNotEmpty())
            assertEquals(theme.renderer.name, theme.name)
        }
    }

    @Test fun legacySelectionMigratesUsingOriginalBuiltInOrder() {
        val reversed = CalendarTheme.entries.reversed() + custom
        assertEquals(CalendarTheme.PHOTO.id, ThemeCatalog.selectedId(null, 2, reversed))
        assertEquals(reversed.first().id, ThemeCatalog.selectedId(null, 99, reversed))
    }

    @Test fun stableSelectionSurvivesReorderingAndRemoval() {
        assertEquals(custom.id, ThemeCatalog.selectedId(custom.id, 0, listOf(custom) + CalendarTheme.entries))
        assertEquals(CalendarTheme.GOLDEN.id, ThemeCatalog.selectedId(custom.id, 2, CalendarTheme.entries))
    }

    @Test fun updatesRequireSameIdentityAndRendererWithIncreasingVersion() {
        ThemeCatalog.validateInstall(custom.copy(version = 2), listOf(custom))
        rejects { ThemeCatalog.validateInstall(custom, listOf(custom)) }
        rejects { ThemeCatalog.validateInstall(custom.copy(version = 2, renderer = ThemeRenderer.PHOTO), listOf(custom)) }
    }

    @Test fun builtInNamespaceCannotBeReplaced() {
        rejects { ThemeCatalog.validateInstall(CalendarTheme.TEDDY.copy(version = 2), emptyList()) }
        rejects { ThemeCatalog.validateInstall(custom.copy(id = "com.calendarmods.other"), emptyList()) }
    }

    @Test fun malformedAndIncompatibleContractsAreRejected() {
        rejects { custom.copy(id = "../escape") }
        rejects { custom.copy(schemaVersion = 3) }
        rejects { custom.copy(version = 0) }
        rejects { custom.copy(title = "\n") }
        rejects { custom.copy(defaultFill = "unknown") }
        rejects { CalendarTheme.GOLDEN.copy(hasImageAsset = true) }
        rejects { CalendarTheme.GOLDEN.copy(defaultFill = "fit") }
        rejects { ThemeSurfaces(-1) }
    }

    private fun rejects(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    @Test fun fontCatalogIsVersionedBoundedAndNamespaced() {
        val font = ThemeFont("book", "书卷")
        rejects { custom.copy(fonts = listOf(font)) }
        rejects { ThemeFont("../book.ttf", "书卷") }
        rejects { ThemeFont("template", "模板") }
        rejects { ThemeFont("default", "默认") }
        rejects { ThemeFont("book", "\n") }
        rejects { custom.copy(schemaVersion = 2, fonts = listOf(font, font)) }
        rejects { custom.copy(schemaVersion = 2, fonts = (1..9).map { ThemeFont("font-$it", "字体 $it") }) }
        rejects { custom.copy(schemaVersion = 2, defaultFont = "missing") }
    }

    @Test fun fontChoicesAreConsumedByEveryRendererWithSafeUpgradeFallback() {
        val font = ThemeFont("book", "书卷")
        CalendarTheme.entries.forEach { base ->
            val theme = base.copy(schemaVersion = 2, fonts = listOf(font), defaultFont = font.id)
            assertEquals(listOf("template", "book"), theme.settings.filterIsInstance<ThemeSetting.Choice>().single { it.key == "font" }.choices)
            assertEquals("book", theme.resolveFont(null))
            assertEquals("book", theme.resolveFont("removed"))
            assertEquals("template", theme.resolveFont("template"))
            assertEquals("template", theme.copy(fonts = emptyList(), defaultFont = "template").resolveFont("book"))
        }
    }
}
