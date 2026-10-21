# 主题设计契约 v1 / v2

主题是可版本化的数据；模板是 App 提供的可信渲染能力。内置主题和安装主题共用 `CalendarTheme`，预览、全屏、设置和选择持久化均消费同一份描述。本地主题商店已提供七个预设主题的添加、收藏和排序；账号、上传、审核和网络下载尚未实现。

## 分层与入口

```mermaid
flowchart TD
    A[内置主题描述] --> C[ThemeRepository / StateFlow]
    B[未来下载或导入客户端] --> V[ThemeManifest 校验 + 图片解码]
    V --> D[私有图片 + AtomicFile 索引]
    D --> C
    C --> E[动态主题切换页 / 稳定 ID]
    E --> F[CalendarPaper / 版本化原生模板]
    E --> G[ThemeSetting / 样式面板]
    G --> H[ThemeStyleStore / 按主题 ID 的用户覆盖值]
    H --> F
    F --> I[同一套预览与全屏]
```

- `ThemeContract.kt`：主题身份、版本、表面颜色、模板支持的设置及安装/选择规则；不依赖 Android。
- `ThemeManifest.kt`：严格 JSON 编解码，作为未来包格式和主题编辑器的共同入口。
- `ThemeRepository.kt`：内置与已安装主题清单、安装/升级/卸载、图片私有副本、原子索引及可观察列表。
- `ThemeStyle.kt`：由契约声明的设置入口、用户覆盖值、旧数据迁移、图片选择与回退。
- `ImmersiveCalendar.kt`：唯一模板分发点。绘制仍由 App 内的 Compose 代码负责。

## 身份、版本与生命周期

| 字段 | 契约 |
| --- | --- |
| `id` | 不变的命名空间 ID，例如 `org.example.winter`。小写字母/数字、点或连字符，最长 120 字符；与标题、排序无关 |
| `schemaVersion` | 接受整数 `1`、`2`；v2 增加字体资源声明。旧 v1 原样兼容，未知版本拒绝安装 |
| `version` | 同一 ID 的正整数版本，更新必须严格递增，禁止回退和重复安装 |
| `title` / `author` | 非空、最长 80 字符、无控制字符；作者字段是展示信息，不是身份认证 |
| `renderer` | 精确匹配宿主模板 wire ID；同一主题 ID 更新不能改变模板，防止设置语义错配 |
| `surfaces` | 可选的宿主表面颜色和系统栏图标模式，缺项继承对应内置模板 |
| `defaults.fill` | 图片模板的默认填充方式；无图片槽的模板禁止声明 |
| `imageAsset` | 是否提供主题默认图片，默认 false；必须与安装参数的图片存在性一致 |
| `fonts` | v2 可选字体目录，最多 8 项，每项为稳定 `id` 和显示 `title`；安装时按 ID 传入字体字节 |
| `defaults.font` | v2 作者默认字体 ID，省略为 `template`（沿用模板原字体），必须属于本主题目录或为 `template` |

`com.calendarmods.*` 保留给内置主题，外部安装不能覆盖。默认按添加先后排序，支持显式排序；升级保留位置，内置主题也可移除，但至少保留一个已安装主题。最多保存 200 个外部主题。

主题选择保存为 `calendar.theme_id`。仅首次无 ID 时，把旧 `calendar.theme` 序号按原七主题顺序迁移。安装新主题、移除前面的主题或更新标题不会改变选中的 ID；选中主题不存在时回退到第一个内置主题。页面总数、语义描述和 Pager key 均来自仓库；底部最多显示九个邻近指示点。

## 七套原生模板与设置

| 内置 ID 后缀 | renderer | 布局内容 | 可调整属性 |
| --- | --- | --- | --- |
| `golden` | `golden.v1` | 黄色星期条、大日期、农历；横屏增加月历 | 无 |
| `teddy` | `illustration.v1` | 插画、农历月历与每日短句 | `image`、`fill` |
| `photo` | `photo.v1` | 照片、月历与每日短句 | `image`、`fill` |
| `good-day` | `good-day.v1` | 衬线大日期、节日、居中宜事项轮播 | 无 |
| `verse` | `verse.v1` | 竖排文句、月历、每日心语 | 无 |
| `almanac` | `almanac.v1` | 复古黄历、节日、完整宜忌、月历 | 无 |
| `hanging` | `hanging.v1` | 悬挂装订、大日期、双项宜忌轮播 | 无 |

