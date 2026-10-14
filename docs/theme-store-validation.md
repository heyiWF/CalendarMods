# 主题商店与午夜动画验收

验收日期：2026-10-14。设备：Android 15，ADB `emulator-5554`，1440 × 2560，横竖屏；调试 APK。使用注入时钟跨日，没有修改设备系统时间。

## 功能与交互

- 七个预设主题一次性迁移为已安装，保留外部主题、顺序、当前选择与样式。删除后重启不重新添加；重新应用排在末尾，恢复默认样式。
- 仓库覆盖收藏批量持久化、失败不发布新状态、至少一个主题、删除图片清理、默认样式重置和排序保存。
- 推荐、收藏、已安装保持三列；主题名称与按钮同行、垂直居中，名称内缩 14dp，整行距预览 10dp。横竖屏实际坐标断言通过。
- “应用”安装后留在当前页，“已安装”打开共用删除确认；已安装页保留“使用”。已移除独立的安装状态文字。
- 收藏页取消收藏后心形变空心、Toast 提示、卡片保留；旋转和后台保持待取消状态，再点可撤销；离页原子提交。模拟文件写入失败时恢复实心收藏并留在原页。
- 真实拖动手势保存排序；阴影仅覆盖圆角预览，标题区域无矩形阴影。上滑不足阈值回弹，超过阈值显示删除确认，不误进全屏；垃圾桶以带滞回的缩放和轻跳反馈阈值。
- 删除正文仅为 `将从已安装主题中移除“主题名称”`。样式按钮横竖屏按预览右边界定位，保持 48dp 点击区域。

## 性能量测

`-e performance true` 创建 7、70、200 个内存测试主题，不写入用户目录。用 Android `FrameMetrics.TOTAL_DURATION` 记录帧时长，每 250ms 采样 PSS；每组先清空缩略图缓存，再预热三个标签，测量切换、拖动及滚动。构建与量测分开执行。以下为调试设备实测，不能等同于所有手机的表现。

| 主题数 | 缓存后切换 P50 / P95 / 最大 | 拖动 P95 / 最大 | 滚动 P95 / 最大 | 测量期间峰值 PSS |
| --- | --- | --- | --- | --- |
| 7 | 5 / 13 / 87ms | 35 / 38ms | 20 / 28ms | 150MiB |
| 70 | 5 / 9 / 55ms | 61 / 101ms | 18 / 124ms | 141MiB |
| 200 | 5 / 19 / 45ms | 13 / 22ms | 17 / 118ms | 142MiB |

三个规模的缓存后切换均未出现超过 100ms 的帧，成本没有随目录数量线性增加。缓存是 32MiB 的 LRU，按需生成并复用，压力测试曾达到约 31.24MiB，始终未超过上限；最终滚动策略下量测为约 9.11–11.71MiB。

**首次生成仍有长帧。** 清空缓存后，7/70/200 组首屏建立加固定 1.8 秒等待分别为 4526/2640/2442ms，最大帧为 781/414/405ms。这是冷缩略图缓存场景，并非三个独立冷进程启动的对比。完整日历首次排版和快照生成仍需主线程参与；已经采用单任务生成、按目标尺寸解码，并在入口展开、滚动和拖动期间暂停生成。70 个主题拖动有一帧 101ms，70/200 滚动各有两帧超过 100ms；不宣称完全消除了长帧。

## 午夜与既有功能

七主题横竖屏跨年均验证完整新旧页面、最终新日期和农历/宜忌；录像检查照片/插画区域与装订稳定、无镜像文字、无残留旧页。日历树分别保存新旧数据及轮播状态。日期、时区调整和后台恢复直接刷新；自然前台午夜才播放。

自动采样通过渲染器观察器捕捉短动画，再检查可访问性树和截图。旋转会重建生产内容，因此以新页面状态判断结束，不能继续读已销毁页面的测试观察器。系统关闭动画时显式跳过午夜过渡；自定义过渡统一读取系统动画缩放。

最终 APK 与测试 APK 构建通过，26 项 JVM 测试通过；Lint 为 0 错误，2 条依赖版本更新提示。设备检查通过：商店 5 组、性能 12 组、午夜及中断 18 组、既有全屏/日期回归 54 组、主题契约 4 组、样式与轮播 12 组。午夜最终复测包含跨年、闰日、动画中返回及旋转、后台恢复和系统关闭动画。

验收后恢复自动旋转、原旋转方向、字体与动画缩放；时区为 Asia/Shanghai。已移除测试 APK，启动正式应用内容，保留用户图片及设置。

## 复现入口与证据

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -e store true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e performance true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e midnight true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e interruptions true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e contract true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

本地完整日志为 `theme-store-final-build.log`、`theme-store-device-final.log`、`theme-store-performance-final.log`、`theme-midnight-final.log`、`theme-store-regression-final.log`、`theme-store-contract-final.log`、`theme-store-styles-final.log`。原始 PNG、录屏及逐帧联系表保存在忽略目录 `docs/verification/theme-store/`；产品截图为 [`theme-store-portrait.jpg`](screenshots/theme-store-portrait.jpg) 和 [`theme-store-landscape.jpg`](screenshots/theme-store-landscape.jpg)。
