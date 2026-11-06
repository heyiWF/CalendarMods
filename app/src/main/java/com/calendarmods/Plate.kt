package com.calendarmods

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.max

internal const val ALIGN_LEFT = 0
internal const val ALIGN_CENTER = 1
internal const val ALIGN_RIGHT = 2

/**
 * Design-space printing plate. All coordinates are the units the layout uses (360 wide in
 * portrait, 640 wide in landscape); the canvas supplies the pixel scale.
 */
internal class Plate(val canvas: Canvas, val ink: Int) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val sans: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    val serif: Typeface = Typeface.create("serif", Typeface.NORMAL)
    val serifBold: Typeface = Typeface.create("serif", Typeface.BOLD)
    val bold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

    fun rule(x: Float, y: Float, w: Float, color: Int = ink, thickness: Float = .65f) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color
        canvas.drawRect(x, y, x + w, y + thickness, paint)
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

    /** One unwrapped line on an explicit baseline; returns the advance so callers align runs. */
    fun textAt(value: String, x: Float, baseline: Float, size: Float, color: Int = ink,
        typeface: Typeface? = null, spacing: Float = 0f): Float {
        if (value.isEmpty()) return 0f
        paint.reset(); paint.isAntiAlias = true
        paint.typeface = typeface ?: sans
        paint.textSize = size
        paint.color = color
        if (spacing <= 0f) {
            canvas.drawText(value, x, baseline, paint)
            return paint.measureText(value)
        }
        // API 14 has no Paint letter spacing: advance per glyph.
        var cursor = x
        value.forEach { ch ->
            canvas.drawText(ch.toString(), cursor, baseline, paint)
            cursor += paint.measureText(ch.toString()) + spacing
        }
        return cursor - x - spacing
    }

    fun advance(value: String, size: Float, typeface: Typeface? = null, spacing: Float = 0f): Float {
        if (value.isEmpty()) return 0f
        paint.reset(); paint.isAntiAlias = true
        paint.typeface = typeface ?: sans
        paint.textSize = size
        if (spacing <= 0f) return paint.measureText(value)
        var total = spacing * (value.length - 1)
        value.forEach { total += paint.measureText(it.toString()) }
        return total
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

    companion object {
        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}
