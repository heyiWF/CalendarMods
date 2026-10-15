package com.calendarmods

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.min

private val PaperInk = Color(0xFF20231E)
private val Cream = Color(0xFFFFFEF9)
internal val LocalPaperInsets = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }

@Composable
fun ImmersiveCalendar(theme: CalendarTheme, data: CalendarData, onExit: () -> Unit, modifier: Modifier = Modifier, animateDay: Boolean = false) {
    val exit by rememberUpdatedState(onExit)
    val haptics = LocalHapticFeedback.current
    Box(modifier.fillMaxSize().testTag("immersive-${theme.name}")
        .semantics {
            testTagsAsResourceId = true
            contentDescription = "${theme.title}，${data.today}，${data.weekdayChinese}，农历${data.lunar}"
            onLongClick("返回主题选择") { exit(); true }
        }
        .pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                exit()
            })
        }
        .pointerInput(Unit) {
            // Consume motion after touch slop so a slow swipe cannot become a long press.
            detectDragGestures { change, _ -> change.consume() }
        }) {
        MidnightPaper(theme, data, animateDay)
    }
}

@Composable
internal fun CalendarPaper(theme: CalendarTheme, data: CalendarData, modifier: Modifier = Modifier) {
    val paper = Color(theme.surfaces.paper)
    val style = LocalThemeStyles.current[theme.id] ?: theme.defaultStyle()
    val fontId = theme.resolveFont(style.fontId)
    CompositionLocalProvider(LocalCalendarFont provides LocalThemeFonts.current[theme.id]?.get(fontId)) {
        Box(modifier.fillMaxSize().background(paper)) {
            // Paint to every edge, but keep printed content clear of camera cutouts.
            Box(Modifier.fillMaxSize().padding(LocalPaperInsets.current)) {
                FullPaper { wide ->
                    when (theme.renderer) {
                        ThemeRenderer.GOLDEN -> GoldenPaper(data, wide)
                        ThemeRenderer.TEDDY -> IllustrationPaper(theme, data, wide, photo = false)
                        ThemeRenderer.PHOTO -> IllustrationPaper(theme, data, wide, photo = true)
                        ThemeRenderer.GOOD_DAY -> GoodDayPaper(data, wide)
                        ThemeRenderer.VERSE -> VersePaper(data, wide)
                        ThemeRenderer.ALMANAC -> AlmanacPaper(data, wide)
                        ThemeRenderer.HANGING -> HangingPaper(data, wide)
                    }
                }
            }
        }
    }
}

