# 主题字体扩展指南

七套模板均通过 `CalendarPaper` 提供字体覆盖；无字体目录的主题保持原来的字体和设置面板。主题有字体时，`CalendarTheme.settings` 在模板原有属性后增加 `font` 选项。

## 声明与安装

完整示例见 [theme-font-example.json](theme-font-example.json)。作者为每款字体声明一个 ID 和标题，导入端将文件映射到 ID：

```kotlin
val installed = ThemeRepository.get(context).install(
    manifest = manifestJson,
    // 图片模板仍可同时传入 image = imageBytes。
    fonts = mapOf("book" to bookTtfBytes, "rounded" to roundedOtfBytes),
)
```

`schemaVersion: 2` 增加 `fonts: [{id, title}]` 和 `defaults.font`。后者省略时为 `template`，表示模板原字体；也可指定本主题字体 ID。v1 不接受这两个字段。没有附带字体时也可使用 v2，默认行为与 v1 一致。

字体 ID 为 `[a-z][a-z0-9-]{0,39}`，同一主题内唯一，`template` 和 `default` 保留。标题 1–80 字符，非空白且无控制字符。跨版本保持同一字体 ID，以保留用户选择；不要用文件路径或下载 URL 作 ID。

每个 ID 对应一份常规字重的 TTF / OTF 文件；当前不支持字体集合 TTC、WOFF、远端字体、多个字重文件或可调变体轴。模板仍决定字号、粗细和排版，所需字重由系统匹配或合成。缺字交由 Android 系统字体回退；作者应优先提供覆盖简繁中文、拉丁字母和数字的字体，并确认嵌入/分发授权。

每个文件最多 16 MiB，每个主题字体合计最多 32 MiB，最多 8 个字体。传入资源的 ID 集合必须与声明完全一致，缺失和多余都拒绝。安装时检查文件头并用 Android 原生字体解析器验证；失败删除本次临时资源，不改变原主题或索引。

资源以随机私有文件名保存，原子提交索引后发布；升级生成新文件，下一次仓库初始化清理旧文件。资源丢失或损坏导致该条目加载诊断错误，保留其他可用主题并阻止继续覆盖索引。

这是供下载/导入客户端使用的资源接口，当前尚无面向用户的 ZIP 导入或第三方在线字体商店。

## 用户设置与渲染

- 样式面板展示每款字体的名称和中英文数字样张；用户可选择“跟随主题默认”、某款附带字体或“模板原字体”。选择按主题 ID 持久化，无覆盖值时随作者默认值更新；升级移除已选字体时回退到新默认值。
- 删除主题会重置字体选择，重新添加采用默认值。图片恢复只恢复图片与填充方式，保留字体；选择“跟随主题默认”只清除字体覆盖。未带字体的内置主题不会出现无效字体选项。
- 同一个字体上下文覆盖日期、星期、月历、农历、每日文句和宜忌；宜忌轮播测量也使用实际字体，保留原有标签间距。应用导航和按钮字体不随主题改变。
- 页面预览、全屏及商店缩略图复用同一渲染入口。缩略图键含主题版本及用户样式，字体切换和资源升级都会触发更新。

作者需验证字体在所选模板的横竖屏、长文本和系统字体缩放下的效果。字体字面和度量差异会影响布局，当前契约不允许字体自行修改模板尺寸。

## 验收入口

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -e fonts true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w -e contract true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

字体验收借用设备 `/system/fonts/NotoSerif-Regular.ttf` 和 `DroidSansMono.ttf`，不把第三方字体分发到仓库。覆盖真实解析、损坏/缺失/超限资源、失败回滚、升级回退、样式隔离、资源清理，以及用户点击切换、预览/全屏像素变化、横竖屏、重建和默认恢复。测试结束恢复原主题索引及选择状态。

### 2026-10-14 本地验收

- 28 项 JVM 测试通过，应用和设备测试 APK 构建成功；Lint 为 0 错误、2 项依赖更新提示。
- `emulator-5554` 字体专项 `PASS 3 checks`（三组场景），包含真实字体解析、失败回滚、图片默认值与字体独立保存、升级回退及界面切换。
- 预览与全屏日期像素在字体切换后变化，恢复作者默认后与初始预览一致；横竖屏和 Activity 重建保持选择。已人工查看字体设置横竖屏及全屏截图。
- 原主题契约回归 `PASS 4 checks`（四组场景）：v1 编解码、错误包、图片安装、设置迁移与隔离、版本升级、重载、旋转和卸载回退。本轮未重跑七主题完整 54 项验收。
- 本地日志：`build/theme-font-build.log`、`build/theme-font-device-final.log`、`build/theme-font-contract-final.log`；截图：`docs/verification/theme-fonts/`。这些均为忽略的本地验收产物。
