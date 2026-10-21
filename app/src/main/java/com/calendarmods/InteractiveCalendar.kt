package com.calendarmods

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs

@Composable
internal fun InteractiveCalendar(theme: CalendarTheme, today: CalendarData, animateDay: Boolean,
    animation: Boolean, onDate: (LocalDate) -> Unit) {
    val origin = rememberSaveable(theme.id) { today.today.toEpochDay() }
    val paging = remember(origin, theme.renderer) { CalendarPaging(LocalDate.ofEpochDay(origin), theme.renderer.pagesByMonth) }
    var anchorDay by rememberSaveable(theme.id) { mutableLongStateOf(origin) }
    var lastToday by rememberSaveable(theme.id) { mutableLongStateOf(today.today.toEpochDay()) }
    val anchor = LocalDate.ofEpochDay(anchorDay)
    val pager = rememberPagerState(initialPage = paging.home, pageCount = { paging.count })
    val scope = rememberCoroutineScope()
    val date = paging.date(pager.settledPage, anchor)
    val todayPage = paging.page(today.today, anchor)
    LaunchedEffect(today.today) {
        // Follow the clock only while displaying today. Browsing another date stays put.
        if (!pager.isScrollInProgress && pager.settledPage == paging.home &&
            anchorDay == lastToday) anchorDay = today.today.toEpochDay()
        lastToday = today.today.toEpochDay()
    }
    SideEffect { onDate(date) }
    fun returnToday() {
        scope.launch {
            // Long journeys should not render every intervening day.
            if (animation && abs(pager.currentPage - todayPage) <= 3) pager.animateScrollToPage(todayPage)
            else pager.scrollToPage(todayPage)
            anchorDay = today.today.toEpochDay()
            pager.scrollToPage(paging.home)
        }
    }
    Column(Modifier.fillMaxSize().background(Color(theme.surfaces.paper))) {
        HorizontalPager(pager, userScrollEnabled = animation, beyondViewportPageCount = 1,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("calendar-date-pager").semantics {
                stateDescription = "日期：$date"
                customActions = listOf(
                    CustomAccessibilityAction(if (paging.monthly) "上个月" else "前一天") {
                        scope.launch { if (animation) pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                            else pager.scrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }; true
                    },
                    CustomAccessibilityAction(if (paging.monthly) "下个月" else "后一天") {
                        scope.launch { if (animation) pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(paging.count - 1))
                            else pager.scrollToPage((pager.currentPage + 1).coerceAtMost(paging.count - 1)) }; true
                    },
                    CustomAccessibilityAction("回到今天") { returnToday(); true })
            }.then(if (animation) Modifier else Modifier.pointerInput(paging) {
                var drag = 0f
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) >= maxOf(48.dp.toPx(), size.width * .15f)) {
                            val next = (pager.currentPage + if (drag < 0) 1 else -1).coerceIn(0, paging.count - 1)
                            scope.launch { pager.scrollToPage(next) }
                        }
                    }) { change, delta -> change.consume(); drag += delta }
            })) { page ->
            val pageDate = paging.date(page, anchor)
            val data by produceState<CalendarData?>(null, pageDate) {
                value = withContext(Dispatchers.Default) { CalendarData.create(pageDate) }
            }
            val printed = if (pageDate == today.today) today else data?.takeIf { it.today == pageDate }
            // Neighbours are composed and their calendar data prepared before the gesture.
            if (printed != null) {
                val cycle = rememberAlmanacCycle(printed, enabled = page == pager.settledPage && !pager.isScrollInProgress)
                val goodDay = rememberAlmanacCycle(printed, centered = true,
                    enabled = page == pager.settledPage && !pager.isScrollInProgress)
                CompositionLocalProvider(LocalAlmanacCycle provides cycle, LocalGoodDayCycle provides goodDay) {
                    // Only the live page needs two retained display lists for midnight.
                    // The pager itself moves historical pages without extra snapshot layers.
                    if (page == paging.home) MidnightPaper(theme, printed, animation && animateDay && !pager.isScrollInProgress)
                    else CalendarPaper(theme, printed)
                }
            }
        }
        // Reserve this space even on today so paging never resizes the printed page
        // and the return action cannot cover lunar text, mottos or month-grid cells.
        Box(Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .height(56.dp), contentAlignment = Alignment.Center) {
            if (date != today.today) {
                FilledTonalButton(onClick = ::returnToday, modifier = Modifier.testTag("calendar-today")) {
                    Text("回到今天")
                }
            } else Text(if (paging.monthly) "左右滑动切换月份" else "左右滑动切换日期",
                style = MaterialTheme.typography.bodySmall, color = Color(theme.surfaces.chrome))
        }
    }
}
