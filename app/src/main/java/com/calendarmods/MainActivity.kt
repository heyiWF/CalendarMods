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
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun CalendarApp(clock: CalendarClock = SystemCalendarClock) {
    val context = LocalContext.current
    val window = LocalActivity.current?.window
    val preferences = remember { context.getSharedPreferences("calendar", Context.MODE_PRIVATE) }
    var immersive by rememberSaveable { mutableStateOf(preferences.getBoolean("immersive", false)) }
    val pager = rememberPagerState(
        initialPage = preferences.getInt("theme", 0).coerceIn(0, CalendarTheme.entries.lastIndex),
        pageCount = { CalendarTheme.entries.size },
    )
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val data by produceState<CalendarData?>(null, context, lifecycle, clock) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            calendarDates(context, clock).collect { today ->
                value = withContext(Dispatchers.Default) { CalendarData.create(today) }
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect {
            preferences.edit { putInt("theme", it) }
        }
    }
    val theme = CalendarTheme.entries[pager.currentPage]
    LaunchedEffect(immersive) { preferences.edit { putBoolean("immersive", immersive) } }
    CalendarWindow(immersive)
    BackHandler(enabled = immersive) { immersive = false }
    val background by animateColorAsState(theme.background, label = "theme background")
    val foreground = if (theme == CalendarTheme.ALMANAC) Color(0xFFEAE1C9) else Color(0xFF51554F)
    SideEffect {
        val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        window?.insetsController?.setSystemBarsAppearance(
            if (theme == CalendarTheme.ALMANAC) 0 else mask, mask)
    }
    if (immersive) {
        data?.let { ImmersiveCalendar(theme, it, onExit = { immersive = false }) }
        return
    }
    Column(
        Modifier.fillMaxSize().background(background).safeDrawingPadding().semantics { testTagsAsResourceId = true },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("CALENDARMODS", color = foreground, fontSize = 10.sp, letterSpacing = 2.sp)
            Text("${(pager.currentPage + 1).toString().padStart(2, '0')} / 07", color = foreground, fontSize = 10.sp)
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("theme-pager").semantics {
                stateDescription = "主题：${theme.title}，${pager.currentPage + 1} / 7"
            },
            key = { CalendarTheme.entries[it].name },
        ) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                data?.let {
                    CalendarArtwork(CalendarTheme.entries[index], it, onSelect = {
                        if (!pager.isScrollInProgress && pager.settledPage == index) immersive = true
                    })
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(theme.title, color = foreground, fontSize = 13.sp, letterSpacing = 2.sp,
                modifier = Modifier.testTag("theme-title"))
            Row(Modifier.padding(top = 10.dp, bottom = 8.dp).semantics {
                contentDescription = "共 7 个主题，当前第 ${pager.currentPage + 1} 个"
            }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CalendarTheme.entries.forEachIndexed { index, _ ->
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
