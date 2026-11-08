[中文](third-party-notices.md) | [English](third-party-notices.en.md)

# Third-party notices and bilingual license index

Original English licenses and attribution remain unchanged in source/APK. Chinese translations are reading aids, not replacement grants. The project uses [MIT](mit.en.md); the root also provides [Chinese text](../../LICENSE.zh-CN).

| Original | Chinese reference |
| --- | --- |
| [Project LICENSE](../../LICENSE) | [MIT](mit.en.md) |
| [tyme4j](../../app/src/main/assets/licenses/tyme4j-LICENSE.txt) | [MIT](mit.en.md), copyright 6tail |

tyme4j (`cn.6tail:tyme4j`) is the offline lunar/almanac engine calculating lunar dates, terms, festivals and suitable/avoid entries. [Project](https://github.com/6tail/tyme4j).

Google Material Symbols style/store icons use [Apache-2.0](apache-2.0.en.md); the [bundled original](../../app/src/main/assets/licenses/material-symbols-Apache-2.0.txt) is retained. Asset notes describe format/package/property changes.

## Deity data

`DeityCalendar.kt` reformats [nihaixia-app's Dart data](https://github.com/jangviktor-web/nihaixia-app/blob/master/lib/data/yuxiaji_deity_data.dart). Original notes identify Yuxiaji, Wikisource export 2026-09-04, compilation 2026-09-05. Changes: Dart map to Kotlin, lookup API added, text retained. [Original notice and Apache license](../../app/src/main/assets/notices/nihaixia-app.txt); [Chinese reference](apache-2.0.en.md).

## Zodiac icons

Wikimedia Chinese-zodiac SVGs become scaled, runtime-tinted Android vectors. Per-file URLs/hashes and originals: [source manifest](../../artwork/almanac/sources.json). Rat/Ox/Tiger/Rabbit/Snake/Horse/Goat are public domain; original histories credit Airridi/Fake User and others in the [category](https://commons.wikimedia.org/wiki/Category:Chinese_zodiac_icons).

Five use CC BY-SA 3.0 selected from the offered licenses: [Dragon_2](https://commons.wikimedia.org/wiki/File:Dragon_2.svg) by Rev; [Monkey_2](https://commons.wikimedia.org/wiki/File:Monkey_2.svg) by Angelus, derived from Monkey.svg; [Rooster](https://commons.wikimedia.org/wiki/File:Rooster.svg), [Dog_2](https://commons.wikimedia.org/wiki/File:Dog_2.svg), [Boar](https://commons.wikimedia.org/wiki/File:Boar.svg) by Angelus. Their converted dragon/monkey/rooster/dog/pig XML assets retain [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/). Format, scale and display color changed; no endorsement is implied.

Western zodiac uses [Tabler v3.46.0](https://github.com/tabler/tabler-icons/tree/v3.46.0/icons/outline), Paweł Kuna, MIT. Originals: [zodiac notice](../../app/src/main/assets/notices/almanac-icons.txt), [Tabler license](../../app/src/main/assets/notices/tabler-icons.txt); [Chinese MIT reference](mit.en.md).

## Shihe artwork

Twelve zodiac masks, 24 term prints and 25 festival prints were fetched from the [public website](https://www.peitaiyi.com/chinese-almanac/) as requested. Original owners retain rights; no open-source grant is asserted. [Resource URLs/hashes](../../app/src/main/assets/peitaiyi/sources.json). The app applies its ink/paper palette, without embedding website scripts, analytics, requests or calculation code. Adaptive native layouts and the shared engine are project code. [Bundled notice](../../app/src/main/assets/notices/peitaiyi-artwork.txt).
