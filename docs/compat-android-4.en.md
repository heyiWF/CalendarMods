[中文](compat-android-4.md) | [English](compat-android-4.en.md)

# Android 4.0 compatibility branch

The compat branch derives from pro and lowers the requirement from Android 12 / API 31 to Android 4.0 / API 14. Framework Views and Canvas replace Compose while retaining calendar calculations, theme contracts, storage and editorial content.

## Why changing minSdk alone is insufficient

The dependency versions used for the rewrite require a newer Android runtime (the Material 3/activity stack required API 24 at manifest merge). Overriding that merge check does not make those APIs work on API 14. The UI therefore has a separate implementation.

| Modern implementation | Compatibility implementation |
| --- | --- |
| Compose/Material layouts | `ChooserView`, `PaperView`, `StyleSheetDialog` |
| Compose Canvas | `Plate` and `PageArtwork`, 360×600 / 640×360 design coordinates |
| Compose text | `Paint` measurement and manual wrapping |
| Pager/motion | `Scroller` and `Camera` page turn |
| `java.time` | Timezone-free `PlainDate`, timezone resolved by `CalendarClock` |
| `ImageDecoder` | `BitmapFactory` and EXIF correction |
| `AtomicFile` | Temporary file, fsync and rename |
| Coroutines/Flow | Background loading, caches and Handler |
| Wavy progress | Direct state changes without that indicator |

Eleven layouts, palettes, typography, grids and traditional columns remain. Theme selection, long-press exit, midnight turn, scheduled/ambient dark mode, image/fill and installed fonts remain available. Built-ins are fixed; there is no delete/reorder/store UI. The app works offline without permission requests.

Fullscreen uses `LAYOUT_HIDE_NAVIGATION` and `LAYOUT_FULLSCREEN` from API 16 so hidden navigation space belongs to the calendar; API 14–15 retain the older supported flags. Returning restores normal system-bar behavior.

Differences: midnight uses one forward page turn instead of renderer-specific motion; store/install progress/delete rebound/shared-element transitions are omitted. Fonts must be system-readable TTF/OTF; API 14 lacks variable weights, so Light/ExtraLight fall back to regular. Launcher icons are raster below API 26 and adaptive above it. Large date numerals use bounded cached rasters for older GPU behavior.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Push CI in `.github/workflows/android.yml` publishes `CalendarMods-debug.apk` under Actions artifacts. Modern-device checks do not substitute for old vendor-hardware acceptance.