/** Uniformly scale type and ornament, while reflowing panels to the actual screen ratio. */
@Composable
private fun FullPaper(content: @Composable (wide: Boolean) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val scale = if (wide) min(maxWidth.value / 640f, maxHeight.value / 360f)
            else min(maxWidth.value / 360f, maxHeight.value / 600f)
        val width = maxWidth / scale
        val height = maxHeight / scale
        Layout(content = { Box(Modifier.fillMaxSize()) { content(wide) } }) { measurables, constraints ->
            val page = measurables.single().measure(Constraints.fixed(width.roundToPx(), height.roundToPx()))
            layout(constraints.maxWidth, constraints.maxHeight) {
                page.placeWithLayer((constraints.maxWidth - page.width) / 2, (constraints.maxHeight - page.height) / 2) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

@Composable
private fun Panels(wide: Boolean, modifier: Modifier = Modifier, firstWeight: Float = .5f,
    first: @Composable BoxScope.() -> Unit, second: @Composable BoxScope.() -> Unit) {
    if (wide) Row(modifier) {
        Box(Modifier.weight(firstWeight).fillMaxHeight(), content = first)
        Box(Modifier.weight(1 - firstWeight).fillMaxHeight(), content = second)
    } else Column(modifier) {
        Box(Modifier.weight(firstWeight).fillMaxWidth(), content = first)
        Box(Modifier.weight(1 - firstWeight).fillMaxWidth(), content = second)
    }
}

@Composable
private fun BigDay(data: CalendarData, modifier: Modifier, family: FontFamily = FontFamily.SansSerif,
    weight: FontWeight = FontWeight.Bold, color: Color = PaperInk) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val fontSize = min(maxWidth.value * .72f, maxHeight.value * .9f) / LocalDensity.current.fontScale
        Text(data.day, color = color, fontSize = fontSize.sp, fontFamily = LocalCalendarFont.current ?: family, fontWeight = weight,
            maxLines = 1, softWrap = false, modifier = Modifier.testTag("immersive-day"),
            style = TextStyle(lineHeight = fontSize.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
    }
}

@Composable
private fun Festival(data: CalendarData) {
    if (data.festivals.isEmpty()) return
    Print(data.festivals.joinToString(" · "), 12,
        Modifier.border(.6.dp, PaperInk.copy(alpha = .6f)).padding(horizontal = 16.dp, vertical = 7.dp),
        family = FontFamily.Serif, maxLines = 2)
}

@Composable
private fun GoldenPaper(data: CalendarData, wide: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(76.dp).background(Color(0xFFF1C31C))) {
            Rings(Modifier.fillMaxWidth().height(19.dp).padding(horizontal = 14.dp), count = if (wide) 30 else 19)
            Print(data.weekday.uppercase(Locale.ENGLISH), 27,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp), Color.White, FontWeight.Bold, spacing = 1f)
        }
        if (wide) Row(Modifier.weight(1f).padding(22.dp)) {
            Column(Modifier.weight(.56f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Print(data.month.uppercase(Locale.ENGLISH), 22, weight = FontWeight.Bold)
                BigDay(data, Modifier.weight(1f).fillMaxWidth(), weight = FontWeight.ExtraBold)
                Print(data.today.year.toString(), 22)
            }
            Column(Modifier.weight(.44f).fillMaxHeight().padding(start = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Print(data.lunar, 16, family = FontFamily.Serif)
                MonthGrid(data, Modifier.weight(1f).fillMaxWidth().padding(vertical = 13.dp))
                Print("WEEK ${data.week}  ·  DAY ${data.today.dayOfYear}", 11, spacing = 1f)
            }
        } else Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 23.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Print(data.month.uppercase(Locale.ENGLISH), 30, weight = FontWeight.Bold)
            BigDay(data, Modifier.weight(1f).fillMaxWidth(), weight = FontWeight.ExtraBold)
            Print(data.today.year.toString(), 32, weight = FontWeight.Medium)
            Print("WEEK ${data.week}  ·  DAY ${data.today.dayOfYear}", 12, Modifier.padding(top = 15.dp), spacing = 1f)
            Rule(Modifier.fillMaxWidth().padding(top = 24.dp))
            Print("${data.lunar} · ${data.weekdayChinese}", 13, Modifier.padding(top = 16.dp), spacing = 1f)
        }
    }
}

@Composable
private fun IllustrationPaper(theme: CalendarTheme, data: CalendarData, wide: Boolean, photo: Boolean) {
    Box(Modifier.fillMaxSize().then(if (photo) Modifier.padding(8.dp) else Modifier).background(Cream)) {
        Panels(wide, Modifier.fillMaxSize().padding(top = 12.dp), firstWeight = if (photo) .56f else .44f,
            first = {
                ThemeImage(theme, Modifier.fillMaxSize())
            }, second = {
                Column(Modifier.fillMaxSize().padding(if (wide) 20.dp else 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (photo) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            Print(data.today.year.toString(), 21, weight = FontWeight.Light)
                            Print(data.today.monthValue.toString().padStart(2, '0'), 38, color = Color(0xFF8F8C84), weight = FontWeight.ExtraBold)
                        }
                    } else {
                        Print(data.chineseMonth, 25, weight = FontWeight.Bold)
                        Print("${data.today.year} · ${data.motto}", 10, Modifier.padding(top = 5.dp), maxLines = 2)
                    }
                    MonthGrid(data, Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp), lunar = !photo, outlined = !photo)
                    if (photo) Print(data.motto, 10, Modifier.padding(top = 8.dp), maxLines = 2)
                }
            })
        Rings(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 12.dp), dark = photo, count = if (wide) 30 else 19)
    }
}

