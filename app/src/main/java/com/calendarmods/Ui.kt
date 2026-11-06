package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.util.concurrent.Executors

internal object Ui {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private const val TAG = "CalendarMods"

    fun io(block: () -> Unit) {
        pool.execute {
            try {
                block()
            } catch (failure: Exception) {
                Log.w(TAG, "background task failed", failure)
            }
        }
    }

    fun post(block: () -> Unit) {
        main.post(block)
    }
}

/** Bitmap cache for chooser previews; a full page redraw is too slow per frame. */
internal object PreviewCache {
    private val MAX_BYTES = minOf(24 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.rowBytes * value.height
    }

    fun key(theme: CalendarTheme, data: CalendarData, style: ThemeStyle, width: Int, height: Int): String =
        "${theme.id}|${data.today}|${style.hashCode()}|${clockText()}|$width|$height"

    fun get(key: String): Bitmap? = cache.get(key)

    fun render(context: Context, key: String, theme: CalendarTheme, data: CalendarData,
        style: ThemeStyle, width: Int, height: Int): Bitmap? {
        cache.get(key)?.let { return it }
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return null
        }
        val canvas = Canvas(bitmap)
        val wide = width > height
        val unit = if (wide) minOf(width / 640f, height / 360f) else minOf(width / 360f, height / 600f)
        // Previews print one settled frame: no rotation and no midnight animation.
        val page = DailyPage(theme, data, clockText(), wide, width / unit, height / unit,
            motion = false, now = 0L, userScale = style.fontScale)
        canvas.save()
        canvas.scale(unit, unit)
        page.draw(canvas)
        canvas.restore()
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()
}