上表列出模板专属属性；所有模板还提供 `page_animation`（默认 true）与 `interactive`（默认 false）两个 `ThemeSetting.Toggle` 开关。它们由宿主提供，按稳定主题 ID 保存为用户偏好，不增加 manifest 字段或改变 v1/v2 包格式。卸载主题时一并重置。

`ThemeRenderer.settings` 声明模板能力，`CalendarTheme.settings` 再根据 v2 的字体目录加入 `font` 选项。`ThemeSetting.Image` 声明本机图片选择，`ThemeSetting.Choice` 声明有限选项；设置面板按属性 key 查找选项，不通过主题 ID 判断能力。主题不能自称支持渲染器没有消费的属性。

交互模式只作用于全屏日历，插画与照片模板按月翻页，其余模板按天翻页；相邻月保留期望日号并在短月截断。浏览状态可跨旋转保留，离开全屏后重置，返回今天后继续跟随系统日期。动画开关同时控制自动跨日与手动翻页，关闭后仍可用滑动手势直接切换。

| 属性 | 类型 / 默认 | 约束与消费方 |
| --- | --- | --- |
| `image` | 用户私有图片路径，可空 | 路径只在本机生成，永不写入可发布 manifest；优先于主题包图片，再回退模板内置图片 |
| `fill` | 枚举，主题默认 `crop` | `crop` 等比填充、`fit` 完整显示、`stretch` 拉伸；预览、全屏与设置预览共同消费 |
| `surfaces.backdrop` | `#AARRGGBB` | 主题选择页背景 |
| `surfaces.paper` | `#AARRGGBB` | 全屏纸张最外层背景；照片框内和复古黄历内页仍由模板定义 |
| `surfaces.chrome` | `#AARRGGBB` | 选择页标题、计数、指示点和提示文字 |
| `surfaces.darkSystemBarIcons` | Boolean | 选择页系统栏使用深色图标；全屏时由宿主隐藏系统栏 |

颜色属于作者设计默认值，目前不生成用户调色面板。v2 支持主题附带字体及用户切换，详见[字体扩展指南](theme-fonts.md)。装订、内页装饰、具体排版、时间与宜忌轮播参数仍是原生模板的一部分，不能随意添加字段。

用户设置按 ID 隔离；同模板的两个主题也互不影响。旧 `TEDDY.image/fill` 和 `PHOTO.image/fill` 一次性迁移到 ID 命名空间。恢复默认会删除对应属性的覆盖值并保留迁移标记，防止旧值再次出现；只修改字体不会把继承的图片填充默认值固定下来。主题升级保留仍受支持的覆盖值，未覆盖的属性采用新默认值。本地主题库删除主题时重置个人设置，重新添加恢复默认；资源在下次仓库初始化时清理不再引用的图片与字体。

## 渲染契约

宿主提供统一 `CalendarData`（设备当地日期、公历、农历、节日、宜忌、42 格月历和每日文句）、安全区、窗口尺寸和公共轮播状态。主题不能自行替换时钟或执行数据请求。

新增模板必须遵守：

1. 预览和全屏共同调用 `CalendarPaper`，禁止维护两份布局。
2. 同一模板有纵向与横向布局，沿用 `FullPaper` 的等比缩放与安全区处理。
3. 主题绘制不接管进入/退出、长按、滑动屏蔽或系统栏行为；这些由宿主统一负责。
4. 每个可调整属性必须同时具有声明、默认值、有限有效值、持久化和实际渲染消费方。
5. 模板 wire ID 一经发布不可改名。未来不兼容改动增加 `.v2`，保留旧渲染器或提供显式迁移，不能静默改变已下载作品。
6. 保持现有日期语义和测试标签；新增模板需验证横竖屏、动态内容、设置隔离以及预览/全屏一致性。

接入一个新的原生主题时，先在 `ThemeRenderer` 注册永久 wire ID 和支持的属性，再添加 `CalendarTheme` 默认描述与 `CalendarPaper` 的渲染实现。只有新的内置主题需要加入 `CalendarTheme.entries`；下载的模板变体只走仓库安装接口。新增属性种类时，同步扩展 `ThemeSetting`、`ThemeStyle`、编解码与设置控件，并为实际渲染行为补充验收，不能只加一个面板开关。

## 可安装示例与接入方式

