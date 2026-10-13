package com.calendarmods

import android.content.Context
import android.os.Bundle
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { CalendarApp() } }
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun CalendarApp(clock: CalendarClock = SystemCalendarClock) {
    val context = LocalContext.current
    val repository = remember { ThemeRepository.get(context) }
    val inventory by repository.inventory.collectAsState()
    // Rebuild positional pager state only when identities change; restore selection by stable ID.
    key(inventory.themes.map { it.id }) {
        CalendarContent(clock, inventory.themes, repository)
    }
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun CalendarContent(clock: CalendarClock, themes: List<CalendarTheme>, repository: ThemeRepository) {
    val context = LocalContext.current
    val window = LocalActivity.current?.window
    val preferences = remember { context.getSharedPreferences("calendar", Context.MODE_PRIVATE) }
    var immersive by rememberSaveable { mutableStateOf(preferences.getBoolean("immersive", false)) }
    val pager = rememberPagerState(
        initialPage = themes.indexOfFirst { it.id == ThemeCatalog.selectedId(
            preferences.getString("theme_id", null), preferences.getInt("theme", 0), themes) },
        pageCount = { themes.size },
    )
    val styleStore = remember { ThemeStyleStore(context.applicationContext) }
    var styles by remember(themes) { mutableStateOf(themes.associate { it.id to styleStore.read(it) }) }
    var editingThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val data by produceState<CalendarData?>(null, context, lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            calendarDates(context, clock).collect { today ->
                value = withContext(Dispatchers.Default) { CalendarData.create(today) }
            }
        }
    }
    LaunchedEffect(pager, themes) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect {
            preferences.edit { putString("theme_id", themes[it].id) }
        }
    }
    val theme = themes[pager.currentPage]
    LaunchedEffect(immersive) { preferences.edit { putBoolean("immersive", immersive) } }
    val displayTransition = updateTransition(immersive, label = "calendar display mode")
    // Keep bars hidden until the return animation has landed on the preview.
    val fullScreenWindow = immersive || displayTransition.currentState || displayTransition.isRunning
    CalendarWindow(fullScreenWindow)
    BackHandler(enabled = fullScreenWindow) { immersive = false }
    val background by animateColorAsState(theme.background, label = "theme background")
    val foreground = Color(theme.surfaces.chrome)
    SideEffect {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window?.insetsController?.setSystemBarsAppearance(
            if (theme.surfaces.darkSystemBarIcons) mask else 0, mask)
    }
    val cycle = rememberAlmanacCycle(data)
    val goodDayCycle = rememberAlmanacCycle(data, centered = true)
    val themeImages = themes.mapNotNull { theme -> repository.imagePath(theme)?.let { theme.id to it } }.toMap()
    CompositionLocalProvider(LocalThemeStyles provides styles, LocalThemeImages provides themeImages, LocalAlmanacCycle provides cycle,
        LocalGoodDayCycle provides goodDayCycle,
        LocalPaperInsets provides WindowInsets.displayCutout.union(WindowInsets.waterfall).asPaddingValues()) {
        themes.firstOrNull { it.id == editingThemeId }?.let { selected ->
            ThemeStyleSheet(selected, styles.getValue(selected.id), styleStore,
                onChange = { style ->
                    styleStore.save(selected, style)
                    styles = styles + (selected.id to style)
                }, onReset = {
                    styleStore.reset(selected)
                    styles = styles + (selected.id to selected.defaultStyle())
                }, onDismiss = { editingThemeId = null })
        }
        SharedTransitionLayout(Modifier.fillMaxSize().background(background)) {
            val sharedScope = this
            displayTransition.AnimatedContent(
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (EnterTransition.None togetherWith ExitTransition.None)
                        .using(SizeTransform(clip = false))
                },
                contentKey = { it },
            ) { fullScreen ->
                val visibilityScope = this
                if (fullScreen) {
                    data?.let {
                        ImmersiveCalendar(theme, it, onExit = { immersive = false },
                            modifier = Modifier.calendarBounds(sharedScope, theme, visibilityScope))
                    }
                } else {
                    ThemeChooser(pager, themes, theme, data, foreground,
                        enabled = !immersive && !displayTransition.isRunning,
                        paperModifier = { Modifier.calendarBounds(sharedScope, it, visibilityScope) },
                        visibilityScope = visibilityScope,
                        onSelect = { immersive = true },
                        onStyle = { editingThemeId = it.id })
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Modifier.calendarBounds(scope: SharedTransitionScope, theme: CalendarTheme,
    visibility: AnimatedVisibilityScope): Modifier = with(scope) {
    this@calendarBounds.sharedBounds(
        sharedContentState = rememberSharedContentState("calendar-${theme.id}"),
        animatedVisibilityScope = visibility,
        boundsTransform = { _, _ -> tween(460, easing = FastOutSlowInEasing) },
        // Both surfaces render identical paper; overlapping alpha fades would wash out its ink.
        enter = EnterTransition.None,
        exit = ExitTransition.None,
        // Measure each paper once at its destination size; scale the layer during motion.
        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit),
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ThemeChooser(pager: PagerState, themes: List<CalendarTheme>, theme: CalendarTheme, data: CalendarData?, foreground: Color,
    enabled: Boolean, paperModifier: @Composable (CalendarTheme) -> Modifier,
    visibilityScope: AnimatedVisibilityScope, onSelect: () -> Unit, onStyle: (CalendarTheme) -> Unit) {
    val chromeModifier = with(visibilityScope) {
        Modifier.animateEnterExit(enter = fadeIn(tween(200, delayMillis = 220)), exit = fadeOut(tween(160)))
    }
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).union(WindowInsets.waterfall))
            .semantics { testTagsAsResourceId = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(chromeModifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CALENDARMODS", color = foreground, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("${(pager.currentPage + 1).toString().padStart(2, '0')} / ${themes.size.toString().padStart(2, '0')}", color = foreground, fontSize = 10.sp)
        }
        HorizontalPager(
            state = pager,
            userScrollEnabled = enabled,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("theme-pager").semantics {
                stateDescription = "主题：${theme.title}，${pager.currentPage + 1} / ${themes.size}"
            },
            key = { themes[it].id },
        ) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                data?.let {
                    CalendarArtwork(themes[index], it, onSelect = {
                        if (enabled && !pager.isScrollInProgress && pager.settledPage == index) onSelect()
                    }, modifier = paperModifier(themes[index]))
                }
                FilledTonalIconButton(onClick = { onStyle(themes[index]) },
                    enabled = enabled && !pager.isScrollInProgress,
                    modifier = chromeModifier.align(Alignment.BottomEnd).size(48.dp)
                        .testTag("style-${themes[index].name}")) {
                    Icon(StyleIcon, "${themes[index].title.substringBefore(" · ")}样式设置")
                }
            }
        }
        Column(chromeModifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(theme.title, color = foreground, fontSize = 13.sp, letterSpacing = 2.sp,
                modifier = Modifier.testTag("theme-title"))
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp).semantics {
                contentDescription = "共 ${themes.size} 个主题，当前第 ${pager.currentPage + 1} 个"
            }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Keep the indicator bounded when a large downloaded library is installed.
                val start = (pager.currentPage - 4).coerceIn(0, (themes.size - 9).coerceAtLeast(0))
                (start until minOf(start + 9, themes.size)).forEach { index ->
                    Box(Modifier.size(if (index == pager.currentPage) 16.dp else 4.dp, 4.dp)
                        .background(foreground.copy(alpha = if (index == pager.currentPage) .85f else .25f), CircleShape))
                }
            }
            Text("左右滑动切换 · 单击全屏 · 长按返回", color = foreground.copy(alpha = .8f), fontSize = 10.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun CalendarWindow(immersive: Boolean) {
    val window = LocalActivity.current?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, lifecycle, immersive) {
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        fun applyMode() {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (immersive) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) applyMode()
        }
        lifecycle.addObserver(observer)
        applyMode()
        onDispose {
            lifecycle.removeObserver(observer)
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
