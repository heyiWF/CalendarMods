# 主题预览、每日文句与样式设置验收

验收日期：2026-10-12。设备：`emulator-5554`，Android 15 / API 35，1440 × 2560，字体与系统动画倍率 1.0。

后续已将“好日”的“宜＋事项”改为整体居中并柔和移动，同时调整“一页”的固定标签对齐；最新动画、设备验证和 APK 校验值见[轮播后续验收](almanac-motion-alignment.md)。下文记录首次主题优化的验收结果。

## 本次变化

- 删除选择页单独维护的七套竖屏布局。选择预览按当前窗口尺寸测量 `CalendarPaper`，再整体缩小；全屏复用同一渲染，图片比例、文字、月历和安全区一致。
- 共享边界继续使用 460 ms 连续缩放。纸张取消双层透明度交叉淡变，避免照片在中途发白；外围页码、主题名和操作提示独立淡变。
- 冬日来信使用中文月份；拾光删除英文月份并将空间归还月历。好日使用英文月份加中文月份，节日框仅在当天存在节气/节日时出现。
- 岁时顶部月份、星期、周序同字号，三栏等宽，星期居中；节日框无内容时不显示，宜忌全部保留。月历日期在内容密集时按剩余行高缩放，避免重叠。
- 一页顶部只显示“年份年”和“月份月”。宜忌每页最多两项，以中点分隔，末页单项居中；标签固定。好日逐项轮播。完整停留 2 秒，再用 220 ms 淡出、切换内容、220 ms 淡入。
- 五主题每日格言共用 366 条原创短句，最长 14 字（含标点）；慢笺另有 366 个不重复二字词。按连续日期取模，任意连续 366 天不重复，跨年不重置，与系统日期、时区和前台恢复共用刷新路径。
- 七主题各有独立样式入口，采用指定 Material Style 图标。冬日来信、拾光支持系统照片选择器、等比填充、完整显示、拉伸填充和恢复默认；其他主题暂时显示暂无选项。
- 自选图片后台解码，长边最多 2048 像素，保留比例，保存应用内副本；图片和填充方式按主题独立持久化，不申请整个相册权限。

设置界面采用标准 [Material 3 ModalBottomSheet](https://developer.android.com/develop/ui/compose/components/bottom-sheets)、按钮、分隔线和单选控件；本机图片使用 [Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)，不支持照片选择器的设备由 Activity 合约回退到文档选择器。

## 验证与证据

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon` 成功；13 个 JVM 测试通过，Lint 为 `No issues found.`。
- 常规设备验收 54 项：七主题横竖屏、双向切换、点击全屏、长按/返回退出、进入途中反向返回、旋转重建、慢滑、系统栏、午夜、时区和后台恢复。
- 样式设备验收 12 项：全部入口、独立填充方式保存、系统选择器与取消、应用内图片副本、限制解码尺寸、重建恢复、横屏面板和完整双项轮播。
- 密集内容验收 4 项：2026-01-22 的 29 项宜和全部 42 格月历，以及全年最长 14 字格言，在横竖屏中均检查可见性并保存截图。测试仅注入 App 时钟，不改变设备系统时间。
- 另外手动从系统照片选择器选中专用测试图，确认默认等比填充、强制停止后重启仍保留，随后通过面板恢复默认。测试图片已清理。
- 录制拾光横竖屏进入、长按退出和进入途中反向返回；逐帧确认排版连续、照片不再变淡。录制好日完整淡变过程，确认旧文字先消失、新文字再出现，标签不动。
- 预览纸张裁切后与全屏统一尺寸比较，14 组横竖屏截图平均 RGB 绝对差均低于 0.7%；差异包括缩放栅格化和轮播拍摄时刻。该指标不作为动画帧率基准。

| 内容 | 证据 |
| --- | --- |
| 七主题横屏预览与全屏 | [对照图](screenshots/refinement-landscape-comparison.jpg) |
| 拾光竖屏进出过渡 | [中间帧](screenshots/refinement-transition-portrait.jpg) |
| 拾光横屏进出过渡 | [中间帧](screenshots/refinement-transition-landscape.jpg) |
| 先淡出、后淡入的宜忌轮播 | [中间帧](screenshots/refinement-carousel.jpg) |
| Material 3 图片样式面板 | [竖屏](screenshots/refinement-style-teddy.jpg)、[横屏](screenshots/refinement-style-landscape.jpg) |
| 系统选图后的冷启动 | [查看](screenshots/refinement-picked-cold.jpg) |
| 29 项宜忌 | [竖屏](screenshots/refinement-dense-almanac-0.jpg)、[横屏](screenshots/refinement-dense-almanac-1.jpg) |
| 最长格言横屏 | [查看](screenshots/refinement-longest-motto-1.jpg) |

本轮只使用 Android 15 设备；未额外覆盖 Android 12 实机或带真实摄像头缺口的设备。验收后保留新版主应用，卸载测试包，恢复自动旋转及原主题/显示模式。

## 复现

```powershell
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e dense true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

原始日志、录屏和全分辨率截图保留于本地 `docs/verification/refinements/`；压缩后的关键截图存入本目录。APK 为 `app/build/outputs/apk/debug/app-debug.apk`。

本次 APK SHA-256：`A41F67B79B1222042915FEEA45B0A3BAF29802DF16458E5BB2F76C1436660F95`。
