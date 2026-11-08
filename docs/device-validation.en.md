[中文](device-validation.md) | [English](device-validation.en.md)

# Daily page device acceptance

This report covers mycompat's single daily layout, dark mode and 12/24-hour clock. Historical screenshots may precede the latest typography; the 2026-11-08 section records the current changes.

## Build and device

Run `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline`. The earlier report recorded 53 JVM tests, no failures and no Lint issues, covering dates, long festival headlines, paging, scale limits, contrast, schedules, light hysteresis, clock formatting and face rules. The current run has 55 JVM tests and passes build/Lint.

The connected device is `emulator-5554`, Android 15 / API 35, 1440×2560, density 360, font scale 1.0. An isolated package protects original application data. Older Android compatibility is checked in code; no old physical device was used in this run.

## Rendering matrix

`CompatAcceptance` renders portrait 360×640 and landscape 640×360 at 85%, 100% and 140% on both light/dark paper, plus time-format cases. It checks meaningful ink coverage, paper-colored bottom three rows, the header's right margin and a filled top-left corner. Landscape contains exactly one full-width header rule.

`headerSpan` checks weekday/lunar separation for Laba, Double Ninth, Mid-Autumn, ordinary dates and nine-character worst-case headlines at all scales/orientations. Terms/festivals join the lunar text without spaces. The colon centers optically on time digits. System sans-serif renders all script and punctuation; no fonts are bundled. Lunar/term/festival labels share a three-second hold and upward transition.

The 2026-11-08 extension covers fixed `上午12:58`/`下午12:58` on Friday, Saturday, Sunday and a long festival date, across both orientations and all three font scales. Weekend red is based solely on weekday. Header units and 星期 are 72% of base size. Numerals and final weekday glyphs use system Black with supported fallbacks; other text is one tier lighter. AM ink aligns at the top of digits, PM at the bottom, both at half numeric ink height. Clock width fitting includes the actual smaller label and lets digits grow within layout guards. Rendered examples are inspected visually as well as checked for edge clipping.

## Settings and state

Font slider value/storage agree. The 24-hour toggle defaults on, updates `hour24` and keeps chooser/fullscreen time formatting consistent. Dark-source radio choices are exclusive; start/end sliders appear only for Scheduled mode. Ambient mode appears only with a light sensor. Animation, face brightness and interactive controls retain their original behavior. Help labels stay short/single-line; the sheet fits within 82% of screen height with Done reachable.

Forcing dark updates window, preview and fullscreen immediately; switching off restores light without restart. Historical schedule checks distinguished 13:00–15:00 from 19:00–07:00 at 01:11. Ambient thresholds/hysteresis/confirmation are unit-tested; simulated readings are not physical lighting certification. Preferences persist dark source/start/end, hour format, size and toggles.

Tap enters fullscreen, long press/Back exits, interrupted entry can reverse, and moved/canceled touches do not act as taps. Interactive swipes browse dates and show Return to Today. Midnight preserves a browsed date and resets the live paging origin appropriately. Fullscreen page/decor dimensions are compared against the display to detect unused bar strips. The connected system reports no software-navigation inset source, so this confirms window/page coverage, not an additional physical software-navigation-device trial.

## Reproduction

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug --offline
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --offline
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CompatAcceptance
```

For an isolated application ID, update both install artifacts and instrumentation package accordingly. Rendering output is in the test app's external `files/acceptance/`. Restore original rotation/navigation settings and remove only the temporary test packages after acceptance; do not clear the user's existing application data.
