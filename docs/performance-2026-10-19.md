# 图标与性能验证 · 2026-10-19

## 变更

- 应用图标延续 ClockMods 的深蓝金属与珊瑚橙配色，使用日历主体、自适应遮罩和独立单色层。设计源图与 Android 实际渲染预览位于 `docs/media/`，生成提示词见 [素材说明](assets.md)。
- 内置图片、主题包图片和用户图片统一在 IO 线程解码。预览、全屏与样式面板共用 32 MiB 有界缓存；并发请求串行去重，按窗口长边分档取样，上限 2048px，旋转复用同一尺寸。丢失或损坏的自选图片依次回退到主题包图片、内置图片。
- 最近 4 天的日历数据与最近 2 个月的 42 格月历复用，日期变化仍更新当天宜忌、节气和节日；线程同步防止并发重复计算。
- 主题索引迁移、图片清理与附带字体校验从首次界面组合移至 IO 线程。主题安装列表和图片路径映射仅在对应库存变化时重建。
- 开始工作前已有的 Snackbar 改动按用户要求独立提交，与图标、性能优化分开。

图片加载调整参考 Android 官方对 [Compose 主线程图片解码开销的说明](https://developer.android.com/codelabs/jetpack-compose-performance)。没有更换历法库、主题素材或界面动画。

## 同设备对比

Android 15，ADB `emulator-5554`（设备报告型号 PHY110），1440 × 2560，Debug APK。同一测试包、同一套内置主题顺序，先旧 APK 后新 APK。测试断言使用自己的日期缓存，避免断言反复计算历法而干扰采样。

| 项目 | 优化前 | 优化后 |
| --- | ---: | ---: |
| 同一天数据连续读取 30 次，中位耗时 | 242.628 ms | 0.001043 ms |
| 同一天数据读取 P95 | 311.094 ms | 0.001629 ms |
| 图片主题切换／展开／收起，帧耗时 P50 | 18 ms | 13 ms |
| 同一操作帧耗时 P95 | 78 ms | 85 ms |
| 最长帧 | 569 ms | 677 ms |
| 超过 100 ms 的帧数／总样本 | 36 / 825 | 35 / 816 |
| 操作结束时进程 PSS | 176 MiB | 172 MiB |

日历数据指标衡量缓存命中，不代表首次计算或整个应用启动耗时。帧数据是单轮 Debug 采样，包含自动输入、布局和系统调度影响，长帧没有稳定改善，不能据此声称所有场景帧率提高。优化前后的“冬日来信”全屏截图 SHA-256 完全一致，图片与排版像素保持一致。

## 检查

- 31 项 JVM 测试通过，覆盖日期缓存复用、闰日／跨月、缓存淘汰后结果一致、并发读取及已有历法／主题规则。
- 图片与图标设备回归 `PASS 4 checks`：8 个并发请求共用位图；旋转尺寸复用；缺失／损坏图片回退；大图按比例降采样与安全淘汰；系统实际渲染自适应图标及单色图标。
- 性能场景 `PASS 2 checks`：30 次日期读取，以及三轮图片主题滑动／全屏／返回。
- 完整设备回归 `PASS 54 checks`：七主题横竖屏、双向切换、进入／退出全屏、过渡中返回、旋转、跨年午夜、时区变化、后台恢复与屏幕常亮。
- 图片样式回归 `PASS 12 checks`：自选图导入、独立填充设置、系统照片选择器及取消、旋转／重建后图片保留、样式面板与宜忌轮播。
- 商店回归 `PASS 6 checks`：收藏延迟保存／撤销、写入失败回退、横竖屏三列预览、拖动排序、删除／重装、应用与重新进入。测试增加明确的标签页就绪等待，避免在旧页面退出动画期间读取旧卡片位置。
- 完整无缓存构建成功，Lint 无问题。自适应图标保留显式 `v26` 目录，当前工具链将其移到无版本 `anydpi` 后会丢失链接资源；仅针对这两个图标及不透明背景设置相应的 Lint 忽略。

命令（Windows 使用 Android Studio JBR 和项目内 `ANDROID_USER_HOME`）：

```powershell
.\gradlew.bat --no-daemon --offline --no-configuration-cache --no-build-cache clean testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb shell am instrument -w -e corePerformance true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e resources true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e store true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

原始日志与设备数据备份存放在本地忽略目录 `docs/verification/`。

验证结束后已恢复并逐字节核对 7 个原应用私有文件（包括主题安装顺序、自选图片与设置），没有遗留额外私有文件。测试包已卸载，设备保留新版本应用并以原配置正常打开。
