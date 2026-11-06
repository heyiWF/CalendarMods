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
    private val time: String,
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

    fun draw(canvas: Canvas) {
        val p = Plate(canvas, ink)
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
        val daySize = DAY_SIZE * scale * layout.headerScale
        layout.ruleY = EDGE + daySize * 1.02f + 5f * scale
        layout.clockSize = CLOCK_SIZE * scale
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
        val daySize = DAY_SIZE * scale * layout.headerScale
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
        var size = wanted
        var guard = 0
        while (size > 10f && p.advance(time, size, p.bold, 1f) > w && guard++ < 60) size -= 1f
        return size
    }

    // ------------------------------------------------------------------ blocks

    /** Date line: 11月 6日 星期五, with the day numeral set larger and bolder than its neighbours. */
    private fun header(p: Plate, layout: Layout, x: Float, w: Float, top: Float) {
        val scale = layout.scale * layout.headerScale
        val baseline = top + DAY_SIZE * scale * .86f
        var cursor = x
        cursor += p.textAt(data.monthLabel, cursor, baseline, MONTH_SIZE * scale, ink, p.serifBold) + 4f * scale
        cursor += p.textAt(data.day, cursor, baseline, DAY_SIZE * scale, ink, p.serifBold) + 2f * scale
        cursor += p.textAt("日", cursor, baseline, MONTH_SIZE * scale, ink, p.serifBold) + 12f * scale
        p.textAt(data.weekdayChinese, cursor, baseline, WEEKDAY_SIZE * scale, ink, p.serifBold)

        val lunarSize = LUNAR_SIZE * scale
        val lunarWidth = p.advance(data.lunar, lunarSize, p.serifBold)
        p.textAt(data.lunar, x + w - lunarWidth, baseline, lunarSize, ink, p.serifBold)
    }

    /** How much the date line has to give up so the lunar name keeps its space on the right. */
    private fun headerFit(p: Plate, w: Float, scale: Float): Float {
        val needed = p.advance(data.monthLabel, MONTH_SIZE * scale, p.serifBold) + 4f * scale +
            p.advance(data.day, DAY_SIZE * scale, p.serifBold) + 2f * scale +
            p.advance("日", MONTH_SIZE * scale, p.serifBold) + 12f * scale +
            p.advance(data.weekdayChinese, WEEKDAY_SIZE * scale, p.serifBold) +
            14f * scale + p.advance(data.lunar, LUNAR_SIZE * scale, p.serifBold)
        if (needed <= w) return 1f
        return (w / needed).coerceIn(.55f, 1f)
    }

    private fun rule(p: Plate, x: Float, y: Float, w: Float) {
        p.rule(x, y, w, Plate.withAlpha(ink, .55f), 1f)
    }

    private fun clock(p: Plate, layout: Layout, x: Float, baseline: Float) {
        p.textAt(time, x, baseline, layout.clockSize, ink, p.bold, spacing = 1f)
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
        val chipW = p.advance(label, chipSize, p.serifBold) + chipSize * .95f
        val chipH = chipSize * 1.55f
        p.paint.reset()
        p.paint.isAntiAlias = true
        p.paint.color = color
        val radius = chipH * .3f
        chipRect.set(layout.almanacX, top, layout.almanacX + chipW, top + chipH)
        p.canvas.drawRoundRect(chipRect, radius, radius, p.paint)
        p.text(label, layout.almanacX, top, chipW, chipH, chipSize, onAccent,
            align = ALIGN_CENTER, typeface = p.serifBold)

        val textX = layout.almanacX + chipW + chipSize * .6f
        val textW = (layout.almanacX + layout.almanacW - textX).coerceAtLeast(1f)
        val lines = wrapItems(p, items, textW, layout.itemSize)
        val lineHeight = layout.itemSize * 1.42f
        var baseline = top + (chipH - lines.size * lineHeight) / 2f + layout.itemSize * 1.02f
        if (baseline < top + layout.itemSize) baseline = top + layout.itemSize
        lines.forEach { line ->
            p.textAt(line, textX, baseline, layout.itemSize, color, p.serifBold)
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
        val chipW = p.advance("宜", layout.chipSize, p.serifBold) + layout.chipSize * .95f
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
        val separatorWidth = p.advance(separator, size, p.serifBold)
        val lines = mutableListOf(StringBuilder())
        var lineWidth = 0f
        items.forEach { item ->
            val itemWidth = p.advance(item, size, p.serifBold)
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
        while (size > 6f && p.advance("88", size, p.bold) > maxWidth && guard++ < 60) size -= .5f
        return size.coerceAtLeast(6f)
    }

    private fun monthGrid(p: Plate, layout: Layout) {
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        val headerH = layout.gridHeaderH
        p.rule(layout.gridX, layout.gridY - 7f * layout.scale, layout.gridW,
            Plate.withAlpha(ink, .22f), .8f)
        names.forEachIndexed { index, name ->
            p.text(name, layout.gridX + index * layout.cellW, layout.gridY, layout.cellW, headerH,
                headerH * .88f, color = if (index >= 5) red else Plate.withAlpha(ink, .8f),
                typeface = p.serifBold)
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
                    bold = true, typeface = if (today) p.bold else p.serifBold)
                val fields = (listOf(cell.lunar) + cell.festivals).distinct()
                rotateLabel(p, fields, left, rowTop + numberH + 1f, cellW, labelH,
                    layout.labelSize, if (cell.inMonth) ink else Plate.withAlpha(ink, .26f))
            }
        }
    }

    /**
     * One cell label. Cells that carry a festival rotate the lunar name and the festival names
     * upwards; every cell shares the same timeline so a row never splits its phase.
     */
    private fun rotateLabel(p: Plate, fields: List<String>, x: Float, y: Float, w: Float, h: Float,
        size: Float, color: Int) {
        if (fields.isEmpty()) return
        val checkpoint = p.canvas.save()
        p.canvas.clipRect(x, y, x + w, y + h)
        if (fields.size == 1 || !motion) {
            p.text(fields[0], x, y, w, h, size, color, maxLines = 1, typeface = p.serifBold)
        } else {
            val phase = now % CYCLE_MS
            val index = ((now / CYCLE_MS) % fields.size).toInt()
            val next = fields[(index + 1) % fields.size]
            if (phase < HOLD_MS) {
                p.text(fields[index], x, y, w, h, size, color, maxLines = 1, typeface = p.serifBold)
            } else {
                val progress = (phase - HOLD_MS).toFloat() / TRANSITION_MS
                p.text(fields[index], x, y - progress * h, w, h, size, color, maxLines = 1,
                    typeface = p.serifBold)
                p.text(next, x, y + (1f - progress) * h, w, h, size, color, maxLines = 1,
                    typeface = p.serifBold)
            }
        }
        p.canvas.restoreToCount(checkpoint)
    }

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
        private const val DAY_SIZE = 46f
        private const val MONTH_SIZE = 20f
        private const val WEEKDAY_SIZE = 18f
        private const val LUNAR_SIZE = 16f
        private const val CLOCK_SIZE = 64f
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
