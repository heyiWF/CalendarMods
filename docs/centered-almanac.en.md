[中文](centered-almanac.md) | [English](centered-almanac.en.md)

# Centered Good Day entries and continuous label motion

The later implementation extends movement to 640 ms, overlaps it with fading and adjusts One Page alignment. See [the follow-up report](almanac-motion-alignment.en.md) for that behavior and APK. This document preserves the initial centering work.

Recorded 2026-10-12 on Android 15 / API 35, `emulator-5554`, 1440 × 2560, font and animation scales 1.0.

The suitability label, gap and entry are centered as one measured group. Two-character entries define the resting position; three/four-character entries shift the label left, and returning to two characters restores it. Landscape centers within the right content panel. Preview and fullscreen share the renderer and carousel state.

Each entry holds two seconds, then fades out for 220 ms. If its length changes, the still-opaque label moves for 260 ms with ease-in/ease-out before the next entry fades in for 220 ms. Equal-length entries need no movement. One Page's fixed labels and paired-entry carousel were unchanged in this initial revision.

## Verification

Offline unit tests, debug build and Lint passed: 13 JVM tests, no Lint issues. The test APK also built. Injected entries were `嫁娶 → 入人口 → 祭祀 → 修饰垣墙 → 嫁娶`. Six checks passed per orientation: at most two pixels of stable center error, larger left movement for longer words, repeatable return positions, transparent entries during movement and continuous direction.

Frame inspection covered 203 portrait and 192 landscape frames, capturing 12–14 position-changing frames per move. All four outward/return paths had no reversal, missing label or overlapping entries. Variable-rate recording is not a performance frame-rate measurement. After acceptance, the test package was removed, the new app retained, and One Page, fullscreen and automatic rotation restored.

| Evidence | Portrait | Landscape |
| --- | --- | --- |
| Two/three/four characters and return | [View](screenshots/centered-almanac-portrait.jpg) | [View](screenshots/centered-almanac-landscape.jpg) |
| Fade out, move, fade in | [Frames](screenshots/centered-almanac-motion-portrait.jpg) | [Frames](screenshots/centered-almanac-motion-landscape.jpg) |

```powershell
adb shell am instrument -w -e centered 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e centered 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

Raw evidence: ignored `docs/verification/centered-almanac/`. These historical checks cover only the stated Android 15 emulator. APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA-256 `9F734DB1DE67F572A771B3924D893A90EA321904A408A9E141B76884CD042D5B`.
