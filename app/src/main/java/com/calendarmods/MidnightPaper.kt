package com.calendarmods

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.min
import kotlin.math.sin

/** Versioned host capability: imported themes inherit the motion of their native renderer. */
internal enum class DayMotion(val millis: Int) {
    BOUND_PAGE(800), ILLUSTRATED_PAGE(760), PHOTO_CARD(650), TYPE_LIFT(650),
    VERSE_REVEAL(780), ALMANAC_PEEL(850), HANGING_PAGE(820);
}

internal val ThemeRenderer.dayMotion: DayMotion get() = when (this) {
    ThemeRenderer.GOLDEN -> DayMotion.BOUND_PAGE
    ThemeRenderer.TEDDY -> DayMotion.ILLUSTRATED_PAGE
    ThemeRenderer.PHOTO -> DayMotion.PHOTO_CARD
    ThemeRenderer.GOOD_DAY -> DayMotion.TYPE_LIFT
    ThemeRenderer.VERSE -> DayMotion.VERSE_REVEAL
    ThemeRenderer.ALMANAC -> DayMotion.ALMANAC_PEEL
    ThemeRenderer.HANGING -> DayMotion.HANGING_PAGE
}

/** Optional host observer used by device acceptance to sample sub-second transitions reliably. */
internal val LocalDayMotionObserver = staticCompositionLocalOf<((java.time.LocalDate, Boolean) -> Unit)?> { null }

private data class PrintedPage(val data: CalendarData, val cycle: AlmanacCycle, val goodDay: AlmanacCycle)
private fun AlmanacCycle.frozen() = AlmanacCycle().also {
    it.step = step; it.previousStep = previousStep; it.targetStep = targetStep
    // Snapshot the carousel's visible state, including a fade already in flight.
    it.opacity = Animatable(opacity.value)
    it.widthProgress = Animatable(widthProgress.value)
}

/** Retain separate render trees for the two display lists: nested Compose layers may otherwise
 * be reused and mutate an outgoing page. The old tree has frozen data and carousel state.
 */
