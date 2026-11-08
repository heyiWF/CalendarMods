[中文](launch-navigation-validation.md) | [English](launch-navigation-validation.en.md)

# Launch and back navigation validation

The launch animation uses the application's calendar icon: the bound header settles, the highlighted date grows into place, and the splash fades into the first calendar frame. It uses the Android 12+ platform splash, observes disabled animations and bounds the initial-data wait.

Navigation coverage:

- Fullscreen calendar back to theme selection.
- Theme store back to theme selection.
- Store edit mode back to the store, preserving pending-favorite rules.
- Theme deletion confirmation back to its parent.
- Every theme's Material style sheet, using Material3's platform predictive-back support.
- On pro, both scheduled-night start/end time dialogs back to the containing style sheet, with unconfirmed input discarded.

Custom surfaces share progress, cancellation and commit handling. Only commit changes navigation. The Android 15 instrumentation dispatches start/progress/cancel/commit events to the focused window's back dispatcher and compares real screenshots during progress; sheet dismissal is checked separately. These checks verify app integration, not system edge-gesture delivery.

The available MuMu system does not attach an EdgeBackGestureHandler, even with its navigation overlay enabled. Its original navigation setting was restored. A gesture-enabled physical device or standard emulator is still required for end-to-end system predictive-back acceptance, especially Material's sheet animation. Do not report dispatcher tests as that acceptance.

Run the existing instrumentation with `-e navigation true` using matching isolated application IDs. Pro additionally supports `-e parity true` to export all eleven themes at equal pixel dimensions and dates for comparison with the framework branch.

All four traditional themes now share the compat default palette: red on weekends, calendar
festivals and enabled special editions; green otherwise. Explicit ink choices override the
default in both day and night modes. `-e palette true` checks weekday/weekend/festival and manual
override colours on real rendered pages.
