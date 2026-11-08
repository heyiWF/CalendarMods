[中文](third-party-notices.md) | [English](third-party-notices.en.md)

# 第三方声明与双语许可索引

原始英文许可和署名随代码／APK 保留不变；中文译文供阅读，不改变授权。项目本身使用 [MIT](mit.md)，根目录另有 [中文许可](../../LICENSE.zh-CN)。

| 原文 | 中文参考 |
| --- | --- |
| [项目 LICENSE](../../LICENSE) | [MIT](mit.md) |
| [tyme4j](../../app/src/main/assets/licenses/tyme4j-LICENSE.txt) | [MIT](mit.md)，版权所有 6tail |

tyme4j（cn.6tail:tyme4j）为离线农历／黄历引擎，计算农历、节气、节日与宜忌；[项目地址](https://github.com/6tail/tyme4j)。

Material Symbols 样式与商店图标来自 Google，按 [Apache-2.0 中文参考](apache-2.0.md) 许可；[随包原文](../../app/src/main/assets/licenses/material-symbols-Apache-2.0.txt) 保留。转换／包名／属性名改动见素材文档。

## 神诞资料

`DeityCalendar.kt` 从 [nihaixia-app 的 yuxiaji_deity_data.dart](https://github.com/jangviktor-web/nihaixia-app/blob/master/lib/data/yuxiaji_deity_data.dart) 转换。原记录注明《玉匣记》、2026-09-04 维基文库导出、2026-09-05 整理。改动为 Dart Map 转 Kotlin、添加查询 API，保留文字。[原始声明与 Apache-2.0 全文](../../app/src/main/assets/notices/nihaixia-app.txt)，[中文许可参考](apache-2.0.md)。

## 生肖与星座图标

Wikimedia Commons 生肖 SVG 转换为 Android VectorDrawable，缩放并按运行时配色；原 SVG、逐文件 URL／哈希见 [来源表](../../artwork/almanac/sources.json)。Rat、Ox、Tiger、Rabbit、Snake、Horse、Goat 为公共领域；来源历史注明 Airridi／Fake User 等贡献者，见 [分类](https://commons.wikimedia.org/wiki/Category:Chinese_zodiac_icons)。

五张素材从上游可选授权中选用 CC BY-SA 3.0：[Dragon_2.svg](https://commons.wikimedia.org/wiki/File:Dragon_2.svg) 作者 Rev；[Monkey_2.svg](https://commons.wikimedia.org/wiki/File:Monkey_2.svg) 作者 Angelus、源自 Monkey.svg；[Rooster.svg](https://commons.wikimedia.org/wiki/File:Rooster.svg)、[Dog_2.svg](https://commons.wikimedia.org/wiki/File:Dog_2.svg)、[Boar.svg](https://commons.wikimedia.org/wiki/File:Boar.svg) 作者 Angelus。对应转换后的 dragon／monkey／rooster／dog／pig XML 仍遵守 [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/)，不暗示作者认可。

星座采用 [Tabler Icons v3.46.0](https://github.com/tabler/tabler-icons/tree/v3.46.0/icons/outline)，作者 Paweł Kuna，MIT。原文：[生肖声明](../../app/src/main/assets/notices/almanac-icons.txt)、[Tabler MIT](../../app/src/main/assets/notices/tabler-icons.txt)；[MIT 中文参考](mit.md)。

## 时和插画

12 张生肖蒙版、24 张节气和 25 张节日图片按用户要求取自 [公开网站](https://www.peitaiyi.com/chinese-almanac/)。权利仍归原作者，未宣称开放源码许可；逐资源 URL／哈希保存在 [来源表](../../app/src/main/assets/peitaiyi/sources.json)。应用仅按当前墨色／纸色绘制，不包含网站脚本、统计、联网请求或网站历法代码；原生布局与共享引擎为本项目代码。[随包英文声明](../../app/src/main/assets/notices/peitaiyi-artwork.txt)。
