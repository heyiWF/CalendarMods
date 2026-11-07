# Android 4.0 兼容分支

`compat` 分支从 `pro` 派生，把最低系统要求从 Android 12 / API 31 降到 Android 4.0 / API 14。
界面层由 Jetpack Compose 改为系统 View 与 Canvas，历法计算与存储保持不变。

## 为什么不是只改一行 minSdk

Compose 的运行时最低要求是 API 21，Material3 1.5 与 activity-compose 1.13 实际要求 API 24。
API 14 上即使强制覆盖清单合并也会在启动时崩溃，因此界面必须换实现：

| 原实现 | 兼容实现 |
| --- | --- |
| Compose 布局 + Material3 组件 | `ChooserView`、`PaperView`、`StyleSheetDialog` 系统控件 |
| Compose `Canvas` 主题绘制 | `Plate` + `DailyPage`，沿用同一套 360×600 / 640×360 设计坐标 |
| `androidx.compose.ui.text` | `Paint` 度量与手工换行 |
| Compose Pager / 动画 | `Scroller` 分页、`Camera` 翻页 |
| `java.time` | `PlainDate`（无时区日期值类型），时区只出现在 `CalendarClock` |
| `ImageDecoder` | `BitmapFactory` + `ExifInterface` 旋转校正 |
| `AtomicFile` | 临时文件 + `fsync` + 重命名 |
| Kotlin 协程 + Flow | 后台加载 + 缓存 + `Handler` |
| Material 波浪进度 | 省略进度指示，直接切换状态 |

## 行为保持

- 日期行、时钟、宜忌与六行七列月历的版式与配色全部由 `DailyPage` 绘制，见 `daily-page.md`。
- 全屏长按退出、跨日翻页、交互式翻页与“回到今天”均可用。
- 全屏时窗口在系统栏之后排版（`LAYOUT_HIDE_NAVIGATION` + `LAYOUT_FULLSCREEN`，API 16 起）：导航栏自动隐藏后，它腾出的位置由日历内容直接补满，页面高度等于物理屏幕高度，Android 5.0 上同样成立。
- 本分支新增可选的摄像头权限，用于全屏人脸感应亮屏；画面只在本机内存中参与判断，应用离线运行。详见 `face-brightness.md`。

## 版式取舍

- 跨日动画统一为一次向前翻页。
- 只使用系统默认的无衬线字族（`sans-serif`）的常规与粗体两个字重。
- 启动图标在 API 26 以下使用位图，26 以上走自适应图标。

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI（`.github/workflows/android.yml`）在推送后构建 `CalendarMods-debug.apk`，产物在 Actions 的 Artifacts 中。
