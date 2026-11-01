# Framework branch history review

The baseline is `pro` at `4733e02`; the review follows the framework rewrite and every subsequent commit through `ff4e65e`. Android 4.0 / API 14 remains the minimum. The theme store and ordering are intentionally absent.

| History | Finding and disposition |
| --- | --- |
| `843deb6` framework rewrite | Keep framework Views, date arithmetic and legacy image decoding. Fix repeated settings construction, asynchronous result races, excessive bitmap budgets, ignored installed fonts, duplicate illustration padding and landscape column widths. |
| `54b04be` midnight preservation | Keep the browsed date. Also reset the live paging origin after midnight and when returning to today; discard superseded date loads. |
| `ad7e760`, `9c2b7a1` layout/store removal | Keep fixed theme inventory. Remove unreachable store mutations and stale documentation. Compare all 11 renderers with the retained pro layouts. |
| `ba105a7` chooser coordinates | Keep the separate strip offset and single-page fling. Reject cancelled and displaced taps. Retain bounded previews. |
| `127498a` page indicators | Keep one indicator per installed theme, including all 11 built-ins. |
| `9f34f1c` numeral bands and gestures | Keep column-aware date bounds. Reject moved, cancelled and multiple-pointer gestures before treating release as a long press. |
| `82b7688` sheet measurement | Keep margin-aware layout parameters and window height cap. Bind existing controls instead of rebuilding the sheet on every change. |
| `fdfeb6c` ambient policy and default ink | Preserve the intentional 10 lux threshold with timed stability, and weekday green / weekend or festival red defaults. These are deliberate compat differences from pro. |
| `068a2d8` short-screen dates | Preserve the minimum free-band calculation; do not restore dimensions that collapsed dates on short screens. |
| `ff4e65e` outlines and rotating text | Keep outline rendering and two-second text paging. Extract glyphs at a normal size, extend the safe path to traditional plates, and stop hidden-page timers. |

Additional regression fixes: interrupting fullscreen entry must retain the page for exit; unchanged sensor samples must not redraw or cancel page transitions; scheduled preview colours must update at the selected minute, not the next five-minute cache boundary.

`CompatAcceptance` exercises real font rasterization, every built-in theme in both orientations, setting row/checkbox behavior, interrupted fullscreen transitions, cancelled/displaced gestures and midnight/return-to-today paging. `compat-validation.md` records the environment and limitations. Build/lint and a modern device do not substitute for testing an actual API 14 vendor device.