完整示例见 [`theme-example.json`](theme-example.json)，复用插画模板。导入客户端在拿到内容后调用：

```kotlin
// suspend API；仓库内部在 IO 调度器完成校验、解码和持久化。
val repository = ThemeRepository.get(context)
val installed = repository.install(manifestJson, imageBytes) // imageAsset=true 才提供图片
repository.uninstall(installed.id)
```

`imageBytes` 支持 Android ImageDecoder 可解码的图片，导入后重编码为 PNG 静态图片。压缩输入最多 8 MiB，原始图片最多 6400 万像素，解码后长边最多 2048 像素。manifest UTF-8 最多 32 KiB；未知字段、错误类型、未知模板、不兼容 schema 或非法属性直接报错，失败不发布新清单。

索引通过 `AtomicFile` 提交后才发布 StateFlow；更新使用新的不可变图片文件，避免正在显示的旧版本读到半张图片。每个主题使用随机图片文件名，manifest 不接受文件路径、URL、脚本、Dex、HTML 或远端字体。索引读失败或单项不兼容时保留可用的内置主题，并在 `ThemeInventory.loadErrors` 提供诊断；此时阻止继续改写索引，避免一次安装把暂时无法读取的作品覆盖丢失。后续商店管理页需要提供对应的修复/重新下载操作。

这里的 API 是后续下载服务的本机安装边界，当前没有导入按钮、ZIP 格式或网络服务。页面已订阅仓库，因此接入下载完成回调后不需要另写一套主题页面。

## 主题商店后续阶段

1. **模板式创作**：编辑器从宿主模板和受支持属性生成 manifest + 图片，提供同一渲染器预览。服务器分配/验证作者身份与主题 ID 所有权；客户端不能靠 `author` 字符串判定更新可信。
2. **发布与下载**：补充包格式、哈希/签名、许可证及素材授权、审核举报、列表搜索、下载进度/取消、更新策略。传输端在读取字节前执行大小限制，下载完成后再调用已有安装 API。
3. **自由布局创作**：新增受约束的声明式布局渲染器和独立 schema，定义文本/图片/日期/月历组件、数据绑定、布局边界、字体与动效预算。保持主题 ID、仓库、安装生命周期和选择机制；不要把远端 Kotlin/JS 当作主题加载。

v1 可以发布现有模板的图片与表面配色变体；完全自由的新布局需要第三阶段。这样先开放作者创作，同时保留以后拓展的路径。