@Composable
private fun GoodDayPaper(data: CalendarData, wide: Boolean) {
    if (wide) Row(Modifier.fillMaxSize().padding(25.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(.55f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Print("${data.month} ${data.chineseMonth}", 22, family = FontFamily.Serif)
            BigDay(data, Modifier.weight(1f).fillMaxWidth(), FontFamily.Serif)
            Print(data.weekdayChinese, 17, family = FontFamily.Serif)
        }
        Column(Modifier.weight(.45f).padding(start = 26.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Print("${data.today.year} · ${data.lunar}", 13, family = FontFamily.Serif)
            Festival(data)
            CenteredGoodDayCarousel(data.recommends, 30, Modifier.fillMaxWidth())
            Rule(Modifier.fillMaxWidth())
            Print(data.motto, 13, weight = FontWeight.SemiBold, family = FontFamily.Serif)
        }
    } else Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Print(data.lunar, 14, family = FontFamily.Serif, spacing = 2f)
        Spacer(Modifier.height(14.dp))
        Festival(data)
        Print("${data.month} ${data.chineseMonth}", 25, Modifier.padding(top = 18.dp), family = FontFamily.Serif)
        Print(data.today.year.toString(), 15, Modifier.padding(top = 10.dp), weight = FontWeight.Bold, family = FontFamily.Serif)
        BigDay(data, Modifier.weight(1f).fillMaxWidth(), FontFamily.Serif)
        Print(data.weekdayChinese, 17, family = FontFamily.Serif)
        CenteredGoodDayCarousel(data.recommends, 32, Modifier.fillMaxWidth().padding(top = 18.dp))
        Rule(Modifier.fillMaxWidth().padding(top = 18.dp))
        Print(data.motto, 13, Modifier.padding(top = 20.dp), weight = FontWeight.SemiBold, family = FontFamily.Serif)
        Print("把每個平凡的日子，過成喜歡的模樣。", 9, Modifier.padding(top = 24.dp), family = FontFamily.Serif, spacing = 1f)
    }
}

@Composable
private fun VersePaper(data: CalendarData, wide: Boolean) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = if (wide) 3.dp else 26.dp)) {
            Column(Modifier.weight(if (wide) .4f else .43f).fillMaxHeight(), horizontalAlignment = Alignment.Start) {
                Print(data.chineseMonth, 22, weight = FontWeight.Light)
                Print(data.monthShort.uppercase(Locale.ENGLISH), 20, weight = FontWeight.Light)
                BigDay(data, Modifier.fillMaxWidth().weight(1f), weight = FontWeight.ExtraLight)
                Print(data.weekdayChinese.toCharArray().joinToString("\n"), if (wide) 17 else 23, weight = FontWeight.Light)
                Print(data.weekday.take(3) + ".", 19, Modifier.padding(top = 12.dp), weight = FontWeight.Light)
            }
            BoxWithConstraints(Modifier.weight(if (wide) .22f else .24f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val size = min(if (wide) 15f else 17f,
                    maxHeight.value / (data.motto.length * 1.17f * LocalDensity.current.fontScale)).toInt().coerceAtLeast(1)
                Print(data.motto.toCharArray().joinToString("\n"), size, family = FontFamily.Serif)
            }
            Column(Modifier.weight(if (wide) .38f else .33f).fillMaxHeight(), horizontalAlignment = Alignment.End) {
                MonthGrid(data, Modifier.fillMaxWidth().height(if (wide) 172.dp else 117.dp), small = !wide, color = Color(0xFF4D5146))
                Print(if (wide) data.lunar else data.lunar.toCharArray().joinToString("\n"), 14,
                    Modifier.padding(top = 24.dp), family = FontFamily.Serif)
            }
        }
        Rule(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Print("今日心语", 12, family = FontFamily.Serif, spacing = 2f)
            Print(data.heartWord, 12, family = FontFamily.Serif, spacing = 2f)
        }
        Rule(Modifier.fillMaxWidth())
    }
}

