package com.calendarmods

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ThemeImageSource(val path: String? = null, val resource: Int = 0)
private data class ThemeImageKey(val source: ThemeImageSource, val edge: Int)

/** Shared by preview, fullscreen and style sheet. Only immutable private paths enter the cache. */
internal object ThemeImages {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<ThemeImageKey, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: ThemeImageKey, value: Bitmap) = value.allocationByteCount
    }
    private val decoding = Mutex()

    // Rotation and shared-element resize reuse one entry; never decode above the import limit.
    fun targetEdge(width: Int, height: Int): Int =
        ((max(width, height).coerceIn(512, 2048) + 255) / 256) * 256

    fun peek(sources: List<ThemeImageSource>, edge: Int): Bitmap? =
        sources.firstNotNullOfOrNull { cache.get(ThemeImageKey(it, edge)) }

    suspend fun load(resources: Resources, sources: List<ThemeImageSource>, edge: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            // Concurrent preview/fullscreen compositions must not decode the same large image twice.
            decoding.withLock {
                sources.firstNotNullOfOrNull { source ->
                    val key = ThemeImageKey(source, edge)
                    cache.get(key) ?: try {
                        val input = source.path?.let { ImageDecoder.createSource(File(it)) }
                            ?: ImageDecoder.createSource(resources, source.resource)
                        ImageDecoder.decodeBitmap(input) { decoder, info, _ ->
                            val scale = (edge.coerceIn(1, 2048).toFloat() / max(info.size.width, info.size.height)).coerceAtMost(1f)
                            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                                (info.size.height * scale).roundToInt().coerceAtLeast(1))
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }.also { it.prepareToDraw(); cache.put(key, it) }
                    } catch (_: java.io.IOException) {
                        null // Missing/corrupt custom asset: try the package image and then built-in art.
                    } catch (_: IllegalArgumentException) {
                        null
                    } catch (_: SecurityException) {
                        null
                    }
                }
            }
        }

    fun discard(path: String) {
        cache.snapshot().keys.filter { it.source.path == path }.forEach(cache::remove)
    }
    fun bytes(): Int = cache.size()
    fun clear() = cache.evictAll()
}