## 验证入口

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb shell am instrument -w -e contract true com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
adb shell am instrument -w com.calendarmods.test/com.calendarmods.CalendarAcceptanceInstrumentation
```

契约单测覆盖稳定身份、旧选择迁移、版本规则、命名空间和非法属性。设备契约验收覆盖 JSON 真实编解码、错误包、图片安装、动态页面、默认值与设置隔离、升级/移除/重载/旋转。测试主题在结束时卸载，个人设置与原选中主题恢复。

### 2026-10-13 本地验收

- 最终应用与测试 APK 构建通过，19 项 JVM 测试通过，Lint 无问题。
- Android 15 / `emulator-5554`：七主题完整回归 `PASS 54 checks`，包含横竖屏、进出全屏及中途返回、午夜、时区和后台恢复。
- 动态主题契约 `PASS 4 checks`（四组场景）：错误包拒绝、旧设置迁移、带图片主题的即时安装及独立设置、更新/重载/旋转/卸载回退。
- 实际截图确认测试主题出现在第 9 页，移除前面的测试主题后保留选中 ID 并变成第 8 页；横屏更新后标题和图片保持正确。
- 重复验收曾暴露测试在底部面板动画中点击的问题，已改为等待选项坐标稳定 500 ms 后操作；最终复测通过。
- 本地日志（统一存放于 `docs/verification/logs/`）：`theme-contract-final-build.log`、`theme-contract-test-build.log`、`theme-contract-device-final.log`、`theme-contract-regression.log`；截图位于 `docs/verification/theme-contract/`，均为忽略的本地验收产物。

## 本地主题库 v2

主题包 manifest 支持 v1/v2；私有 `themes-v1/index.json` 为 v2 对象，包含 `packages`（外部描述、图片和字体文件映射）、`order`（已添加主题顺序）、`addedAt`（添加时间）、`favorites`、`resetStyles`（删除样式的恢复标记）。从旧数组索引一次性迁移，七个预设排在已有外部主题之前；迁移失败保留原文件并禁止写入。包 schema 与本地索引版本独立，旧条目无需字体映射。

`ThemeInventory.catalog` 是可用目录，`themes` 只返回已安装顺序。`add(id)` 幂等添加、`uninstall(id)` 移除、`toggleFavorite(id)` 切换收藏、`setFavorites(Map<String, Boolean>)` 原子设置一批明确收藏状态、`reorder(ids)` 接受完整无重复的已安装 ID 排列。所有变更串行执行，原子写入成功才发布 StateFlow；不能删除最后一个主题。删除保留目录资源及收藏，重置个人样式，重新添加排在末尾。未安装的外部主题保留已获取资源，可通过仓库重新添加，不需要提升包版本；导入升级仍要求版本递增。

删除标记与安装状态一起落盘，启动时重复执行样式清理，防止删除后崩溃留下旧样式。主题选择保存稳定 ID；删除当前主题选择其后继或前驱，排序不改变当前主题。商店入口独立于主题列表，不写入主题 ID。

## 宿主跨日过渡

每个 `ThemeRenderer` 对应一个 `DayMotion`，未来使用该模板的主题自动继承其效果，不扩展 manifest v1。日期订阅区分首次/恢复刷新、自然跨日与时间调整；只有持续前台全屏的自然午夜播放。两张 GPU 显示列表保存完整新旧页面，旧日期及宜忌轮播不会在动画中重新计算；图像和装订区域保持稳定。退出、旋转、后台或系统关闭动画时及时结束并显示最新日期。

## 商店预览与交互

名称与按钮分两行水平居中，名称在上、按钮在下；推荐和收藏页使用“应用／已安装”按钮；已安装页独立提供“使用”和编辑操作。所有移除入口共用确认及最少一个主题保护。收藏页的待取消 ID 使用可保存状态保留，心形即时改变、卡片暂不移除；只有切换标签或离开商店时才批量提交。旋转、后台和弹窗不提交，保存失败还原收藏显示并提示。

商店预览按可见项生成静态位图，三个标签共用 32 MiB LRU。键包含主题契约/版本、日期、样式、图片文件修订、窗口方向、目标像素尺寸、密度和字体缩放。生成限流为一个，离屏任务取消；入口展开、拖动和滚动时暂停未缓存预览的生成，静止后再继续；图源在 IO 线程按目标尺寸解码，渲染完成后释放临时图源与完整日历树。安装状态、收藏、顺序变化不使相同预览失效。商店打开时暂停宿主宜忌轮播。

设备验收参数：`-e store true`（商店交互与持久化）、`-e performance true`（7/70/200 个测试主题与 FrameMetrics/PSS/cache 量测）、`-e midnight true`（七主题横竖屏跨年与闰日/后台/旋转/禁用动画）。测试目录不写入用户主题库。

本轮完整结果、性能限制及复现方式见[主题商店与午夜动画验收](theme-store-validation.md)。

### 商店过渡与应用进度

标签切换采用先淡出、后淡入的短距离滑动过渡，复用同一组带稳定 ID 的网格节点；目标页面完成布局后才开始淡入，避免两套卡片同时重建；过渡期间暂停新的缩略图生成。缩略图内容身份与网格列的像素取整分离，移动到宽度相差 1–2px 的列时复用已有位图；已开始的快照不会因拖动暂停信号被中途清空。

商店的 `onAdd(id, reportProgress)` 为挂起接口：准备/安装阶段传入 `null`，有实际下载字节进度时传入 `0..1`。使用 Material 3 [`CircularWavyProgressIndicator`](https://developer.android.com/reference/kotlin/androidx/compose/material3/CircularWavyProgressIndicator.composable) 的不确定/确定进度变体；本地预设不模拟联网下载百分比。等待期间拦截重复操作，成功后显示已安装，失败恢复应用按钮并提示。指示器使用 `material3:1.5.0-beta01` 提供的新式组件。

每次进入商店重置到推荐页，旋转保留当前标签，三页滚动位置继续保存。“使用”单独采用商店淡出、目标预览滑入的过渡；普通返回沿商店入口容器收起。样式面板的完成操作等待 `SheetState.hide()` 结束才移除面板。删除手势在纵向超过触摸阈值后锁定整段指针事件，抑制横向翻页，抬手/取消后释放；垃圾桶围绕固定圆心弹性缩放。
