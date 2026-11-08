[中文](verification-2026-11-08.md) | [English](verification-2026-11-08.en.md)

# Validation and changes · 2026-11-08

Branch: `compat`. Work began from a clean checkout after fetching remote refs. Build, 56 JVM tests, app/test APKs and Lint passed. Device: emulator-5554, Android 15 / API 35, isolated application ID `com.calendarmods.audit.compat`.

248 software font checks, 16 hardware-window checks and both fullscreen orientations passed. No old Android hardware was tested this round; version guards preserve original minimum APIs. The connected system reports no software-navigation inset source. Page/window bounds match the display; this is not an additional old-device or different-navigation-hardware acceptance claim.

Framework fullscreen uses layout-hide-navigation plus layout-fullscreen flags from API 16. Compose retains enableEdgeToEdge/WindowInsetsControllerCompat and resolves cutout, waterfall and desktop-caption protection with layout-phase windowInsetsPadding. Hidden navigation bars reserve no space. Midnight drawing reads current insets.

The mycompat review fixed interrupted ambient-light confirmation and unnecessary animation wakeups when system motion is disabled. System fonts are cached; the clock fits proportionally after measuring its actual runs once. Smaller units/weekday prefixes emphasize heavier numerals and weekday suffixes; weekend weekday labels are red. Twelve-hour periods use half numeric ink height: AM top aligned, PM bottom aligned. Device coverage includes both orientations, 85%/100%/140%, both schemes, weekends and long headers, with screenshot inspection.

Old acceptance assumptions were corrected: a fixed 100 ms animation phase, padded single-digit days, hardcoded production package and last-theme identity. Test-harness problems were not classified as product regressions. Original app data was not cleared. Logs/screenshots remain in ignored local build directories; historical evidence retains its original dates.

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Runner: `CompatAcceptance`. Quote isolated application properties in PowerShell, e.g. `'-Pcalendarmods.applicationId=com.calendarmods.audit'`, and use matching app/test packages. Main supports `-e lifecycle true` for targeted date/lifecycle checks. Historical test counts, APK hashes and unpublished-status notes describe their original revision only.
