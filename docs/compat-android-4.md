# Android 4.0 兼容分支

`compat` 分支从 `pro` 派生，把最低系统要求从 Android 12 / API 31 降到 Android 4.0 / API 14。
界面层由 Jetpack Compose 改为系统 View 与 Canvas，历法、主题契约、存储与文案保持不变。

## 为什么不是只改一行 minSdk

Compose 的运行时最低要求是 API 21，Material3 1.5 与 activity-compose 1.13 实际要求 API 24。
API 14 上即使强制覆盖清单合并也会在启动时崩溃，因此界面必须换实现：

| 原实现 | 兼容实现 |
| --- | --- |
| Compose 布局 + Material3 组件 | `ChooserView`、`PaperView`、`StyleSheetDialog` 系统控件 |
| Compose `Canvas` 主题绘制 | `Plate` + `PageArtwork`，沿用同一套 360×600 / 640×360 设计坐标 |
| `androidx.compose.ui.text` | `Paint` 度量与手工换行 |
| Compose Pager / 动画 | `Scroller` 分页、`Camera` 翻页 |
| `java.time` | `PlainDate`（无时区日期值类型），时区只出现在 `CalendarClock` |
| `ImageDecoder` | `BitmapFactory` + `ExifInterface` 旋转校正 |
| `AtomicFile` | 临时文件 + `fsync` + 重命名 |
| Kotlin 协程 + Flow | 同步仓库 + 监听器 + `Handler` |
| Material 波浪进度 | 省略进度指示，直接切换状态 |

## 行为保持

- 11 款主题的版式、配色、字体层级、月历网格、传统黄历分栏全部保留，四款传统主题仍复用原有绘制代码。
- 主题选择、全屏长按退出、跨日翻页、定时深色、光线自适应、图片替换与填充方式、随包字体均可用。
- 内置主题固定安装、不可删除或排序；`compat` 分支移除了主题商店。
- 应用仍不申请任何权限，不联网。

## 已知差异

- 跨日动画统一为一次向前翻页，不再按主题区分旋转、升降或揭页的缓动曲线。
- 商店、应用主题进度、删除回弹与共享元素过渡已省略。
- 收藏页取消收藏立即生效，不再保留“离开前可撤销”的会话式缓冲。
- 字体只使用系统可解析的 TTF/OTF；API 14 没有可变字重，`Light`/`ExtraLight` 退化为常规字重。
- 启动图标在 API 26 以下使用位图，26 以上仍走自适应图标。

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

CI（`.github/workflows/android.yml`）在推送后构建 `CalendarMods-debug.apk`，产物在 Actions 的 Artifacts 中。
