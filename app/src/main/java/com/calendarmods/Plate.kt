package com.calendarmods

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

internal const val ALIGN_LEFT = 0
internal const val ALIGN_CENTER = 1
internal const val ALIGN_RIGHT = 2

/**
 * Design-space printing plate. All coordinates are the same units the Compose version used
 * (360x600 portrait, 640x360 landscape), so the layouts translate one to one.
 */
internal class Plate(val canvas: Canvas, val ink: Int, val paper: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Compose's Print defaulted to sans-serif; themes that asked for serif pass it explicitly.
    val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val serif: Typeface = Typeface.create("serif", Typeface.NORMAL)
    val serifBold: Typeface = Typeface.create("serif", Typeface.BOLD)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val condensed: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    fun color(value: Long): Int = value.toInt()

    fun rule(x: Float, y: Float, w: Float, color: Int = ink, thickness: Float = .65f) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color
        canvas.drawRect(x, y, x + w, y + thickness, paint)
    }

    fun box(x: Float, y: Float, w: Float, h: Float, color: Int = ink, stroke: Float = 0f) {
        paint.reset(); paint.isAntiAlias = true; paint.color = color
        if (stroke > 0f) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke
            canvas.drawRect(x, y, x + w, y + h, paint)
        } else canvas.drawRect(x, y, x + w, y + h, paint)
    }

    /** The spiral binding used by the yellow pad and the hanging page. */
    fun rings(x: Float, y: Float, w: Float, h: Float, count: Int, dark: Boolean = false) {
        if (count <= 0) return
        val gap = w / count
        paint.reset(); paint.isAntiAlias = true
        repeat(count) { index ->
            val cx = x + gap * (index + .5f)
            paint.color = 0xFF504B40.toInt()
            canvas.drawRoundRect(RectF(cx - 4, y + h * .61f, cx + 4, y + h * .61f + 6), 2f, 2f, paint)
            paint.color = if (dark) 0xFF302D27.toInt() else 0xFF7B7A70.toInt()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.8f
            canvas.drawRoundRect(RectF(cx - 2.3f, y, cx + 2.3f, y + h * .82f), 3f, 3f, paint)
            paint.style = Paint.Style.FILL
            paint.color = 0xBFFFFFFF.toInt(); paint.strokeWidth = .65f
            canvas.drawLine(cx - 1.5f, y + 2, cx - 1.5f, y + h * .67f, paint)
        }
    }

    /**
     * Prints text inside a box: wrapped, vertically centred, shrunk until it fits the height.
     * `maxLines` mirrors the Compose ellipsis limit.
     */
    fun text(value: String, x: Float, y: Float, w: Float, h: Float, size: Float,
        color: Int = ink, bold: Boolean = false, align: Int = ALIGN_CENTER,
        spacing: Float = 0f, maxLines: Int = Int.MAX_VALUE, typeface: Typeface? = null) {
        if (value.isEmpty() || w <= 0f || h <= 0f) return
        paint.reset(); paint.isAntiAlias = true
        paint.typeface = typeface ?: if (bold) this.bold else sans
        paint.color = color
        var actual = size
        var lines: List<String>
        while (true) {
            paint.textSize = actual
            lines = wrap(value, w, maxLines)
            val lineHeight = actual * 1.17f
            if (lines.size * lineHeight <= h || actual <= 1f) break
            actual -= max(.5f, actual * .02f)
        }
        val lineHeight = actual * 1.17f
        val metrics = paint.fontMetrics
        val glyphHeight = metrics.descent - metrics.ascent
        val total = lineHeight * lines.size
        var baseline = y + (h - total) / 2f - metrics.ascent + (lineHeight - glyphHeight) / 2f
        lines.forEach { line ->
            drawLine(line, x, w, baseline, align, spacing)
            baseline += lineHeight
        }
    }

    private fun drawLine(line: String, x: Float, w: Float, baseline: Float, align: Int, spacing: Float) {
        if (line.isEmpty()) return
        if (spacing <= 0f) {
            val left = when (align) {
                ALIGN_LEFT -> x
                ALIGN_RIGHT -> x + w - paint.measureText(line)
                else -> x + (w - paint.measureText(line)) / 2f
            }
            canvas.drawText(line, left, baseline, paint)
            return
        }
        // API 14 has no Paint letter spacing: advance per glyph.
        val widths = FloatArray(line.length) { paint.measureText(line, it, it + 1) }
        val total = widths.sum() + spacing * (line.length - 1)
        var cursor = when (align) {
            ALIGN_LEFT -> x
            ALIGN_RIGHT -> x + w - total
            else -> x + (w - total) / 2f
        }
        line.forEachIndexed { index, ch ->
            canvas.drawText(ch.toString(), cursor, baseline, paint)
            cursor += widths[index] + spacing
        }
    }

    private fun wrap(value: String, width: Float, maxLines: Int): List<String> {
        val result = mutableListOf<String>()
        value.split('\n').forEach { paragraph ->
            if (paragraph.isEmpty()) { result += ""; return@forEach }
            var current = StringBuilder()
            var hasWord = false
            paragraph.split(' ').forEachIndexed { wordIndex, word ->
                if (wordIndex > 0 && hasWord) {
                    val candidate = "$current "
                    if (paint.measureText(candidate + word) <= width) current = StringBuilder(candidate)
                    else { result += current.toString(); current = StringBuilder(); hasWord = false }
                }
                word.forEach { ch ->
                    if (current.isNotEmpty() && paint.measureText(current.toString() + ch) > width) {
                        result += current.toString(); current = StringBuilder()
                    }
                    current.append(ch)
                }
                hasWord = current.isNotEmpty()
            }
            if (current.isNotEmpty()) result += current.toString()
        }
        if (result.isEmpty()) return listOf("")
        if (result.size > maxLines) {
            val clipped = result.take(maxLines).toMutableList()
            val last = clipped.last()
            var text = last
            while (text.isNotEmpty() && paint.measureText("$text…") > width) text = text.dropLast(1)
            clipped[clipped.lastIndex] = "$text…"
            return clipped
        }
        return result
    }

    /**
     * The oversized date numeral, fitted to the box the layout hands over.
     *
     * Drawn from the glyph outline instead of drawText: a vendor ROM can resolve a requested
     * family to a face that measures but paints nothing, and a degenerate measurement used to
     * blow the text size up until nothing landed inside the box. Fitting the outline keeps the
     * numeral inside its band on every screen and falls back to the default face when needed.
     */
    fun bigDay(day: String, x: Float, y: Float, w: Float, h: Float, color: Int = ink,
        typeface: Typeface? = bold, scale: Float = .82f) {
        if (w <= 1f || h <= 1f || day.isEmpty()) return
        paint.reset(); paint.isAntiAlias = true
        paint.color = color
        paint.typeface = typeface ?: bold
        // Keep glyph extraction at a normal size even on old vendor font rasterizers.
        paint.textSize = 100f
        val key = NumeralKey(day, paint.typeface)
        val glyph = synchronized(numerals) {
            numerals[key] ?: run {
                val path = Path()
                val box = RectF()
                paint.getTextPath(day, 0, day.length, 0f, 0f, path)
                path.computeBounds(box, true)
                if (box.width() < .01f || box.height() < .01f) {
                    paint.typeface = Typeface.DEFAULT_BOLD
                    path.reset()
                    paint.getTextPath(day, 0, day.length, 0f, 0f, path)
                    path.computeBounds(box, true)
                }
                Numeral(path, box).also { numerals.put(key, it) }
            }
        }
        val outline = Path(glyph.path)
        val bounds = glyph.bounds
        if (bounds.width() < .01f || bounds.height() < .01f) {
            // A malformed vendor face must not leave the date blank.
            text(day, x, y, w, h, min(w * .72f, h * .9f), color,
                typeface = Typeface.DEFAULT_BOLD, maxLines = 1)
            return
        }
        // Same size rule as the Compose original: the text is min(0.72 w, 0.9 h) tall, so 1 and
        // 28 print at one size; only shrink further when the outline would leave the box.
        var fit = min(w * .72f, h * .9f) / 100f
        if (bounds.width() * fit > w || bounds.height() * fit > h) {
            fit = min(w * scale / bounds.width(), h * scale / bounds.height())
        }
        if (!fit.isFinite() || fit <= 0f) return
        val place = Matrix()
        place.postScale(fit, fit)
        place.postTranslate(x + (w - bounds.width() * fit) / 2f - bounds.left * fit,
            y + (h - bounds.height() * fit) / 2f - bounds.top * fit)
        outline.transform(place)
        canvas.drawPath(outline, paint)
    }

    /** Sunday-first month grid copied from the Compose layout, including the lunar row. */
    fun monthGrid(data: CalendarData, x: Float, y: Float, w: Float, h: Float,
        lunar: Boolean = false, outlined: Boolean = false, small: Boolean = false, color: Int = ink) {
        val headerHeight = if (small) 16f else 25f
        val cellWidth = w / 7f
        val rowHeight = (h - headerHeight) / 6f
        val header = listOf("日", "一", "二", "三", "四", "五", "六")
        header.forEachIndexed { index, name ->
            text(name, x + index * cellWidth, y, cellWidth, headerHeight, if (small) 8f else 10f,
                color = if (index == 0 || index == 6) 0xFFAD4B42.toInt() else color)
        }
        data.cells.chunked(7).forEachIndexed { row, week ->
            week.forEachIndexed { column, cell ->
                val cx = x + column * cellWidth
                val cy = y + headerHeight + row * rowHeight
                if (outlined) box(cx, cy, cellWidth, rowHeight, color = withAlpha(color, .18f), stroke = .35f)
                val today = cell.date == data.today
                val weekend = cell.date.dayOfWeek in listOf(6, 7)
                val diameter = if (small) 16f else if (lunar) 19f else 24f
                val fit = min(1f, min(cellWidth / diameter, rowHeight / (diameter + if (lunar) 9f else 0f)))
                val size = diameter * fit
                val centerX = cx + cellWidth / 2f
                if (today) {
                    paint.reset(); paint.isAntiAlias = true; paint.color = 0xFFAD4B42.toInt()
                    canvas.drawCircle(centerX, cy + size / 2f + 1f, size / 2f, paint)
                }
                val numberColor = when {
                    today -> 0xFFFFFFFF.toInt()
                    !cell.inMonth -> withAlpha(color, .28f)
                    weekend -> 0xFFAD4B42.toInt()
                    else -> color
                }
                text(cell.date.dayOfMonth.toString(), centerX - size / 2f, cy, size, size,
                    ((if (small) 9f else 14f) * fit).coerceAtLeast(1f), color = numberColor, bold = today)
                if (lunar) {
                    val label = cell.festivals.firstOrNull() ?: cell.lunar
                    val labelColor = when {
                        !cell.inMonth -> withAlpha(color, .25f)
                        cell.festivals.isNotEmpty() -> 0xFFAD4B42.toInt()
                        else -> withAlpha(color, .6f)
                    }
                    text(label, cx, cy + size + 1f, cellWidth, rowHeight - size - 1f,
                        (7f * fit).coerceAtLeast(1f), color = labelColor, maxLines = 1)
                }
            }
        }
    }

    fun festival(data: CalendarData, x: Float, y: Float, w: Float, h: Float, size: Float = 12f) {
        if (data.festivals.isEmpty()) return
        box(x, y, w, h, color = withAlpha(ink, .6f), stroke = .6f)
        text(data.festivals.joinToString(" · "), x + 16f, y + 7f, w - 32f, h - 14f, size, maxLines = 2)
    }

    fun cat(x: Float, y: Float, w: Float, h: Float, color: Int = 0xFF526250.toInt()) {
        val path = Path().apply {
            moveTo(x + w * .15f, y + h * .82f)
            cubicTo(x + w * .6f, y + h * 1.1f, x + w * .85f, y + h * .7f, x + w * .68f, y + h * .5f)
            lineTo(x + w * .54f, y + h * .5f)
            lineTo(x + w * .47f, y + h * .16f)
            lineTo(x + w * .33f, y + h * .3f)
            lineTo(x + w * .18f, y + h * .14f)
            close()
        }
        paint.reset(); paint.isAntiAlias = true
        paint.color = withAlpha(color, .3f); canvas.drawPath(path, paint)
        paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(x + w * .26f, y + h * .42f, 1f, paint)
        canvas.drawCircle(x + w * .42f, y + h * .42f, 1f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        val oval = RectF(x + w * .56f, y + h * .29f, x + w * .92f, y + h * .74f)
        canvas.drawArc(oval, -80f, 245f, false, paint)
    }

    fun image(bitmap: Bitmap, x: Float, y: Float, w: Float, h: Float, fill: ImageFill) {
        val source = Rect(0, 0, bitmap.width, bitmap.height)
        val target = RectF(x, y, x + w, y + h)
        when (fill) {
            ImageFill.STRETCH -> canvas.drawBitmap(bitmap, source, target, imagePaint())
            ImageFill.CROP -> {
                val scale = max(w / bitmap.width, h / bitmap.height)
                val cropW = (w / scale).toInt().coerceIn(1, bitmap.width)
                val cropH = (h / scale).toInt().coerceIn(1, bitmap.height)
                val left = (bitmap.width - cropW) / 2
                val top = (bitmap.height - cropH) / 2
                canvas.save()
                canvas.clipRect(target)
                canvas.drawBitmap(bitmap, Rect(left, top, left + cropW, top + cropH), target, imagePaint())
                canvas.restore()
            }
            ImageFill.FIT -> {
                val scale = min(w / bitmap.width, h / bitmap.height)
                val drawW = bitmap.width * scale
                val drawH = bitmap.height * scale
                canvas.drawBitmap(bitmap, null,
                    RectF(x + (w - drawW) / 2f, y + (h - drawH) / 2f, x + (w + drawW) / 2f, y + (h + drawH) / 2f),
                    imagePaint())
            }
        }
    }

    private fun imagePaint(): Paint {
        paint.reset(); paint.isAntiAlias = true; paint.isFilterBitmap = true; paint.isDither = true
        return paint
    }

    companion object {
        private data class NumeralKey(val text: String, val font: Typeface?)
        private data class Numeral(val path: Path, val bounds: RectF)
        private val numerals = android.util.LruCache<NumeralKey, Numeral>(128)
        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}
