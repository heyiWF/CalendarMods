[中文](performance-2026-10-19.md) | [English](performance-2026-10-19.en.md)

# Icon and performance validation · 2026-10-19

This is a historical Compose-branch report; its cache sizes and test counts describe that revision, not every current branch.

## Changes

- The launcher icon uses midnight-blue metal and coral-orange calendar artwork, adaptive masking and a separate monochrome layer. Sources and Android renders are in `docs/media/`; the generation prompt is in [asset notes](assets.en.md).
- Built-in, package and user images decode on the IO thread. Preview, fullscreen and style sheets share a bounded 32 MiB cache. Concurrent requests are serialized and deduplicated. Sampling uses buckets of the window's longest edge, capped at 2048 px, with orientation reuse. Missing/corrupt custom images fall back to package images, then built-ins.
- The last four days of calendar data and two months of 42-cell grids are reused. Date changes still update almanac entries, terms and festivals. Synchronization prevents duplicate calculations.
- Index migration, image cleanup and bundled-font validation moved out of initial composition onto IO. Installed-theme lists and image-path maps rebuild only when their inventory changes.
- A pre-existing Snackbar change was committed separately as requested.

Image loading followed the official [Compose performance codelab](https://developer.android.com/codelabs/jetpack-compose-performance). The calendar library, artwork and animations were retained.

## Same-device comparison

Android 15, `emulator-5554` reporting PHY110, 1440 × 2560, debug APK. The same test package and theme sequence ran against the old APK then the new APK. Assertions used their own date cache to avoid polluting measurements.

| Measurement | Before | After |
| --- | ---: | ---: |
| Median of 30 reads of one date | 242.628 ms | 0.001043 ms |
| Date-read P95 | 311.094 ms | 0.001629 ms |
| Image-theme switch/expand/collapse frame P50 | 18 ms | 13 ms |
| Frame P95 | 78 ms | 85 ms |
| Longest frame | 569 ms | 677 ms |
| Frames over 100 ms / samples | 36 / 825 | 35 / 816 |
| Final process PSS | 176 MiB | 172 MiB |

Date measurements represent cache hits, not first calculation or app startup. This single debug-frame sample includes injected input, layout and scheduling; long frames did not improve consistently, so it does not establish universally better frame rates. Winter Letter fullscreen screenshots had identical SHA-256 hashes before/after, preserving image and layout pixels.

## Checks

31 JVM tests passed, covering cache reuse, leap/month boundaries, eviction equivalence, concurrent reads and existing rules. Image/icon acceptance passed four groups: eight concurrent callers sharing a bitmap, orientation-size reuse, missing/corrupt fallback, proportional large-image sampling/safe eviction, and actual adaptive/monochrome launcher rendering. Performance passed two groups: 30 date reads and three rounds of image-theme navigation/fullscreen/return.

The full device suite passed 54 checks across seven themes and both orientations, including reverse navigation, interrupted entry, rotation, year-end midnight, timezone changes, resume and keep-awake. Image style acceptance passed 12 checks for importing, independent fill, picker/cancel, persistence through rotation/recreation, sheets and carousel. Store acceptance passed six groups for delayed favorite saves/undo, write-failure recovery, three-column previews, ordering, delete/reinstall and re-entry. Explicit target-tab readiness avoids reading outgoing card coordinates.

A clean uncached build and Lint passed. Adaptive icons retain explicit `v26` directories because the toolchain lost linked resources when moving them to unversioned `anydpi`; only the corresponding icon/background Lint suppressions were applied.

Windows uses Android Studio JBR and a writable project-local `ANDROID_USER_HOME`:

```powershell
.\gradlew.bat --no-daemon --offline --no-configuration-cache --no-build-cache clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb shell am instrument -w -e corePerformance true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e resources true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e store true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

Raw logs and private-data backups are ignored under `docs/verification/`. Seven original private files, including theme order, custom images and settings, were restored and checked byte for byte, with no extra private files left. The test APK was removed; the updated application reopened with the original configuration.
