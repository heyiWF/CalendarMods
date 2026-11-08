[中文](traditional-theme.md) | [English](traditional-theme.en.md)

# Four traditional almanac themes

The pro branch started from main `fa0b413` and appended four built-ins without changing the original seven IDs/order. The first is `com.calendarmods.traditional`, renderer `traditional.v1`. New installations contain eleven themes; an existing Compose library can add Nanyang, Lingnan, Classic and Shihe from the store. The compat descendant uses a fixed eleven-theme inventory without a store.

## Layout and interaction

The supplied green tear-off almanac dated 2025-04-03 informed Nanyang's cream paper, patterned month header, large unpadded date, Chinese/English/Malay labels, vertical side text, fortune strip, scrolls, dense columns and nine-palace diagram. The app draws native Canvas content with Android glyph metrics, not a photograph background or watermark. Pro uses Compose Canvas; compat uses framework Canvas. Font resolution and rendering are shared by previews/fullscreen. Portrait retains the vertical hierarchy; landscape places the date left and almanac right, scaling text proportionally.

All suitable/avoid and deity entries participate in layout and accessibility rather than truncation. Themes inherit daily paging, midnight motion, return-to-today, long-press exit and per-theme interaction settings. Same-day data comes from `AlmanacRepository → AlmanacEngine → AlmanacDay`; see [data contract](almanac-engine.en.md). Results are remembered by date and share existing caches. No network calculation dependency is added.

## Data and conventions

| Field | Source/convention |
| --- | --- |
| Gregorian date, weekdays, month size | Platform date values; 31 days is a large month, otherwise small |
| Lunar date, stems/branches, elements, nayin, fetal deity, mansions, officers | Existing tyme4j 1.5.1 `LunarDay` convention |
| Suitable/avoid, auspicious/inauspicious stars | Same source as `CalendarData`; no mixed cnlunar adjudication |
| Next solar term | Search the next 16 days for a start date; today/tomorrow/day-after/in-N-days wording |
| Hijri date, Malay month/weekday | tyme4j Hijri calculation plus local labels; not claimed to be Malaysia's official observational calendar |
| Today's eight characters | Selected date at 00:00, explicitly Zi hour; not a personal birth chart |
| Twelve double hours | Zi at 00:00, then 02:00 through 22:00; tyme4j auspiciousness |
| Directions, combinations, clashes | tyme4j HeavenlyStem/EarthBranch APIs, separate yin/yang noble directions |
| Noble hours | Common Tianyi table: 甲戊庚丑未、乙己子申、丙丁亥酉、辛寅午、壬癸卯巳 |
| Daily lu | Ten-stem table; daily stem's lu branch and stems with lu in the daily branch |
| Nine palaces | tyme4j daily star at center, Luo Shu distribution rotated to north-up/east-right |
| Zodiac number mapping | 1–49, current-year animal at 1, reverse cycle every 12; Lunar New Year boundary by default, injectable Beginning-of-Spring convention |
| Six auspicious animals | Project rule: combinations first, then same element and ordered fill; unique and excluding clash/harm; not a publisher's ranking |
| Six daily numbers | Decorative project rule: element/Hetu endings rotated by date; no prediction claim |
| Zhuge travel selection | `qimen-vol19-diagrams-v1`, described below |
| Folk deity birthdays | Fixed lunar dates from nihaixia-app Yuxiaji data; no repeated leap-month celebrations; explicit no-entry text |
| Fortune | Two birth years' zodiac/month relationship with neutral project editorial text |
| Feng shui/dreams | Original short symbolic descriptions, deterministic epoch-day rotation; not daily transcriptions of the reference calendar |
| Bottom seasonal summary | Term hint plus festivals, then deity/phenology fallback; tide column removed |

### Zhuge rule version

