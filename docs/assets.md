[中文](assets.md) | [English](assets.en.md)

# 素材来源

界面全部由 Canvas 绘制，文字使用系统默认的无衬线字族（`sans-serif`），按系统支持取用 Black／ExtraBold，并回退至粗体／常规，不打包任何字体文件；随包只有启动图标与本页列出的第三方许可。

## 应用图标

使用 Codex 内置 imagegen 工具生成，采用深蓝金属质感、珊瑚橙凸起色块和左上方光源，主体为双装订环与六个日期格的日历。源图保存在 `docs/media/app-icon-source.png`，512px 发布图为 `docs/media/app-icon-playstore.png`，应用资源为 `drawable-nodpi/ic_launcher_artwork.png`。单色主题图标为项目手绘矢量。

生成提示词：

Use case: logo-brand. Generate a new production Android launcher icon for CalendarMods, Square 1024x1024 full-bleed icon artwork. Match the exact deep midnight navy background, dark blue-gray beveled metallic/glass material, subtle rim highlights, soft dimensional shadows, and vibrant coral-orange raised accent of the reference. Subject: a large front-facing gently rounded square calendar page, with two short metallic binding tabs at the top, a raised coral-orange header occupying the top quarter, and a clean restrained 3-by-2 arrangement of rounded square date cells on the dark slate page; one date cell coral-orange to reinforce the focus. No numerals, no text, no letters, no clock hands, no extra symbols. Maintain a bold simple recognizable silhouette at launcher sizes. Center all identifying calendar shape inside the central 64% of the canvas width and height with plenty of continuous dark background beyond it so Android adaptive circle/squircle masking never clips the calendar. Lighting from upper left, polished premium restrained 3D rendering, crisp geometry and carefully balanced proportions. No rounded outer app tile or border: dark background goes to all four square edges. Do not add busy decoration or photorealistic environment. Deliver only the icon.

Android 图标使用自适应遮罩与独立单色层；布局参考 [Android adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)。

## 历法库

农历、节气、节日与宜忌由 `tyme4j` 计算，许可文本随应用保存在 `assets/licenses/tyme4j-LICENSE.txt`。

## 版式与文案

页面版式、对齐规则与轮播时间轴见 [页面版式与数据来源](daily-page.md)。「宜」「忌」与农历由历法计算得出，不随包任何文本资源，属于民俗历法内容。