@Composable
private fun AlmanacPaper(data: CalendarData, wide: Boolean) {
    Box(Modifier.fillMaxSize().padding(8.dp).background(Color(0xFFEAE1C9))) {
        Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(Color(0xFF877B5D).copy(alpha = .035f), Offset(0f, y), Offset(size.width, y), .45.dp.toPx())
                y += 2.dp.toPx()
            }
        }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Print(data.chineseMonth, 16, Modifier.weight(1f), family = FontFamily.Serif, align = androidx.compose.ui.text.style.TextAlign.Start)
                Print(data.weekdayChinese, 16, Modifier.weight(1f), family = FontFamily.Serif)
                Print("第 ${data.week} 周", 16, Modifier.weight(1f), family = FontFamily.Serif, align = androidx.compose.ui.text.style.TextAlign.End)
            }
            Print(data.stems, 10, Modifier.fillMaxWidth().padding(vertical = 13.dp), family = FontFamily.Serif, spacing = 1f)
            Rule(Modifier.fillMaxWidth())
            Panels(wide, Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), firstWeight = if (wide) .49f else .48f,
                first = {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BigDay(data, Modifier.weight(1f).fillMaxWidth(), FontFamily.Serif, color = Color(0xFF421E12))
                        Print("${data.today.year} / ${data.today.monthValue.toString().padStart(2, '0')} · 农历${data.lunar}", 11, family = FontFamily.Serif)
                        Box(Modifier.padding(top = 7.dp, bottom = 10.dp)) { Festival(data) }
                    }
                }, second = {
                    Column(Modifier.fillMaxSize().padding(start = if (wide) 20.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!wide) Rule(Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Column(Modifier.weight(1f).padding(end = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Print("宜", 20, color = Color(0xFF637051), family = FontFamily.Serif)
                                Print(data.suitableText(Int.MAX_VALUE), 10, Modifier.padding(top = 6.dp), color = Color(0xFF777260), family = FontFamily.Serif)
                            }
                            Column(Modifier.weight(1f).padding(start = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Print("忌", 20, color = Color(0xFF875C49), family = FontFamily.Serif)
                                Print(data.avoidText(Int.MAX_VALUE), 10, Modifier.padding(top = 6.dp), color = Color(0xFF777260), family = FontFamily.Serif)
                            }
                        }
                        MonthGrid(data, Modifier.fillMaxWidth().weight(1f).padding(top = 13.dp), color = Color(0xFF665D48))
                    }
                })
            Print("岁时有序 · 日月成章", 8, Modifier.fillMaxWidth().padding(top = 12.dp), Color(0xFF8E856E), family = FontFamily.Serif, spacing = 2f)
        }
    }
}

@Composable
private fun HangingPaper(data: CalendarData, wide: Boolean) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(52.dp).background(Color(0xFFDEDFD9))) {
            Rings(Modifier.fillMaxWidth().height(18.dp), count = 2)
            Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 23.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Print("${data.today.year}年", 11, spacing = 1f)
                Print("${data.today.monthValue}月", 11, spacing = 1f)
            }
        }
        Panels(wide, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), firstWeight = if (wide) .53f else .57f,
            first = {
                BigDay(data, Modifier.fillMaxSize(), FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)), FontWeight.Black)
            }, second = {
                Column(Modifier.fillMaxSize().padding(start = if (wide) 22.dp else 0.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly) {
                    Print(data.weekdayChinese, 24, weight = FontWeight.Medium, spacing = 2f)
                    Print(data.lunar + if (data.festivals.isEmpty()) "" else " · ${data.festivals.joinToString(" · ")}", 12, color = Color(0xFF75796E), family = FontFamily.Serif)
                    Rule(Modifier.fillMaxWidth())
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlmanacCarousel("宜", data.recommends, 2, 11, Modifier.fillMaxWidth())
                        AlmanacCarousel("忌", data.avoids, 2, 11, Modifier.fillMaxWidth())
                    }
                    Rule(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Print(data.motto, 10, Modifier.weight(1f), color = Color(0xFF696F61), family = FontFamily.Serif)
                        CatSketch(Modifier.size(46.dp, 37.dp))
                    }
                }
            })
    }
}
