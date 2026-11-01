package com.calendarmods

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import kotlin.math.ceil
import kotlin.math.max

/** Rasterize only the large numeral on the CPU, then upload a bounded bitmap.
 * Older hardware Canvas drivers can silently omit a scaled glyph path in fullscreen even
 * though the same path works in a software thumbnail. Keep the rest of the page accelerated. */
internal object DateNumerals {
    private data class GlyphKey(val text: String, val font: Typeface?)
    internal class Glyph(val path: Path, val bounds: RectF)
    private data class RasterKey(val glyph: Glyph, val edge: Int, val color: Int)
    private val glyphs = LruCache<GlyphKey, Glyph>(128)
    private val rasters = object : LruCache<RasterKey, Bitmap>(
        minOf(8 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: RasterKey, value: Bitmap) = value.rowBytes * value.height
    }

    @Synchronized fun glyph(text: String, font: Typeface?): Glyph {
        val key = GlyphKey(text, font)
        glyphs.get(key)?.let { return it }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = 100f }
        val path = Path(); val bounds = RectF()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        path.computeBounds(bounds, true)
        if (bounds.width() < .01f || bounds.height() < .01f) {
            paint.typeface = Typeface.DEFAULT_BOLD
            path.reset()
            paint.getTextPath(text, 0, text.length, 0f, 0f, path)
            path.computeBounds(bounds, true)
        }
        return Glyph(path, bounds).also { glyphs.put(key, it) }
    }

    fun draw(canvas: Canvas, glyph: Glyph, x: Float, y: Float, w: Float, h: Float, color: Int) {
        if (w <= 0f || h <= 0f || !w.isFinite() || !h.isFinite() || glyph.bounds.isEmpty) return
        val target = RectF(x, y, x + w, y + h)
        val device = RectF(target)
        val matrix = Matrix()
        @Suppress("DEPRECATION")
        canvas.getMatrix(matrix)
        matrix.mapRect(device)
        val needed = max(device.width(), device.height())
        val edge = when { needed <= 128f -> 128; needed <= 256f -> 256; needed <= 512f -> 512; else -> 1024 }
        val bitmap = raster(glyph, edge, color) ?: raster(glyph, 128, color) ?: return
        // Avoid recycling cached bitmaps: a hardware display list may still reference them.
        canvas.drawBitmap(bitmap, Rect(2, 2, bitmap.width - 2, bitmap.height - 2), target,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    }

    @Synchronized private fun raster(glyph: Glyph, edge: Int, color: Int): Bitmap? {
        val key = RasterKey(glyph, edge, color)
        rasters.get(key)?.let { return it }
        val bounds = glyph.bounds
        val scale = edge / max(bounds.width(), bounds.height())
        val width = ceil(bounds.width() * scale).toInt().coerceAtLeast(1)
        val height = ceil(bounds.height() * scale).toInt().coerceAtLeast(1)
        val bitmap = try { Bitmap.createBitmap(width + 4, height + 4, Bitmap.Config.ARGB_8888) }
            catch (_: OutOfMemoryError) { return null }
        val software = Canvas(bitmap)
        software.translate(2f, 2f)
        software.scale(width / bounds.width(), height / bounds.height())
        software.translate(-bounds.left, -bounds.top)
        software.drawPath(glyph.path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        rasters.put(key, bitmap)
        return bitmap
    }
}
