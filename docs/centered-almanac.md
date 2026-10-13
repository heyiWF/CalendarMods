# 好日宜项居中与连续位移动画

后续已将位移改为更舒缓的 640 ms 并与淡变重叠，同时调整“一页”的固定对齐；最新行为、验证和 APK 见[后续验收](almanac-motion-alignment.md)。本文保留首次居中优化的记录。

2026-10-12，Android 15 / API 35 模拟器 `emulator-5554`，1440 × 2560，字体与动画倍率 1.0。

“宜＋间距＋事项”按实际文字宽度整体居中。两字事项为默认位置；三字、四字事项使“宜”相应左移，回到两字时返回原位。横屏居中于右侧内容区。选择页与全屏使用相同渲染和轮播状态。

每项完整显示 2 秒，接着 220 ms 淡出；若长度变化，“宜”保持不透明，用 260 ms 缓入缓出移动到新位置，再用 220 ms 淡入新事项。等长事项之间无需位移。“一页”的固定宜忌标签及双项轮播保持原有行为。

## 验证

- `testDebugUnitTest assembleDebug lintDebug --offline --no-daemon` 成功：13 个 JVM 测试通过，Lint 无问题。`assembleDebugAndroidTest` 成功。
- 新增设备测试注入“嫁娶→入人口→祭祀→修饰垣墙→嫁娶”，横竖屏各 6 项检查通过：每次稳定显示的整组中心误差不超过 2 像素；长词对应更大的左移距离；两次回位一致；位移阶段事项透明；移动方向连续。
- 录屏逐帧检查竖屏 203 帧、横屏 192 帧，每次位移均捕获 12–14 个位置变化帧。四段往返没有反向跳位，标签持续可见，先淡出后淡入，没有双项重叠。录屏为可变帧率，该数量不代表性能帧率测试。
- 验收后卸载测试包，保留新版主应用，恢复原主题“一页”、全屏模式及自动旋转。

| 证据 | 竖屏 | 横屏 |
| --- | --- | --- |
| 两字、三字、四字与回位 | [查看](screenshots/centered-almanac-portrait.jpg) | [查看](screenshots/centered-almanac-landscape.jpg) |
| 先淡出、平移、再淡入的连续帧 | [查看](screenshots/centered-almanac-motion-portrait.jpg) | [查看](screenshots/centered-almanac-motion-landscape.jpg) |

复现：

```powershell
adb shell am instrument -w -e centered 0 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e centered 1 com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

原始录屏、位置采样和日志保留于本地 `docs/verification/centered-almanac/`。本轮仅覆盖上述 Android 15 模拟器。

APK：`app/build/outputs/apk/debug/app-debug.apk`，SHA-256：`9F734DB1DE67F572A771B3924D893A90EA321904A408A9E141B76884CD042D5B`。
