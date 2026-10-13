# CalendarMods

[![Build APK](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml/badge.svg)](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml)

一个使用 **Jetpack Compose** 编写的日历 App，支持 **Android 12 / API 31 及以上**。七种纸质日历主题，左右横滑切换，并记住上次使用的主题。包名 `com.calendarmods`，可与 ClockMods 同时安装。

专注于日历展示，支持各主题独立的样式面板。所有日期数据与每日文句均离线提供，无需联网权限。

## 使用方式

1. 在主题选择页左右滑动，查看七种主题。
2. **单击日历**进入该主题的沉浸显示：纸张铺满屏幕，隐藏状态栏、导航栏和选择页控件。
3. **长按界面任意位置**返回主题选择页，停留在当前主题；再左右滑动并单击即可换主题。系统返回键也可返回选择页。
4. 主题选择页右下角的样式按钮可打开当前主题设置；冬日来信和拾光支持替换本机图片。
5. 沉浸模式下，普通点击和滑动不会切换主题。横竖屏会重新排版，主题和显示模式在旋转、后台恢复及重新启动后保留。

沉浸显示时保持屏幕常亮，返回选择页后恢复正常熄屏策略。内容会避开摄像头缺口；系统边缘手势仍可临时呼出系统栏。

进入时，日历从当前预览位置连续放大到全屏；退出时沿原路径缩回。过渡约 460 毫秒，标题和提示随之淡入淡出，横屏与竖屏均支持。进入途中按返回键，可以从当前位置反向收回。

## 七种主题

| 晴日 · 黄页台历 | 冬日来信 · 插画月历 | 拾光 · 照片台历 |
| --- | --- | --- |
| <img src="docs/screenshots/refinement-portrait-1-golden.jpg" width="220" alt="黄色星期条与大日期"> | <img src="docs/screenshots/refinement-portrait-2-teddy.jpg" width="220" alt="小熊插画与月历"> | <img src="docs/screenshots/refinement-portrait-3-photo.jpg" width="220" alt="暖色照片与月历"> |

| 好日 · 字体日历 | 慢笺 · 竖排文句 | 岁时 · 复古黄历 | 一页 · 悬挂日历 |
| --- | --- | --- | --- |
| <img src="docs/screenshots/refinement-portrait-4-good_day.jpg" width="180" alt="留白衬线日历"> | <img src="docs/screenshots/refinement-portrait-5-verse.jpg" width="180" alt="淡绿竖排日历"> | <img src="docs/screenshots/refinement-portrait-6-almanac.jpg" width="180" alt="复古黄历"> | <img src="docs/screenshots/refinement-portrait-7-hanging.jpg" width="180" alt="吊绳纸页日历"> |

预览与全屏复用同一套主题渲染，布局保持一致。沉浸页的纸张铺满显示区域：竖屏纵向排布，横屏将日期与说明、照片与月历重新排成左右布局。月历采用周日起始的 42 格布局。截图记录的是验收当天，实际 App 会随日期变化。

| 沉浸竖屏 | 沉浸横屏 |
| --- | --- |
| <img src="docs/screenshots/immersive-portrait-6-almanac.png" width="240" alt="全屏复古黄历竖屏"> | <img src="docs/screenshots/immersive-landscape-6-almanac.png" width="440" alt="全屏复古黄历横屏"> |

## 日期、宜忌和字体从哪里来

