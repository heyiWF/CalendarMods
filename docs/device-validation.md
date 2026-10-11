# CalendarMods 0.1.0 设备验收

验收日期：2026-10-11。独立 Jetpack Compose 工程，包名为 `com.calendarmods`。

## 结果

- 最终构建：`testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --offline --no-daemon` 成功。
- 10 个单元测试通过，0 失败：2000–2100 年全部月份的 42 格对齐及闰日、3 个农历新年、ISO 跨年周序、节气首日、同日多节日、入九首日、真实宜忌及午夜/夏令时日长。
- Lint：`No issues found.`
- ADB：`emulator-5554`，Android 15 / API 35，1440 × 2560，density 360。
- 34 项设备验收通过：七主题横竖屏显示、真实日期和宜忌、双向滑动、短滑回弹、非默认主题旋转重建恢复，以及下面的动态日期场景。
- 前台跨年：测试界面从 2026-12-31 午夜自动进入 2027-01-01，日期、月份、年份更新；不重启 App，七主题使用新日期。
- 时区变化：在同一测试时刻，切换系统时区 Pacific/Kiritimati → Pacific/Honolulu，界面立即从 2027-01-01 更新为 2026-12-31。
- 后台恢复：测试界面回到前台后读取 2028-02-29，并更新相应农历和宜忌。
- 恢复正常系统时钟来源后，界面重新显示设备实际日期 2026-10-11。测试只注入界面时钟，没有修改设备系统时间。
- 另行验证：强制停止并冷启动，仍为原先的第七个悬挂日历主题，日期为 2026-10-11，星期日，农历九月初二。
- 已检查七个主题竖屏截图及横屏布局，修正插画月历农历文字裁切。
- 验收后恢复时区 `Asia/Shanghai`、`accelerometer_rotation=1`、`user_rotation=0` 和原主题，字体缩放保留 `1.0`；测试包已移除，主 app 保留并打开。
- 未改动 ClockMods 源码或原有工作区文件。

已在 APK 清单中确认 `minSdkVersion=31`（Android 12）；本次实际运行设备为 Android 15，未另行在 Android 12 设备上测试。第一版以竖屏纸张比例为主，横屏等比缩放。

APK：`app/build/outputs/apk/debug/app-debug.apk`

本地验收 APK SHA-256：`9CE432786E239CEC77FB75819BA5F735355728DE5BB2EB9A4374EACAD4FEECD4`

CI 使用独立构建环境和 Debug 签名，生成的 APK 校验值见各次构建产物中的 `SHA256SUMS.txt`。

## 实机截图

| 主题 | 竖屏 | 横屏 |
| --- | --- | --- |
| 晴日 · 黄页台历 | [查看](screenshots/portrait-1-golden.png) | [查看](screenshots/landscape-1-golden.png) |
| 冬日来信 · 插画月历 | [查看](screenshots/portrait-2-teddy.png) | [查看](screenshots/landscape-2-teddy.png) |
| 拾光 · 照片台历 | [查看](screenshots/portrait-3-photo.png) | [查看](screenshots/landscape-3-photo.png) |
| 好日 · 字体日历 | [查看](screenshots/portrait-4-good_day.png) | [查看](screenshots/landscape-4-good_day.png) |
| 慢笺 · 竖排文句 | [查看](screenshots/portrait-5-verse.png) | [查看](screenshots/landscape-5-verse.png) |
| 岁时 · 复古黄历 | [查看](screenshots/portrait-6-almanac.png) | [查看](screenshots/landscape-6-almanac.png) |
| 一页 · 悬挂日历 | [查看](screenshots/portrait-7-hanging.png) | [查看](screenshots/landscape-7-hanging.png) |

## 复现

构建和设备验收命令见 [README](../README.md#验证)。测试包 `CalendarAcceptanceInstrumentation` 输出逐项结果，最终为 `PASS 34 checks`。机器路径和原始 UI 转储保存在本地忽略目录 `docs/verification/`，不随源码发布。

素材是内置 imagegen 生成的替代照片和插画，生成方式、资源路径和完整提示词见 [素材说明](assets.md)。固定文句属于主题设计；实际日期、农历、节气、复古黄历宜忌来自本地日期计算。
