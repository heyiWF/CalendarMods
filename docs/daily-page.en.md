[中文](daily-page.md) | [English](daily-page.en.md)

# Daily page layout and data

**Today · One Daily Page** is the only built-in layout on mycompat. `DailyPage` draws it with Canvas and the system sans-serif family, without bundled fonts.

## Coordinates and type hierarchy

Portrait uses a 360 × 600 design space: date, clock, month grid and bottom-left almanac. Landscape uses 640 × 360: full-width date line, clock/almanac on the left and month grid on the right. `PaperView` scales design coordinates by the actual window dimensions; the remaining height participates in layout. The page margin is 14 design units.

Numerals in the header, clock and month grid, and the final weekday glyph (一–日), request the system's Black weight (900) on API 28+. Supporting text requests ExtraBold (800). Android chooses supported family/glyph fallbacks. API 21–27 uses `sans-serif-black` and bold; if the OEM omits the Black family alias, it falls back to bold and regular. API 14–20 uses bold and regular. Font objects are cached once per process. No font files are shipped.

The date line shares one baseline. Month/day digits, the final weekday glyph and the lunar headline use base size 46; 月, 日 and 星期 use 72% of it. The entire Saturday/Sunday label is red, based solely on day of week, independent of holidays. Other weekdays retain normal ink. Gaps are proportional to the base size: `.12 / .05 / .30 / .34 em`. Measurement uses the same run sizes and weights as drawing. If the complete line is too wide, all runs and gaps shrink together, with no lower font-size floor, preserving separation before the right-aligned lunar headline.

`CalendarData.lunarHeadline` joins the lunar date directly to the day's term or first festival, without a space. Priority: solar term, first festival, nothing. For example `九月廿九立冬`; simultaneous festivals use the first entry in data order. This header never rotates. Acceptance includes three-character festival names and synthetic nine-character headlines, verifying that the weekday ends before the lunar run begins.

## Clock

`ClockFace(period, hour, minute)` separates display runs. In 24-hour mode `period` is empty and the hour is padded (`01:05`). In 12-hour mode the hour is unpadded (`上午9:05`, `下午12:30`). The period's visible glyph height is half the numeric glyph height, and actual ink bounds align AM at the digits' top and PM at their bottom. The numeric baseline remains stable. The colon is measured separately and vertically centered on the digits.

Numeric tracking is 1.6% of size; the gap after the period is 14%. The normal portrait target is 64; a 12-hour clock can use up to 1.5 times that target before width/layout protection. Landscape also targets up to 1.5 times the base size. `fitClock` measures all actual runs and gaps once and scales to column width, avoiding iterative one-point probing. Freed period-label space is available to the digits while the month grid/almanac guards still apply.

Landscape retains only the full-width rule below the header. The month-grid rule appears only in portrait. The landscape grid starts below the header and ends near the bottom margin.

## Month grid and almanac

The grid always has six rows and seven columns, Monday first. Numerals and weekday headings use the stronger weight; lunar/festival labels use the supporting weight. Weekend dates are red, today uses a solid circle with reversed text, and out-of-month cells use 26% opacity. Labels share normal light/dark ink with numerals. The number band occupies the space above a label band of about 30% of cell height; fitting uses actual cell width and available height.

Green 宜 and red 忌 chips sit at the lower left, with matching entry text separated by ideographic spaces and wrapped to width. Whole-day verdicts split by column: `馀事勿取` closes the suitable column and `诸事不宜` closes the avoid column. Empty suitable/avoid columns show `诸事不宜`/`无` respectively.

## Schemes and sizing protection

| Token | Light | Dark |
| --- | --- | --- |
| `paper` | `#F7EBCE` | `#1B1913` |
| `ink` | `#14110C` | `#F3EAD4` |
| `accent` | `#B3362B` | `#E1715C` |
| `herb` | `#2E6F3E` | `#7CC08D` |
| `onAccent` | `#F7EBCE` | `#14120C` |

Color changes do not change geometry. Dark chips use light fill with dark text. See [dark mode](dark-mode.en.md) for switching policy.

The font slider ranges from 85% to 140%, default 100%. Layout starts at the requested size and retries in 2% steps to a 70% floor when necessary. The date line has its own unrestricted proportional fit. Grid cells must remain at least 26 units high; the almanac must begin at least six scaled units below the clock. Numerals fit within 92% of cell width and the available number band. Label size is `min(cellH × .30, cellW × .46)`. Almanac text is independently constrained to 26% of portrait or 46% of landscape height.

Device rendering checks both orientations, all three slider reference sizes and both schemes, including page-edge pixels and header-run boundaries. Fixed AM/PM examples, weekends and long headlines extend this matrix.

## Label animation and long text

All cells share `SystemClock.uptimeMillis()`. Each field holds for 3000 ms, then slides upward for 240 ms: outgoing `y → y-h`, incoming `y+h → y`, cycle 3240 ms. Resting and moving labels use the same font-metric baseline. `frameDelay` returns the time to a boundary while settled and 16 ms during movement. Scheduling uses the timestamp of the drawn frame so a slow frame cannot skip the final resting position.

Static previews and motion-disabled rendering show the first field. System-disabled animation stops animation wakeups; the clock still redraws on minute boundaries.

Two-character labels print centered. Longer names shrink to fit when the ratio remains at least `.62`; three characters normally fit this way. Otherwise they scroll horizontally: pause 1000 ms, move at 40 design units/second, then remain at the final position for the rest of the hold. Motion updates request 16 ms only while needed; `labelDelay` participates in the host's next redraw. Each new carousel cycle restarts the scroll. No field is truncated or replaced with an ellipsis. `Plate.text` shrinks to fit available height rather than discarding text.

## Data and caches

All calculations use local `tyme4j`: lunar month/day names, `termDay` only when `dayIndex == 0`, lunar/solar festivals, first-day nine-day and dog-day markers, and `lunarDay.recommends/avoids`. Multiple same-day festivals rotate in grid labels; the header chooses one using the same order. Seasonal nine-day/dog-day markers participate in the grid but not the header suffix.

Eight daily `CalendarData` entries and four monthly grids are cached. Rotation/resume can reuse daily data; midnight changes daily content while retaining the 42-cell monthly grid. `PaperView` aligns clock refresh to `60_000 - currentTimeMillis % 60_000`. `PlainDate` is timezone-free; `CalendarClock` resolves the device-local date.
