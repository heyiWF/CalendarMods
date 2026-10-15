package com.calendarmods

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

val CalendarTheme.background: Color get() = Color(surfaces.backdrop)

private val Ink = Color(0xFF20231E)
private val Red = Color(0xFFAD4B42)

/** The preview measures the same full-window paper, then scales it as a single layer. */
@Composable
fun CalendarArtwork(theme: CalendarTheme, data: CalendarData, modifier: Modifier = Modifier,
    onSelect: (() -> Unit)? = null) {
    val select by rememberUpdatedState(onSelect)
    val windowSize = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Layout(
            modifier = modifier.pointerInput(Unit) { detectTapGestures(onTap = { select?.invoke() }) }
                .semantics {
                    if (onSelect != null) {
                        role = Role.Button
                        onClick("全屏显示${theme.title}") { select?.invoke(); true }
                    }
                },
            content = {
                CalendarPaper(theme, data, Modifier.testTag("art-${theme.name}").semantics {
                    contentDescription = "${theme.title}，${data.today}，${data.weekdayChinese}，农历${data.lunar}"
                })
            },
        ) { measurables, constraints ->
            val w = windowSize.width.coerceAtLeast(1)
            val h = windowSize.height.coerceAtLeast(1)
            val scale = min(constraints.maxWidth.toFloat() / w, constraints.maxHeight.toFloat() / h)
            val page = measurables.single().measure(Constraints.fixed(w, h))
            val width = (w * scale).roundToInt()
            val height = (h * scale).roundToInt()
            layout(width, height) {
                page.placeWithLayer((width - w) / 2, (height - h) / 2) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

@Composable
internal fun Print(
    text: String, size: Int, modifier: Modifier = Modifier, color: Color = Ink,
    weight: FontWeight = FontWeight.Normal, family: FontFamily = FontFamily.SansSerif,
    align: TextAlign = TextAlign.Center, spacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight,
        fontFamily = LocalCalendarFont.current ?: family, textAlign = align, letterSpacing = spacing.sp,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(lineHeight = (size * 1.17).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
internal fun Rule(modifier: Modifier, color: Color = Ink.copy(alpha = .22f)) {
    Box(modifier.height(.65.dp).background(color))
}

@Composable
internal fun Rings(modifier: Modifier, dark: Boolean = false, count: Int = 16) {
    Canvas(modifier) {
        val gap = size.width / count
        repeat(count) { index ->
            val x = gap * (index + .5f)
            drawRoundRect(Color(0xFF504B40), Offset(x - 4.dp.toPx(), size.height * .61f),
                Size(8.dp.toPx(), 6.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawRoundRect(if (dark) Color(0xFF302D27) else Color(0xFF7B7A70),
                Offset(x - 2.3.dp.toPx(), 0f), Size(4.6.dp.toPx(), size.height * .82f),
                CornerRadius(3.dp.toPx()), style = Stroke(1.8.dp.toPx()))
            drawLine(Color.White.copy(alpha = .75f), Offset(x - 1.5.dp.toPx(), 2.dp.toPx()),
                Offset(x - 1.5.dp.toPx(), size.height * .67f), .65.dp.toPx())
        }
    }
}

@Composable
internal fun MonthGrid(data: CalendarData, modifier: Modifier, lunar: Boolean = false, outlined: Boolean = false,
    small: Boolean = false, color: Color = Ink) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(if (small) 16.dp else 25.dp)) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { index, name ->
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Print(name, if (small) 8 else 10, color = if (index == 0 || index == 6) Red else color)
                }
            }
        }
        data.cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { cell ->
                    val today = cell.date == data.today
                    val weekend = cell.date.dayOfWeek.value in listOf(6, 7)
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${cell.date}，农历${cell.lunar}" +
                            if (cell.festivals.isEmpty()) "" else "，${cell.festivals.joinToString("、")}"
                    }
                        .then(if (outlined) Modifier.border(.35.dp, color.copy(alpha = .18f)) else Modifier),
                        contentAlignment = Alignment.Center) {
                        // Long complete almanacs can leave a short grid. Fit each date inside
                        // its allocated row instead of allowing fixed circles to overlap rows.
                        val baseDiameter = if (small) 16f else if (lunar) 19f else 24f
                        val cellScale = min(1f, min(maxWidth.value / baseDiameter,
                            maxHeight.value / (baseDiameter + if (lunar) 9f else 0f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size((baseDiameter * cellScale).dp)
                                .then(if (today) Modifier.background(Red, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                Print(cell.date.dayOfMonth.toString(), ((if (small) 9 else 14) * cellScale).toInt().coerceAtLeast(1),
                                    color = when {
                                        today -> Color.White
                                        !cell.inMonth -> color.copy(alpha = .28f)
                                        weekend -> Red
                                        else -> color
                                    }, weight = if (today) FontWeight.Bold else FontWeight.Medium)
                            }
                            if (lunar) Print(cell.festivals.firstOrNull() ?: cell.lunar, (7 * cellScale).toInt().coerceAtLeast(1),
                                color = when {
                                    !cell.inMonth -> color.copy(alpha = .25f)
                                    cell.festivals.isNotEmpty() -> Red
                                    else -> color.copy(alpha = .6f)
                                }, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatSketch(modifier: Modifier) {
    Canvas(modifier) {
        val dark = Color(0xFF526250)
        val body = Path().apply {
            moveTo(size.width * .15f, size.height * .82f)
            cubicTo(size.width * .6f, size.height * 1.1f, size.width * .85f, size.height * .7f, size.width * .68f, size.height * .5f)
            lineTo(size.width * .54f, size.height * .5f)
            lineTo(size.width * .47f, size.height * .16f)
            lineTo(size.width * .33f, size.height * .3f)
            lineTo(size.width * .18f, size.height * .14f)
            close()
        }
        drawPath(body, dark.copy(alpha = .3f))
        drawPath(body, dark, style = Stroke(1.dp.toPx()))
        drawCircle(dark, 1.dp.toPx(), Offset(size.width * .26f, size.height * .42f))
        drawCircle(dark, 1.dp.toPx(), Offset(size.width * .42f, size.height * .42f))
        drawArc(dark, -80f, 245f, false, Offset(size.width * .56f, size.height * .29f), Size(size.width * .36f, size.height * .45f), style = Stroke(2.dp.toPx()))
    }
}
