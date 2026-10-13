# 好日柔和位移与一页固定对齐

2026-10-13 整理。验证设备为 Android 15 / API 35 模拟器 `emulator-5554`，1440 × 2560，字体和系统动画倍率均为 1.0。

## 行为

- **好日**：保留每项完整停留 2 秒。长度变化时，标签位移从 260 ms 延长为 640 ms，采用对称的 `CubicBezierEasing(.42, 0, .58, 1)`，缓慢启动和停止。位移与文字的 280 ms 淡出、60 ms 留白、300 ms 淡入同时推进；文字完全透明时才换项，没有新旧事项交叉显示。位移目标与当前文字分别保存，避免换项时标签突然跳位。等长事项继续使用原来的顺序淡变。
- **一页**：用“宜＋间距＋两个两字事项及中点”的实际排版宽度计算居中起点，宜、忌标签固定且上下对齐。所有事项（包括末页单项）都从固定事项起点左对齐，长事项向右延伸；宜/忌与轮播字段左端的间距始终固定。轮播仍为每页停留 2 秒、220 ms 淡出、220 ms 淡入。
- 横竖屏、选择页预览与全屏共用渲染和状态。

## 验证

末页单项改为左对齐后，重新构建主应用及测试包成功，横竖屏共 10 项固定间距检查通过；新日志和截图在本地 `docs/verification/fixed-almanac-spacing/`。下列好日动画与单元测试结果来自调整单项对齐之前的同轮验证。

- 本轮初次 `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon` 成功，13 个 JVM 测试通过，Lint 无问题。
- 好日横竖屏各 6 项设备检查通过：两字、三字、四字及返回两字的整组中心偏差不超过 2 像素，回位一致，移动方向连续，移动和淡变确有重叠。
- 一页横竖屏各 5 项设备检查通过：两个两字事项的整组居中、长事项保持左起点并向右延伸、长短单项均左对齐、返回首项，以及宜忌标签在整个周期内固定且上下对齐。
- 好日录屏逐帧核对竖屏 191 帧、横屏 178 帧，检查了往返位置及淡变衔接，未见标签消失或反向跳位。录屏为可变帧率，且同时运行设备断言；此检查不作为稳定帧率的性能结论。
- 一页的完整轮播也已录制，稳定显示时的对比见下图。验收后保留新版主应用，卸载测试包，恢复原主题“一页”、全屏状态和自动旋转。

| 内容 | 证据 |
| --- | --- |
| 好日位移与淡变重叠 | [竖屏连续帧](screenshots/gentle-almanac-motion-portrait.jpg)、[横屏连续帧](screenshots/gentle-almanac-motion-landscape.jpg) |
| 一页固定标签及长短事项 | [横竖屏对照](screenshots/anchored-almanac-comparison.jpg) |

复现设备检查，`0` 为竖屏，`1` 为横屏：

```powershell
adb shell am instrument -w -e centered 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e centered 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e anchored 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e anchored 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

原始录屏、采样与日志在本地 `docs/verification/almanac-motion-alignment/`。仅覆盖上述 Android 15 模拟器。

APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256：`890BFF432A296059CF89EC39C21C8E866E057E2F370A14C8644979CEDA6D571A`。
