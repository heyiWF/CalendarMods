package com.calendarmods

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A vector printing plate: crisp at thumbnail and full-screen sizes, no screenshot baked in. */
@Composable
internal fun TraditionalPaper(theme: CalendarTheme, data: CalendarData, wide: Boolean) {
    val repository = LocalAlmanacRepository.current
    val almanac = remember(data.today, repository) { repository.day(data.today) }
    val style = LocalThemeStyles.current[theme.id] ?: theme.defaultStyle()
    val environment = LocalAlmanacEnvironment.current
    val resolvedPalette = almanacPalette(theme, style, environment.minute, environment.ambientDark)
    val webRed = theme.renderer == ThemeRenderer.PEITAIYI && style.ink == AlmanacInk.DEFAULT &&
        (data.today.dayOfWeek.value >= 6 || (style.festive && PeitaiyiArt.feature(data, almanac)?.second?.startsWith("festivals_") == true))
    val palette = if (webRed) resolvedPalette.copy(ink = if (resolvedPalette.dark) AlmanacInk.RED.night else 0xFFB5372A) else resolvedPalette
    val festival = if (style.festive) almanac.festivalEdition else null
    val context = LocalContext.current
    val icons = remember(context) { AlmanacIcons(context) }
    val webArt = remember(context, theme.renderer) { if (theme.renderer == ThemeRenderer.PEITAIYI) PeitaiyiArtwork(context) else null }
    val resolver = LocalFontFamilyResolver.current
    val family = LocalCalendarFont.current
    val regular = resolver.resolve(family ?: FontFamily.Serif).value as Typeface
    val bold = resolver.resolve(family ?: FontFamily.SansSerif, FontWeight.Black).value as Typeface
    val painter = remember(theme, data, almanac, regular, bold, palette, festival, icons, webArt, style.festive) {
        TongShengPlate(data, almanac, Color(palette.ink).toArgb(), Color(palette.paper).toArgb(), regular, bold,
            theme.renderer, icons, festival, webArt, style.festive)
    }
    Canvas(Modifier.fillMaxSize().testTag("traditional-fields").semantics {
        contentDescription = "${theme.title}，${if (palette.dark) "深色" else "浅色"}，${festival?.title.orEmpty()}，${almanac.accessibility}"
    }) {
        val canvas = drawContext.canvas.nativeCanvas
        val checkpoint = canvas.save()
        try {
            val designWidth = if (wide) 2100f else 1200f
            val scale = if (wide) size.width / designWidth else min(size.width / designWidth, size.height / 1600f)
            val height = size.height / scale
            canvas.translate((size.width - designWidth * scale) / 2, 0f)
            canvas.scale(scale, scale)
            painter.draw(canvas, height, wide)
        } finally { canvas.restoreToCount(checkpoint) }
    }
}

