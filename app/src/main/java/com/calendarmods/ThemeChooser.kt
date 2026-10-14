package com.calendarmods

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeChooser(pager: PagerState, themes: List<CalendarTheme>, theme: CalendarTheme,
    data: CalendarData?, foreground: Color, enabled: Boolean,
    paperModifier: @Composable (CalendarTheme) -> Modifier, modifier: Modifier,
    visibilityScope: AnimatedVisibilityScope, onSelect: () -> Unit, onStyle: (CalendarTheme) -> Unit,
    onStore: () -> Unit, onDelete: (String) -> Unit) {
    val chrome = with(visibilityScope) {
        Modifier.animateEnterExit(enter = fadeIn(tween(200, delayMillis = 220)), exit = fadeOut(tween(160)))
    }
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    var previewWidth by remember(window) { mutableStateOf(240.dp) }
    var verticalDrag by remember { mutableStateOf(false) }
    val shop = pager.currentPage == themes.size
    val total = themes.size + 1
    Column(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).union(WindowInsets.waterfall))
        .semantics { testTagsAsResourceId = true }, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(chrome.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CALENDARMODS", color = foreground, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(if (shop) "主题商店" else "${(pager.currentPage + 1).toString().padStart(2, '0')} / ${themes.size.toString().padStart(2, '0')}",
                color = foreground, fontSize = 10.sp)
        }
        HorizontalPager(state = pager, userScrollEnabled = enabled && !verticalDrag,
            modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds().testTag("theme-pager").semantics {
                stateDescription = if (shop) "主题商店" else "主题：${theme.title}，${pager.currentPage + 1} / ${themes.size}"
            }, key = { themes.getOrNull(it)?.id ?: "store-entry" }) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp).onSizeChanged {
                val ratio = window.width.toFloat() / window.height.coerceAtLeast(1)
                previewWidth = with(density) { minOf(it.width.toFloat(), it.height * ratio).toDp() }
            }, contentAlignment = Alignment.Center) {
                val item = themes.getOrNull(index)
                if (item == null) {
                    PreviewFrame {
                        Surface(onClick = onStore, enabled = enabled,
                            modifier = modifier.fillMaxSize().testTag("store-entry"),
                            shape = RoundedCornerShape(12.dp), shadowElevation = 5.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Box(contentAlignment = Alignment.Center) { Icon(AddIcon, "打开主题商店", Modifier.size(28.dp)) }
                        }
                    }
                } else data?.let { date ->
                    SwipeDeletePreview(item, enabled && !pager.isScrollInProgress && pager.settledPage == index,
                        onDelete = { onDelete(item.id) }, onDragLock = { verticalDrag = it }) { swipeModifier, dragging ->
                        CalendarArtwork(item, date, modifier = paperModifier(item).then(swipeModifier),
                            onSelect = { if (enabled && !dragging && !pager.isScrollInProgress && pager.settledPage == index) onSelect() })
                    }
                }
            }
        }
        Column(chrome.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.width(previewWidth), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                Spacer(Modifier.size(48.dp))
                Text(if (shop) "主题商店" else theme.title, color = foreground, fontSize = 13.sp,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).testTag("theme-title"))
                if (shop) Spacer(Modifier.size(48.dp)) else FilledTonalIconButton(onClick = { onStyle(theme) },
                    enabled = enabled && !pager.isScrollInProgress, modifier = Modifier.size(48.dp).testTag("style-${theme.name}")) {
                    Icon(StyleIcon, "${theme.title.substringBefore(" · ")}样式设置")
                }
            }
            Row(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val start = (pager.currentPage - 4).coerceIn(0, (total - 9).coerceAtLeast(0))
                (start until minOf(start + 9, total)).forEach { index ->
                    Box(Modifier.size(if (index == pager.currentPage) 16.dp else 4.dp, 4.dp)
                        .background(foreground.copy(alpha = if (index == pager.currentPage) .85f else .25f), CircleShape))
                }
            }
            Text(if (shop) "发现喜欢的日历 · 管理你的主题" else "左右滑动切换 · 单击全屏 · 上滑删除", color = foreground.copy(alpha = .8f), fontSize = 10.sp)
        }
    }
}