The [volume 19 travel-diagram transcription](https://www.diancangwang.cn/xuanxuewushu/ddd428e2df4d/cdfb37c8d73f.html) starts the first day of first/second/third seasonal months at 堂房/天盗/朱雀. The diagram arrows determine order:

- Months 1/4/7/10: 堂房、金库、金堂、顺阳、贼盗、宝仓, six-day cycle.
- Months 2/5/8/11: 天盗、天门、天堂、天财、天贼、天阳、天侯、天仓, eight-day cycle.
- Months 3/6/9/12: 朱雀、白虎头、白虎肋、白虎足、玄武、青龙头、青龙肋、青龙足, eight-day cycle.

The prose summary omits entries, so implementation follows the diagram rather than that incomplete list. Leap months use their named month group. Lunar 3/6 produces 青龙头 through the rule, without a Gregorian-date exception. Verdicts abbreviate the historical text; the 白虎头 disagreement with [Yuxiaji](https://zh.wikisource.org/wiki/玉匣記) is explicitly documented. One reference sheet cannot establish year-round publisher parity.

### Sources and differences

[tyme4j](https://6tail.cn/tyme.html) reuses the existing Java 1.5.1 dependency, without adding a parallel Kotlin engine. [Deity data](https://github.com/jangviktor-web/nihaixia-app/blob/master/lib/data/yuxiaji_deity_data.dart) was structurally converted from Dart to Kotlin; Apache-2.0 license/change notices ship in `assets/notices/nihaixia-app.txt`. [Yitu Danjing's Tianyi table](https://www.shidianguji.com/book/SDZJ0171/chapter/1kcbsgt5kd7oa) informs the common noble table.

No noncommercial Qintianjian code, uncertain-origin dream SQL or tide model is bundled. Noble hours, stars/palaces, suitability and numbers may differ from the reference print; computed values are not overwritten merely to match a photograph.

## Appearance and night mode

Each theme independently stores Original, Pine Green, Vermilion, Indigo, Ochre, Purple and Black ink choices. Dark mode uses lighter related ink on dark paper/backdrop. Manual dark overrides automatic choices. Scheduled night defaults to 22:00–07:00, with native time pickers; start inclusive/end exclusive, midnight crossing supported, equal endpoints disabled.

The pro ambient rule requires at most 10 lx for 30 seconds to darken and at least 40 lx for 10 seconds to lighten; the middle band retains state and cancels confirmation. Any enabled dark condition wins. Sensors run only in the foreground for the current adaptive almanac, unregister in background and restart on resume. No sensor means an unavailable explanation, not camera-based light simulation; manual/scheduled modes remain. Scheduling uses actual device time and updates within 15 foreground seconds, independently of browsed dates. Compat intentionally uses its own 10 lx threshold policy; consult its compatibility history.

Special editions default on and can be disabled. Lunar New Year days 1/2/3 use distinct headings; lantern, dragon-boat leaves, mid-autumn moon, National Day stars and New Year headings retain the full almanac. October 1–3 decoration does not represent official compensatory workdays. Lunar festival decoration excludes repeated leap months.

Text centers on actual ink bounds, the eight characters share four columns, and almanac entries wrap to width without a two-item limit. Naming columns were removed from all four themes.

## Artwork

Chinese zodiac uses a matching twelve-image Wikimedia set; Western zodiac uses twelve Tabler v3.46.0 outlines. SVGs, source URLs and hashes are in `artwork/almanac/`, converted resources in `res/drawable/almanac_*.xml`. Dragon/monkey/rooster/dog/pig remain CC BY-SA 3.0, seven others public domain, Tabler MIT. APK notices retain attribution/licenses. There is no runtime network image loading.

Shihe's stable ID is `com.calendarmods.peitaiyi`, renderer `peitaiyi.v1`, using the same ALMANAC capability/cache/settings. [The reference site](https://www.peitaiyi.com/chinese-almanac/) informs its striped board, large date, fan-shaped term panel and four almanac columns; landscape rearranges them. Default weekdays are green, weekends/festivals red; explicit user colors take priority. Term/festival artwork covers the entire date region, proportional cover, horizontally centered and top aligned, with date/title overprinted left. Selection uses actual shared-engine term/festival dates, not the website's fixed-date approximations.

The 12 zodiac PNGs, 24 term and 25 festival WebPs total 61 images/about 17.5 MB. `assets/peitaiyi/sources.json` records URL, bytes and SHA-256; `tools/fetch-peitaiyi-art.py` reproduces fetching. Original owners retain rights; the site provides no explicit open-source license, so these images are not MIT/CC merely because the app is. A 20 MiB process decode cache supports selected ink/night colors without network requests.

## Interaction and sizing refinements

All eleven themes retain identical page constraints in interactive/static mode, without a reserved bottom strip or swipe hint. Away from today, the centered floating return button fades/moves in over 220 ms and out over 180 ms without relayout. Lingnan side wishes/long editorial text share vertical bounds; nayin/officer/stars/mansions share size, and auspicious/inauspicious columns start together.

All four large dates fit a common two-digit glyph reference, so day 1 and day 31 use the same print size. Scale follows paper/available height; decorated term/festival regions are fitted separately. Digits remain centered between side text columns. `-e overlay true` compares all themes/orientations before/after enabling interaction and checks floating-button position, unchanged bounds and hiding on return.

## Tests and historical results

`TraditionalAlmanacTest` covers 2025-04-03, Lunar-New-Year/Beginning-of-Spring mapping boundaries, leap-month deity dates, all Zhuge groups, twelve hours, center-star placement, unique/reproducible animals/numbers and original theme order. `-e traditional true` uses reference dates 2025-04-03, 2022-01-09 and 2021-01-09, leap day 2028-02-29 and dense 2026-01-22 in both orientations. Coverage includes preview/fullscreen, paging/data/return/exit, festivals, colors, persistence, time pickers, schedule boundaries and injected ambient state. Landscape checks bottom ink coverage so fixed heights cannot leave excessive blank space while text/icons/palaces remain proportional.

The independent package used `'-Pcalendarmods.applicationId=com.calendarmods.proaudit'`; production remains `com.calendarmods`.

On 2026-10-30, 52 JVM tests passed, including shared engine/cache/night policy/festival-term precedence/all 24 term and 12 zodiac resources. Lint had zero errors and 32 vector complexity/dimension/KTX suggestions. Traditional passed 45 checks; overlay passed 22 (eleven themes × two orientations with identical page samples), store passed six. The device exposed TYPE_LIGHT; state-machine/injected-page tests were not presented as physical lighting measurements. The initial round was local with no CI; later work was pushed as recorded below.

Twelve showcase day/night/orientation images were inspected; the night board uses a dark background. Test packages were cleaned and rotation/previous foreground restored. Initial APK SHA-256: `9a21ef1e52cdb5ec5a72b8cdd5ec780552be2df37b60bdef02f9d5ee536d965b`.

| Theme | Portrait | Landscape |
| --- | --- | --- |
| Nanyang | [View](screenshots/pro-traditional-portrait.png) | [View](screenshots/pro-traditional-landscape.png) |
| Lingnan | [View](screenshots/pro-canton-portrait.png) | [View](screenshots/pro-canton-landscape.png) |
| Classic | [View](screenshots/pro-classic-portrait.png) | [View](screenshots/pro-classic-landscape.png) |
| Shihe | [View](screenshots/pro-peitaiyi-portrait.png) | [View](screenshots/pro-peitaiyi-landscape.png) |

Additional evidence: [Lingnan alignment](screenshots/pro-canton-alignment.png), [Mid-Autumn](screenshots/pro-peitaiyi-moon.png), [Qingming](screenshots/pro-peitaiyi-solar.png), [dark landscape](screenshots/pro-peitaiyi-night.png), [floating today action](screenshots/pro-floating-today.png).

The 2026-10-30 overlay/type-size retest inspected 16 Shihe cover-art day/night/orientation images. Shared `daySize`/`dayNumber` uses a 760-unit band for Nanyang/Lingnan/Classic, 800 for ordinary Shihe and 390 for illustrated Shihe. Traditional acceptance rose to 49 checks, adding single/two-digit ink-height comparisons: 600 px for the first three and 631 px for Shihe. Temporarily changing 1440×2560 to 1080×2400 reduced the same date's ink height from 630 to 472 px (0.75), then resolution was restored. 52 JVM tests and Lint passed; the pro branch was pushed. Final historical APK SHA-256: `84a1820a679e929471c5780db5a96c358a9226c02ff604bfac91b24c9f740c1c`.