internal class TongShengPlate(
    val data: CalendarData, val a: AlmanacDay, val ink: Int, val paper: Int,
    val regular: Typeface, val bold: Typeface,
    val variant: ThemeRenderer, val icons: AlmanacIcons, val festival: FestiveDay?,
    val webArt: PeitaiyiArtwork?, val festiveEnabled: Boolean,
) {
    internal val p = Paint(Paint.ANTI_ALIAS_FLAG)
    internal lateinit var c: Canvas
    internal var verticalStretch = 1f
    internal inline fun unstretched(centerY: Float, block: () -> Unit) {
        val before = verticalStretch
        c.save(); c.translate(0f, centerY); c.scale(1f, 1f / before); c.translate(0f, -centerY)
        verticalStretch = 1f
        try { block() } finally { verticalStretch = before; c.restore() }
    }
    internal fun line(x: Float, y: Float, x2: Float, y2: Float, width: Float = 2f) {
        p.color = ink; p.strokeWidth = width; p.style = Paint.Style.STROKE
        c.drawLine(x, y, x2, y2, p); p.style = Paint.Style.FILL
    }
    internal fun box(x: Float, y: Float, w: Float, h: Float, filled: Boolean = false, width: Float = 2f) {
        p.color = ink; p.strokeWidth = width; p.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        c.drawRect(x, y, x + w, y + h, p); p.style = Paint.Style.FILL
    }
    /** Fits every line; no ellipsis and no truncation of long yi/ji or deity lists. */
    internal fun text(value: String, x: Float, y: Float, w: Float, h: Float, size: Float,
        heavy: Boolean = false, inverse: Boolean = false, left: Boolean = false, top: Boolean = false) {
        p.typeface = if (heavy) bold else regular; p.textSize = size
        p.color = if (inverse) paper else ink; p.style = Paint.Style.FILL
        val lines = value.split('\n')
        val widest = lines.maxOfOrNull { p.measureText(it) }?.coerceAtLeast(1f) ?: 1f
        val sample = lines.joinToString("").ifEmpty { "国" }
        val bounds = Rect()
        p.getTextBounds(sample, 0, sample.length, bounds)
        p.textSize *= min(1f, min((w - 8).coerceAtLeast(1f) / widest,
            h * min(1f, verticalStretch) / (bounds.height().coerceAtLeast(1) + size * 1.22f * (lines.size - 1))))
        p.getTextBounds(sample, 0, sample.length, bounds)
        val step = p.textSize * 1.22f
        // Optical glyph bounds, rather than font ascent padding, keep Chinese labels centred.
        var baseline = y + (if (top) 0f else (h - bounds.height() - step * (lines.size - 1)) / 2) - bounds.top
        unstretched(y + h / 2) {
            lines.forEach {
                c.drawText(it, if (left) x + 4 else x + (w - p.measureText(it)) / 2, baseline, p)
                baseline += step
            }
        }
    }
    /** One numeral size per plate: a two-digit day sets it, so 3 and 28 print at the same size. */
    internal fun daySize(maxWidth: Float, maxHeight: Float): Float {
        p.typeface = Typeface.create(bold, 1000, false); p.textSize = 1000f
        val reference = Rect(); p.getTextBounds("28", 0, 2, reference)
        return 1000f * min(maxWidth / reference.width().toFloat().coerceAtLeast(1f),
            maxHeight / reference.height().toFloat().coerceAtLeast(1f))
    }
    /** Prints the day at the shared size, optically centred in its box; no leading zeroes. */
    internal fun dayNumber(day: String, centerX: Float, top: Float, height: Float, maxWidth: Float) {
        p.typeface = Typeface.create(bold, 1000, false); p.textSize = daySize(maxWidth, height); p.color = ink
        val bounds = Rect(); p.getTextBounds(day, 0, day.length, bounds)
        c.drawText(day, centerX - bounds.exactCenterX(), top + (height - bounds.height()) / 2 - bounds.top, p)
    }
    internal fun words(items: List<String>, x: Float, y: Float, w: Float, h: Float, size: Float = 32f, top: Boolean = false) {
        val values = items.ifEmpty { listOf("无") }
        p.typeface = regular
        var actual = size
        var lines: List<String>
        while (true) {
            p.textSize = actual
            val wrapped = mutableListOf<String>()
            var current = ""
            values.forEach { word ->
                val next = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && p.measureText(next) > w - 12) { wrapped += current; current = word }
                else current = next
            }
            if (current.isNotEmpty()) wrapped += current
            lines = wrapped
            if (lines.size * actual * 1.22f <= h || actual <= 8f) break
            actual -= .5f
        }
        text(lines.joinToString("\n"), x, y, w, h, actual, top = top)
    }
    internal fun label(value: String, x: Float, y: Float, w: Float, size: Float = 28f) {
        p.color = ink
        c.drawRoundRect(x, y, x + w, y + 36, 9f, 9f, p)
        text(value, x, y, w, 36f, size, heavy = true, inverse = true)
    }
    private fun vertical(value: String, x: Float, y: Float, w: Float, h: Float, size: Float = 28f) {
        val chars = value.filterNot { it.isWhitespace() }.toList()
        val maxRows = (h / (size * 1.13f)).toInt().coerceAtLeast(1)
        val columns = ((chars.size + maxRows - 1) / maxRows).coerceAtLeast(1)
        // Balance the columns so a final character or punctuation never stands alone.
        val rows = ((chars.size + columns - 1) / columns).coerceAtLeast(1)
        val actual = min(size, w / (columns * 1.12f))
        chars.chunked(rows).forEachIndexed { col, chunk ->
            chunk.forEachIndexed { row, ch ->
                text(ch.toString(), x + w - (col + 1) * actual * 1.12f, y + row * actual * 1.13f,
                    actual * 1.1f, actual * 1.2f, actual)
            }
        }
    }
    /** Evenly spaced columns share the same top and bottom regardless of text length. */
    internal fun verticalFitted(value: String, x: Float, y: Float, w: Float, h: Float, size: Float) {
        val chars = value.filterNot { it.isWhitespace() }.toList()
        if (chars.isEmpty()) return
        val maxRows = (h / (size * 1.15f)).toInt().coerceAtLeast(1)
        val columns = (chars.size + maxRows - 1) / maxRows
        val rows = (chars.size + columns - 1) / columns
        val actual = min(size, w / (columns * 1.2f))
        chars.chunked(rows).forEachIndexed { col, chunk ->
            chunk.forEachIndexed { row, ch ->
                val yy = if (chunk.size == 1) (h - actual) / 2 else row * (h - actual) / (chunk.size - 1)
                text(ch.toString(), x + w - (col + 1) * w / columns, y + yy, w / columns, actual, actual)
            }
        }
    }
    fun draw(canvas: Canvas, height: Float, wide: Boolean) {
        c = canvas
        // Fine, deterministic paper fibres; they never obscure the printed information.
        p.color = android.graphics.Color.argb(10, 93, 78, 39)
        repeat(520) { i ->
            val x = (i * 67 % (if (wide) 2100 else 1200)).toFloat()
            val y = (i * 193 % height.toInt()).toFloat()
            c.drawRect(x, y, x + 1, y + 5, p)
        }
        if (variant == ThemeRenderer.PEITAIYI) {
            webPage(height, wide)
        } else if (variant != ThemeRenderer.TRADITIONAL) {
            redPage(height, wide, variant == ThemeRenderer.AUSPICIOUS)
        } else if (wide) {
            landscape(height, 830f, { bottom -> heading(); hero(bottom) }, { grid() })
        } else {
            heading()
            val gridY = height - 830f
            hero(gridY)
            c.save(); c.translate(0f, gridY); grid(); c.restore()
        }
    }
    private fun heading() {
        box(32f, 32f, 1136f, 78f, width = 3f)
        box(468f, 22f, 265f, 94f, width = 3f)
        p.color = paper; c.drawRect(472f, 26f, 729f, 112f, p)
        text(data.month.uppercase(Locale.ENGLISH), 80f, 30f, 355f, 80f, 60f, true)
        text(data.today.year.toString(), 475f, 12f, 250f, 112f, 92f, true)
        text("${data.chineseMonth} ${if (data.today.lengthOfMonth() == 31) "大" else "小"}", 765f, 30f, 350f, 80f, 58f, true)
        listOf(42f, 1127f).forEach { x ->
            box(x, 42f, 27f, 21f); box(x, 80f, 27f, 21f)
            line(x + 7, 48f, x + 20, 48f); line(x + 7, 48f, x + 7, 57f)
            line(x + 7, 86f, x + 20, 86f); line(x + 7, 86f, x + 7, 95f)
        }
    }
    private fun hero(bottom: Float) {
        text("${a.hijriYear}\n${a.hijriMonth}", 36f, 121f, 245f, 87f, 35f, true, left = true)
        val malay = listOf("Isnin", "Selasa", "Rabu", "Khamis", "Jumaat", "Sabtu", "Ahad")[data.today.dayOfWeek.value - 1]
        text("$malay\n${a.hijriDay}hb", 958f, 121f, 210f, 87f, 34f, true)
        val heroHeight = bottom - 218f - if (festival == null) 0f else 105f
        dayNumber(data.today.dayOfMonth.toString(), 600f, 225f, heroHeight - 28f, 760f)
        text("风\n水", 37f, 230f, 75f, 98f, 42f, true)
        vertical(a.editorial.fengShui, 36f, 340f, 100f, (bottom - 420).coerceAtLeast(200f), 27f)
        vertical(a.editorial.observances, 146f, (bottom - 235).coerceAtLeast(350f), 100f, 205f, 27f)
        vertical("诸葛武侯择日：${a.editorial.zhuge}", 1001f, 239f, 165f, heroHeight - 20f, 28f)
        festival?.let { festiveBanner(270f, bottom - 108f, 660f, 88f, it) }
    }
    private fun grid() {
        // Column geometry mirrors the original: zodiac | left panels | five elements | right panels | numbers.
        text("【运程·编选】 ${a.editorial.fortune}", 28f, 0f, 1144f, 44f, 24f)
        box(28f, 46f, 1144f, 718f, width = 4f)
        listOf(162f, 478f, 726f, 1036f).forEach { line(it, 46f, it, 764f, 3f) }
        line(28f, 170f, 1172f, 170f, 3f)
        box(166f, 50f, 308f, 116f, true)
        text(a.lunarDay, 171f, 48f, 298f, 84f, 70f, true, true)
        text(a.lunarHeading, 171f, 132f, 298f, 28f, 26f, inverse = true)
        text(a.termNotice, 482f, 49f, 240f, 110f, 53f, true)
        box(730f, 50f, 302f, 116f, true)
        text(data.weekdayChinese, 735f, 50f, 292f, 80f, 62f, true, true)
        text(data.weekday.uppercase(Locale.ENGLISH), 735f, 130f, 292f, 30f, 26f, true, true)
        animal(dayAnimal, 62f, 52f, 75f)
        text("${dayAnimal}日", 32f, 132f, 126f, 29f, 26f)
        zodiacIcon(signIndex, 1071f, 52f, 74f)
        text(WesternZodiac.names[signIndex], 1040f, 132f, 128f, 29f, 25f)
        scroll(36f, "吉祥生肖", a.luckyZodiacs)
        scroll(1044f, "今日吉数", a.editorial.luckyNumbers)
        // Left: all yi/ji, directions, zodiac relations, a daily cultural excerpt.
        yiJi(169f, 181f, 304f, 180f)
        line(170f, 370f, 470f, 370f)
        line(353f, 378f, 353f, 605f)
        label("吉祥方位", 177f, 381f, 167f)
        text(a.directions.joinToString("\n"), 170f, 424f, 176f, 175f, 32f, true)
        text("三合${a.threeHarmony}", 357f, 380f, 116f, 40f, 25f)
        animal(dayAnimal, 369f, 432f, 92f)
        text(a.clash.replace(" ", "\n"), 358f, 537f, 114f, 64f, 24f)
        line(170f, 610f, 470f, 610f)
        text("贵人时 ${a.nobleBranches}", 170f, 613f, 300f, 39f, 29f)
        line(170f, 658f, 470f, 658f)
        label("周公解梦·编选", 208f, 667f, 220f, 23f)
        text(a.editorial.dream, 170f, 709f, 300f, 48f, 25f)
        // Centre: the dense typographic spine and the nine-palace compass.
        val rows = listOf(Triple("天干", a.stem, "属${a.stemElement}"), Triple("地支", a.branch, "属${a.branchElement}"),
            Triple("纳音", a.sound.dropLast(1), a.sound.takeLast(1)), Triple("廿八宿", a.mansion, "宿"), Triple("十二神", a.duty, "日"))
        rows.forEachIndexed { i, (title, value, tail) ->
            val y = 177f + i * 60
            text(title, 483f, y, 78f, 54f, 29f)
            text(value, 561f, y, 99f, 54f, if (value.length == 1) 53f else 31f, true)
            text(tail, 663f, y, 58f, 54f, 29f)
            line(488f, y + 58, 716f, y + 58)
        }
        text(a.nineStar, 486f, 484f, 232f, 43f, 27f, true)
        compass(602f, 644f)
        // Right: deity list, explicitly midnight pillars, hours, day fortunes and fetus.
        line(871f, 180f, 871f, 355f)
        label("吉星", 746f, 183f, 103f); label("今日八字", 885f, 183f, 138f)
        words(a.goodGods, 733f, 229f, 130f, 119f, 30f)
        pillars(878f, 228f, 150f, 110f)
        line(734f, 359f, 1028f, 359f)
        label("时辰吉凶", 790f, 370f, 186f)
        a.hours.forEachIndexed { i, hour ->
            val x = 734f + i * 24.4f
            text(hour.branch, x, 416f, 25f, 29f, 26f)
            text(hour.start + "\n" + hour.end, x, 449f, 25f, 41f, 16f)
            text(hour.luck, x, 494f, 25f, 28f, 25f)
        }
        line(734f, 530f, 1028f, 530f)
        label("日禄", 744f, 542f, 103f); label("择日须知", 874f, 542f, 149f)
        line(861f, 541f, 861f, 694f)
        text(a.dayLu, 733f, 585f, 121f, 57f, 26f)
        text("凶煞 " + a.badGods.chunked(3).joinToString("\n") { it.joinToString(" ") }, 733f, 646f, 121f, 43f, 19f)
        text(a.pengZu.replace(" ", "\n"), 868f, 586f, 160f, 103f, 27f)
        line(734f, 698f, 1028f, 698f)
        text("胎神  ${a.fetus}", 734f, 707f, 294f, 49f, 26f)
        box(28f, 769f, 1144f, 40f, true)
        text("六合：${a.sixHarmony} ${a.editorial.zodiacNumbers}", 35f, 769f, 443f, 39f, 25f, inverse = true)
        text("南 洋 择 吉 通 胜", 478f, 769f, 248f, 39f, 24f, true, true)
        text("岁时：${a.seasonalNote}", 730f, 769f, 436f, 39f, 23f, inverse = true)
        text("民俗参考 · 吉肖吉数为本版规则 · 文案编选 · 子时八字", 100f, 812f, 1000f, 18f, 15f)
    }
    private fun scroll(x: Float, title: String, values: List<String>) {
        box(x, 181f, 120f, 573f, width = 3f)
        listOf(179f, 745f).forEach { y ->
            box(x - 8, y, 136f, 13f, true)
            box(x - 12, y - 5, 6f, 23f); box(x + 126, y - 5, 6f, 23f)
        }
        text(title.toList().joinToString("\n"), x + 23, 204f, 74f, 153f, 34f)
        values.ifEmpty { listOf("待", "校", "准") }.forEachIndexed { i, value ->
            if (title == "吉祥生肖") {
                animal(value, x + 16, 365f + i * 61, 54f)
                text(value, x + 74, 365f + i * 61, 34f, 54f, 25f)
            } else text(value, x + 10, 365f + i * 61, 100f, 54f, 43f, true)
        }
    }
    internal fun compass(cx: Float, cy: Float) {
        val shrink = min(1f, verticalStretch)
        unstretched(cy) {
            c.save(); c.scale(shrink, shrink, cx, cy); compassArtwork(cx, cy); c.restore()
        }
    }
    private fun compassArtwork(cx: Float, cy: Float) {
        val path = Path()
        repeat(8) { i ->
            val t = Math.toRadians((22.5 + i * 45))
            val x = cx + cos(t).toFloat() * 116; val y = cy + sin(t).toFloat() * 116
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }; path.close()
        p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 3f; c.drawPath(path, p)
        c.drawCircle(cx, cy, 65f, p); p.style = Paint.Style.FILL
        val bagua = listOf("坎 北", "艮 东北", "震 东", "巽 东南", "离 南", "坤 西南", "兑 西", "乾 西北")
        val trigrams = listOf("010", "100", "001", "110", "101", "000", "011", "111")
        bagua.forEachIndexed { i, name ->
            c.save(); c.rotate(i * 45f, cx, cy)
            text(name, cx - 46, cy - 107, 92f, 27f, 22f, true)
            repeat(3) { n ->
                val y = cy - 76 + n * 4
                if (trigrams[i][n] == '1') line(cx - 22, y, cx + 22, y, 2f)
                else { line(cx - 22, y, cx - 4, y, 2f); line(cx + 4, y, cx + 22, y, 2f) }
            }
            c.restore()
        }
        // North-up compass: flip the south-up Luo Shu plate 180 degrees.
        a.ninePalace.reversed().forEachIndexed { i, n -> text(n.toString(), cx - 54 + i % 3 * 36,
            cy - 54 + i / 3 * 36, 36f, 36f, 30f, true) }
    }
    private val dayAnimal get() = a.dayZodiac
    private val signIndex get() = a.westernZodiac
    private fun animal(name: String, x: Float, y: Float, size: Float) {
        val actual = size * min(1f, verticalStretch)
        unstretched(y + size / 2) { icons.animal(c, name, x + (size - actual) / 2, y + (size - actual) / 2, actual, ink) }
    }
    private fun zodiacIcon(index: Int, x: Float, y: Float, size: Float) {
        val actual = size * min(1f, verticalStretch)
        unstretched(y + size / 2) { icons.sign(c, index, x + (size - actual) / 2, y + (size - actual) / 2, actual, ink) }
    }
    private fun yiJi(x: Float, y: Float, w: Float, h: Float) {
        val count = (a.recommends.sumOf { it.length } + a.avoids.sumOf { it.length }).coerceAtLeast(1)
        val first = h * (a.recommends.sumOf { it.length }.toFloat() / count).coerceIn(.32f, .68f)
        label("宜", x, y + (first - 36) / 2, 40f, 29f)
        words(a.recommends, x + 46, y + 2, w - 46, first - 6, 32f)
        line(x + 46, y + first, x + w, y + first)
        label("忌", x, y + first + (h - first - 36) / 2, 40f, 29f)
        words(a.avoids, x + 46, y + first + 4, w - 46, h - first - 8, 32f)
    }
    internal fun pillars(x: Float, y: Float, w: Float, h: Float) {
        val cell = w / 4
        listOf("年", "月", "日", "子时").forEachIndexed { i, title ->
            text(title, x + i * cell, y, cell, h * .26f, 24f)
            text(a.pillars[i].take(1), x + i * cell, y + h * .28f, cell, h * .34f, 34f)
            text(a.pillars[i].takeLast(1), x + i * cell, y + h * .64f, cell, h * .34f, 34f)
        }
    }
    private fun redPage(height: Float, wide: Boolean, classic: Boolean) {
        if (wide) {
            landscape(height, 900f, { bottom -> redHero(bottom, classic) }, {
                if (classic) classicGrid() else cantonGrid()
            })
        } else {
            redHero(height - 900f, classic)
            c.save(); c.translate(0f, height - 900f)
            if (classic) classicGrid() else cantonGrid()
            c.restore()
        }
    }
    internal fun landscape(height: Float, gridHeight: Float, hero: (Float) -> Unit, table: () -> Unit) {
        val margin = 20f
        val leftWidth = 852f
        val rightWidth = 1188f
        val leftScale = leftWidth / 1200f
        val rightScale = rightWidth / 1200f
        val usable = height - margin * 2
        c.save(); c.translate(margin, margin); c.scale(leftScale, leftScale)
        hero(usable / leftScale)
        c.restore()
        c.save(); c.translate(margin + leftWidth + 20f, margin)
        verticalStretch = usable / (gridHeight * rightScale)
        c.scale(rightScale, rightScale * verticalStretch)
        table()
        verticalStretch = 1f
        c.restore()
    }
    private fun redHero(bottom: Float, classic: Boolean) {
        val sideTop = (bottom * .56f).coerceAtLeast(430f)
        if (classic) {
            text(data.today.year.toString(), 74f, 35f, 330f, 94f, 80f, true)
            text("通 胜", 488f, 44f, 224f, 68f, 39f)
            box(488f, 44f, 224f, 68f, width = 3f)
            text(data.month.take(3).uppercase(Locale.ENGLISH), 790f, 35f, 330f, 94f, 81f, true)
            vertical("${data.chineseMonth}${if (data.today.lengthOfMonth() == 31) "大" else "小"}", 82f, 195f, 90f, sideTop - 215f, 72f)
            vertical(data.weekdayChinese, 1020f, 195f, 90f, sideTop - 215f, 66f)
            text("${a.hijriYear} ${a.hijriMonth} ${a.hijriDay}日", 240f, 135f, 720f, 34f, 27f)
        } else {
            text(data.today.year.toString(), 42f, 30f, 260f, 80f, 66f, true)
            text(data.month.uppercase(Locale.ENGLISH), 305f, 30f, 590f, 80f, 63f, true)
            text(data.chineseMonth, 917f, 30f, 240f, 80f, 63f, true)
            line(42f, 120f, 1158f, 120f)
            text("${a.hijriMonth.uppercase(Locale.ENGLISH)}\n回历 ${a.hijriYear}", 40f, 145f, 235f, 80f, 29f)
            text("${data.weekday}\n${a.hijriDay}hb", 940f, 145f, 220f, 80f, 32f)
            verticalFitted("福水长流幸福家", 54f, 270f, 90f, sideTop - 310f, 41f)
            verticalFitted("财源广进平安宅", 1050f, 270f, 90f, sideTop - 310f, 41f)
        }
        val top = if (classic) 205f else 230f
        val dayBottom = bottom - if (festival == null) 60f else 155f
        dayNumber(data.today.dayOfMonth.toString(), 600f, top, (dayBottom - top).coerceAtLeast(220f), 760f)
        verticalFitted(a.editorial.fengShui, 35f, sideTop, 150f, bottom - sideTop - 24, 23f)
        verticalFitted("诸葛择日：${a.editorial.zhuge}", 1015f, sideTop, 150f, bottom - sideTop - 24, 23f)
        festival?.let { festiveBanner(250f, bottom - 143f, 700f, 84f, it) }
        text(a.editorial.observances, 230f, bottom - 47f, 740f, 36f, 27f)
    }
    internal fun hours(x: Float, y: Float, w: Float) {
        a.hours.forEachIndexed { i, hour ->
            val cell = w / 12; val left = x + i * cell
            text(hour.branch, left, y, cell, 28f, 27f, true)
            text("${hour.start}–${hour.end}", left, y + 30, cell, 22f, 19f)
            text(hour.luck, left, y + 56, cell, 28f, 25f)
        }
    }
    private fun zodiacSix(x: Float, y: Float, w: Float, h: Float) {
        a.luckyZodiacs.forEachIndexed { i, name ->
            val size = min(w / 6 - 12, h - 31)
            val left = x + i * w / 6 + (w / 6 - size) / 2
            animal(name, left, y, size)
            text(name, x + i * w / 6, y + size + 3, w / 6, 26f, 25f)
        }
    }
    private fun cantonGrid() {
        text("运程·编选  ${a.editorial.fortune}", 32f, 0f, 1136f, 44f, 24f)
        box(28f, 50f, 1144f, 790f, width = 4f)
        box(157f, 54f, 440f, 118f, true); box(603f, 54f, 435f, 118f, true)
        zodiacIcon(signIndex, 60f, 58f, 76f)
        text(WesternZodiac.names[signIndex], 32f, 138f, 121f, 30f, 25f)
        animal(dayAnimal, 1064f, 58f, 78f); text("${dayAnimal}日", 1042f, 138f, 126f, 30f, 26f)
        text(a.lunarHeading, 165f, 61f, 424f, 34f, 30f, inverse = true)
        text(a.lunarDay, 165f, 98f, 424f, 66f, 64f, true, true)
        text(data.weekday.uppercase(Locale.ENGLISH), 611f, 61f, 419f, 34f, 31f, true, true)
        text(data.weekdayChinese, 611f, 98f, 419f, 66f, 58f, true, true)
        line(28f, 176f, 1172f, 176f, 3f)
        line(147f, 176f, 147f, 840f, 3f)
        verticalFitted("${a.sound}${a.duty}日", 47f, 206f, 80f, 271f, 36f)
        line(41f, 505f, 134f, 505f)
        verticalFitted("${a.nineStar}${a.mansion}宿", 47f, 532f, 80f, 281f, 36f)
        hours(159f, 183f, 1000f)
        line(147f, 278f, 1172f, 278f)
        line(490f, 278f, 490f, 840f); line(830f, 278f, 830f, 840f)
        yiJi(157f, 290f, 323f, 220f)
        line(147f, 520f, 490f, 520f)
        label("吉祥方位", 222f, 532f, 188f)
        text(a.directions.joinToString("\n"), 157f, 579f, 323f, 139f, 28f, true)
        text("${a.stem}属${a.stemElement}  ${a.branch}属${a.branchElement}", 499f, 290f, 322f, 40f, 32f, true)
        label("今日八字 · 子时", 545f, 337f, 230f, 26f)
        pillars(520f, 383f, 280f, 102f)
        compass(660f, 607f)
        label("吉神", 850f, 291f, 95f); label("凶煞", 1050f, 291f, 95f)
        words(a.goodGods, 842f, 343f, 155f, 116f, 28f, top = true)
        words(a.badGods, 1005f, 343f, 154f, 116f, 28f, top = true)
        line(830f, 469f, 1172f, 469f)
        label("吉祥生肖", 901f, 480f, 185f)
        a.luckyZodiacs.forEachIndexed { i, name ->
            val x = 852f + i % 3 * 103; val y = 528f + i / 3 * 91
            animal(name, x, y, 66f); text(name, x + 65, y, 33f, 66f, 24f)
        }
        line(147f, 727f, 1172f, 727f)
        text("贵人时 ${a.nobleBranches} · 三合${a.threeHarmony}\n${a.dayLu}\n${a.clash}", 157f, 739f, 323f, 91f, 26f)
        text("胎神 ${a.fetus}\n${a.pengZu.replace(" ", "\n")}", 500f, 738f, 320f, 91f, 25f)
        text("吉数 ${a.editorial.luckyNumbers.joinToString(" ")}\n${a.editorial.dream}", 841f, 738f, 320f, 91f, 25f)
        redFooter("福 历 堂 · 岭 南 通 胜")
    }
    private fun classicGrid() {
        // Slanting edge marks recall the red perforated frame of the pocket tear-off print.
        box(47f, 43f, 1106f, 795f, width = 3f)
        repeat(45) { i ->
            val x = 49f + i * 24.5f
            line(x, 39f, x + 12, 31f, 4f); line(x, 842f, x + 12, 850f, 4f)
        }
        repeat(32) { i -> val y = 46f + i * 24.5f; line(42f, y, 34f, y + 12, 4f); line(1158f, y, 1166f, y + 12, 4f) }
        line(207f, 43f, 207f, 838f, 3f)
        text(a.nineStar, 55f, 53f, 145f, 37f, 27f, true)
        text("${a.stem}${a.branch}日\n${a.sound}\n${a.mansion}宿 ${a.duty}日", 55f, 101f, 145f, 111f, 30f)
        line(47f, 221f, 207f, 221f)
        text("时辰吉凶", 55f, 229f, 145f, 35f, 28f, true)
        a.hours.forEachIndexed { i, hour ->
            val y = 279f + i * 43.5f
            text(hour.branch, 57f, y, 32f, 35f, 27f, true)
            text("${hour.start}–${hour.end}", 89f, y, 68f, 35f, 17f)
            text(hour.luck, 162f, y, 36f, 35f, 26f)
        }
        text(a.lunarDay, 218f, 50f, 453f, 78f, 72f, true)
        text("${a.lunarHeading}\n${data.weekdayChinese} · ${a.termNotice}", 680f, 52f, 461f, 75f, 31f)
        line(207f, 137f, 1153f, 137f, 3f)
        yiJi(221f, 150f, 917f, 130f)
        line(207f, 292f, 1153f, 292f, 3f)
        line(476f, 292f, 476f, 654f); line(782f, 292f, 782f, 838f)
        label("吉祥方位", 244f, 305f, 205f)
        text(a.directions.joinToString("\n"), 218f, 350f, 247f, 161f, 31f, true)
        words(a.goodGods, 218f, 521f, 247f, 62f, 27f)
        words(a.badGods.map { "凶·$it" }, 218f, 590f, 247f, 54f, 23f)
        text("${a.stem}属${a.stemElement} ${a.branch}属${a.branchElement}", 484f, 306f, 290f, 38f, 30f)
        compass(629f, 476f)
        text("贵人时 ${a.nobleBranches}", 485f, 608f, 288f, 34f, 29f)
        label("今日八字", 858f, 306f, 217f)
        pillars(805f, 355f, 323f, 106f)
        text("${a.dayLu}\n三合${a.threeHarmony} · ${a.clash}\n胎神 ${a.fetus}", 794f, 480f, 348f, 116f, 27f)
        zodiacIcon(signIndex, 815f, 604f, 48f)
        text(WesternZodiac.names[signIndex], 870f, 606f, 268f, 40f, 30f)
        line(207f, 654f, 1153f, 654f)
        zodiacSix(217f, 667f, 553f, 95f)
        text("今日吉数 ${a.editorial.luckyNumbers.joinToString(" ")}", 218f, 774f, 552f, 45f, 29f)
        text("${a.pengZu.replace(" ", "\n")}\n${a.editorial.dream}", 794f, 666f, 346f, 156f, 27f)
        text("【运程·编选】${a.editorial.fortune}", 215f, 0f, 932f, 29f, 19f)
        redFooter("鸿 运 · 经 典 皇 历")
    }
    private fun redFooter(title: String) {
        box(28f, 857f, 1144f, 35f, true)
        text("六合 ${a.sixHarmony} ${a.editorial.zodiacNumbers}", 35f, 857f, 380f, 35f, 24f, inverse = true)
        text(title, 418f, 857f, 365f, 35f, 26f, true, true)
        text(a.seasonalNote, 788f, 857f, 376f, 35f, 22f, inverse = true)
    }
    private fun festiveBanner(x: Float, y: Float, w: Float, h: Float, day: FestiveDay) {
        // A separate title cartouche, emblem and patterned border, not just a date recolour.
        box(x, y, w, h, width = 3f)
        box(x + 6, y + 6, w - 12, h - 12, width = 1f)
        val cx = x + 53; val cy = y + h / 2
        p.color = ink; p.style = Paint.Style.STROKE; p.strokeWidth = 3f
        when (day.kind) {
            FestiveKind.MOON -> {
                c.drawCircle(cx, cy, 27f, p)
                c.drawArc(cx - 18, cy - 27, cx + 30, cy + 27, 90f, 180f, false, p)
                line(cx - 33, cy + 34, cx + 35, cy + 34)
            }
            FestiveKind.SPRING, FestiveKind.LANTERN -> {
                c.drawOval(cx - 24, cy - 27, cx + 24, cy + 24, p)
                c.drawOval(cx - 12, cy - 27, cx + 12, cy + 24, p)
                line(cx - 16, cy - 29, cx + 16, cy - 29, 5f)
                line(cx, cy + 24, cx, cy + 39, 3f)
            }
            FestiveKind.DRAGON_BOAT -> {
                val leaf = Path().apply { moveTo(cx, cy - 30); lineTo(cx - 29, cy + 25); lineTo(cx + 29, cy + 25); close() }
                c.drawPath(leaf, p); line(cx - 13, cy, cx + 16, cy + 25); line(cx + 11, cy - 9, cx - 17, cy + 25)
            }
            else -> {
                val star = Path()
                repeat(10) { i ->
                    val angle = Math.toRadians(i * 36.0 - 90); val r = if (i % 2 == 0) 29f else 13f
                    val px = cx + cos(angle).toFloat() * r; val py = cy + sin(angle).toFloat() * r
                    if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                }; star.close(); c.drawPath(star, p)
            }
        }
        p.style = Paint.Style.FILL
        text(day.title, x + 110, y + 10, w - 134, h * .48f, 40f, true)
        text(day.greeting, x + 110, y + h * .61f, w - 134, h * .26f, 24f)
    }
}
