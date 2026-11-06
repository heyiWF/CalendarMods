package com.calendarmods

import android.graphics.Canvas
import kotlin.math.min

/**
 * The bundled daily page: a date line, the wall clock, 宜 / 忌 and a Monday-first month grid whose
 * lunar labels rotate with festivals and solar terms. Every block shares one left margin and one
 * right margin, so the header, the clock, the almanac lines and the grid line up down the page.
 */
internal class DailyPage(
    private val theme: CalendarTheme,
    private val data: CalendarData,
    private val time: String,
    private val wide: Boolean,
    private val width: Float,
    private val height: Float,
    private val motion: Boolean,
    private val now: Long,
) {
    private val ink = 0xFF23201C.toInt()
    private val muted = 0xFF8A8378.toInt()
    private val red = 0xFFB03A31.toInt()

    fun draw(canvas: Canvas) {
        val p = Plate(canvas, ink)
        canvas.drawColor(theme.surfaces.paper.toInt())
        if (wide) landscape(p) else portrait(p)
    }

    // ------------------------------------------------------------------ portrait

    private fun portrait(p: Plate) {
        val margin = 26f
        val contentW = width - margin * 2f
        val baseline = header(p, margin, 30f, contentW)
        val ruleY = baseline + 13f
        p.rule(margin, ruleY, contentW, Plate.withAlpha(ink, .55f), .9f)

        val clockBaseline = clock(p, margin, ruleY + 26f + 64f * .78f)

        val contentTop = clockBaseline + 34f
        val contentBottom = height - 30f
        val gridSpan = ((contentBottom - contentTop) * .60f).coerceAtLeast(60f)
        val headerH = 15f
        val cellW = contentW / 7f
        val cellH = min((gridSpan - headerH) / 6f, cellW * 1.04f)
        val gridH = cellH * 6f + headerH
        val gridTop = contentBottom - gridH

        almanac(p, margin, contentTop, contentW, gridTop - 24f - contentTop)
        monthGrid(p, margin, gridTop, contentW, cellH)
    }

    // ------------------------------------------------------------------ landscape

    private fun landscape(p: Plate) {
        val margin = 28f
        val contentW = width - margin * 2f
        val leftW = contentW * .54f
        val gridX = margin + leftW + 26f
        val gridW = width - margin - gridX

        val baseline = header(p, margin, margin + 2f, leftW)
        val ruleY = baseline + 13f
        p.rule(margin, ruleY, leftW, Plate.withAlpha(ink, .55f), .9f)

        val clockBaseline = clock(p, margin, ruleY + 24f + 64f * .78f)
        val contentTop = clockBaseline + 32f
        almanac(p, margin, contentTop, leftW, height - margin - 6f - contentTop)

        val cellW = gridW / 7f
        val cellH = min((height - margin * 2f - 15f) / 6f, cellW * 1.04f)
        val gridH = cellH * 6f + 15f
        monthGrid(p, gridX, margin + (height - margin * 2f - gridH) / 2f, gridW, cellH)
    }

    // ------------------------------------------------------------------ blocks

    /** Date line: 11月 6日 星期五, with the day numeral set larger and bolder than its neighbours. */
    private fun header(p: Plate, x: Float, top: Float, w: Float): Float {
        val baseline = top + 42f
        var cursor = x
        cursor += p.textAt(data.monthLabel, cursor, baseline, 20f, ink, p.serif) + 5f
        cursor += p.textAt(data.day, cursor, baseline, 42f, ink, p.serifBold) + 2f
        cursor += p.textAt("日", cursor, baseline, 20f, ink, p.serif) + 15f
        p.textAt(data.weekdayChinese, cursor, baseline, 16f, ink, p.serif)

        val lunarWidth = p.advance(data.lunar, 15f, p.serif)
        p.textAt(data.lunar, x + w - lunarWidth, baseline, 15f, muted, p.serif)
        return baseline
    }

    private fun clock(p: Plate, x: Float, baseline: Float): Float {
        p.textAt(time, x, baseline, 64f, ink, p.light, spacing = 1.2f)
        return baseline
    }

    /**
     * 宜 and 忌, spread over whatever height the clock leaves. Equal gaps above, between and
     * below the two lists keep the column from leaving one awkward hole under 忌.
     */
    private fun almanac(p: Plate, x: Float, y: Float, w: Float, h: Float) {
        if (h <= 0f) return
        val labelH = 22f
        val size = 9.5f
        // The almanac prints a summary line on days that list no entry for one side.
        val gives = data.recommends.ifEmpty { listOf("诸事不宜") }
        val avoids = data.avoids.ifEmpty { listOf("诸事皆宜") }
        val linesA = wrapItems(p, gives, w, size)
        val linesB = wrapItems(p, avoids, w, size)
        val heightA = labelH + 2f + linesA.size * size * LINE_HEIGHT
        val heightB = labelH + 2f + linesB.size * size * LINE_HEIGHT
        val spare = h - heightA - heightB
        val gap = if (spare >= 48f) (spare / 3f).coerceAtMost(90f) else 10f
        block(p, "宜", gives, x, y + gap, w, heightA, labelH, size)
        block(p, "忌", avoids, x, y + gap + heightA + gap, w, heightB, labelH, size)
    }

    private fun block(p: Plate, label: String, items: List<String>, x: Float, y: Float, w: Float,
        h: Float, labelH: Float, size: Float) {
        p.text(label, x, y, 24f, labelH, 18f, ink, bold = true, align = ALIGN_LEFT, typeface = p.serifBold)
        writeItems(p, items, x, y + labelH + 2f, w, h - labelH - 2f, size)
    }

    /** Wraps the 宜 / 忌 list on the plate itself so the leading stays wider than a paragraph. */
    private fun writeItems(p: Plate, items: List<String>, x: Float, y: Float, w: Float, h: Float, startSize: Float) {
        if (items.isEmpty() || h <= 0f) return
        // Keep every entry on the page: step the type down until the list fits its block.
        var size = startSize
        var lines: List<String>
        while (true) {
            lines = wrapItems(p, items, w, size)
            if (lines.size * size * LINE_HEIGHT <= h || size <= 7.5f) break
            size -= .5f
        }
        var baseline = y + size * 1.15f
        lines.forEach { line ->
            p.textAt(line, x, baseline, size, muted, p.serif)
            baseline += size * LINE_HEIGHT
        }
    }

    private fun wrapItems(p: Plate, items: List<String>, w: Float, size: Float): List<String> {
        if (items.isEmpty()) return emptyList()
        val separator = "　"
        val separatorWidth = p.advance(separator, size, p.serif)
        val lines = mutableListOf(StringBuilder())
        var lineWidth = 0f
        items.forEach { item ->
            val itemWidth = p.advance(item, size, p.serif)
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

    private fun monthGrid(p: Plate, x: Float, y: Float, w: Float, cellH: Float) {
        val headerH = 15f
        val cellW = w / 7f
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        p.rule(x, y - 10f, w, Plate.withAlpha(ink, .18f), .5f)
        names.forEachIndexed { index, name ->
            p.text(name, x + index * cellW, y, cellW, headerH, 8f,
                color = if (index >= 5) red else muted, typeface = p.serif)
        }

        val numberSize = cellH * .40f
        val labelSize = (cellH * .17f).coerceIn(5.5f, 8.5f)
        val labelH = labelSize * 1.25f
        val numberH = cellH - labelH - 1f

        data.cells.chunked(7).forEachIndexed { row, week ->
            val rowTop = y + headerH + row * cellH
            week.forEachIndexed { column, cell ->
                val left = x + column * cellW
                val centerX = left + cellW / 2f
                val today = cell.date == data.today
                val weekend = cell.date.dayOfWeek >= 6
                if (today) {
                    p.paint.reset(); p.paint.isAntiAlias = true
                    p.paint.color = ink
                    p.canvas.drawCircle(centerX, rowTop + numberH / 2f,
                        min(cellW * .48f, numberH * .5f), p.paint)
                }
                p.text(cell.date.dayOfMonth.toString(), left, rowTop, cellW, numberH, numberSize,
                    color = when {
                        today -> theme.surfaces.paper.toInt()
                        !cell.inMonth -> Plate.withAlpha(muted, .40f)
                        weekend -> red
                        else -> ink
                    },
                    bold = today, typeface = if (today) p.bold else p.sans)
                val labelColor = when {
                    !cell.inMonth -> Plate.withAlpha(muted, .35f)
                    cell.festivals.isNotEmpty() || weekend -> red
                    else -> muted
                }
                val fields = (listOf(cell.lunar) + cell.festivals).distinct()
                rotateLabel(p, fields, left, rowTop + numberH + 1f, cellW, labelH, labelSize, labelColor)
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
            p.text(fields[0], x, y, w, h, size, color, maxLines = 1, typeface = p.serif)
        } else {
            val phase = now % CYCLE_MS
            val index = ((now / CYCLE_MS) % fields.size).toInt()
            val next = fields[(index + 1) % fields.size]
            if (phase < HOLD_MS) {
                p.text(fields[index], x, y, w, h, size, color, maxLines = 1, typeface = p.serif)
            } else {
                val progress = (phase - HOLD_MS).toFloat() / TRANSITION_MS
                p.text(fields[index], x, y - progress * h, w, h, size, color, maxLines = 1, typeface = p.serif)
                p.text(next, x, y + (1f - progress) * h, w, h, size, color, maxLines = 1, typeface = p.serif)
            }
        }
        p.canvas.restoreToCount(checkpoint)
    }

    companion object {
        const val HOLD_MS = 3_000L
        const val TRANSITION_MS = 240L
        const val CYCLE_MS = HOLD_MS + TRANSITION_MS
        private const val FRAME_MS = 16L
        private const val LINE_HEIGHT = 1.75f

        /** Delay until the page can look different again: a clock minute or the next slide step. */
        fun frameDelay(now: Long): Long {
            val phase = now % CYCLE_MS
            return if (phase >= HOLD_MS) FRAME_MS else HOLD_MS - phase + 2L
        }
    }
}
