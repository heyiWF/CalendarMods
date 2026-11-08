[中文](compat-android-4.md) | [English](compat-android-4.en.md)

# Android 4.0 compatibility branch

mycompat derives from the pro/compat framework implementation and lowers the requirement from Android 12 / API 31 to Android 4.0 / API 14. Framework Views and Canvas replace Compose while retaining calendar calculations, theme contracts, storage and editorial content.

## Why changing minSdk alone is insufficient

The dependency versions used for the rewrite require a newer Android runtime (the Material 3/activity stack required API 24 at manifest merge). Overriding that merge check does not make those APIs work on API 14. The UI therefore has a separate implementation.

| Modern implementation | Compatibility implementation |
| --- | --- |
| Compose/Material layouts | `ChooserView`, `PaperView`, `StyleSheetDialog` |
| Compose Canvas | `Plate` and `DailyPage`, 360×600 / 640×360 design coordinates |
| Compose text | `Paint` measurement and manual wrapping |
| Pager/motion | `Scroller` and `Camera` page turn |
| `java.time` | Timezone-free `PlainDate`, timezone resolved by `CalendarClock` |
| `ImageDecoder` | `BitmapFactory` and EXIF correction |
| `AtomicFile` | Temporary file, fsync and rename |
| Coroutines/Flow | Background loading, caches and Handler |
| Wavy progress | Direct state changes without that indicator |

mycompat now has one DailyPage layout with date, clock, almanac and a Monday-first 42-cell grid. Long-press exit, midnight/interactive paging and Return to Today remain. It adds optional camera permission for fullscreen face-aware brightness; frames stay in local memory. See [face brightness](face-brightness.en.md). There is no theme store or bundled font/image package workflow.

Fullscreen uses `LAYOUT_HIDE_NAVIGATION` and `LAYOUT_FULLSCREEN` from API 16 so hidden navigation space belongs to the calendar; API 14–15 retain the older supported flags. Returning restores normal system-bar behavior.

The daily page uses one forward midnight turn and system sans-serif weights: Black/ExtraBold on modern APIs, then supported black/bold/regular fallback on older devices. No fonts are bundled. Launcher icons are raster below API 26 and adaptive above it. See [layout](daily-page.en.md).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Push CI in `.github/workflows/android.yml` publishes `CalendarMods-debug.apk` under Actions artifacts. Modern-device checks do not substitute for old vendor-hardware acceptance.
