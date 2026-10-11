package com.calendarmods

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

enum class CalendarTheme(val title: String, val background: Color) {
    GOLDEN("晴日 · 黄页台历", Color(0xFFECEEE9)),
    TEDDY("冬日来信 · 插画月历", Color(0xFF9CC9DF)),
    PHOTO("拾光 · 照片台历", Color(0xFFD5C6B6)),
    GOOD_DAY("好日 · 字体日历", Color(0xFFF8F7F3)),
    VERSE("慢笺 · 竖排文句", Color(0xFFE5E6DE)),
    ALMANAC("岁时 · 复古黄历", Color(0xFF53534D)),
    HANGING("一页 · 悬挂日历", Color(0xFFE6E8E1)),
}

private val Ink = Color(0xFF20231E)
private val Red = Color(0xFFAD4B42)
private val Paper = Color(0xFFFFFEF9)
private val Serif = FontFamily.Serif

/** A fitted Compose artboard keeps poster proportions intact on phones, tablets and rotation. */
@Composable
fun CalendarArtwork(theme: CalendarTheme, data: CalendarData, onSelect: (() -> Unit)? = null) {
    val select by rememberUpdatedState(onSelect)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / 360f, maxHeight.value / 560f)
        Layout(
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { select?.invoke() }) }
                .semantics {
                    if (onSelect != null) {
                        role = Role.Button
                        onClick("全屏显示${theme.title}") { select?.invoke(); true }
                    }
                },
            content = {
                Box(Modifier.size(360.dp, 560.dp).testTag("art-${theme.name}").semantics {
                    contentDescription = "${theme.title}，${data.today}，${data.weekdayChinese}，农历${data.lunar}"
                }) {
                    when (theme) {
                        CalendarTheme.GOLDEN -> GoldenCalendar(data)
                        CalendarTheme.TEDDY -> TeddyCalendar(data)
                        CalendarTheme.PHOTO -> PhotoCalendar(data)
                        CalendarTheme.GOOD_DAY -> GoodDayCalendar(data)
                        CalendarTheme.VERSE -> VerseCalendar(data)
                        CalendarTheme.ALMANAC -> AlmanacCalendar(data)
                        CalendarTheme.HANGING -> HangingCalendar(data)
                    }
                }
            },
        ) { measurables, _ ->
            val placeable = measurables.single().measure(Constraints.fixed(360.dp.roundToPx(), 560.dp.roundToPx()))
            val width = (placeable.width * scale).roundToInt()
            val height = (placeable.height * scale).roundToInt()
            layout(width, height) {
                placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
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
        fontFamily = family, textAlign = align, letterSpacing = spacing.sp,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(lineHeight = (size * 1.17).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun BoxScope.Label(
    text: String, x: Int, y: Int, width: Int, size: Int,
    color: Color = Ink, weight: FontWeight = FontWeight.Normal,
    family: FontFamily = FontFamily.SansSerif, align: TextAlign = TextAlign.Center, spacing: Float = 0f,
) = Print(text, size, Modifier.offset(x.dp, y.dp).width(width.dp), color, weight, family, align, spacing)

@Composable
private fun Sheet(modifier: Modifier, color: Color = Paper, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.shadow(14.dp, RoundedCornerShape(1.dp), clip = false,
        ambientColor = Color(0xFF514A3C), spotColor = Color(0xFF514A3C)).background(color), content = content)
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
                    Box(Modifier.weight(1f).fillMaxHeight().semantics {
                        contentDescription = "${cell.date}，农历${cell.lunar}" +
                            if (cell.festivals.isEmpty()) "" else "，${cell.festivals.joinToString("、")}"
                    }
                        .then(if (outlined) Modifier.border(.35.dp, color.copy(alpha = .18f)) else Modifier),
                        contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(if (small) 16.dp else if (lunar) 19.dp else 24.dp)
                                .then(if (today) Modifier.background(Red, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                                Print(cell.date.dayOfMonth.toString(), if (small) 9 else 14,
                                    color = when {
                                        today -> Color.White
                                        !cell.inMonth -> color.copy(alpha = .28f)
                                        weekend -> Red
                                        else -> color
                                    }, weight = if (today) FontWeight.Bold else FontWeight.Medium)
                            }
                            if (lunar) Print(cell.festivals.firstOrNull() ?: cell.lunar, 7,
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
private fun BoxScope.GoldenCalendar(data: CalendarData) {
    // Layered bottom edge and warm shelf evoke the physical tear-off desk calendar.
    Box(Modifier.offset(0.dp, 476.dp).size(360.dp, 60.dp).background(
        Brush.verticalGradient(listOf(Color(0xFFCAB28C).copy(alpha = .65f), Color(0xFFDBCCB2).copy(alpha = 0f)))))
    Box(Modifier.offset(25.dp, 37.dp).size(312.dp, 450.dp).background(Color(0xFFC3C2B6)))
    Sheet(Modifier.offset(22.dp, 27.dp).size(312.dp, 454.dp)) {
        Box(Modifier.fillMaxWidth().height(68.dp).background(Color(0xFFF1C31C)))
        Label(data.weekday.uppercase(Locale.ENGLISH), 0, 26, 312, 22, Color.White, FontWeight.Bold, spacing = 1f)
        Label(data.month.uppercase(Locale.ENGLISH), 0, 91, 312, 27, weight = FontWeight.Bold)
        Label(data.day, 0, 124, 312, 174, weight = FontWeight.ExtraBold)
        Label(data.today.year.toString(), 0, 331, 312, 31, weight = FontWeight.Medium)
        Label("WEEK ${data.week}  •  DAY ${data.today.dayOfYear}", 0, 377, 312, 12, spacing = 1f)
        Label(data.lunar + "  ·  " + data.weekdayChinese, 0, 414, 312, 10, color = Ink.copy(alpha = .58f), spacing = 2f)
        repeat(3) { Rule(Modifier.offset(1.dp, (447 + it * 2).dp).width(310.dp), Ink.copy(alpha = .10f)) }
    }
    Rings(Modifier.offset(30.dp, 16.dp).size(296.dp, 26.dp), count = 18)
}

@Composable
private fun BoxScope.TeddyCalendar(data: CalendarData) {
    Box(Modifier.offset(23.dp, 38.dp).size(321.dp, 499.dp).background(Color(0xFF23465D)))
    Sheet(Modifier.offset(14.dp, 25.dp).size(326.dp, 493.dp)) {
        Image(painterResource(R.drawable.winter_bear), "雪地里的小熊与热可可", Modifier.offset(10.dp, 17.dp).size(306.dp, 200.dp), contentScale = ContentScale.Crop)
        Label(data.month.uppercase(Locale.ENGLISH), 0, 230, 326, 23, weight = FontWeight.Bold)
        Label("${data.today.year}  /  A LITTLE WARMTH, EVERY DAY", 0, 259, 326, 8, spacing = 1f)
        MonthGrid(data, Modifier.offset(13.dp, 277.dp).size(300.dp, 208.dp), lunar = true, outlined = true)
    }
    Rings(Modifier.offset(23.dp, 13.dp).size(306.dp, 28.dp), count = 19)
}

@Composable
private fun BoxScope.PhotoCalendar(data: CalendarData) {
    Sheet(Modifier.offset(15.dp, 23.dp).size(330.dp, 515.dp), Color(0xFF232421)) {
        Image(painterResource(R.drawable.sunset_memory), "日落海边的温暖合影", Modifier.offset(7.dp, 8.dp).size(316.dp, 324.dp), contentScale = ContentScale.Crop)
        Box(Modifier.offset(7.dp, 332.dp).size(316.dp, 157.dp).background(Color(0xFFF8F6F0))) {
            Label("MOMENTS TO KEEP", 12, 12, 218, 7, align = TextAlign.Left, spacing = 2f)
            MonthGrid(data, Modifier.offset(11.dp, 30.dp).size(223.dp, 112.dp), small = true)
            Label(data.today.monthValue.toString().padStart(2, '0'), 241, 27, 67, 49,
                Color(0xFF8F8C84), FontWeight.ExtraBold, FontFamily.SansSerif)
            Label(data.today.year.toString(), 239, 87, 67, 20, Color(0xFF8F8C84), FontWeight.Light)
            Label(data.monthShort.uppercase(Locale.ENGLISH), 239, 121, 67, 9, Color(0xFF8F8C84), spacing = 1f)
        }
        Label("THE DAYS BECOME OUR STORIES", 0, 498, 330, 6, Color(0xFFD0C9BC), spacing = 2f)
    }
    Rings(Modifier.offset(26.dp, 11.dp).size(307.dp, 27.dp), dark = true)
}

@Composable
private fun BoxScope.GoodDayCalendar(data: CalendarData) {
    Box(Modifier.fillMaxSize().background(Color(0xFFFFFEFC))) {
        Label(data.lunar, 0, 21, 360, 14, family = Serif, spacing = 2f)
        Box(Modifier.offset(if (data.festivals.isEmpty()) 151.dp else 70.dp, 57.dp)
            .size(if (data.festivals.isEmpty()) 58.dp else 220.dp, 34.dp).border(.7.dp, Ink), contentAlignment = Alignment.Center) {
            Print(data.festivals.joinToString(" · ").ifBlank { "好日" }, 12, family = Serif)
        }
        Label("${data.month} ${data.today.monthValue}月", 0, 104, 360, 25, family = Serif)
        Label(data.today.year.toString(), 0, 145, 360, 15, weight = FontWeight.Bold, family = Serif)
        Label(data.day, 25, 155, 310, 183, weight = FontWeight.Bold, family = Serif)
        Label("好\n日\n曆", 17, 224, 18, 12, family = Serif)
        Label("G\nO\nO\nD\n \nD\nA\nY", 329, 215, 15, 8, spacing = 1f)
        Label(data.weekdayChinese, 0, 355, 360, 17, family = Serif)
        Label("宜 ${data.suitableText(1)}", 0, 399, 360, 30, weight = FontWeight.Bold, family = Serif)
        Rule(Modifier.offset(23.dp, 446.dp).width(314.dp))
        Label("你可以成為你想要成為的人，\n一切就先從相信自己開始。", 24, 470, 312, 13, weight = FontWeight.SemiBold, family = Serif)
        Label("把每個平凡的日子，過成喜歡的模樣。", 20, 532, 320, 9, family = Serif, spacing = 1f)
    }
}

@Composable
private fun BoxScope.VerseCalendar(data: CalendarData) {
    Sheet(Modifier.offset(10.dp, 13.dp).size(340.dp, 528.dp), Color(0xFFE4E4D5)) {
        Label(data.chineseMonth, 29, 45, 81, 21, weight = FontWeight.Light, align = TextAlign.Left)
        Label(data.monthShort.uppercase(Locale.ENGLISH), 29, 72, 81, 20, weight = FontWeight.Light, align = TextAlign.Left)
        Label(data.day, 21, 101, 113, 95, weight = FontWeight.ExtraLight)
        Label(data.weekdayChinese.toCharArray().joinToString("\n"), 28, 328, 26, 22, weight = FontWeight.Light)
        Label(data.weekday.take(3) + ".", 29, 414, 85, 21, weight = FontWeight.Light, align = TextAlign.Left)
        Label("慢慢來，日子自會給你答案。".toCharArray().joinToString("\n"), 171, 43, 25, 16, family = Serif)
        MonthGrid(data, Modifier.offset(229.dp, 43.dp).size(91.dp, 102.dp), small = true, color = Color(0xFF4D5146))
        Label(data.lunar.toCharArray().joinToString("\n"), 292, 175, 18, 14, family = Serif, color = Color(0xFF5C6054))
        Rule(Modifier.offset(27.dp, 461.dp).width(286.dp))
        Label("今日心语", 29, 478, 100, 12, family = Serif, align = TextAlign.Left, spacing = 2f)
        Label("从容", 242, 478, 70, 12, family = Serif, align = TextAlign.Right, spacing = 2f)
        Rule(Modifier.offset(27.dp, 506.dp).width(286.dp))
    }
}

@Composable
private fun BoxScope.AlmanacCalendar(data: CalendarData) {
    Sheet(Modifier.offset(8.dp, 8.dp).size(344.dp, 544.dp), Color(0xFF30312D)) {
        Box(Modifier.offset(13.dp, 13.dp).size(318.dp, 518.dp).background(Color(0xFFEAE1C9))) {
            Canvas(Modifier.fillMaxSize()) {
                // Fine printed-stock lines are drawn once per render, no bitmap blur or filtering.
                for (y in 0..259) drawLine(Color(0xFF877B5D).copy(alpha = .035f), Offset(0f, y * 2.dp.toPx()), Offset(size.width, y * 2.dp.toPx()), .45.dp.toPx())
            }
            Label(data.chineseMonth, 15, 16, 66, 17, family = Serif, align = TextAlign.Left)
            Label(data.weekdayChinese, 112, 16, 88, 17, family = Serif)
            Label("第 ${data.week} 周", 224, 20, 78, 12, family = Serif, align = TextAlign.Right)
            Label(data.stems, 12, 51, 294, 10, family = Serif, spacing = 1f)
            Rule(Modifier.offset(14.dp, 75.dp).width(290.dp))
            Box(Modifier.offset(16.dp, 94.dp).size(22.dp, 43.dp).border(.6.dp, Color(0xFF8F7954)), contentAlignment = Alignment.Center) {
                Print((data.solarTerm ?: "农历").toCharArray().joinToString("\n"), 11, color = Color(0xFF8F7954), family = Serif)
            }
            Label(data.day, 28, 72, 278, 178, Color(0xFF421E12), FontWeight.Bold, Serif)
            Label("${data.today.year} / ${data.today.monthValue.toString().padStart(2, '0')}", 18, 279, 150, 11, family = Serif, align = TextAlign.Left)
            Label("农历 ${data.lunar}" + if (data.festivals.isEmpty()) "" else " · ${data.festivals.joinToString(" · ")}",
                18, 299, 284, 10, family = Serif, align = TextAlign.Left)
            Rule(Modifier.offset(14.dp, 325.dp).width(290.dp))
            Column(Modifier.offset(18.dp, 342.dp).width(126.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Print("宜", 17, color = Color(0xFF637051), family = Serif)
                Print(data.suitableText(6), 10,
                    Modifier.padding(top = 9.dp), color = Color(0xFF777260), family = Serif)
                Print("忌", 17, Modifier.padding(top = 18.dp), color = Color(0xFF875C49), family = Serif)
                Print(data.avoidText(4), 10,
                    Modifier.padding(top = 9.dp), color = Color(0xFF777260), family = Serif)
            }
            Box(Modifier.offset(158.dp, 342.dp).size(.6.dp, 139.dp).background(Ink.copy(alpha = .15f)))
            Label("本 月 月 历", 174, 345, 126, 12, family = Serif)
            MonthGrid(data, Modifier.offset(173.dp, 369.dp).size(127.dp, 109.dp), small = true, color = Color(0xFF665D48))
            Label("岁时有序 · 日月成章", 0, 495, 318, 8, Color(0xFF8E856E), family = Serif, spacing = 2f)
        }
    }
}

@Composable
private fun BoxScope.HangingCalendar(data: CalendarData) {
    Canvas(Modifier.fillMaxSize()) {
        val rope = Path().apply {
            moveTo(108.dp.toPx(), 98.dp.toPx())
            quadraticTo(123.dp.toPx(), 25.dp.toPx(), 179.dp.toPx(), 17.dp.toPx())
            quadraticTo(220.dp.toPx(), 29.dp.toPx(), 251.dp.toPx(), 98.dp.toPx())
        }
        drawPath(rope, Color(0xFFFAFAF1), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color(0xFFB5B7AC), 3.dp.toPx(), Offset(179.dp.toPx(), 17.dp.toPx()))
    }
    Sheet(Modifier.offset(41.dp, 104.dp).size(282.dp, 409.dp), Color(0xFFD0D2CA)) {}
    Sheet(Modifier.offset(37.dp, 98.dp).size(282.dp, 409.dp), Color(0xFFFFFEF9)) {
        Box(Modifier.fillMaxWidth().height(34.dp).background(Color(0xFFDEDFD9)))
        Label(data.today.year.toString(), 16, 15, 65, 9, align = TextAlign.Left, spacing = 1f)
        Label(data.solarTerm ?: "日历", 104, 14, 74, 10, family = Serif, spacing = 2f)
        Label(data.today.monthValue.toString().padStart(2, '0'), 220, 15, 45, 9, align = TextAlign.Right, spacing = 1f)
        Label(data.day, 0, 51, 282, 141, weight = FontWeight.Black, family = FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)))
        Label(data.weekdayChinese, 0, 220, 282, 21, weight = FontWeight.Medium, spacing = 2f)
        Label(data.lunar + if (data.festivals.isEmpty()) "" else " · ${data.festivals.joinToString(" · ")}",
            9, 251, 264, 10, color = Color(0xFF75796E), family = Serif)
        Rule(Modifier.offset(19.dp, 283.dp).width(244.dp))
        Label("宜 ${data.suitableText(2)}  ｜  忌 ${data.avoidText(2)}", 10, 293, 262, 8, color = Color(0xFF767B70), family = Serif)
        Rule(Modifier.offset(19.dp, 314.dp).width(244.dp))
        Label("不慌不忙地生活，\n把每一天都过得有声有色。", 23, 345, 176, 10, color = Color(0xFF696F61), family = Serif, align = TextAlign.Left)
        CatSketch(Modifier.offset(210.dp, 342.dp).size(51.dp, 39.dp))
    }
    Box(Modifier.offset(24.dp, 83.dp).size(310.dp, 23.dp).shadow(3.dp).background(Color(0xFFF8F8EE)))
    Canvas(Modifier.offset(37.dp, 83.dp).size(282.dp, 27.dp)) {
        listOf(71f, 214f).forEach { x ->
            drawCircle(Ink, 3.dp.toPx(), Offset(x.dp.toPx(), 16.dp.toPx()))
            drawLine(Color(0xFFEAEADB), Offset(x.dp.toPx(), 0f), Offset(x.dp.toPx(), 16.dp.toPx()), 2.dp.toPx())
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