@Composable
internal fun MidnightPaper(theme: CalendarTheme, data: CalendarData, animate: Boolean) {
    val resolver = LocalContext.current.contentResolver
    val first = rememberGraphicsLayer()
    val second = rememberGraphicsLayer()
    var oldPage by remember { mutableStateOf<PrintedPage?>(null) }
    val cycle = LocalAlmanacCycle.current
    val goodDay = LocalGoodDayCycle.current
    var lastCycles by remember { mutableStateOf(cycle to goodDay) }
    var outgoing by remember { mutableStateOf<GraphicsLayer?>(null) }
    var printed by remember { mutableStateOf(data) }
    val latest by rememberUpdatedState(data)
    val progress = remember { Animatable(1f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val window = LocalWindowInfo.current.containerSize
    val motion = theme.renderer.dayMotion
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (event == Lifecycle.Event.ON_PAUSE) { outgoing = null; oldPage = null; printed = latest }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(data.today, animate, theme.id, window, resumed) {
        val changed = printed.today != data.today
        val systemScale = systemAnimationScale(resolver)
        if (changed && animate && resumed && systemScale > 0f) {
            oldPage = PrintedPage(printed, lastCycles.first.frozen(), lastCycles.second.frozen())
            outgoing = first
            printed = data
            progress.snapTo(0f)
            try {
                // Explicitly propagate the system setting to this host-owned coroutine animation.
                withAnimationScale(systemScale) {
                    progress.animateTo(1f, tween(motion.millis, easing = FastOutSlowInEasing))
                }
            }
            finally { outgoing = null; oldPage = null }
        } else { outgoing = null; oldPage = null; printed = data; progress.snapTo(1f) }
    }
    SideEffect { if (printed.today == data.today) lastCycles = cycle to goodDay }
    val observer = LocalDayMotionObserver.current
    SideEffect { observer?.invoke(printed.today, outgoing != null) }
    val incoming = second
    val density = LocalDensity.current
    val insets = LocalPaperInsets.current
    val direction = LocalLayoutDirection.current
    val leftInset = with(density) { insets.calculateLeftPadding(direction).toPx() }
    val rightInset = with(density) { insets.calculateRightPadding(direction).toPx() }
    val topInset = with(density) { insets.calculateTopPadding().toPx() }
    val bottomInset = with(density) { insets.calculateBottomPadding().toPx() }
    Box(Modifier.fillMaxSize()) {
        oldPage?.let { snapshot ->
            Box(Modifier.fillMaxSize().clearAndSetSemantics { }.drawWithContent {
                first.record { this@drawWithContent.drawContent() }
            }) {
                CompositionLocalProvider(LocalAlmanacCycle provides snapshot.cycle, LocalGoodDayCycle provides snapshot.goodDay) {
                    CalendarPaper(theme, snapshot.data)
                }
            }
        }
        Box(Modifier.fillMaxSize().testTag("midnight-paper").semantics {
            stateDescription = if (outgoing == null) "静止：${printed.today}" else "跨日过渡：${printed.today}"
        }.drawWithContent {
            // Reset a buffer reused after a previous midnight before recording the new page.
            incoming.alpha = 1f; incoming.rotationX = 0f; incoming.rotationY = 0f
            incoming.translationX = 0f; incoming.translationY = 0f; incoming.scaleX = 1f; incoming.scaleY = 1f
            incoming.shadowElevation = 0f; incoming.clip = false
            incoming.record { this@drawWithContent.drawContent() }
            drawLayer(incoming)
            outgoing?.let { old ->
                val p = progress.value
                val wide = size.width > size.height
                val paperHeight = size.height - topInset - bottomInset
                val unit = min((size.width - leftInset - rightInset) / (if (wide) 640f else 360f), paperHeight / (if (wide) 360f else 600f))
                val margin = if (theme.renderer == ThemeRenderer.PHOTO || theme.renderer == ThemeRenderer.ALMANAC) 8f * unit else 0f
                var region = Rect(leftInset + margin, topInset + margin, size.width - rightInset - margin, size.height - bottomInset - margin)
                when (motion) {
                    DayMotion.BOUND_PAGE -> region = region.copy(top = topInset + 19f * unit)
                    DayMotion.HANGING_PAGE -> region = region.copy(top = topInset + 18f * unit)
                    DayMotion.ILLUSTRATED_PAGE, DayMotion.PHOTO_CARD -> {
                        val fraction = if (motion == DayMotion.PHOTO_CARD) .56f else .44f
                        region = if (wide) region.copy(left = region.left + region.width * fraction,
                            top = maxOf(region.top + 12f * unit, topInset + 22f * unit))
                        else region.copy(top = region.top + 12f * unit + (region.height - 12f * unit) * fraction)
                    }
                    else -> Unit
                }
                old.setRectOutline(region.topLeft, region.size)
                old.clip = true
                old.pivotOffset = Offset(region.center.x, region.top)
                old.cameraDistance = size.height * 4f
                old.alpha = 1f; old.rotationX = 0f; old.rotationY = 0f
                old.translationX = 0f; old.translationY = 0f
                old.scaleX = 1f; old.scaleY = 1f
                val shade = sin(p * Math.PI).toFloat().coerceAtLeast(0f)
                old.shadowElevation = 18f * unit * shade
                when (motion) {
                    DayMotion.BOUND_PAGE, DayMotion.ILLUSTRATED_PAGE, DayMotion.HANGING_PAGE -> {
                        // Stop at the edge: the reverse of a printed page must never mirror its text.
                        old.rotationX = -89.5f * p
                        old.alpha = (1f - ((p - .76f) / .24f).coerceIn(0f, 1f))
                        if (motion == DayMotion.HANGING_PAGE) old.translationY = -10f * unit * shade
                    }
                    DayMotion.PHOTO_CARD -> { old.translationY = -24f * unit * p; old.alpha = 1f - p }
                    DayMotion.TYPE_LIFT -> { old.translationY = -18f * unit * p; old.scaleX = 1f - .025f * p; old.scaleY = old.scaleX; old.alpha = 1f - p }
                    DayMotion.VERSE_REVEAL -> {
                        old.setRectOutline(region.topLeft, region.copy(right = region.right - region.width * p).size)
                        old.translationX = -12f * unit * p
                    }
                    DayMotion.ALMANAC_PEEL -> { old.rotationX = -36f * p; old.translationY = -region.height * p; old.alpha = 1f - p * p }
                }
                // The shadow follows only the moving paper; the image, frame and rings stay fixed.
                drawLayer(old)
            }
        }) { CalendarPaper(theme, printed) }
    }
}
