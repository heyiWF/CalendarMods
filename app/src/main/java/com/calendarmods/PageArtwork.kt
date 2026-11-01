package com.calendarmods

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.min

internal const val PAPER_INK = 0xFF20231E.toInt()
internal const val CREAM = 0xFFFFFEF9.toInt()

/**
 * Draws one calendar page into a design-space box. Colours, spacing and panel weights follow the
 * Compose implementation so both branches print the same page.
 */
internal class PageArtwork(
    val theme: CalendarTheme,
    val data: CalendarData,
    val style: ThemeStyle,
    val environment: AlmanacEnvironment,
    val wide: Boolean,
    val width: Float,
    val height: Float,
    val image: Bitmap?,
    val installedFont: Typeface?,
    val icons: AlmanacIcons,
    val artwork: PeitaiyiArtwork?,
) {
    private val almanacDay: AlmanacDay? = if (theme.renderer.isTongSheng)
        Almanacs.shared.day(data.today) else null

    /** Red on weekends and festival days, the shared default for all four almanac plates. */
    private val festiveInk: Boolean get() = data.today.dayOfWeek >= 6 || data.festivals.isNotEmpty() ||
        (style.festive && almanacDay?.festivalEdition != null)

    fun paperColor(): Int {
        val color = if (theme.renderer.isTongSheng)
            almanacPalette(theme, style, environment.minute, environment.ambientDark, festiveInk).paper
        else theme.surfaces.paper
        return color.toInt()
    }

    fun draw(canvas: Canvas) {
        c = canvas
        when (theme.renderer) {
            ThemeRenderer.GOLDEN -> golden(canvas)
            ThemeRenderer.TEDDY -> illustration(canvas, photo = false)
            ThemeRenderer.PHOTO -> illustration(canvas, photo = true)
            ThemeRenderer.GOOD_DAY -> goodDay(canvas)
            ThemeRenderer.VERSE -> verse(canvas)
            ThemeRenderer.ALMANAC -> almanacPage(canvas)
            ThemeRenderer.HANGING -> hanging(canvas)
            ThemeRenderer.TRADITIONAL, ThemeRenderer.CANTON, ThemeRenderer.AUSPICIOUS, ThemeRenderer.PEITAIYI ->
                traditional(canvas)
        }
    }

    private lateinit var c: Canvas

    private fun plate(ink: Int = PAPER_INK, paper: Int = CREAM) = Plate(c, ink, paper, installedFont)

    // ---------------------------------------------------------------- 晴日 · 黄页台历

    private fun golden(canvas: Canvas) {
        val p = plate()
        val header = 76f
        p.box(0f, 0f, width, header, color = 0xFFF1C31C.toInt())
        p.rings(14f, 0f, width - 28f, 19f, count = if (wide) 30 else 19)
        p.text(data.weekday.uppercase(Locale.ENGLISH), 0f, header - 13f - 31f, width, 31f, 27f,
            color = 0xFFFFFFFF.toInt(), bold = true, spacing = 1f)

        if (wide) {
            val innerW = width - 44f
            val leftW = innerW * .56f
            val rightW = innerW * .44f - 20f
            val top = header + 22f
            val innerH = height - top - 22f
            p.text(data.month.uppercase(Locale.ENGLISH), 22f, top, leftW, 26f, 22f, bold = true)
            p.bigDay(data.day, 22f, top + 26f, leftW, (innerH - 52f).coerceAtLeast(80f), typeface = p.extraBold)
            p.text(data.today.year.toString(), 22f, top + innerH - 26f, leftW, 26f, 22f)

            val rightX = 22f + leftW + 20f
            p.text(data.lunar, rightX, top, rightW, 19f, 16f, typeface = p.serif)
            val weekH = 13f
            val gridTop = top + 19f + 13f
            val gridH = (innerH - 19f - 13f - weekH - 13f).coerceAtLeast(30f)
            p.monthGrid(data, rightX, gridTop, rightW, gridH)
            p.text("WEEK ${data.week}  ·  DAY ${data.today.dayOfYear}", rightX, top + innerH - weekH,
                rightW, weekH, 11f, spacing = 1f)
        } else {
            val left = 23f
            val contentW = width - left * 2
            val top = header + 24f
            val bottom = height - 24f
            var y = top
            p.text(data.month.uppercase(Locale.ENGLISH), left, y, contentW, 35f, 30f, bold = true)
            y += 35f
            val fixed = 37f + 15f + 14f + 24f + .65f + 16f + 15f
            val dayH = (bottom - y - fixed).coerceAtLeast(40f)
            p.bigDay(data.day, left, y, contentW, dayH, typeface = p.extraBold)
            y += dayH
            p.text(data.today.year.toString(), left, y, contentW, 37f, 32f, typeface = p.medium)
            y += 37f
            p.text("WEEK ${data.week}  ·  DAY ${data.today.dayOfYear}", left, y + 15f, contentW, 14f, 12f, spacing = 1f)
            y += 15f + 14f
            p.rule(left, y + 24f, contentW)
            p.text("${data.lunar} · ${data.weekdayChinese}", left, y + 24f + .65f + 16f, contentW, 15f, 13f, spacing = 1f)
        }
    }

    // ---------------------------------------------------------------- 冬日来信 / 拾光

    private fun illustration(canvas: Canvas, photo: Boolean) {
        val p = plate()
        val inset = if (photo) 8f else 0f
        p.box(inset, inset, width - inset * 2, height - inset * 2, color = CREAM)
        val contentTop = inset + 12f
        val contentH = height - contentTop - inset
        val contentW = width - inset * 2
        val firstWeight = if (photo) .56f else .44f

        val imageX: Float; val imageY: Float; val imageW: Float; val imageH: Float
        val textX: Float; val textY: Float; val textW: Float; val textH: Float
        if (wide) {
            imageX = inset; imageY = contentTop
            imageW = contentW * firstWeight; imageH = contentH
            textX = inset + imageW; textY = contentTop
            textW = contentW - imageW; textH = contentH
        } else {
            imageX = inset; imageY = contentTop
            imageW = contentW; imageH = contentH * firstWeight
            textX = inset; textY = contentTop + imageH
            textW = contentW; textH = contentH - imageH
        }
        if (image != null) p.image(image, imageX, imageY, imageW, imageH, style.fill)
        p.rings(inset + 12f, inset, contentW - 24f, 22f, count = if (wide) 30 else 19, dark = photo)

        val pad = if (wide) 20f else 15f
        val x = textX + pad
        val w = textW - pad * 2
        var y = textY + pad
        if (photo) {
            val headerH = 45f
            p.text(data.today.year.toString(), x, y + headerH - 25f, w * .5f, 25f, 21f, typeface = p.light, align = ALIGN_LEFT)
            p.text(data.today.monthValue.toString().padStart(2, '0'), x + w * .5f, y, w * .5f, headerH,
                38f, color = 0xFF8F8C84.toInt(), bold = true, align = ALIGN_RIGHT)
            y += headerH
        } else {
            p.text(data.chineseMonth, x, y, w, 29f, 25f, bold = true)
            y += 29f
            val caption = "${data.today.year} · ${data.motto}"
            val captionH = p.textHeight(caption, w, 10f, maxLines = 2)
            p.text(caption, x, y + 5f, w, captionH, 10f, maxLines = 2)
            y += 5f + captionH
        }
        val footer = if (photo) p.textHeight(data.motto, w, 10f, maxLines = 2) + 8f else 0f
        val gridTop = y + 12f
        val gridH = (textY + textH - pad - gridTop - footer).coerceAtLeast(30f)
        p.monthGrid(data, x, gridTop, w, gridH, lunar = !photo, outlined = !photo)
        if (photo) p.text(data.motto, x, textY + textH - pad - footer + 8f, w, footer - 8f, 10f, maxLines = 2)
    }

    // ---------------------------------------------------------------- 好日 · 字体日历

    private fun goodDay(canvas: Canvas) {
        val p = plate()
        if (wide) {
            val top = 25f
            val leftW = (width - 50f) * .55f
            val contentH = height - 50f
            p.text("${data.month} ${data.chineseMonth}", 25f, top, leftW, 26f, 22f, typeface = p.serif)
            p.bigDay(data.day, 25f, top + 26f, leftW, (contentH - 54f).coerceAtLeast(80f),
                typeface = p.serifBold)
            p.text(data.weekdayChinese, 25f, top + contentH - 20f, leftW, 20f, 17f, typeface = p.serif)

            val x = 25f + leftW + 26f
            val w = width - 25f - x
            // The right column is a single centred block, matching the Compose row alignment.
            var blockHeight = 16f + 18f + 37f + 18f + .65f + 18f + 16f
            if (data.festivals.isNotEmpty()) blockHeight += 30f + 18f
            var y = top + ((contentH - blockHeight) / 2f).coerceAtLeast(0f)
            p.text("${data.today.year} · ${data.lunar}", x, y, w, 16f, 13f, typeface = p.serif)
            y += 16f + 18f
            if (data.festivals.isNotEmpty()) { p.festival(data, x, y, w, 30f); y += 30f + 18f }
            p.carousel("宜", data.recommends, x, y, w, 37f, 30f, p.serif)
            y += 37f + 18f
            p.rule(x, y, w)
            p.text(data.motto, x, y + .65f + 18f, w, 16f, 13f, bold = true, typeface = p.serifBold)
        } else {
            val left = 24f
            val w = width - 48f
            var y = 24f
            p.text(data.lunar, left, y, w, 17f, 14f, spacing = 2f, typeface = p.serif)
            y += 17f + 14f
            if (data.festivals.isNotEmpty()) { p.festival(data, left, y, w, 30f); y += 30f }
            p.text("${data.month} ${data.chineseMonth}", left, y + 18f, w, 30f, 25f, typeface = p.serif)
            y += 18f + 30f
            p.text(data.today.year.toString(), left, y + 10f, w, 18f, 15f, bold = true, typeface = p.serifBold)
            y += 10f + 18f
            val tail = 17f + 18f + 37f + 18f + .65f + 20f + 16f + 24f + 11f
            val dayH = (height - 24f - y - tail).coerceAtLeast(60f)
            p.bigDay(data.day, left, y, w, dayH, typeface = p.serifBold)
            y += dayH
            p.text(data.weekdayChinese, left, y, w, 17f, 17f, typeface = p.serif)
            y += 17f
            p.carousel("宜", data.recommends, left, y + 18f, w, 37f, 32f, p.serif)
            y += 18f + 37f
            p.rule(left, y + 18f, w)
            p.text(data.motto, left, y + 18f + .65f + 20f, w, 16f, 13f, bold = true, typeface = p.serifBold)
            p.text("把每個平凡的日子，過成喜歡的模樣。", left, height - 24f - 11f, w, 11f, 9f, spacing = 1f, typeface = p.serif)
        }
    }

    // ---------------------------------------------------------------- 慢笺 · 竖排文句

    private fun verse(canvas: Canvas) {
        val p = plate()
        val left = 24f
        val w = width - 48f
        val top = 24f
        val bottomArea = .65f + 16f + 14f + 16f + .65f
        val rowH = height - top * 2 - bottomArea
        val leftW = w * (if (wide) .4f else .43f)
        val midW = w * (if (wide) .22f else .24f)
        val rightW = w - leftW - midW

        val rowPad = if (wide) 3f else 26f
        val firstY = top + rowPad
        p.text(data.chineseMonth, left, firstY, leftW, 26f, 22f, typeface = p.light, align = ALIGN_LEFT)
        p.text(data.monthShort.uppercase(Locale.ENGLISH), left, firstY + 26f, leftW, 24f, 20f, typeface = p.light, align = ALIGN_LEFT)
        val weekdaySize = if (wide) 17f else 23f
        val weekdayBox = weekdaySize * 1.17f * data.weekdayChinese.length
        // firstY already sits 26 units below the row top; keep the weekday clear of the divider.
        val dayH = (rowH - rowPad * 2 - 50f - weekdayBox - 12f - 22f).coerceAtLeast(40f)
        val weekdayTop = firstY + 50f + dayH
        p.bigDay(data.day, left, firstY + 50f, leftW, dayH, typeface = p.extraLight)
        p.text(data.weekdayChinese.toCharArray().joinToString("\n"), left, weekdayTop, leftW, weekdayBox,
            weekdaySize, typeface = p.light, align = ALIGN_LEFT)
        p.text(data.weekday.take(3) + ".", left, weekdayTop + weekdayBox + 12f, leftW, 22f, 19f, typeface = p.light, align = ALIGN_LEFT)

        val midX = left + leftW
        val size = min(if (wide) 15f else 17f, (rowH - rowPad * 2) / (data.motto.length * 1.17f)).toInt().coerceAtLeast(1).toFloat()
        p.text(data.motto.toCharArray().joinToString("\n"), midX, firstY, midW, size * 1.17f * data.motto.length, size, typeface = p.serif)

        val rightX = midX + midW
        val gridH = if (wide) 172f else 117f
        p.monthGrid(data, rightX, firstY, rightW, gridH, small = !wide, color = 0xFF4D5146.toInt())
        p.text(if (wide) data.lunar else data.lunar.toCharArray().joinToString("\n"),
            rightX, firstY + gridH + 24f, rightW, 14f * 1.17f * (if (wide) 1 else data.lunar.length), 14f, typeface = p.serif, align = ALIGN_RIGHT)

        val ruleY = top + rowH
        p.rule(left, ruleY, w)
        p.text("今日心语", left, ruleY + .65f + 16f, w * .4f, 14f, 12f, spacing = 2f, align = ALIGN_LEFT)
        p.text(data.heartWord, left + w * .3f, ruleY + .65f + 16f, w * .7f, 14f, 12f, spacing = 2f, align = ALIGN_RIGHT)
        p.rule(left, ruleY + .65f + 16f + 14f + 16f, w)
    }

    // ---------------------------------------------------------------- 岁时 · 复古黄历

    private fun almanacPage(canvas: Canvas) {
        val p = plate(ink = PAPER_INK, paper = 0xFFEAE1C9.toInt())
        p.box(8f, 8f, width - 16f, height - 16f, color = 0xFFEAE1C9.toInt())
        paintRuled(canvas)
        val left = 26f
        val w = width - 52f
        var y = 26f
        p.text(data.chineseMonth, left, y, w / 3f, 19f, 16f, align = ALIGN_LEFT, typeface = p.serif)
        p.text(data.weekdayChinese, left + w / 3f, y, w / 3f, 19f, 16f, typeface = p.serif)
        p.text("第 ${data.week} 周", left + w * 2f / 3f, y, w / 3f, 19f, 16f, align = ALIGN_RIGHT, typeface = p.serif)
        y += 19f
        p.text(data.stems, left, y + 13f, w, 12f, 10f, spacing = 1f, typeface = p.serif)
        y += 13f + 12f + 13f
        p.rule(left, y, w)
        y += .65f + 10f

        val footer = 12f + 10f
        val contentH = (height - 26f - footer - y).coerceAtLeast(120f)
        if (wide) {
            val firstW = w * .49f
            val secondW = w - firstW - 20f
            val festivalH = if (data.festivals.isEmpty()) 0f else p.textHeight(data.festivals.joinToString(" · "), firstW - 32f, 12f, maxLines = 2) + 14f
            val dayH = (contentH - 13f - 17f - festivalH).coerceAtLeast(40f)
            p.bigDay(data.day, left, y, firstW, dayH, color = 0xFF421E12.toInt(), typeface = p.serifBold)
            p.text("${data.today.year} / ${data.today.monthValue.toString().padStart(2, '0')} · 农历${data.lunar}",
                left, y + dayH, firstW, 15f, 11f, typeface = p.serif)
            if (data.festivals.isNotEmpty()) p.festival(data, left + 10f, y + dayH + 15f, firstW - 20f, 30f)
            val secondX = left + firstW + 20f
            val half = secondW / 2f
            val wordsH = maxOf(p.textHeight(data.suitableText(Int.MAX_VALUE), half - 7f, 10f, p.serif),
                p.textHeight(data.avoidText(Int.MAX_VALUE), half - 7f, 10f, p.serif))
            p.text("宜", secondX, y + 10f, half - 7f, 24f, 20f, color = 0xFF637051.toInt(), typeface = p.serif)
            p.text(data.suitableText(Int.MAX_VALUE), secondX, y + 40f, half - 7f, wordsH, 10f, color = 0xFF777260.toInt(), typeface = p.serif)
            p.text("忌", secondX + half + 7f, y + 10f, half - 7f, 24f, 20f, color = 0xFF875C49.toInt(), typeface = p.serif)
            p.text(data.avoidText(Int.MAX_VALUE), secondX + half + 7f, y + 40f, half - 7f, wordsH, 10f, color = 0xFF777260.toInt(), typeface = p.serif)
            val gridTop = y + 53f + wordsH
            p.monthGrid(data, secondX, gridTop, secondW, (y + contentH - gridTop).coerceAtLeast(30f),
                color = 0xFF665D48.toInt())
        } else {
            // Portrait stacks the two panels: date above, 宜忌 and the month grid below.
            val firstH = contentH * .48f
            val festivalH = if (data.festivals.isEmpty()) 0f else p.textHeight(data.festivals.joinToString(" · "), w - 32f, 12f, maxLines = 2) + 14f
            val dayH = (firstH - 13f - 17f - festivalH).coerceAtLeast(40f)
            p.bigDay(data.day, left, y, w, dayH, color = 0xFF421E12.toInt(), typeface = p.serifBold)
            p.text("${data.today.year} / ${data.today.monthValue.toString().padStart(2, '0')} · 农历${data.lunar}",
                left, y + dayH, w, 15f, 11f, typeface = p.serif)
            if (data.festivals.isNotEmpty()) p.festival(data, left + 10f, y + dayH + 15f, w - 20f, 30f)
            var sy = y + firstH
            p.rule(left, sy, w)
            sy += .65f
            val half = w / 2f
            val wordsH = maxOf(p.textHeight(data.suitableText(Int.MAX_VALUE), half - 7f, 10f, p.serif),
                p.textHeight(data.avoidText(Int.MAX_VALUE), half - 7f, 10f, p.serif))
            p.text("宜", left, sy + 10f, half - 7f, 24f, 20f, color = 0xFF637051.toInt(), typeface = p.serif)
            p.text(data.suitableText(Int.MAX_VALUE), left, sy + 40f, half - 7f, wordsH, 10f, color = 0xFF777260.toInt(), typeface = p.serif)
            p.text("忌", left + half + 7f, sy + 10f, half - 7f, 24f, 20f, color = 0xFF875C49.toInt(), typeface = p.serif)
            p.text(data.avoidText(Int.MAX_VALUE), left + half + 7f, sy + 40f, half - 7f, wordsH, 10f, color = 0xFF777260.toInt(), typeface = p.serif)
            val gridTop = sy + 53f + wordsH
            p.monthGrid(data, left, gridTop, w, (y + contentH - gridTop).coerceAtLeast(30f),
                color = 0xFF665D48.toInt())
        }

        p.text("岁时有序 · 日月成章", left, height - 26f - footer + 12f, w, 10f, 8f,
            color = 0xFF8E856E.toInt(), spacing = 2f, typeface = p.serif)
    }

    private fun paintRuled(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Plate.withAlpha(0xFF877B5D.toInt(), .035f)
        paint.strokeWidth = .45f
        var y = 8f
        while (y < height - 8f) {
            canvas.drawLine(8f, y, width - 8f, y, paint)
            y += 2f
        }
    }

    // ---------------------------------------------------------------- 一页 · 悬挂日历

    private fun hanging(canvas: Canvas) {
        val p = plate()
        val header = 52f
        p.box(0f, 0f, width, header, color = 0xFFDEDFD9.toInt())
        p.rings(0f, 0f, width, 18f, count = 2)
        p.text("${data.today.year}年", 23f, header - 23f, (width - 46f) / 2f, 13f, 11f, align = ALIGN_LEFT, spacing = 1f)
        p.text("${data.today.monthValue}月", width / 2f, header - 23f, (width - 46f) / 2f, 13f, 11f, align = ALIGN_RIGHT, spacing = 1f)

        val left = 24f
        val top = header + 18f
        val w = width - 48f
        val h = height - top - 18f
        val firstW = w * if (wide) .53f else 1f
        val firstH = if (wide) h else h * .57f
        p.bigDay(data.day, left, top, firstW, firstH, typeface = weightedFont(p.condensed, 900))

        val x = if (wide) left + firstW + 22f else left
        val secondW = if (wide) w - firstW - 22f else w
        var contentTop = top
        var contentH = h
        if (wide) {
            contentTop = top
        } else {
            contentTop = top + firstH
            contentH = h - firstH
        }
        val carouselH = 11f * 1.17f
        val linesH = carouselH * 2 + 8f
        val itemHeights = listOf(28f, 14f, .65f, linesH, .65f, 37f)
        val gap = ((contentH - itemHeights.sum()) / (itemHeights.size + 1)).coerceAtLeast(0f)
        var y = contentTop + gap
        p.text(data.weekdayChinese, x, y, secondW, 28f, 24f, bold = true, spacing = 2f)
        y += 28f + gap
        val lunar = data.lunar + if (data.festivals.isEmpty()) "" else " · ${data.festivals.joinToString(" · ")}"
        p.text(lunar, x, y, secondW, 14f, 12f, color = 0xFF75796E.toInt(), typeface = p.serif)
        y += 14f + gap
        p.rule(x, y, secondW)
        y += .65f + gap
        p.carousel("宜", data.recommends, x, y, secondW, carouselH, 11f, p.serif, pageSize = 2)
        p.carousel("忌", data.avoids, x, y + carouselH + 8f, secondW, carouselH, 11f, p.serif, pageSize = 2)
        y += linesH + gap
        p.rule(x, y, secondW)
        y += .65f + gap
        p.text(data.motto, x, y, secondW - 50f, 36f, 10f, color = 0xFF696F61.toInt(), typeface = p.serif)
        p.cat(x + secondW - 46f, y, 46f, 37f)
    }

    // ---------------------------------------------------------------- 传统黄历四款

    private fun traditional(canvas: Canvas) {
        val almanac = almanacDay ?: return
        val palette = almanacPalette(theme, style, environment.minute, environment.ambientDark, festiveInk)
        val ink = palette.ink
        val festival = if (style.festive) almanac.festivalEdition else null
        val regular = installedFont ?: Typeface.create("serif", Typeface.NORMAL)
        val bold = weightedFont(installedFont ?: Typeface.create("sans-serif", Typeface.BOLD), 1000)
        val plate = TongShengPlate(data, almanac, ink.toInt(), palette.paper.toInt(), regular, bold,
            theme.renderer, icons, festival, artwork, style.festive)
        val designWidth = if (wide) 2100f else 1200f
        val scale = if (wide) width / designWidth else min(width / designWidth, height / 1600f)
        canvas.save()
        canvas.translate((width - designWidth * scale) / 2f, 0f)
        canvas.scale(scale, scale)
        plate.draw(canvas, height / scale, wide)
        canvas.restore()
    }
}

/** One rotating 宜/忌 line, matching the 2 s cycle of the Compose carousel. */
private fun Plate.carousel(label: String, items: List<String>, x: Float, y: Float, w: Float, h: Float, size: Float,
    typeface: Typeface, pageSize: Int = 1) {
    val pages = almanacPages(items, pageSize)
    val step = ((System.currentTimeMillis() / 2_000) % pages.size).toInt()
    val gap = size * .5f
    paint.reset(); paint.isAntiAlias = true
    paint.typeface = typeface; paint.textSize = size; paint.color = ink
    val labelWidth = paint.measureText(label)
    val itemWidth = paint.measureText(pages[step])
    val left = x + (w - labelWidth - gap - itemWidth) / 2f
    text(label, left, y, labelWidth + 2f, h, size, align = ALIGN_LEFT, typeface = typeface)
    text(pages[step], left + labelWidth + gap, y, itemWidth + 2f, h, size, align = ALIGN_LEFT, typeface = typeface)
}

internal fun almanacPages(items: List<String>, pageSize: Int): List<String> =
    items.ifEmpty { listOf("暂无") }.chunked(pageSize).map { it.joinToString(" · ") }
