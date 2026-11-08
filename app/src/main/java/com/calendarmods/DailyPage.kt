package com.calendarmods

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * The bundled daily page: a full-width date line, the wall clock, 宜 / 忌 and a Monday-first month
 * grid whose lunar labels rotate with festivals and solar terms.
 *
 * Every size is derived from [userScale], then checked against the space that is actually left:
 * the header shrinks when the date line would run into the lunar name, the grid takes whatever
 * height remains, and the whole layout falls back to a smaller scale when the grid would otherwise
 * drop below [MIN_CELL]. Nothing is clipped, overlapped or pushed off the page at any slider step.
 */
internal class DailyPage(
    private val scheme: PageScheme,
    private val data: CalendarData,
    private val face: ClockFace,
    private val wide: Boolean,
    private val width: Float,
    private val height: Float,
    private val motion: Boolean,
    private val now: Long,
    private val userScale: Float = 1f,
) {
    private val ink = scheme.ink.toInt()
    private val paper = scheme.paper.toInt()
    private val red = scheme.accent.toInt()
    private val green = scheme.herb.toInt()
    private val onAccent = scheme.onAccent.toInt()
    // The RectF overload of drawRoundRect is API 1; the float form is API 21.
    private val chipRect = RectF()

    /**
     * Milliseconds until a walking label needs another frame; [Long.MAX_VALUE] when every label is
     * settled. Read by the host view after [draw] to schedule exactly the redraws the page needs.
     */
    var labelDelay = Long.MAX_VALUE
        private set

    fun draw(canvas: Canvas) {
        val p = Plate(canvas, ink)
        labelDelay = Long.MAX_VALUE
        canvas.drawColor(paper)
        if (wide) landscape(p) else portrait(p)
    }

    // ------------------------------------------------------------------ portrait

    private fun portrait(p: Plate) {
        var scale = ThemeStyle.clampFontScale(userScale)
        var layout = portraitLayout(p, scale)
        var guard = 0
        while (!layout.fits && scale > MIN_SCALE && guard++ < 40) {
            scale = (scale - .02f).coerceAtLeast(MIN_SCALE)
            layout = portraitLayout(p, scale)
        }
        header(p, layout, layout.margin, layout.contentW, layout.margin)
        rule(p, layout.margin, layout.ruleY, layout.contentW)
        clock(p, layout, layout.margin, layout.clockBaseline)
        monthGrid(p, layout)
        almanac(p, layout)
    }

    private fun portraitLayout(p: Plate, scale: Float): Layout {
        val layout = Layout(scale)
        layout.margin = EDGE
        layout.contentW = width - EDGE * 2f
        layout.headerScale = headerFit(p, layout.contentW, scale)
        val daySize = HEADER_SIZE * scale * layout.headerScale
        layout.ruleY = EDGE + daySize * 1.02f + 5f * scale
        layout.clockSize = fitClock(p, layout.contentW,
            CLOCK_SIZE * scale * if (face.period.isEmpty()) 1f else 1.5f)
        layout.clockBaseline = layout.ruleY + 8f * scale + layout.clockSize * .84f
        layout.clockBottom = layout.ruleY + 8f * scale + layout.clockSize * 1.02f

        layout.almanacX = EDGE
        layout.almanacW = layout.contentW
        almanacSizes(p, layout, height * ALMANAC_PORTRAIT_SHARE, 1f)
        val almanacH = almanacHeight(p, layout)
        layout.almanacY = height - EDGE - almanacH

        layout.gridX = EDGE
        layout.gridW = layout.contentW
        layout.cellW = layout.contentW / 7f
        layout.gridHeaderH = GRID_HEADER * scale
        val gridBottom = layout.almanacY - 10f * scale
        val top = layout.clockBottom + 9f * scale
        layout.cellH = min((gridBottom - top - layout.gridHeaderH) / 6f,
            layout.cellW * 1.45f * scale)
        // A capped grid leaves slack; split it above and below so the page never shows one hole.
        layout.gridY = top + ((gridBottom - top) - layout.gridHeaderH - layout.cellH * 6f) / 2f
        sizeCells(p, layout)
        return layout
    }

    // ------------------------------------------------------------------ landscape

    private fun landscape(p: Plate) {
        var scale = ThemeStyle.clampFontScale(userScale)
        var layout = landscapeLayout(p, scale)
        var guard = 0
        while (!layout.fits && scale > MIN_SCALE && guard++ < 40) {
            scale = (scale - .02f).coerceAtLeast(MIN_SCALE)
            layout = landscapeLayout(p, scale)
        }
        // The date line and its rule run the full width of the screen on landscape.
        header(p, layout, layout.margin, layout.contentW, layout.margin)
        rule(p, 0f, layout.ruleY, width)
        clock(p, layout, layout.margin, layout.clockBaseline)
        monthGrid(p, layout)
        almanac(p, layout)
    }

    private fun landscapeLayout(p: Plate, scale: Float): Layout {
        val layout = Layout(scale)
        layout.margin = EDGE
        layout.contentW = width - EDGE * 2f
        layout.headerScale = headerFit(p, layout.contentW, scale)
        val daySize = HEADER_SIZE * scale * layout.headerScale
        layout.ruleY = EDGE + daySize * 1.02f + 5f * scale
        val contentTop = layout.ruleY + 10f * scale
        val contentBottom = height - EDGE

        val leftW = layout.contentW * .42f
        layout.clockSize = fitClock(p, leftW - 2f * scale, CLOCK_SIZE * scale * 1.5f)
        layout.clockBaseline = contentTop + layout.clockSize * .84f
        layout.clockBottom = contentTop + layout.clockSize * 1.02f

        layout.almanacX = EDGE
        layout.almanacW = leftW
        almanacSizes(p, layout, height * ALMANAC_LANDSCAPE_SHARE, 1.15f)
        val almanacH = almanacHeight(p, layout)
        layout.almanacY = contentBottom - almanacH

        // The grid sits under the full-width rule and ends on the bottom edge, so it reads as a
        // block that was moved down rather than a column centred beside the clock.
        layout.gridX = EDGE + leftW + 18f * scale
        layout.gridW = width - EDGE - layout.gridX
        layout.cellW = layout.gridW / 7f
        layout.gridHeaderH = GRID_HEADER * scale
        layout.cellH = min((contentBottom - contentTop - layout.gridHeaderH) / 6f,
            layout.cellW * 1.18f)
        layout.gridY = contentBottom - layout.gridHeaderH - layout.cellH * 6f
        sizeCells(p, layout)
        return layout
    }

    /** Landscape prints the clock larger, but never wider than its column. */
    private fun fitClock(p: Plate, w: Float, wanted: Float): Float {
        // Every run and gap scales linearly; measure once instead of probing up to 80 sizes.
        return wanted * min(1f, w / clockWidth(p, wanted))
    }

    // ------------------------------------------------------------------ blocks

    /**
     * Date line: 11月 6日 星期六 with the day's lunar name right-aligned on the same baseline. The
     * lunar run carries the day's solar term or first festival without a space — `九月廿九立冬`.
     * Units and the weekday prefix are smaller; numerals and the final weekday glyph are heavier.
     * When the line is too wide all runs shrink together, including their gaps.
     */
    private fun header(p: Plate, layout: Layout, x: Float, w: Float, top: Float) {
        val size = HEADER_SIZE * layout.scale * layout.headerScale
        val baseline = top + size * .86f
        var cursor = x
        cursor += p.textAt(data.today.monthValue.toString(), cursor, baseline, size, ink, p.heavy)
        cursor += p.textAt("月", cursor, baseline, size * HEADER_MINOR, ink, p.bold) + GAP_MONTH * size
        cursor += p.textAt(data.day, cursor, baseline, size, ink, p.heavy) + GAP_DAY * size
        cursor += p.textAt("日", cursor, baseline, size * HEADER_MINOR, ink, p.bold) + GAP_WEEKDAY * size
        val weekdayColor = if (data.today.dayOfWeek >= 6) red else ink
        cursor += p.textAt("星期", cursor, baseline, size * HEADER_MINOR, weekdayColor, p.bold)
        p.textAt(data.weekdayChinese.takeLast(1), cursor, baseline, size, weekdayColor, p.heavy)

        val lunarWidth = p.advance(data.lunarHeadline, size, p.bold)
        p.textAt(data.lunarHeadline, x + w - lunarWidth, baseline, size, ink, p.bold)
    }

    /**
     * How much the date line has to give up so the lunar run keeps its space on the right. There is
     * no floor: the headline can carry a term or festival (`十一月三十重阳节` is nine characters),
     * and the weekday run clearing the lunar run outranks keeping a minimum size.
     */
    private fun headerFit(p: Plate, w: Float, scale: Float, headline: String = data.lunarHeadline): Float {
        val needed = headerWidth(p, HEADER_SIZE * scale, headline)
        if (needed <= w) return 1f
        return w / needed
    }

    /** Everything left of the lunar run, run gaps included. */
    private fun headerLeftWidth(p: Plate, size: Float): Float =
        p.advance(data.today.monthValue.toString(), size, p.heavy) +
            p.advance("月", size * HEADER_MINOR, p.bold) + GAP_MONTH * size +
            p.advance(data.day, size, p.heavy) + GAP_DAY * size +
            p.advance("日", size * HEADER_MINOR, p.bold) + GAP_WEEKDAY * size +
            p.advance("星期", size * HEADER_MINOR, p.bold) +
            p.advance(data.weekdayChinese.takeLast(1), size, p.heavy)

    /** Gaps are em-based, so the whole line gives up size together and never closes up. */
    private fun headerWidth(p: Plate, size: Float, headline: String): Float =
        headerLeftWidth(p, size) + GAP_LUNAR * size + p.advance(headline, size, p.bold)

    /**
     * Where the two halves of the date line land, measured the way [header] prints them. The
     * device acceptance pass walks it over every marked day of the year to prove that the weekday
     * run stops short of the lunar run whatever length the term or festival adds.
     */
    internal fun headerSpan(p: Plate, scale: Float, headline: String = data.lunarHeadline): HeaderSpan {
        val w = width - EDGE * 2f
        val size = HEADER_SIZE * scale * headerFit(p, w, scale, headline)
        return HeaderSpan(
            size = size,
            leftEnd = EDGE + headerLeftWidth(p, size),
            lunarLeft = EDGE + w - p.advance(headline, size, p.bold),
        )
    }

    internal class HeaderSpan(val size: Float, val leftEnd: Float, val lunarLeft: Float)

    private fun rule(p: Plate, x: Float, y: Float, w: Float) {
        p.rule(x, y, w, Plate.withAlpha(ink, .55f), 1f)
    }

    /**
     * The wall clock. The colon is a mark, not a numeral, so it is printed on its own baseline,
     * lifted until its ink sits on the same centre line as the digits around it.
     */
    private fun clock(p: Plate, layout: Layout, x: Float, baseline: Float) {
        val size = layout.clockSize
        val tracking = size * CLOCK_TRACKING
        var cursor = x
        if (face.period.isNotEmpty()) {
            val periodSize = periodSize(p, size)
            val digitTop = p.inkBounds(face.hour + face.minute, size, p.heavy).top
            val periodTop = p.inkBounds(face.period, periodSize, p.bold).top
            cursor += p.textAt(face.period, cursor, baseline + digitTop - periodTop,
                periodSize, ink, p.bold) + size * CLOCK_GAP
        }
        cursor += p.textAt(face.hour, cursor, baseline, size, ink, p.heavy, tracking) + tracking
        val mark = ":"
        val digits = p.inkBounds(face.hour, size, p.heavy)
        val center = (digits.top + digits.bottom) / 2f
        val colon = p.inkBounds(mark, size, p.bold)
        val markBaseline = baseline + center - (colon.top + colon.bottom) / 2f
        cursor += p.textAt(mark, cursor, markBaseline, size, ink, p.bold) + tracking
        p.textAt(face.minute, cursor, baseline, size, ink, p.heavy, tracking)
    }

    /** Half the numeric line's visible height, aligned by ink rather than font ascenders. */
    private fun periodSize(p: Plate, size: Float): Float {
        val digitHeight = p.inkBounds("0123456789", 1000f, p.heavy).height()
        val periodHeight = p.inkBounds(face.period, 1000f, p.bold).height().coerceAtLeast(1)
        return size * digitHeight / periodHeight * .5f
    }

    /** Same runs and gaps as drawing, including the smaller period label. */
    internal fun clockWidth(p: Plate, size: Float): Float {
        val tracking = size * CLOCK_TRACKING
        var total = p.advance(face.hour, size, p.heavy, tracking) + tracking +
            p.advance(":", size, p.bold) + tracking +
            p.advance(face.minute, size, p.heavy, tracking)
        if (face.period.isNotEmpty()) {
            total += p.advance(face.period, periodSize(p, size), p.bold) + size * CLOCK_GAP
        }
        return total
    }

    // ------------------------------------------------------------------ almanac

    /**
     * 宜 and 忌 hang in the bottom-left corner: a filled chip for the word itself and the day's
     * entries beside it, printed in the chip's colour.
     */
    private fun almanac(p: Plate, layout: Layout) {
        var y = layout.almanacY
        y += almanacRow(p, layout, "宜", entries(data.recommends, "诸事不宜"), green, y)
        y += ALMANAC_GAP * layout.scale
        almanacRow(p, layout, "忌", entries(data.avoids, "诸事皆宜"), red, y)
    }

    private fun entries(items: List<String>, fallback: String) = items.ifEmpty { listOf(fallback) }

    private fun almanacRow(p: Plate, layout: Layout, label: String, items: List<String>,
        color: Int, top: Float): Float {
        val chipSize = layout.chipSize
        val chipW = p.advance(label, chipSize, p.bold) + chipSize * .95f
        val chipH = chipSize * 1.55f
        p.paint.reset()
        p.paint.isAntiAlias = true
        p.paint.color = color
        val radius = chipH * .3f
        chipRect.set(layout.almanacX, top, layout.almanacX + chipW, top + chipH)
        p.canvas.drawRoundRect(chipRect, radius, radius, p.paint)
        p.text(label, layout.almanacX, top, chipW, chipH, chipSize, onAccent,
            align = ALIGN_CENTER, typeface = p.bold)

        val textX = layout.almanacX + chipW + chipSize * .6f
        val textW = (layout.almanacX + layout.almanacW - textX).coerceAtLeast(1f)
        val lines = wrapItems(p, items, textW, layout.itemSize)
        val lineHeight = layout.itemSize * 1.42f
        var baseline = top + (chipH - lines.size * lineHeight) / 2f + layout.itemSize * 1.02f
        if (baseline < top + layout.itemSize) baseline = top + layout.itemSize
        lines.forEach { line ->
            p.textAt(line, textX, baseline, layout.itemSize, color, p.bold)
            baseline += lineHeight
        }
        return max(chipH, lines.size * lineHeight)
    }

    /** 宜 / 忌 may grow with the slider, but only inside their own share of the page. */
    private fun almanacSizes(p: Plate, layout: Layout, maxHeight: Float, factor: Float) {
        layout.chipSize = CHIP_SIZE * layout.scale * factor
        layout.itemSize = ITEM_SIZE * layout.scale * factor
        var guard = 0
        while (almanacHeight(p, layout) > maxHeight && layout.itemSize > 7f && guard++ < 60) {
            layout.itemSize *= .94f
            layout.chipSize = max(layout.chipSize * .97f, layout.itemSize * 1.2f)
        }
    }

    private fun almanacHeight(p: Plate, layout: Layout): Float {
        val chipW = p.advance("宜", layout.chipSize, p.bold) + layout.chipSize * .95f
        val textW = (layout.almanacW - chipW - layout.chipSize * .6f).coerceAtLeast(1f)
        val lineHeight = layout.itemSize * 1.42f
        val chipH = layout.chipSize * 1.55f
        val first = wrapItems(p, entries(data.recommends, "诸事不宜"), textW, layout.itemSize).size
        val second = wrapItems(p, entries(data.avoids, "诸事皆宜"), textW, layout.itemSize).size
        return max(chipH, first * lineHeight) + ALMANAC_GAP * layout.scale +
            max(chipH, second * lineHeight)
    }

    /** Entry lists wrap on the plate itself, separated by an ideographic space. */
    private fun wrapItems(p: Plate, items: List<String>, w: Float, size: Float): List<String> {
        if (items.isEmpty()) return emptyList()
        val separator = "　"
        val separatorWidth = p.advance(separator, size, p.bold)
        val lines = mutableListOf(StringBuilder())
        var lineWidth = 0f
        items.forEach { item ->
            val itemWidth = p.advance(item, size, p.bold)
            val hasText = lines.last().isNotEmpty()
            if (hasText && lineWidth + separatorWidth + itemWidth > w) {
                lines += StringBuilder()
                lineWidth = 0f
            }
            if (lines.last().isNotEmpty()) {
                lines.last().append(separator)
                lineWidth += separatorWidth
            }
            lines.last().append(item)
            lineWidth += itemWidth
        }
        return lines.map { it.toString() }.filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ month grid

    private fun sizeCells(p: Plate, layout: Layout) {
        val cellW = layout.cellW
        layout.labelSize = min(layout.cellH * .30f, cellW * .46f)
        val numberH = layout.cellH - layout.labelSize * 1.22f - 1f
        layout.numberSize = fitNumber(p, cellW, numberH)
    }

    /**
     * Largest numeral that still fits the cell: twice the widest digit across, and the height the
     * label leaves behind.
     */
    private fun fitNumber(p: Plate, cellW: Float, numberH: Float): Float {
        var size = min(cellW * .84f, numberH * .86f)
        val maxWidth = cellW * .92f
        var guard = 0
        while (size > 6f && p.advance("88", size, p.heavy) > maxWidth && guard++ < 60) size -= .5f
        return size.coerceAtLeast(6f)
    }

    private fun monthGrid(p: Plate, layout: Layout) {
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        val headerH = layout.gridHeaderH
        // Landscape: the full-width rule under the date line already sits just above the grid,
        // so the grid keeps its own rule only on the portrait page.
        if (!wide) {
            p.rule(layout.gridX, layout.gridY - 7f * layout.scale, layout.gridW,
                Plate.withAlpha(ink, .22f), .8f)
        }
        names.forEachIndexed { index, name ->
            p.text(name, layout.gridX + index * layout.cellW, layout.gridY, layout.cellW, headerH,
                headerH * .88f, color = if (index >= 5) red else Plate.withAlpha(ink, .8f),
                typeface = p.heavy)
        }

        val labelH = layout.labelSize * 1.24f
        val numberH = layout.cellH - labelH - 1f
        val cellH = layout.cellH
        val cellW = layout.cellW

        data.cells.chunked(7).forEachIndexed { row, week ->
            val rowTop = layout.gridY + headerH + row * cellH
            week.forEachIndexed { column, cell ->
                val left = layout.gridX + column * cellW
                val centerX = left + cellW / 2f
                val today = cell.date == data.today
                val weekend = cell.date.dayOfWeek >= 6
                if (today) {
                    p.paint.reset()
                    p.paint.isAntiAlias = true
                    p.paint.color = ink
                    p.canvas.drawCircle(centerX, rowTop + numberH / 2f,
                        min(cellW * .47f, min(numberH * .50f, layout.numberSize * .78f)), p.paint)
                }
                p.text(cell.date.dayOfMonth.toString(), left, rowTop, cellW, numberH,
                    layout.numberSize,
                    color = when {
                        today -> paper
                        !cell.inMonth -> Plate.withAlpha(ink, .26f)
                        weekend -> red
                        else -> ink
                    },
                    bold = true, typeface = p.heavy)
                val fields = (listOf(cell.lunar) + cell.festivals).distinct()
                rotateLabel(p, fields, left, rowTop + numberH + 1f, cellW, labelH,
                    layout.labelSize, if (cell.inMonth) ink else Plate.withAlpha(ink, .26f))
            }
        }
    }

    /**
     * One cell label. Cells that carry a festival rotate the lunar name and the festival names
     * upwards; every cell shares the same timeline so a row never splits its phase. A name too
     * long for the cell walks sideways instead of being cut off with an ellipsis.
     */
    private fun rotateLabel(p: Plate, fields: List<String>, x: Float, y: Float, w: Float, h: Float,
        size: Float, color: Int) {
        if (fields.isEmpty()) return
        val checkpoint = p.canvas.save()
        p.canvas.clipRect(x, y, x + w, y + h)
        if (!motion) {
            drawLabel(p, fields[0], x, y, w, h, size, color, 0L, animate = false)
        } else {
            val phase = now % CYCLE_MS
            val index = ((now / CYCLE_MS) % fields.size).toInt()
            if (fields.size == 1 || phase < HOLD_MS) {
                drawLabel(p, fields[index], x, y, w, h, size, color, phase, animate = true)
            } else {
                val progress = (phase - HOLD_MS).toFloat() / TRANSITION_MS
                val next = fields[(index + 1) % fields.size]
                drawLabel(p, fields[index], x, y - progress * h, w, h, size, color, phase,
                    animate = true)
                // The incoming name is already at its first frame, so it starts centred.
                drawLabel(p, next, x, y + (1f - progress) * h, w, h, size, color, 0L,
                    animate = true)
            }
        }
        p.canvas.restoreToCount(checkpoint)
    }

    /**
     * One label line. A name that fits — or that still reads when shrunk to the cell (three
     * characters do) — is set whole and centred. A longer one, whose shrink would fall below
     * [LabelMarquee.MIN_FIT], walks left after a short pause so every character is read. Neither
     * path ever truncates or prints “…”.
     */
    private fun drawLabel(p: Plate, value: String, x: Float, y: Float, w: Float, h: Float,
        size: Float, color: Int, elapsed: Long, animate: Boolean) {
        if (value.isEmpty()) return
        p.paint.reset()
        p.paint.isAntiAlias = true
        p.paint.typeface = p.bold
        p.paint.color = color
        p.paint.textSize = size
        var width = p.paint.measureText(value)
        val chars = value.codePointCount(0, value.length)
        if (!animate || !LabelMarquee.scrolls(chars, width, w)) {
            // Set the whole name: shrink only as far as the cell requires.
            val ratio = LabelMarquee.fitRatio(width, w)
            if (ratio < 1f) {
                p.paint.textSize = size * ratio
                width = w
            }
            p.canvas.drawText(value, x + (w - width) / 2f, labelBaseline(p, y, h), p.paint)
            return
        }
        val overflow = width - w
        val progress = LabelMarquee.progress(elapsed, overflow)
        p.canvas.drawText(value, x - overflow * progress, labelBaseline(p, y, h), p.paint)
        labelDelay = min(labelDelay, LabelMarquee.delayMillis(elapsed, overflow))
    }

    /** Baseline that centres one line of the current paint inside the label band. */
    private fun labelBaseline(p: Plate, y: Float, h: Float): Float =
        y + h / 2f - (p.paint.descent() + p.paint.ascent()) / 2f

    // ------------------------------------------------------------------ layout

    /** One resolved page: every block has its own top edge, so nothing can overlap. */
    private class Layout(val scale: Float) {
        var margin = 0f
        var contentW = 0f
        var headerScale = 1f
        var ruleY = 0f
        var clockSize = 0f
        var clockBaseline = 0f
        var clockBottom = 0f
        var gridX = 0f
        var gridY = 0f
        var gridW = 0f
        var gridHeaderH = 0f
        var cellW = 0f
        var cellH = 0f
        var numberSize = 0f
        var labelSize = 0f
        var almanacX = 0f
        var almanacY = 0f
        var almanacW = 0f
        var chipSize = 0f
        var itemSize = 0f

        /** The grid may not shrink into an unreadable strip, and the almanac may not reach the clock. */
        val fits: Boolean
            get() = cellH >= MIN_CELL && almanacY >= clockBottom + 6f * scale
    }

    companion object {
        const val HOLD_MS = 3_000L
        const val TRANSITION_MS = 240L
        const val CYCLE_MS = HOLD_MS + TRANSITION_MS
        private const val FRAME_MS = 16L

        /** Page edge; the page keeps a tighter border than the old template so type can grow. */
        private const val EDGE = 14f

        /** Base size for the date numbers, final weekday glyph and lunar headline. */
        internal const val HEADER_SIZE = 46f
        private const val HEADER_MINOR = .72f
        /** Gaps between the runs, in ems of the date line. */
        private const val GAP_MONTH = .12f
        private const val GAP_DAY = .05f
        private const val GAP_WEEKDAY = .30f
        private const val GAP_LUNAR = .34f
        private const val CLOCK_SIZE = 64f
        /** Tracking and the gap after 上午 / 下午, both as a share of the clock size. */
        private const val CLOCK_TRACKING = .016f
        private const val CLOCK_GAP = .14f
        private const val CHIP_SIZE = 19f
        private const val ITEM_SIZE = 15f
        private const val ALMANAC_GAP = 6f
        /** 宜 / 忌 never take more than this share of the page, so the grid keeps its room. */
        private const val ALMANAC_PORTRAIT_SHARE = .26f
        private const val ALMANAC_LANDSCAPE_SHARE = .46f
        private const val GRID_HEADER = 14f
        private const val MIN_CELL = 26f
        private const val MIN_SCALE = .7f

        /** Delay until the page can look different again: a clock minute or the next slide step. */
        fun frameDelay(now: Long): Long {
            val phase = now % CYCLE_MS
            return if (phase >= HOLD_MS) FRAME_MS else HOLD_MS - phase + 2L
        }
    }
}
