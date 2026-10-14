# 素材来源

以下两张原创素材使用 Codex 内置 imagegen 工具生成，作为参考图的替代照片/插画，未把实物台历照片直接用作界面。其余纸张、装订环、吊绳、小猫及排版均为 Compose 绘制。

## winter_bear.png

位置：`app/src/main/res/drawable-nodpi/winter_bear.png`

提示词：

Use case: illustration-story. Asset type: image-only header illustration for an Android calendar app. Create a charming tactile miniature winter scene, a friendly caramel teddy bear wearing a small red Santa hat sitting in an ivory ceramic hot chocolate cup, tiny gingerbread house at right, snowy pine branches and simple red ornaments. Soft handmade felt and clay textures, powder blue backdrop, cream and warm brown, nostalgic cozy desktop calendar print. Landscape 3:2 composition, bear centered and fully visible. Full bleed illustration only; absolutely no text, no numbers, no calendar, no spiral, no border, no watermark. Save the generated image asset for use in the app.

## sunset_memory.png

位置：`app/src/main/res/drawable-nodpi/sunset_memory.png`

提示词：

Use case: photorealistic-natural. Asset type: full bleed photograph used in a photo desk calendar app. Candid close portrait of two adult friends, a man in a dark knit beanie and a woman with brown shoulder-length hair, smiling naturally together on a seaside walk at sunset. Golden rim light, muted warm analog film palette, tactile grain, ocean and rocky coastline softly out of focus. Spontaneous memory, relaxed expressions, realistic faces and hands. Square 1:1 framing, subjects composed centrally for crop to 4:3 and portrait, faces in upper central half. Photograph only, no calendar, no typography, no frame, no watermark, no brands.

## 样式图标与每日文句

样式入口使用用户指定的 [Material Symbols Outlined / style](https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/style.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50)，Google，Apache License 2.0；源文件为 `StyleIcon.kt`，仅修改包名及 Kotlin 属性名。许可随应用保存在 `assets/licenses/material-symbols-Apache-2.0.txt`。

每日格言和二字心语各 366 条，由本项目原创整理。格言最长 14 字（含标点），不标注名人作者；它们属于日常文案，不属于历法宜忌。按连续公历日取模轮换，不在元旦重置。

## 主题商店图标

添加、返回、收藏、删除和排序手柄来自 Google Material Symbols Outlined（24dp，400 weight，FILL 0），Apache-2.0。对应文件为 `AddIcon.kt`、`BackIcon.kt`、`FavoriteIcon.kt`、`DeleteIcon.kt`、`DragIcon.kt`；仅调整包名和属性可见性/名称。原始下载 URL 使用 `https://fonts.gstatic.com/render/v1/Material+Symbols+Outlined/24dp/{name}.kt?var=opsz,wght,FILL,GRAD,ROND@24,400,0,0,50`，name 分别为 `add`、`arrow_back`、`favorite`、`delete`、`drag_indicator`。许可与样式图标共用。

收藏实心图标 `FavoriteFilledIcon.kt` 使用上述 Material Symbols favorite 的同一外轮廓，移除镂空内轮廓。
