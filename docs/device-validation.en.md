[中文](device-validation.md) | [English](device-validation.en.md)

# CalendarMods 0.1.0 device acceptance

Historical first acceptance: 2026-10-11; fullscreen transition retest: 2026-10-12. This report concerns the original independent Compose app, `com.calendarmods`, minimum Android 12 / API 31. It is retained as history on descendant branches; consult the branch README and current verification report for today's implementation.

## Build and device

Offline `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --no-daemon` passed. Ten unit tests covered 2000–2100 month grids/leap days, lunar New Year, cross-year week numbering, term start dates, multiple festivals, nine-day periods, computed almanac data, midnight and DST day lengths. No tests failed; Lint reported no issues.

Device: `emulator-5554`, Android 15 / API 35, 1440 × 2560, density 360, font scale 1.0. No separate Android 12 device was tested. The initial gesture/date suite passed 52 checks.

## Immersive display

All seven themes passed portrait/landscape selection swipes, tap-to-fullscreen and long-press return to the same theme, including short-drag rebound and reverse swipes. Fullscreen fills the window, hides chooser titles/counts/hints and checks system-bar visibility plus keep-awake flags. Returning restores bars and normal screen timeout.

The device used hardware navigation and exposed no software-navigation `InsetsSource`. Status hiding and restoration to the original navigation state were verified; actual software-navigation rendering was not independently tested.

Landscape rearranges image/grid/date/details. Fixes included clipped Verse lunar text in landscape and insufficient space for the vintage grid's last portrait row. Immersive mode survived left/right landscape and portrait recreation. Ordinary tap/swipe did not exit or switch themes. Home/resume reapplied immersive bars; system Back returned to the chooser. A separate cold-start test kept the seventh hanging theme in fullscreen after force-stop/relaunch on 2026-10-11.

After the initial run, Asia/Shanghai, automatic rotation 1 and user rotation 0 were restored; font scale remained 1.0 and automatic time stayed on. The test package was removed; the updated app remained on the seventh theme fullscreen.

## Live dates

Foreground fullscreen crossed 2026-12-31 midnight into 2027-01-01 without reopening, updating day/month/year and all seven themes' data. At one fixed instant, changing Pacific/Kiritimati to Pacific/Honolulu changed the date from 2027-01-01 to 2026-12-31. An injected background-clock change resumed on leap day 2028-02-29 with new lunar/festival/almanac data. Restoring the production clock restored the actual date. Only the app clock was injected; system time was not changed.

## Screenshots

Images show 2026-10-11; live content follows the device clock. Generated substitute photos/illustrations are documented in [assets](assets.en.md). Ordinary sayings are editorial design, while suitable/avoid entries are calculated calendar data.

| Theme | Chooser portrait | Chooser landscape | Fullscreen portrait | Fullscreen landscape |
| --- | --- | --- | --- | --- |
| Golden | [View](screenshots/portrait-1-golden.png) | [View](screenshots/landscape-1-golden.png) | [View](screenshots/immersive-portrait-1-golden.png) | [View](screenshots/immersive-landscape-1-golden.png) |
| Winter Letter | [View](screenshots/portrait-2-teddy.png) | [View](screenshots/landscape-2-teddy.png) | [View](screenshots/immersive-portrait-2-teddy.png) | [View](screenshots/immersive-landscape-2-teddy.png) |
| Captured Light | [View](screenshots/portrait-3-photo.png) | [View](screenshots/landscape-3-photo.png) | [View](screenshots/immersive-portrait-3-photo.png) | [View](screenshots/immersive-landscape-3-photo.png) |
| Good Day | [View](screenshots/portrait-4-good_day.png) | [View](screenshots/landscape-4-good_day.png) | [View](screenshots/immersive-portrait-4-good_day.png) | [View](screenshots/immersive-landscape-4-good_day.png) |
| Verse | [View](screenshots/portrait-5-verse.png) | [View](screenshots/landscape-5-verse.png) | [View](screenshots/immersive-portrait-5-verse.png) | [View](screenshots/immersive-landscape-5-verse.png) |
| Almanac | [View](screenshots/portrait-6-almanac.png) | [View](screenshots/landscape-6-almanac.png) | [View](screenshots/immersive-portrait-6-almanac.png) | [View](screenshots/immersive-landscape-6-almanac.png) |
| One Page | [View](screenshots/portrait-7-hanging.png) | [View](screenshots/landscape-7-hanging.png) | [View](screenshots/immersive-portrait-7-hanging.png) | [View](screenshots/immersive-landscape-7-hanging.png) |

## Continuous transition retest, 2026-10-12

Preview/fullscreen share bounds, expanding from and shrinking into the same paper position over about 460 ms while respecting system animation duration. Paper content hands over during scaling; titles/hints fade independently. Stable safe areas and restoring bars only after exit prevent mid-transition jumps. Theme swipes/repeated entry are blocked during transition; Back can reverse entry. Both orientations returned to the original preview position/size.

The final APK passed 54 device checks, including the original themes, bars, dates, timezone, resume and rotation. Tests read fresh accessibility nodes and wait for outgoing pages to disappear. Ten JVM tests and Lint passed. Android 12 hardware and software-navigation devices were not additionally covered.

Entry, long-press exit, quick Back and re-entry were recorded in both orientations. [Portrait frames](screenshots/transition-portrait.jpg) and [landscape frames](screenshots/transition-landscape.jpg) are ordered left-to-right in time. Recording frame rate is not a performance benchmark.

After retesting, the test package was removed, app retained, Asia/Shanghai and rotation 1/0 restored with animation scale 1.0. The device ended on the teddy chooser page.

## Reproduction

Build/check commands are in the [README](../README.en.md). `CalendarAcceptanceInstrumentation` prints individual results. CI runs JVM tests and Lint; device interaction uses ADB separately. Background tests wait for the launcher before externally reopening the app to avoid transition timeouts.

APK: `app/build/outputs/apk/debug/app-debug.apk`. CI uses its debug signing setup and publishes per-build `SHA256SUMS.txt`. Historical local SHA-256: `767EA43B99314520DC5BB055642F5992AA9E7D77493D724EE3DC442331BC7BED`.

For the later shared previews, 366 sayings/phrases, carousel and image settings, see [theme refinement acceptance](theme-refinements.en.md).
