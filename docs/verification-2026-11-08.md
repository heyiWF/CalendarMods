[中文](verification-2026-11-08.md) | [English](verification-2026-11-08.en.md)

# 2026-11-08 验收与改动记录

分支：`main`。远端同步后以干净工作区开始；本次构建、单元测试（37 项）、应用／测试 APK 与 Lint 均通过。设备为 Android 15 / API 35 的 emulator-5554，独立包 `com.calendarmods.audit.main`。

全主题横竖屏矩阵通过；修正独立包恢复命令后，9 项日期／生命周期专项通过。旧 Android 本轮不做实机验收，代码按原最低 API 保留保护。设备没有报告软件导航栏 inset 来源；窗口／页面尺寸与实际显示范围一致，不把这一结果扩展为旧设备或另一种导航硬件的实测。

传统 View 全屏从 API 16 起同时使用布局隐藏导航栏与布局全屏标志；Compose 保留 enableEdgeToEdge 和 WindowInsetsControllerCompat，使用布局阶段 windowInsetsPadding 处理物理缺口、瀑布边与桌面标题栏，隐藏导航栏不预留空间。午夜过渡读取当前绘制阶段的边距。

mycompat 审查修复环境光候选计时中断未清零、关闭系统动画仍被无效唤醒的问题；字体按进程缓存，时钟一次测量同比拟合。日期单位／星期前缀缩小、周末星期标红、数字字重提高。12 小时制标签为数字墨迹半高，上午顶对齐、下午底对齐。设备覆盖横竖屏、85%／100%／140%、深浅色、周末和长标题，已检查截图。

本次修正旧验收代码中的固定 100 ms 动画时刻假设、单日补零断言、硬编码正式包名与最后主题假设；不把测试脚本问题当成产品回归。原应用数据未清空。日志和截图保留在本机忽略的 build 目录，正式截图在各历史报告中。

## 复现

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

ADB runner：`CalendarAcceptanceInstrumentation`。独立包构建参数在 PowerShell 中加引号，如 `'-Pcalendarmods.applicationId=com.calendarmods.audit'`；ADB 的测试包必须与构建匹配。main 可用 `-e lifecycle true` 单独重跑日期／生命周期。旧报告的测试数量、APK 哈希和未推送说明仍只代表当时记录。
