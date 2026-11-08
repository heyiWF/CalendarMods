[中文](theme-store-validation.md) | [English](theme-store-validation.en.md)

# Theme store and midnight animation acceptance

Historical acceptance: 2026-10-14, Android 15, `emulator-5554`, 1440 × 2560, portrait/landscape, debug APK. Midnight used an injected clock without changing system time. This describes the Compose store; compat has no store UI.

## Features and interaction

The original seven presets migrated once into installed themes, preserving external themes, order, selection and styles. Removed themes do not return on restart; re-adding appends them with defaults. Repository coverage included batch favorites, no state publication after write failure, last-theme protection, image cleanup, style reset and saved ordering.

Recommended, Favorites and Installed use three columns. Title and button occupy separate centered rows, 10 dp below the preview and 4 dp apart; actual coordinates passed in both orientations. Apply stays on the current page and becomes Installed, which opens shared removal confirmation; Installed retains Use. Separate installation-status text was removed.

Unfavoriting immediately clears the heart and reports feedback while retaining the card. Rotation/background retain pending state; clicking again undoes it; leaving commits atomically. Simulated write failure restored the filled heart and retained the page. Dragging persisted order; shadow follows only the rounded preview, not the title. Upward drags below threshold rebound, beyond it request deletion without entering fullscreen. The trash target scales around a fixed center with hysteresis. Confirmation says only that the named theme will be removed from installed themes. Style controls retain a 48 dp target aligned with the preview's right edge.

## Performance sample

`-e performance true` creates 7/70/200 in-memory themes without writing the user catalog. `FrameMetrics.TOTAL_DURATION` records frames; PSS is sampled every 250 ms. Each set clears thumbnails, warms all three tabs, then switches/drags/scrolls. Builds and measurements run separately. Debug-device results do not represent every phone.

| Themes | Warm switching P50/P95/max | Drag P95/max | Scroll P95/max | Peak PSS |
| --- | --- | --- | --- | --- |
| 7 | 5/13/87 ms | 35/38 ms | 20/28 ms | 150 MiB |
| 70 | 5/9/55 ms | 61/101 ms | 18/124 ms | 141 MiB |
| 200 | 5/19/45 ms | 13/22 ms | 17/118 ms | 142 MiB |

In this first sample, warm switching had no frame over 100 ms and cost did not grow linearly with catalog size. The 32 MiB LRU peaked around 31.24 MiB under stress and stayed within budget; final scrolling measurements used about 9.11–11.71 MiB.

Cold thumbnail generation still has long frames. Initial screen generation plus a fixed 1.8-second wait took 4526/2640/2442 ms, with maximum frames of 781/414/405 ms. These are cold-cache cases, not three independent cold-process starts. Initial calendar layout/capture still involve the main thread despite serialized generation, sampled decoding and pausing during entry/drag/scroll. The 70-theme drag included a 101 ms frame; 70/200-theme scrolling each included two frames over 100 ms. Long frames were not eliminated.

## Midnight and existing behavior

Seven themes in both orientations crossed New Year with complete old/new pages, final dates and lunar/almanac data. Video inspection checked stable photo/illustration/binding, no mirrored text and no stale page. Each tree retains its own date/carousel state. Time/date/timezone adjustments and resume refresh immediately; only natural foreground midnight animates.

Renderer observers capture short transitions before checking accessibility/screenshots. Rotation rebuilds production content, so completion must use the new page instead of a destroyed observer. Disabled system animations skip midnight; custom motion reads the system scale.

The first final run passed both APKs, 26 JVM tests and Lint with zero errors/two dependency notices. Device groups passed: store 5, performance 12, midnight/interruption 18, existing fullscreen/date 54, contract 4, styles/carousel 12. Midnight retesting included New Year, leap day, back/rotation mid-animation, resume and disabled animation. Rotation, font/animation scales and Asia/Shanghai timezone were restored; the test APK was removed and original content/settings retained.

## Reproduction and evidence

```powershell
New-Item -ItemType Directory -Force docs/verification/logs | Out-Null
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest 2>&1 | Tee-Object docs/verification/logs/build.log
adb shell am instrument -w -e store true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e performance true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e midnight true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e interruptions true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e contract true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

Ignored logs under `docs/verification/logs/`: `theme-store-final-build.log`, `theme-store-device-final.log`, `theme-store-performance-final.log`, `theme-midnight-final.log`, `theme-store-regression-final.log`, `theme-store-contract-final.log`, `theme-store-styles-final.log`. Raw PNG/video/contact sheets: `docs/verification/theme-store/`. Product images: [portrait](screenshots/theme-store-portrait.jpg), [landscape](screenshots/theme-store-landscape.jpg).

After changing title/button to two centered rows, portrait/landscape coordinate assertions, APK build and five store groups passed. Logs: `theme-store-centered-build.log`, `theme-store-centered-device.log`; product images were refreshed.

## Interaction retest on 2026-10-14

Tabs fade out before a short sliding fade-in; target layout completes before entry. One stable-ID grid/cache is reused, and quick reversals cancel/reconnect transitions. Tests inspect intermediate alpha directly instead of relying on accessibility queries that may span the entire animation.

Cross-column drag reuses images despite 1–2 px width rounding. Pixel sampling while a real drag is held verifies that image regions do not become solid color. Apply uses the official wavy circular indicator, testing indeterminate, a real 42% callback, success and failure/retry; local presets do not simulate network downloads. The component comes from `material3:1.5.0-beta01`; fullscreen and settings were retested.

Done animates the sheet closed before removal. A vertical-delete gesture remains locked during subsequent horizontal movement; ordinary horizontal paging resumes after it ends. Trash only scales, without vertical displacement. The store entry shares rounded Surface ripple. Re-entry resets Recommended, while Use returns by fading instead of a shared-container overlay.

| Themes | Warm switching P95/max | Switching frames >100 ms | Drag P95/max | Peak PSS |
| --- | --- | --- | --- | --- |
| 7 | 35/87 ms | 0 | 15/24 ms | 142 MiB |
| 70 | 12/188 ms | 2 | 20/24 ms | 144 MiB |
| 200 | 16/56 ms | 0 | 11/16 ms | 145 MiB |

Cache use was 9.11–18.22 MiB within 32 MiB. Scrolling 70/200 themes still produced 5/3 frames above 100 ms. The current seven-theme warm switch/drag/scroll sample had none; cold and occasional long frames remain.

Use `-e storeMotion true` for sheet motion, gesture lock, intermediate tab frames and install progress. Logs: `theme-store-motion-final-build.log`, `theme-store-motion-device.log`, `theme-store-motion-regression.log`, `theme-store-motion-performance-final.log`, `theme-store-motion-fullscreen.log`, `theme-store-motion-styles.log`. Videos `store-motion.mp4`, `store-verified-tabs.mp4` and frames are under ignored `docs/verification/theme-store/`.

Final counts in that round: 26 JVM tests, both APKs, Lint zero errors/two notices; device motion/progress 5 groups, store 6, performance 12, fullscreen 54, styles 12. Device settings/content were restored and test APK removed.

### Full-width card buttons

Title and actions remain on separate centered rows. Apply, Installed and Use fill card width, and the progress region uses the same width. Spacing remains 10 dp from preview and 4 dp to button. Screenshots and six store groups passed in both orientations; 26 JVM tests and APK builds passed, with the same two Lint notices. Logs: `store-button-width-build.log`, `store-button-width-device.log`.
