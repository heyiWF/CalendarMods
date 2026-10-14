package com.calendarmods

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ThumbnailImage(val bitmap: Bitmap?)
internal val LocalThumbnailGeneration = compositionLocalOf { true }
internal val LocalThumbnailImage = staticCompositionLocalOf<ThumbnailImage?> { null }

/** Immutable artwork only: membership, favorites and ordering deliberately do not enter the key. */
internal data class ThumbnailKey(val theme: CalendarTheme, val date: java.time.LocalDate,
    val style: ThemeStyle, val asset: String?, val imageRevision: Long, val window: IntSize,
    val size: IntSize, val density: Float, val fontScale: Float)

internal object ThemeThumbnails {
    const val MAX_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<ThumbnailKey, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: ThumbnailKey, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
    }
    // At most one temporary full render/image decode; queued off-screen items cancel with their composition.
    val generation = Semaphore(1)
    fun get(key: ThumbnailKey): ImageBitmap? = cache.get(key)
    fun put(key: ThumbnailKey, image: ImageBitmap) { cache.put(key, image) }
    fun bytes(): Int = cache.size()
    fun clear() = cache.evictAll()
}

@Composable
internal fun ThemeThumbnail(theme: CalendarTheme, data: CalendarData) {
    val generationEnabled = LocalThumbnailGeneration.current
    val resources = LocalResources.current
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val style = LocalThemeStyles.current[theme.id] ?: theme.defaultStyle()
    val asset = LocalThemeImages.current[theme.id]
    val path = style.imagePath ?: asset
    val revision = remember(path) { path?.let { File(it).lastModified() } ?: 0L }
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val size = IntSize(constraints.maxWidth.coerceAtLeast(1), constraints.maxHeight.coerceAtLeast(1))
        val key = remember(theme, data.today, style, asset, revision, window, size, density) {
            ThumbnailKey(theme, data.today, style, asset, revision, window, size, density.density, density.fontScale)
        }
        var image by remember(key) { mutableStateOf(ThemeThumbnails.get(key)) }
        var rendering by remember(key) { mutableStateOf(false) }
        var source by remember(key) { mutableStateOf<Bitmap?>(null) }
        var drawn by remember(key) { mutableStateOf(CompletableDeferred<Unit>()) }
        val layer = rememberGraphicsLayer()
        LaunchedEffect(key, generationEnabled) {
            if (!generationEnabled) return@LaunchedEffect
            if (image != null) return@LaunchedEffect
            kotlinx.coroutines.delay(120) // Let an interrupted gesture settle before composing a cache miss.
            ThemeThumbnails.generation.withPermit {
                image = ThemeThumbnails.get(key)
                if (image == null) {
                    source = withContext(Dispatchers.IO) {
                        if (theme.renderer.image == null) null else {
                            val resource = if (theme.renderer.image == BuiltInImage.BEAR) R.drawable.winter_bear else R.drawable.sunset_memory
                            val sources = listOfNotNull(path, asset).distinct().map { File(it) }
                            fun decode(input: ImageDecoder.Source) = ImageDecoder.decodeBitmap(input) { decoder, info, _ ->
                                val scale = max(size.width.toFloat() / info.size.width, size.height.toFloat() / info.size.height).coerceAtMost(1f)
                                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1),
                                    (info.size.height * scale).roundToInt().coerceAtLeast(1))
                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            }
                            sources.firstNotNullOfOrNull { runCatching { decode(ImageDecoder.createSource(it)) }.getOrNull() }
                                ?: decode(ImageDecoder.createSource(resources, resource))
                        }
                    }
                    try {
                        drawn = CompletableDeferred()
                        rendering = true
                        drawn.await()
                        val snapshot = layer.toImageBitmap()
                        ThemeThumbnails.put(key, snapshot)
                        image = snapshot
                    } finally { rendering = false; source = null }
                }
            }
        }
        if (image != null) Image(image!!, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        else if (rendering) CompositionLocalProvider(LocalThumbnailImage provides ThumbnailImage(source)) {
            Box(Modifier.fillMaxSize().drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
                drawn.complete(Unit)
            }) { CalendarArtwork(theme, data) }
        }
    }
}
