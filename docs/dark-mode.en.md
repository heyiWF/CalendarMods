[中文](dark-mode.md) | [English](dark-mode.en.md)

# Dark mode

The daily calendar has warm paper and dark paper schemes sharing one layout and calendar data. Only color tokens differ.

## Four sources

The style sheet offers mutually exclusive choices, applied immediately and saved in `theme_styles`: Off (always light, default), Always Dark, Scheduled, and Ambient Light (only on devices with a light sensor).

The default schedule is 19:00–07:00. Start/end sliders adjust whole hours when Scheduled is selected. Midnight-spanning ranges such as 22:00–06:00 work. Equal endpoints form an empty window and keep the page light.

## Light thresholds

`LuxDarkGate` considers readings at or below 10 lx dark, at or above 25 lx light, and retains the current state between them. A candidate must remain valid for 1.2 seconds. Returning to the middle band or receiving invalid data cancels pending confirmation, so separate brief threshold excursions cannot add up to a transition.

The sensor registers only between resume and pause at `SENSOR_DELAY_NORMAL`. A 600 ms recheck of the latest reading allows a stable candidate to commit when its confirmation period expires. Hysteresis and confirmation reduce flicker from passersby, shadows and screen reflections.

Scheduled mode checks at local minute boundaries using `60_000 - currentTimeMillis % 60_000`; manual time/timezone changes are reflected by subsequent local-hour reads.

## Update path

The style sheet updates `ThemeStyle.darkMode`; `DarkModeController.resolve()` emits `onChanged(dark)` to `MainActivity.applyDark()`. The window uses `scheme.backdrop`, status icons follow the light scheme on API 23+, the chooser redraws using a preview key containing the dark flag, and fullscreen redraws in place without a page turn. Light and dark previews have separate cache identities.

| Token | Light | Dark | Use |
| --- | --- | --- | --- |
| `backdrop` | `#DDC79C` | `#0E0D0A` | Chooser/window background |
| `paper` | `#F7EBCE` | `#1B1913` | Paper |
| `chrome` | `#443B2B` | `#C8BCA0` | Chooser text |
| `ink` | `#14110C` | `#F3EAD4` | Main text, numerals, lunar labels |
| `accent` | `#B3362B` | `#E1715C` | Avoidance and weekends |
| `herb` | `#2E6F3E` | `#7CC08D` | Suitable activities |
| `onAccent` | `#F7EBCE` | `#14120C` | Chip text |

Dark chips use light fills and dark text; light chips use colored fills and pale text. Unit tests require body contrast of at least 7:1 and almanac/chip contrast of at least 3:1 in both schemes.

The implementation uses `SharedPreferences`, `SensorManager` and Canvas on API 14+. Light status-bar flags are applied only on API 23+; older systems retain their default status icons.