- **当前日期**读取设备系统时间和当前时区。前台在当地午夜自动刷新；系统日期、时间或时区改变时立即重算；从后台回来重新读取。公历年月日、星期、周序、农历、月历高亮和宜忌共用当天的数据。
- **农历、干支、节气、节日及传统宜忌**使用与 ClockMods 相同版本的 [`tyme4j 1.5.1`](https://github.com/6tail/tyme4j)。节日合并规则参考 ClockMods 的 `LunarAlmanac.festivals()`：实际交节日、农历节日、公历节日、入九及入伏首日。节气只在对应日期标记，不把前一个节气显示成今日节气；同一天的多个节日保留。
- 所有标为 **宜 / 忌** 的字段都来自当天历法计算，“好日”逐项轮播，“一页”按两项一组轮播，“岁时”完整展示；空数据显示“暂无”。传统宜忌属于民俗历法内容；五个主题共用 366 条原创短格言，“慢笺”另有 366 个二字心语；按当地连续日期每天轮换，任意连续 366 天不重复，跨年不重置。
- **字体**来自 Android 系统的 `sans-serif`、`serif`、`sans-serif-condensed` 及不同字重，没有下载或捆绑额外字体；字形可能随设备而变化。
- 小熊与照片是生成素材，参考图用于布局和视觉方向。详见 [素材说明](docs/assets.md)；tyme4j 的 MIT 许可随 App 保存在 `app/src/main/assets/licenses/`。

## 下载 APK

打开 [Build APK](https://github.com/heyiWF/CalendarMods/actions/workflows/android.yml)，选择成功的运行，在 **Artifacts** 中下载 `CalendarMods-debug-运行序号`，解压后安装 `CalendarMods-debug.apk`。同一压缩包提供 SHA-256 校验文件。

每次 push（所有分支和标签）都会触发构建，也支持 Pull Request 和手动运行。CI 运行单元测试、Lint、APK 编译，APK 保留 30 天，测试报告保留 14 天。安装包为开发测试用 Debug APK，未配置发布签名；不同环境生成的 Debug 签名可能不同，正式连续分发前需统一签名。

## 本地构建

使用 Android Studio 打开工程，或配置 `JAVA_HOME` 和 `ANDROID_HOME` 后运行命令。也可以在未提交的 `local.properties` 中指定 `sdk.dir`。

| 项目 | 版本 |
| --- | --- |
| 最低 Android | 12 / API 31 |
| compileSdk / targetSdk | 37 |
| SDK 包 | `platforms;android-37.0`、`build-tools;36.0.0` |
| Gradle Wrapper / AGP | 9.4.1 / 9.2.1 |
| Compose 编译插件 / BOM | 2.2.10 / 2026.09.00 |
| Java | JDK 17 及以上；CI 使用 Temurin 25，字节码目标 17 |

Windows PowerShell：

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.calendarmods/.MainActivity
```

macOS / Linux：

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 验证

单元测试覆盖月历格子、闰日、跨年周序、农历新年、同日多节日、节气/入九首日、宜忌格式，以及午夜和夏令时日长计算。

设备验收覆盖七主题的双向横滑、短滑回弹、点击进入沉浸模式、系统栏隐藏、长按返回、全屏占满、横竖屏重排、左右横屏旋转、普通点击/滑动不误退出、返回键和后台恢复，以及沉浸模式下的跨年午夜更新、系统时区变更和生产日期恢复。另检查横竖屏进入动画中途返回后，预览位置、大小和系统栏恢复正确。跨日测试仅在测试包中注入时钟，不修改设备系统时间；时区、主题和原显示模式在测试结束后恢复。建议使用专门的测试设备。

```powershell
.\gradlew.bat --no-daemon assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

测试以 `PASS` 或 `FAIL` 开头输出结果，截图位于设备应用外部文件目录 `acceptance/`。运行记录和全尺寸截图见 [设备验收](docs/device-validation.md)。CI 的测试为本地 JVM 测试和 Lint，设备手势验收需通过 ADB 单独运行。

## 代码入口

- `MainActivity.kt`：安全区、横向 Pager、主题记忆、全屏过渡和生命周期。
- `CalendarClock.kt`：系统日期来源、午夜更新及时间/时区监听。
- `CalendarData.kt`：统一公历/农历数据、节日、宜忌和 42 格月历。
- `CalendarThemes.kt`：按窗口比例缩放的共享预览、装订和自适应月历组件。
- `DailyWords.kt`：366 条短格言及 366 个二字心语。
- `AlmanacCarousel.kt`：跨预览/全屏保持状态的顺序淡变轮播。
- `ThemeStyle.kt`：Material 3 样式面板、图片导入与按主题保存。
- `ImmersiveCalendar.kt`：七主题全屏纸张、横竖屏重排和长按退出。
- `app/src/main/res/drawable-nodpi/`：可替换的小熊插画与照片。
- `.github/workflows/android.yml`：每次 push 自动编译 APK。

## 主题样式与每日内容

- 七主题的选择预览和全屏使用同一套响应式纸张渲染。预览以当前窗口比例缩小，横屏直接预览横屏布局。
- 每个主题右下角的 Material Style 按钮打开独立的 Material 3 样式面板。冬日来信、拾光支持系统照片选择器、等比填充（默认）、完整显示、拉伸填充，以及恢复默认；其余主题暂时显示暂无选项。
- 自选图片在后台按长边不超过 2048 像素解码并保存应用内副本，不要求全相册权限。图片和填充方式按主题独立保存，取消选择保留原设置。
- “好日”“岁时”的节日框仅在当天存在节日/节气时出现。“好日”将“宜＋事项”整体居中，每项完整停留 2 秒；事项长度变化时，用 640 ms 对称缓入缓出移动“宜”，与 280 ms 淡出、60 ms 留白、300 ms 淡入重叠，等长事项继续使用顺序淡变。“一页”以“宜 xx · xx”的宽度整体居中确定宜/忌标签位置，然后固定标签和事项起点；长事项向右延伸，末页单项也从同一起点左对齐，宜/忌与事项之间的间距始终固定，每页仍停留 2 秒后顺序淡变。横竖屏与预览/全屏共用这些规则。
- 格言库位于 `DailyWords.kt`，均为原创短文句（最长 14 字，含标点），不冒用名人署名。轮换与日期共用设备当前时区，并在午夜和前台恢复时更新。

新增样式与轮播设备验收：

```powershell
adb shell am instrument -w -e refinements true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

本轮主题调优与验收记录：[预览、每日文句与样式设置](docs/theme-refinements.md)。
