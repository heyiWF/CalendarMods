[中文](theme-refinements.md) | [English](theme-refinements.en.md)

# Theme previews, daily words and style acceptance

Historical acceptance: 2026-10-12, Android 15 / API 35, `emulator-5554`, 1440 × 2560, font/animation scales 1.0. Later Good Day centering and One Page alignment supersede the initial motion described here; see [follow-up acceptance](almanac-motion-alignment.en.md).

## Changes

Seven separate portrait preview implementations were removed. Chooser previews now measure `CalendarPaper` against the current window and scale the complete page, sharing image proportions, text, grids and safe areas with fullscreen. Shared bounds retain a continuous 460 ms scale. Removing double opacity crossfading prevents photographs whitening midway; page counters, names and hints fade independently.

Winter Letter uses Chinese month names; Captured Light removes the English month to free grid space. Good Day combines English and Chinese month names and shows a festival box only on marked days. The vintage almanac aligns month, weekday and week number in equal-width columns at one size; empty festival boxes disappear and all almanac entries remain. Dense month-grid numerals shrink to remaining row height.

One Page shows only year/month at the top. Its original carousel displayed up to two items with a middle dot, a centered final single item and fixed labels; Good Day rotated individual entries. Both held two seconds, faded out for 220 ms, switched and faded in for 220 ms. The final single item's alignment was subsequently changed as documented in the follow-up.

Five themes share 366 original sayings of at most 14 Chinese characters including punctuation; Verse has 366 unique two-character phrases. Continuous-day indexing avoids repeats in any 366-day window and does not reset at New Year. Date/timezone/resume updates share one path.

Each theme gained a Material Style entry. Winter Letter and Captured Light support the system picker, crop/fit/stretch and reset. At that revision other themes displayed no options. Images decode off the main thread, preserve aspect ratio with a 2048 px longest-edge limit and save private copies. Image/fill persist independently per theme without whole-library permission.

Settings use [Material 3 ModalBottomSheet](https://developer.android.com/develop/ui/compose/components/bottom-sheets), buttons, dividers and radio controls. [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) falls back through the Activity contract to a document picker where unavailable.

## Evidence and limits

Offline unit/build/test-APK/Lint tasks passed: 13 JVM tests and no Lint issues. The general suite passed 54 checks across seven themes/orientations, reverse navigation, fullscreen, long press/back, interrupted entry, recreation, slow drag, system bars, midnight, timezone and resume. Style acceptance passed 12 checks: all entries, independent fill, picker/cancel, private image copy, decode limits, recreation, landscape sheet and complete paired carousel.

Four dense-content checks covered 29 suitable activities and all 42 cells on 2026-01-22, plus the year's longest 14-character saying in both orientations. Only the app clock was injected; system time stayed unchanged. A dedicated image was also selected manually, survived force-stop/restart with crop fill, then was reset and cleaned up.

Portrait/landscape photo transitions, long-press exit and interrupted return were recorded and inspected: layout remained continuous and photos no longer faded pale. Good Day recording confirmed sequential disappearance/appearance with a fixed label in this initial revision. Fourteen preview/fullscreen crop comparisons had mean absolute RGB difference below 0.7%, including scaling and capture-phase differences; this is not an animation performance benchmark.

| Evidence | Links |
| --- | --- |
| Seven-theme landscape preview/fullscreen | [Comparison](screenshots/refinement-landscape-comparison.jpg) |
| Photo transitions | [Portrait](screenshots/refinement-transition-portrait.jpg), [landscape](screenshots/refinement-transition-landscape.jpg) |
| Sequential carousel fading | [Frames](screenshots/refinement-carousel.jpg) |
| Material image settings | [Portrait](screenshots/refinement-style-teddy.jpg), [landscape](screenshots/refinement-style-landscape.jpg) |
| Selected image after cold start | [View](screenshots/refinement-picked-cold.jpg) |
| 29 almanac entries | [Portrait](screenshots/refinement-dense-almanac-0.jpg), [landscape](screenshots/refinement-dense-almanac-1.jpg) |
| Longest saying | [Landscape](screenshots/refinement-longest-motto-1.jpg) |

Only Android 15 was used; Android 12 hardware and physical camera-cutout devices were not separately covered. The updated app was retained; the test package removed and original theme/display/rotation restored.

```powershell
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e dense true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

Raw logs/videos/full-resolution images: ignored `docs/verification/refinements/`; key compressed images are committed. Historical APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `A41F67B79B1222042915FEEA45B0A3BAF29802DF16458E5BB2F76C1436660F95`.
