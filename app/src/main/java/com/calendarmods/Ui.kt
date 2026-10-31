package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import java.util.concurrent.Executors

internal object Ui {
    val main = Handler(Looper.getMainLooper())
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

    fun dp(context: Context, value: Float): Float = value * context.resources.displayMetrics.density

    fun builtInImage(renderer: ThemeRenderer): Int? = when (renderer.image) {
        BuiltInImage.BEAR -> R.drawable.winter_bear
        BuiltInImage.SUNSET -> R.drawable.sunset_memory
        null -> null
    }

    /** Custom image, then packaged theme asset, then the built-in illustration. */
    fun themeImage(context: Context, theme: CalendarTheme, style: ThemeStyle,
        inventory: ThemeInventory, edge: Int): Bitmap? {
        val builtIn = builtInImage(theme.renderer) ?: return null
        val packaged = inventory.installed.firstOrNull { it.theme.id == theme.id }?.imageFile
            ?.let { java.io.File(context.filesDir, "themes-v1/$it").absolutePath }
        val sources = listOfNotNull(style.imagePath, packaged).map { ThemeImageSource(path = it) } +
            ThemeImageSource(resource = builtIn)
        return ThemeImages.load(context.resources, sources, edge)
    }

    fun themeFont(theme: CalendarTheme, style: ThemeStyle, inventory: ThemeInventory): Typeface? {
        val id = theme.resolveFont(style.fontId)
        return inventory.installed.firstOrNull { it.theme.id == theme.id }?.fontFamilies?.get(id)
    }
}

/** Bitmap cache for chooser previews and store cards; a full page redraw is too slow per frame. */
internal object PreviewCache {
    private val MAX_BYTES = minOf(24 * 1024 * 1024L, Runtime.getRuntime().maxMemory() / 8).toInt()
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = ThemeImages.bitmapBytes(value)
    }

    fun key(theme: CalendarTheme, data: CalendarData, style: ThemeStyle,
        environment: AlmanacEnvironment, width: Int, height: Int): String =
        "${theme.id}|${data.today}|${style.hashCode()}|${environment.minute / 5}|" +
            "${environment.ambientDark}|$width|$height"

    fun get(key: String): Bitmap? = cache.get(key)

    fun render(context: Context, key: String, theme: CalendarTheme, data: CalendarData,
        style: ThemeStyle, environment: AlmanacEnvironment, width: Int, height: Int,
        inventory: ThemeInventory): Bitmap? {
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
        val artwork = PageArtwork(theme, data, style, environment, wide,
            width / unit, height / unit,
            if (theme.renderer.image == null) null else
                Ui.themeImage(context, theme, style, inventory, ThemeImages.targetEdge(width, height)),
            Ui.themeFont(theme, style, inventory), AlmanacIcons(context),
            if (theme.renderer == ThemeRenderer.PEITAIYI) PeitaiyiArtwork(context) else null)
        canvas.drawColor(artwork.paperColor())
        canvas.scale(unit, unit)
        artwork.draw(canvas)
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()
}
