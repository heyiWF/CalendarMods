[中文](README.md) | [English](README.en.md)

# CalendarMods

<img src="docs/media/app-icon-playstore.png" width="112" alt="CalendarMods navy and coral calendar icon">

**Keep a calendar you enjoy on your screen.**

CalendarMods turns an Android phone or tablet into a live calendar. The mycompat branch offers one layout, **Today · One Daily Page**: warm paper, a prominent date, quiet clock, daily suitable/avoid entries and a Monday-first month grid. Tap to expand into fullscreen.

**One layout · 42-cell month grid · Lunar dates and terms · Adjustable type · Light/dark paper · Immersive display · Midnight page turn · Offline**

Requires **Android 4.0 / API 14 or later**.

Screenshots refreshed on **2026-11-10** from this branch’s latest build. Preview images retain the style button at the lower right; fullscreen images are shown separately.

| Portrait | Landscape |
| --- | --- |
| <img src="docs/screenshots/readme/fullscreen-light-portrait.png" width="240" alt="Daily page in portrait"> | <img src="docs/screenshots/readme/fullscreen-light-landscape.png" width="440" alt="Full-width date header and landscape month grid"> |
| <img src="docs/screenshots/readme/fullscreen-dark-portrait.png" width="240" alt="Dark portrait page"> | <img src="docs/screenshots/readme/fullscreen-dark-landscape.png" width="440" alt="Dark landscape page"> |

## Today on one page

Open to today — date, lunar name, term and festival in one line, a clock and the day's 宜 and 忌 beside them, the whole month grid below. Portrait stacks them vertically, landscape spreads them wide, and light and dark paper switch at a tap.

- **Today at a glance:** a large date in a filled circle, weekends in red, so rest days stand out.
- **The month in hand:** a Monday-first grid where lunar dates, terms and festivals rotate day by day.
- **宜 and 忌 in place:** green for suitable, red for avoid — folk-calendar content that changes every day.

## Tap to expand

Tap the calendar for an animated fullscreen view. Status/navigation bars disappear and the calendar uses the released space. The screen stays awake for desk/stand use. Long press or system Back returns to preview and normal screen timeout. Portrait and landscape have their own layouts.

## Your preferences

The Aa style control at the preview's lower right applies and saves changes immediately:

- **Size:** 85%–140%, with fitting guards keeping the page inside available space.
- **12/24-hour time:** 24-hour by default. In 12-hour mode the smaller `上午` aligns with the digits' top and `下午` with their bottom; both use half the numeric ink height, freeing width for larger digits.
- **Dark mode:** manual, scheduled or ambient-light driven when a sensor is available.
- **Page animation:** enabled by default for midnight changes; disabling it changes the date directly.
- **Face-aware brightness:** optional on camera devices. While fullscreen, a detected face raises window brightness; absence dims it while the screen remains awake. Processing stays in memory on the device.
- **Interactive calendar:** swipe through dates in fullscreen; Return to Today resumes the live date.

| Preview and Aa control | Dark preview |
| --- | --- |
| <img src="docs/screenshots/readme/preview-light.png" width="260" alt="Daily calendar preview and style control"> | <img src="docs/screenshots/readme/preview-dark.png" width="260" alt="Dark calendar preview"> |

## Live and offline

The page follows device date/timezone, turns at foreground midnight and refreshes on resume. Gregorian/lunar dates, terms, festivals and suitable/avoid entries are calculated locally. [tyme](https://github.com/6tail/tyme) supplies the calendar rules, including mainstream solar and lunar festivals such as New Year, Spring Festival, Lantern Festival, Qingming, Dragon Boat, Qixi, Ghost Festival, Mid-Autumn, Double Ninth, Laba and Lunar New Year's Eve, plus Women's Day, Labor Day, Children's Day, Teachers' Day and National Day.

## Start and download

1. Open Aa to choose type size, time format, dark mode and optional behavior.
2. Tap the calendar to enter fullscreen.
3. Long press or press Back to return.

[![Build APK](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml/badge.svg?branch=mycompat)](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml?query=branch%3Amycompat)

Open a successful **mycompat** run in [GitHub Actions](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml?query=branch%3Amycompat), download `CalendarMods-debug-<run-number>` from Artifacts, extract and install `CalendarMods-debug.apk`. These are development-preview builds.

## Development and reference

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CompatAcceptance
```

Use Android Studio JBR and the SDK configured in `local.properties`. For isolated testing, quote `'-Pcalendarmods.applicationId=com.calendarmods.audit'` in PowerShell and use the matching package in ADB.

[Documentation index](docs/README.en.md) · [Layout/data](docs/daily-page.en.md) · [Dark mode](docs/dark-mode.en.md) · [Face brightness](docs/face-brightness.en.md) · [Device acceptance](docs/device-validation.en.md) · [Compatibility](docs/compat-android-4.en.md) · [Assets](docs/assets.en.md) · [Issues](https://github.com/heyiWF/CalendarMods/issues) · [MIT license](LICENSE)

Screenshots show their capture date; the live calendar follows the device. Traditional almanac entries are presented as folk-calendar content.
