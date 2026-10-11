# CalendarMods 0.1.0 设备验收

验收日期：2026-10-11。独立 Jetpack Compose 工程，包名 `com.calendarmods`，最低 Android 12 / API 31。

## 构建与设备

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon` 构建成功。
- 10 个单元测试通过，0 失败：2000–2100 年月份格子及闰日、农历新年、跨年周序、节气首日、同日多节日、入九、真实宜忌、午夜和夏令时日长。
- Lint：`No issues found.`
- ADB 设备：`emulator-5554`，Android 15 / API 35，1440 × 2560，density 360，字体缩放 1.0。本轮没有另行使用 Android 12 设备。
- 设备手势与日期验收最终结果：`PASS 52 checks`。

## 沉浸显示

- 七种主题分别完成竖屏和横屏验收：左右滑动选择、单击进入全屏、长按返回同一主题；保留短滑回弹和反向滑动行为。
- 沉浸页占满窗口，选择页标题、页码和提示隐藏；检查系统栏隐藏及屏幕常亮标志。返回选择页恢复系统栏和正常熄屏策略。
- 设备采用硬件导航，没有软件导航栏 InsetsSource；状态栏隐藏经过设备验证，退出时导航状态与设备原值一致。软件导航栏的实际显示效果尚未单独验证。
- 七主题适配横竖屏：照片与月历、日期与说明在横屏重新排列。修正慢笺横屏农历截断、岁时竖屏月历最后一行空间不足。
- 沉浸模式在左横屏、右横屏和竖屏重建后保留；普通单击和左右滑动不会退出或换主题。
- 从桌面回到 App 后继续隐藏系统栏；系统返回操作回到选择页。
- 另行通过冷启动验证：进入第七个悬挂日历主题，强制停止并重新打开后，仍为沉浸模式，显示实际日期 2026-10-11。
- 验收后恢复时区 `Asia/Shanghai`、`accelerometer_rotation=1`、`user_rotation=0`；字体缩放保持 1.0、自动时间保持开启。测试包已卸载，设备上保留新版 App，并停在第七主题的沉浸显示。

## 动态日期

- 沉浸模式前台从 2026-12-31 午夜自动进入 2027-01-01，日期、月份和年份更新；不重启 App，返回选择页后七个主题均使用新日期。
- 同一测试时刻切换系统时区 Pacific/Kiritimati → Pacific/Honolulu，日期从 2027-01-01 更新为 2026-12-31。
- 进入后台后改变注入的测试时钟，恢复前台时显示 2028-02-29，并更新农历、节日和宜忌。
- 恢复生产时钟后显示设备实际日期。测试只注入界面时钟，没有修改设备系统时间。

## 设备截图

截图日期为 2026-10-11，App 实际日期随设备时间变化。素材为生成的替代照片和插画，详见 [素材说明](assets.md)；普通文句属于主题设计，宜忌来自历法计算。

| 主题 | 选择页竖屏 | 选择页横屏 | 沉浸竖屏 | 沉浸横屏 |
| --- | --- | --- | --- | --- |
| 晴日 · 黄页台历 | [查看](screenshots/portrait-1-golden.png) | [查看](screenshots/landscape-1-golden.png) | [查看](screenshots/immersive-portrait-1-golden.png) | [查看](screenshots/immersive-landscape-1-golden.png) |
| 冬日来信 · 插画月历 | [查看](screenshots/portrait-2-teddy.png) | [查看](screenshots/landscape-2-teddy.png) | [查看](screenshots/immersive-portrait-2-teddy.png) | [查看](screenshots/immersive-landscape-2-teddy.png) |
| 拾光 · 照片台历 | [查看](screenshots/portrait-3-photo.png) | [查看](screenshots/landscape-3-photo.png) | [查看](screenshots/immersive-portrait-3-photo.png) | [查看](screenshots/immersive-landscape-3-photo.png) |
| 好日 · 字体日历 | [查看](screenshots/portrait-4-good_day.png) | [查看](screenshots/landscape-4-good_day.png) | [查看](screenshots/immersive-portrait-4-good_day.png) | [查看](screenshots/immersive-landscape-4-good_day.png) |
| 慢笺 · 竖排文句 | [查看](screenshots/portrait-5-verse.png) | [查看](screenshots/landscape-5-verse.png) | [查看](screenshots/immersive-portrait-5-verse.png) | [查看](screenshots/immersive-landscape-5-verse.png) |
| 岁时 · 复古黄历 | [查看](screenshots/portrait-6-almanac.png) | [查看](screenshots/landscape-6-almanac.png) | [查看](screenshots/immersive-portrait-6-almanac.png) | [查看](screenshots/immersive-landscape-6-almanac.png) |
| 一页 · 悬挂日历 | [查看](screenshots/portrait-7-hanging.png) | [查看](screenshots/landscape-7-hanging.png) | [查看](screenshots/immersive-portrait-7-hanging.png) | [查看](screenshots/immersive-landscape-7-hanging.png) |

## 复现

构建和设备验收命令见 [README](../README.md#验证)。测试包 `CalendarAcceptanceInstrumentation` 输出逐项结果；CI 执行 JVM 单元测试和 Lint，设备交互测试通过 ADB 单独运行。

后台测试会等待桌面完成切换，再通过系统命令重新打开 App，以避免桌面尚未完成恢复就立即再次启动造成的设备过渡超时。

APK：`app/build/outputs/apk/debug/app-debug.apk`。CI 使用独立 Debug 签名，各次构建的校验值随 APK 存放在 `SHA256SUMS.txt` 中。

本地验收 APK SHA-256：`09AFFF5460DB25A8981AA4CD5ACD64FE4982A23D47D781AA67DA6BE62B9E71A8`。
