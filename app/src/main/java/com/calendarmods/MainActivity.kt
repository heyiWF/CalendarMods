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
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
    CalendarContent(clock, inventory, repository)
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun CalendarContent(clock: CalendarClock, inventory: ThemeInventory, repository: ThemeRepository) {
    val themes = inventory.themes
    val context = LocalContext.current
    val window = LocalActivity.current?.window
    val preferences = remember { context.getSharedPreferences("calendar", Context.MODE_PRIVATE) }
    var immersive by rememberSaveable { mutableStateOf(preferences.getBoolean("immersive", false)) }
    var storeOpen by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf(ThemeCatalog.selectedId(
        preferences.getString("theme_id", null), preferences.getInt("theme", 0), themes)) }
    var atStoreEntry by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = themes.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
        pageCount = { themes.size + 1 })
    val styleStore = remember { ThemeStyleStore(context.applicationContext) }
    var styles by remember(themes) { mutableStateOf(themes.associate { it.id to styleStore.read(it) }) }
    var editingThemeId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() }
            catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                snackbar.showSnackbar(if (failure is java.io.IOException) "保存失败，请重试" else failure.message ?: "操作失败，请重试")
            } finally { busy = false }
        }
    }
    fun requestDelete(id: String) {
        if (themes.size == 1) scope.launch { snackbar.showSnackbar("至少保留一个主题") }
        else deletingId = id
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val day by produceState<CalendarDay?>(null, context, lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            calendarDayEvents(context, clock).collect { event ->
                value = withContext(Dispatchers.Default) { CalendarDay(CalendarData.create(event.date), event.animate) }
            }
        }
    }
    val data = day?.data
    var previousOrder by remember { mutableStateOf(themes.map { it.id }) }
    LaunchedEffect(themes.map { it.id }) {
        val ids = themes.map { it.id }
        if (selectedId !in ids) {
            val index = previousOrder.indexOf(selectedId).coerceAtLeast(0)
            selectedId = previousOrder.drop(index + 1).firstOrNull { it in ids }
                ?: previousOrder.take(index).lastOrNull { it in ids } ?: ids.first()
        }
        pager.requestScrollToPage(if (atStoreEntry) themes.size else ids.indexOf(selectedId))
        previousOrder = ids
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { page ->
            if (!storeOpen) {
                atStoreEntry = page == themes.size
                themes.getOrNull(page)?.let { selectedId = it.id }
            }
        }
    }
    LaunchedEffect(selectedId) { preferences.edit { putString("theme_id", selectedId) } }
    LaunchedEffect(immersive) { preferences.edit { putBoolean("immersive", immersive) } }
    val theme = themes.find { it.id == selectedId } ?: themes.first()
    val displayTransition = updateTransition(if (storeOpen) 2 else if (immersive) 1 else 0, label = "calendar screen")
    val fullScreenWindow = immersive || displayTransition.currentState == 1
    CalendarWindow(fullScreenWindow)
    BackHandler(enabled = fullScreenWindow) { immersive = false }
    val background by animateColorAsState(if (storeOpen || atStoreEntry) MaterialTheme.colorScheme.surface else theme.background,
        label = "theme background")
    val foreground = if (atStoreEntry) MaterialTheme.colorScheme.onSurface else Color(theme.surfaces.chrome)
    SideEffect {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window?.insetsController?.setSystemBarsAppearance(
            if (storeOpen || atStoreEntry || theme.surfaces.darkSystemBarIcons) mask else 0, mask)
    }
    val cycle = rememberAlmanacCycle(data, enabled = !storeOpen)
    val goodDayCycle = rememberAlmanacCycle(data, centered = true, enabled = !storeOpen)
    val themeImages = inventory.catalog.mapNotNull { t -> repository.imagePath(t)?.let { t.id to it } }.toMap()
    val savedScreens = rememberSaveableStateHolder()
    CompositionLocalProvider(LocalThemeStyles provides styles, LocalThemeImages provides themeImages,
        LocalAlmanacCycle provides cycle, LocalGoodDayCycle provides goodDayCycle,
        LocalPaperInsets provides WindowInsets.displayCutout.union(WindowInsets.waterfall).asPaddingValues()) {
        themes.firstOrNull { it.id == editingThemeId }?.let { selected ->
            ThemeStyleSheet(selected, styles.getValue(selected.id), styleStore,
                onChange = { style -> styleStore.save(selected, style); styles = styles + (selected.id to style) },
                onReset = { styleStore.reset(selected); styles = styles + (selected.id to selected.defaultStyle()) },
                onDismiss = { editingThemeId = null })
        }
        deletingId?.let { id ->
            val name = inventory.catalog.find { it.id == id }?.title.orEmpty()
            AlertDialog(modifier = Modifier.semantics { testTagsAsResourceId = true }, onDismissRequest = { if (!busy) deletingId = null },
                title = { Text("删除主题？") },
                text = { Text("将从已安装主题中移除“$name”") },
                confirmButton = { TextButton(enabled = !busy, onClick = {
                    operation { repository.uninstall(id); deletingId = null }
                }, modifier = Modifier.testTag("confirm-delete")) { Text("删除") } },
                dismissButton = { TextButton(enabled = !busy, onClick = { deletingId = null }) { Text("取消") } })
        }
        SharedTransitionLayout(Modifier.fillMaxSize().background(background)) {
            val sharedScope = this
            displayTransition.AnimatedContent(modifier = Modifier.fillMaxSize(), transitionSpec = {
                (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false))
            }) { screen ->
                val visibility = this
                @Composable
                fun Modifier.shared(key: String): Modifier = with(sharedScope) {
                    sharedBounds(rememberSharedContentState(key), visibility,
                        boundsTransform = { _, _ -> tween(460, easing = FastOutSlowInEasing) },
                        enter = EnterTransition.None, exit = ExitTransition.None,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit))
                }
                when (screen) {
                    1 -> day?.let { current ->
                        ImmersiveCalendar(theme, current.data, onExit = { immersive = false },
                            modifier = Modifier.shared("calendar-${theme.id}"), animateDay = current.animate && immersive)
                    }
                    2 -> savedScreens.SaveableStateProvider("store") {
                        ThemeStore(inventory, data, busy, Modifier.shared("theme-store"),
                            previewsEnabled = !displayTransition.isRunning,
                            onBack = { storeOpen = false }, onAdd = { operation { repository.add(it) } },
                            onFavorite = { operation { repository.toggleFavorite(it) } },
                            onSaveFavorites = { repository.setFavorites(it) },
                            onError = { message -> scope.launch { snackbar.showSnackbar(message) } }, onDelete = ::requestDelete,
                            onReorder = { ids -> operation { repository.reorder(ids) } },
                            onUse = { id ->
                                selectedId = id; atStoreEntry = false
                                // The pager is detached in the store; schedule its next layout rather
                                // than suspending until it exists before dismissing the store.
                                pager.requestScrollToPage(themes.indexOfFirst { it.id == id }.coerceAtLeast(0))
                                storeOpen = false
                            })
                    }
                    else -> ThemeChooser(pager, themes, theme, data, foreground,
                        enabled = !displayTransition.isRunning && !busy,
                        paperModifier = { Modifier.shared("calendar-${it.id}") },
                        modifier = Modifier.shared("theme-store"), visibilityScope = visibility,
                        onSelect = { immersive = true }, onStyle = { editingThemeId = it.id },
                        onStore = { storeOpen = true }, onDelete = ::requestDelete)
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                SnackbarHost(snackbar, Modifier.navigationBarsPadding())
            }
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
