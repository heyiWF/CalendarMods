[中文](compat-validation.md) | [English](compat-validation.en.md)

> Historical compat report; mycompat later became a single daily page. These counts describe the recorded revision.

# Framework compatibility maintenance

The compat branch retains the eleven built-in calendars, existing installed theme data,
per-theme fonts/images/fill, page motion, interactive day/month paging and almanac appearance.
It has no theme store, favorites editor or free ordering UI. Unreachable repository mutation
operations were removed; existing package/index migration remains supported.

Changes include stable bound settings controls with full-row click targets, an animated sheet
entrance, fullscreen scale/fade transitions, normal-size cached numeral outlines with a default
font fallback, bounded image decoding and heap-relative caches, EXIF-aware imports, stale
request guards, lifecycle cleanup and month paging that preserves the preferred day.

Validation on 2026-11-01:

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` passed.
- Framework instrumentation on Android 15 passed 248 ordinary/traditional date/font raster checks, eleven themes in
  both orientations, and label/checkbox parity with retained control identity for every theme.
  Follow-up checks cover interrupted fullscreen entry, cancelled/displaced gestures, midnight
  browsing preservation and return-to-today paging. Pro/compat galleries use the same date and
  360×640 / 640×360 pixel sizes; intentional ink and legacy-font differences are documented in
  `compat-history-review.md`.
- A separate application ID was used so the existing app's data was not replaced.
- The minimum SDK remains 14. No Android 4.0 device is connected; actual API 14 and vendor font
  driver acceptance remain additional device checks, rather than inferred from the build.

Run `adb shell am instrument -w <applicationId>.test/com.calendarmods.CompatAcceptance` with
the matching app and androidTest APKs. The runner exports a theme gallery to the test app's
external `files/acceptance` directory.

Follow-up for older hardware: large numerals now use bounded, cached software rasters before
hardware composition. Only numeral glyphs are rasterized; the page and animations remain
accelerated. This addresses the software-preview/hardware-fullscreen rendering split reported
on a YOGA Tablet 2-1050F running Android 5.0.1. The window instrumentation now verifies actual
hardware Canvas output as well as offscreen rendering. Physical validation on that device is
still required; the connected device is a different Android version/GPU. Days 1–9 are unpadded.

Follow-up validation: 56 shared unit tests, lint/build, 248 software numeral cases and 16
hardware-window cases pass. Hardware screenshots of day 1 in the four traditional themes and
the golden theme were inspected on the connected Android 15 device; all date numerals appear.
