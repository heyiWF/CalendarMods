[中文](almanac-motion-alignment.md) | [English](almanac-motion-alignment.en.md)

# Gentle Good Day motion and fixed One Page alignment

Recorded 2026-10-13 on Android 15 / API 35, `emulator-5554`, 1440 × 2560, font and animation scales 1.0.

## Behavior

- **Good Day:** each item remains fully visible for two seconds. When its length changes, label movement lasts 640 ms instead of 260 ms, using symmetric `CubicBezierEasing(.42, 0, .58, 1)`. Movement overlaps the 280 ms fade-out, 60 ms blank interval and 300 ms fade-in. Content switches only at zero opacity, so old/new items never overlap. Movement target and displayed text are independent to prevent a jump at replacement. Equal-length items retain sequential fading.
- **One Page:** the fixed centered origin is measured from the actual width of the suitability label, gap, two two-character items and separator. Suitability/avoidance labels remain fixed and vertically aligned. All entries, including a single final entry, align left at the same origin; longer text extends right. The label-to-entry gap stays fixed. Each page holds two seconds, fades out for 220 ms and fades in for 220 ms.
- Portrait, landscape, chooser preview and fullscreen share rendering and state.

## Verification

After making the final single item left aligned, both APKs were rebuilt and ten fixed-spacing checks passed across both orientations. Evidence is in ignored `docs/verification/fixed-almanac-spacing/`. The following Good Day and unit results precede that final alignment adjustment in the same validation round.

The initial offline build, unit tests, APKs and Lint passed: 13 JVM tests, no Lint issues. Good Day passed six checks per orientation covering two-, three-, four-character entries and returning to two characters: center error at most two pixels, repeatable return position, continuous movement direction, and overlapping motion/fade. One Page passed five checks per orientation: centered two-item group, long entries retaining their origin, left-aligned long/short final entries, return to the first entry, and fixed aligned labels throughout the cycle.

Frame inspection covered 191 portrait and 178 landscape frames for Good Day with no disappearing label or reverse jump. Recording used variable frame rate alongside assertions, so this is not a frame-rate benchmark. A complete One Page cycle was also recorded. The new app was retained, the test package removed, and original One Page/fullscreen/rotation settings restored.

| Evidence | Links |
| --- | --- |
| Good Day motion overlapping fading | [Portrait](screenshots/gentle-almanac-motion-portrait.jpg), [landscape](screenshots/gentle-almanac-motion-landscape.jpg) |
| Fixed labels and long/short One Page entries | [Comparison](screenshots/anchored-almanac-comparison.jpg) |

Replay on the historical Compose runner (`0` portrait, `1` landscape):

```powershell
adb shell am instrument -w -e centered 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e centered 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e anchored 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e anchored 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

Raw recordings, samples and logs: ignored `docs/verification/almanac-motion-alignment/`. Only the stated Android 15 emulator was covered. Historical APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `890BFF432A296059CF89EC39C21C8E866E057E2F370A14C8644979CEDA6D571A`.
