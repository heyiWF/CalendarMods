package com.calendarmods

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal fun almanacPages(items: List<String>, pageSize: Int): List<String> =
    items.ifEmpty { listOf("暂无") }.chunked(pageSize).map { it.joinToString(" · ") }

internal class AlmanacCycle {
    var step by mutableIntStateOf(0)
    var previousStep by mutableIntStateOf(0)
    var targetStep by mutableIntStateOf(0)
    var opacity = Animatable(1f)
    var widthProgress = Animatable(1f)
}

internal val LocalAlmanacCycle = staticCompositionLocalOf { AlmanacCycle() }
internal val LocalGoodDayCycle = staticCompositionLocalOf { AlmanacCycle() }

/** Hoisted above both surfaces: entering fullscreen never restarts or crossfades two items. */
@Composable
internal fun rememberAlmanacCycle(data: CalendarData?, centered: Boolean = false, enabled: Boolean = true): AlmanacCycle {
    val cycle = remember(data?.today, data?.recommends, centered) { AlmanacCycle() }
    val items = data?.recommends.orEmpty().ifEmpty { listOf("暂无") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(cycle, lifecycle, enabled) {
        if (!enabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            cycle.widthProgress.snapTo(1f)
            cycle.opacity.snapTo(1f)
            while (true) {
                delay(2_000)
                val next = if (cycle.step == Int.MAX_VALUE) 0 else cycle.step + 1
                // Reset while both width endpoints still refer to the current item.
                // The target is independent of the visible item so motion can overlap both fades.
                cycle.previousStep = cycle.step
                cycle.widthProgress.snapTo(0f)
                cycle.targetStep = next
                if (centered && items[cycle.previousStep % items.size].length.coerceAtLeast(2) !=
                    items[next % items.size].length.coerceAtLeast(2)) {
                    coroutineScope {
                        launch {
                            cycle.widthProgress.animateTo(1f, tween(640, easing = CubicBezierEasing(.42f, 0f, .58f, 1f)))
                        }
                        launch {
                            cycle.opacity.animateTo(0f, tween(280))
                            cycle.step = next
                            delay(60)
                            cycle.opacity.animateTo(1f, tween(300))
                        }
                    }
                } else {
                    cycle.opacity.animateTo(0f, tween(220))
                    cycle.step = next
                    cycle.widthProgress.snapTo(1f)
                    cycle.opacity.animateTo(1f, tween(220))
                }
            }
        }
    }
    return cycle
}

/** Good Day centers the label + item, with gentle motion overlapping sequential text fades. */
@Composable
internal fun CenteredGoodDayCarousel(items: List<String>, size: Int, modifier: Modifier = Modifier) {
    val pages = remember(items) { almanacPages(items, 1) }
    val cycle = LocalGoodDayCycle.current
    val measurer = rememberTextMeasurer()
    val widths = remember(pages, size, measurer) {
        val style = TextStyle(fontSize = size.sp, fontFamily = FontFamily.Serif, letterSpacing = 0.sp)
        val minimum = measurer.measure(AnnotatedString("嫁娶"), style, softWrap = false).size.width
        pages.map { measurer.measure(AnnotatedString(it), style, softWrap = false).size.width.coerceAtLeast(minimum) }
    }
    val current = cycle.step % pages.size
    val previous = cycle.previousStep % pages.size
    val target = cycle.targetStep % pages.size
    Layout(modifier = modifier.heightIn(min = (size * 1.5f).dp).testTag("good-day-almanac"), content = {
        Print("宜", size, Modifier.testTag("almanac-label-宜"), family = FontFamily.Serif, maxLines = 1)
        Print(pages[current], size, Modifier.testTag("almanac-item-宜"), family = FontFamily.Serif, maxLines = 1)
    }) { measurables, constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val label = measurables[0].measure(loose)
        val item = measurables[1].measure(loose)
        val gap = size.sp.toPx() * .5f
        val height = maxOf(label.height, item.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            val width = widths[previous] + (widths[target] - widths[previous]) * cycle.widthProgress.value
            val left = (constraints.maxWidth - label.width - gap - width) / 2f
            label.placeWithLayer(0, (height - label.height) / 2) { translationX = left }
            item.placeWithLayer(0, (height - item.height) / 2) {
                translationX = left + label.width + gap + (width - item.width) / 2f
                alpha = if (pages.size == 1) 1f else cycle.opacity.value
            }
        }
    }
}

@Composable
internal fun AlmanacCarousel(label: String, items: List<String>, pageSize: Int, size: Int,
    modifier: Modifier = Modifier) {
    val pages = remember(items, pageSize) { almanacPages(items, pageSize) }
    val cycle = LocalAlmanacCycle.current
    val measurer = rememberTextMeasurer()
    val baselineWidth = remember(pageSize, size, measurer) {
        measurer.measure(AnnotatedString(List(pageSize) { "嫁娶" }.joinToString(" · ")),
            TextStyle(fontSize = size.sp, fontFamily = FontFamily.Serif, letterSpacing = 0.sp),
            softWrap = false).size.width
    }
    val page = cycle.step % pages.size
    Layout(modifier = modifier.heightIn(min = (size * 1.5f).dp).testTag("hanging-almanac-$label"), content = {
        Print(label, size, Modifier.testTag("almanac-label-$label"), family = FontFamily.Serif, maxLines = 1)
        Print(pages[page], size, Modifier.testTag("almanac-item-$label"), family = FontFamily.Serif, maxLines = 1)
    }) { measurables, constraints ->
        val labelPlaceable = measurables[0].measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        val gap = size.sp.toPx() * .75f
        val left = ((constraints.maxWidth - labelPlaceable.width - gap - baselineWidth) / 2f).coerceAtLeast(0f)
        val itemLeft = left + labelPlaceable.width + gap
        val item = measurables[1].measure(Constraints(
            maxWidth = (constraints.maxWidth - itemLeft).toInt().coerceAtLeast(0), maxHeight = constraints.maxHeight))
        val height = maxOf(labelPlaceable.height, item.height).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            labelPlaceable.placeWithLayer(0, (height - labelPlaceable.height) / 2) { translationX = left }
            item.placeWithLayer(0, (height - item.height) / 2) {
                // Every page, including a single item, starts at the same fixed label gap.
                translationX = itemLeft
                alpha = if (pages.size == 1) 1f else cycle.opacity.value
            }
        }
    }
}
