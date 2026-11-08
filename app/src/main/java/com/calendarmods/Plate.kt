package com.calendarmods

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
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
    val sans: Typeface get() = regularFace
    val bold: Typeface get() = supportingFace
    val heavy: Typeface get() = numeralFace
    private val bounds = Rect()

    fun inkBounds(value: String, size: Float, typeface: Typeface): Rect {
        paint.typeface = typeface
        paint.textSize = size
        paint.getTextBounds(value, 0, value.length, bounds)
        return bounds
    }

    fun rule(x: Float, y: Float, w: Float, color: Int = ink, thickness: Float = .65f) {
        paint.reset(); paint.isAntiAlias = true
        paint.color = color
        canvas.drawRect(x, y, x + w, y + thickness, paint)
    }

    /**
     * Prints text inside a box: wrapped, vertically centred, shrunk until it fits the height.
     * Text is never truncated, so a caller that cannot grow the box must measure first.
     */
    fun text(value: String, x: Float, y: Float, w: Float, h: Float, size: Float,
        color: Int = ink, bold: Boolean = false, align: Int = ALIGN_CENTER,
        spacing: Float = 0f, typeface: Typeface? = null) {
        if (value.isEmpty() || w <= 0f || h <= 0f) return
        paint.reset(); paint.isAntiAlias = true
        paint.typeface = typeface ?: if (bold) this.bold else sans
        paint.color = color
        var actual = size
        var lines: List<String>
        while (true) {
            paint.textSize = actual
            lines = wrap(value, w)
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

    /**
     * Baseline shift that lifts a mark such as “:” to the optical centre of the digits it sits
     * between. Measured from the ink boxes, so it holds for any font the system resolves.
     */
    fun centerShift(reference: String, mark: String, size: Float, typeface: Typeface? = null): Float {
        if (reference.isEmpty() || mark.isEmpty()) return 0f
        paint.reset(); paint.isAntiAlias = true
        paint.typeface = typeface ?: sans
        paint.textSize = size
        val digits = Rect()
        paint.getTextBounds(reference, 0, reference.length, digits)
        val symbol = Rect()
        paint.getTextBounds(mark, 0, mark.length, symbol)
        val digitCenter = (digits.top.toFloat() + digits.bottom) / 2f
        val markCenter = (symbol.top.toFloat() + symbol.bottom) / 2f
        return digitCenter - markCenter
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

    private fun wrap(value: String, width: Float): List<String> {
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
        return if (result.isEmpty()) listOf("") else result
    }

    companion object {
        // Resolve once, not once per animation frame. Android chooses the closest supported
        // system weight, including the fallback family for Chinese glyphs.
        private val regularFace = Typeface.create("sans-serif", Typeface.NORMAL)
        private val legacyBoldFace = Typeface.create(regularFace, Typeface.BOLD)
        private val numeralFace = if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(regularFace, 900, false)
        } else if (Build.VERSION.SDK_INT >= 21) {
            // An unavailable OEM family name resolves to the default regular face.
            Typeface.create("sans-serif-black", Typeface.NORMAL).let {
                if (it == regularFace || it == Typeface.DEFAULT) legacyBoldFace else it
            }
        } else legacyBoldFace
        private val supportingFace = if (Build.VERSION.SDK_INT >= 28) {
            Typeface.create(regularFace, 800, false)
        } else if (Build.VERSION.SDK_INT >= 21) {
            if (numeralFace == legacyBoldFace) regularFace else legacyBoldFace
        } else regularFace

        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}
