package com.calendarmods

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ThemeImageSource(val path: String? = null, val resource: Int = 0)
private data class ThemeImageKey(val source: ThemeImageSource, val edge: Int)

/** Shared by preview, fullscreen and style sheet. Only immutable private paths enter the cache. */
internal object ThemeImages {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<ThemeImageKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: ThemeImageKey, value: Bitmap) = bitmapBytes(value)
    }
    private val decoding = Any()

    /** Bitmap.allocationByteCount is API 19; byteCount covers the older devices. */
    internal fun bitmapBytes(bitmap: Bitmap): Int =
        if (android.os.Build.VERSION.SDK_INT >= 19) bitmap.allocationByteCount else bitmap.byteCount

    // Rotation and page resize reuse one entry; never decode above the import limit.
    fun targetEdge(width: Int, height: Int): Int =
        ((max(width, height).coerceIn(512, 2048) + 255) / 256) * 256

    fun peek(sources: List<ThemeImageSource>, edge: Int): Bitmap? =
        sources.firstNotNullOfOrNull { cache.get(ThemeImageKey(it, edge)) }

    fun load(resources: Resources, sources: List<ThemeImageSource>, edge: Int): Bitmap? = synchronized(decoding) {
        sources.firstNotNullOfOrNull { source ->
            val key = ThemeImageKey(source, edge)
            cache.get(key) ?: try {
                val bitmap = when {
                    source.path != null -> decodeFile(File(source.path), edge)
                    else -> decodeResource(resources, source.resource, edge)
                }
                bitmap?.also { it.prepareToDraw(); cache.put(key, it) }
            } catch (_: java.io.IOException) {
                null // Missing or corrupt custom asset: try the package image, then built-in art.
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }
    }

    fun decodeFile(file: File, edge: Int): Bitmap? {
        if (!file.isFile) return null
        val bitmap = decodeSampled(edge) { BitmapFactory.decodeFile(file.absolutePath, it) } ?: return null
        return applyExifRotation(file.absolutePath, bitmap)
    }

    fun decodeUri(context: Context, uri: Uri, edge: Int): Bitmap? {        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null
        return scaleToEdge(bitmap, edge)
    }

    fun decodeBytes(bytes: ByteArray, edge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        require(bounds.outWidth.toLong() * bounds.outHeight <= 64_000_000L) { "Image dimensions are too large" }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return scaleToEdge(bitmap, edge)
    }

    private fun decodeResource(resources: Resources, id: Int, edge: Int): Bitmap? {
        if (id == 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, id, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeResource(resources, id, options) ?: return null
        return scaleToEdge(bitmap, edge)
    }

    private inline fun decodeSampled(edge: Int, decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return decode(options)?.let { scaleToEdge(it, edge) }
    }

    private fun sampleSize(width: Int, height: Int, edge: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= edge && height / (sample * 2) >= edge) sample *= 2
        return sample
    }

    private fun scaleToEdge(bitmap: Bitmap, edge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= edge) return bitmap
        val scale = edge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** The framework decoder has always ignored EXIF; camera JPEGs still need the rotation. */
    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        } catch (_: OutOfMemoryError) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }

    fun discard(path: String) {
        cache.snapshot().keys.filter { it.source.path == path }.forEach(cache::remove)
    }
    fun bytes(): Int = cache.size()
    fun clear() = cache.evictAll()
}
