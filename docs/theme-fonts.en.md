[中文](theme-fonts.md) | [English](theme-fonts.en.md)

# Theme font extension guide

All templates accept a font override through their shared paper renderer. Themes without a font catalog retain their original fonts and settings. When fonts are declared, `CalendarTheme.settings` appends a `font` choice after the renderer's settings.

## Declaration and installation

See [the complete example](theme-font-example.json). Declare a stable ID and title for each font, then map the font bytes to those IDs:

```kotlin
val installed = ThemeRepository.get(context).install(
    manifest = manifestJson,
    // Image renderers can also receive image = imageBytes.
    fonts = mapOf("book" to bookTtfBytes, "rounded" to roundedOtfBytes),
)
```

Schema 2 adds `fonts: [{id, title}]` and `defaults.font`. The default is `template`, meaning the original renderer font; a declared font ID is also valid. Schema 1 rejects both fields. Schema 2 without bundled fonts behaves like schema 1.

Font IDs match `[a-z][a-z0-9-]{0,39}` and must be unique within a theme. `template` and `default` are reserved. Titles contain 1–80 characters, cannot be blank, and cannot contain control characters. Keep IDs stable across upgrades to preserve user selections; paths and download URLs are not IDs.

Each ID maps to one regular-weight TTF or OTF file. TTC, WOFF, remote fonts, multiple weight files and configurable variation axes are unsupported. The renderer controls size, weight and layout; Android matches or synthesizes the requested weight and provides missing-glyph fallback. Prefer fonts covering simplified/traditional Chinese, Latin and numerals, and confirm embedding and redistribution rights.

Limits: 16 MiB per file, 32 MiB total per theme, eight fonts. Supplied resource IDs must exactly equal the declaration; missing and extra resources are rejected. Installation checks file signatures and validates bytes through Android's native font parser. Failure removes new temporary resources without changing the previous theme or index.

Resources use random private filenames. The inventory is published only after the index commits atomically. Upgrades create new files; the next repository initialization removes obsolete files. Missing/corrupt resources produce a load diagnostic, retain other usable themes and prevent overwriting the damaged index.

This API is the boundary for a future download/import client. There is currently no end-user ZIP import or third-party online font store. On the fixed-inventory compat branch, existing installed font data remains readable; the install/store workflow described here is a Compose-branch developer reference.

## User settings and rendering

- The style sheet shows font names and Chinese, Latin and numeric samples. Users can follow the author default, choose a bundled font, or explicitly choose the original template font. Selection is stored per theme ID. With no override, author-default changes apply on upgrade; a removed font falls back to the new default.
- Deleting a theme resets its font preference; adding it again uses defaults. Resetting an image resets only image/fill. Following the theme font default clears only the font override. Built-ins without font catalogs show no unusable font option.
- One font context covers dates, weekdays, month grids, lunar labels, daily words and almanac text. Carousel measurement uses the same font and preserves label spacing. App navigation and buttons retain their own typography.
- Preview, fullscreen and store thumbnails share the rendering entry point. Thumbnail identity includes theme version and user style, so font changes and resource upgrades refresh the image.

Authors must check portrait/landscape, long text and system font scaling. Glyph shapes and metrics affect fit; fonts cannot modify template dimensions.

## Verification

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -e fonts true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e contract true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

These commands target Compose branches. Font acceptance borrows `/system/fonts/NotoSerif-Regular.ttf` and `DroidSansMono.ttf` from the device; those files are not redistributed. Coverage includes parsing, corrupt/missing/oversized resources, rollback, upgrade fallback, independent settings, cleanup, clicking font choices, preview/fullscreen pixel changes, orientation, recreation and reset. Original index and selection are restored afterward.

### Historical acceptance: 2026-10-14

28 JVM tests and both APK builds passed; Lint reported zero errors and two dependency-update notices. The font suite on `emulator-5554` passed three scenario groups, including real parsing, rollback, independent image/font defaults, upgrade fallback and UI switching. Preview/fullscreen date pixels changed with the font and returned to their original appearance after reset. Portrait/landscape and Activity recreation retained selection; screenshots were inspected.

The existing contract suite passed four groups: schema 1 round trip, invalid packages, image installation, migration/isolation, upgrade, reload, rotation and uninstall fallback. The full 54-check seven-theme suite was not rerun in that font-specific round.

Local ignored evidence: `build/theme-font-build.log`, `build/theme-font-device-final.log`, `build/theme-font-contract-final.log`, and `docs/verification/theme-fonts/`. These historical counts are not a claim about the latest build.