/** Same window aspect and available bounds as CalendarArtwork. */
@Composable
internal fun PreviewFrame(content: @Composable BoxScope.() -> Unit) {
    val window = LocalWindowInfo.current.containerSize
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val ratio = window.width.toFloat() / window.height.coerceAtLeast(1)
        val width = minOf(maxWidth, maxHeight * ratio)
        Box(Modifier.width(width).height(width / ratio), content = content)
    }
}

@Composable
private fun SwipeDeletePreview(theme: CalendarTheme, enabled: Boolean, onDelete: () -> Unit, onDragLock: (Boolean) -> Unit,
    content: @Composable (Modifier, Boolean) -> Unit) {
    var drag by remember(theme.id) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val threshold = with(density) { (height * .2f).coerceIn(72.dp.toPx(), 120.dp.toPx()) }
    val resolver = LocalContext.current.contentResolver
    var armed by remember(theme.id) { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        val scale = systemAnimationScale(resolver)
        if (scale == 0f) { pulse.snapTo(if (armed) 1.08f else 1f); return@LaunchedEffect }
        withAnimationScale(scale) {
            if (armed) {
                pulse.animateTo(1.18f, spring(dampingRatio = .5f, stiffness = 650f))
                pulse.animateTo(1.08f, spring(dampingRatio = .75f))
            } else pulse.animateTo(1f, spring())
        }
    }
    val settled by animateFloatAsState(drag, if (dragging) snap() else tween(260), label = "preview return")
    val offset = if (dragging) drag else settled
    val delete by rememberUpdatedState(onDelete)
    val lock by rememberUpdatedState(onDragLock)
    val canDrag by rememberUpdatedState(enabled)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PreviewFrame {
            FilledTonalIconButton(onClick = { if (enabled) delete() },
                modifier = Modifier.align(Alignment.BottomCenter).size(48.dp).graphicsLayer {
                    alpha = (-offset / threshold).coerceIn(0f, 1f)
                    scaleX = pulse.value; scaleY = pulse.value
                }, enabled = enabled && offset <= -threshold) {
                Icon(DeleteIcon, "删除${theme.title}")
            }
        }
        content(Modifier.onSizeChanged { height = it.height }
            .graphicsLayer { translationY = offset }
            .pointerInput(theme.id, threshold) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!canDrag) return@awaitEachGesture
                    var total = androidx.compose.ui.geometry.Offset.Zero
                    var vertical = false
                    try {
                        while (true) {
                            // Claim the complete pointer stream before the parent pager once
                            // vertical touch slop wins. Later sideways drift cannot change axes.
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (vertical) { change.consume(); if (drag <= -threshold) delete() }
                                break
                            }
                            if (change.isConsumed && !vertical) break
                            val delta = change.positionChange()
                            total += delta
                            if (!vertical) {
                                if (kotlin.math.abs(total.x) > viewConfiguration.touchSlop &&
                                    kotlin.math.abs(total.x) >= kotlin.math.abs(total.y)) break
                                if (kotlin.math.abs(total.y) <= viewConfiguration.touchSlop) continue
                                vertical = true; dragging = true; lock(true)
                            }
                            change.consume()
                            drag = (drag + delta.y).coerceIn(-threshold * 1.5f, 0f)
                            if (!armed && drag <= -threshold) armed = true
                            else if (armed && drag > -threshold + 8.dp.toPx()) armed = false
                        }
                    } finally {
                        dragging = false; drag = 0f; armed = false; lock(false)
                    }
                }
            }.semantics { customActions = listOf(CustomAccessibilityAction("删除主题") { delete(); true }) }, dragging)
    }
}
