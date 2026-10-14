package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class ImageFill(val title: String, val description: String, val scale: ContentScale) {
    CROP("等比填充", "铺满图片区域，边缘可能被裁切", ContentScale.Crop),
    FIT("完整显示", "保留完整图片，空余区域留白", ContentScale.Fit),
    STRETCH("拉伸填充", "铺满图片区域，可能改变原图比例", ContentScale.FillBounds),
}

internal data class ThemeStyle(val imagePath: String? = null, val fill: ImageFill = ImageFill.CROP)
internal fun CalendarTheme.defaultStyle() = ThemeStyle(fill = ImageFill.entries.single { it.name.lowercase() == defaultFill })
internal val LocalThemeStyles = staticCompositionLocalOf<Map<String, ThemeStyle>> { emptyMap() }
internal val LocalThemeImages = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

internal class ThemeStyleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("theme_styles", Context.MODE_PRIVATE)
    fun read(theme: CalendarTheme): ThemeStyle {
        if (theme.settings.isEmpty()) return theme.defaultStyle()
        // Migrate once, including an explicit reset, so old values cannot reappear later.
        val key = theme.id
        val legacy = CalendarTheme.entries.firstOrNull { it.id == key }?.name
        if (!preferences.contains("$key.migrated") && legacy != null) {
            preferences.edit {
                if (preferences.contains("$legacy.image")) putString("$key.image", preferences.getString("$legacy.image", null))
                if (preferences.contains("$legacy.fill")) putString("$key.fill", preferences.getString("$legacy.fill", null))
                putBoolean("$key.migrated", true)
            }
        }
        return ThemeStyle(preferences.getString("$key.image", null),
            ImageFill.entries.firstOrNull { it.name == preferences.getString("$key.fill", null) } ?: theme.defaultStyle().fill)
    }
    fun save(theme: CalendarTheme, style: ThemeStyle) {
        preferences.edit {
            putString("${theme.id}.image", style.imagePath)
            putString("${theme.id}.fill", style.fill.name)
            putBoolean("${theme.id}.migrated", true)
        }
    }

    fun reset(theme: CalendarTheme) {
        preferences.edit(commit = true) {
            remove("${theme.id}.image"); remove("${theme.id}.fill")
            putBoolean("${theme.id}.migrated", true)
        }
    }

    /** Decode off the UI thread, honor EXIF rotation and cap memory before storing a private copy. */
    suspend fun importImage(theme: CalendarTheme, uri: Uri): String = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val factor = (2048f / max(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * factor).roundToInt().coerceAtLeast(1),
                (info.size.height * factor).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val directory = File(context.filesDir, "theme-images").apply { mkdirs() }
        val file = File(directory, "${theme.id}-${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file.absolutePath
        } catch (failure: Exception) {
            file.delete()
            throw failure
        } finally {
            bitmap.recycle()
        }
    }

    fun discard(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.parentFile?.canonicalFile == File(context.filesDir, "theme-images").canonicalFile) {
            imageCache.remove(path)
            file.delete()
        }
    }
}

private val imageCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

@Composable
internal fun ThemeImage(theme: CalendarTheme, modifier: Modifier) {
    val style = LocalThemeStyles.current[theme.id] ?: theme.defaultStyle()
    val thumbnail = LocalThumbnailImage.current
    if (thumbnail != null) {
        thumbnail.bitmap?.let { Image(it.asImageBitmap(), null, modifier, contentScale = style.fill.scale) }
        return
    }
    val defaultPath = LocalThemeImages.current[theme.id]
    val path = style.imagePath ?: defaultPath
    val bitmap by produceState<Bitmap?>(path?.let(imageCache::get), path, defaultPath) {
        value = withContext(Dispatchers.IO) {
            listOfNotNull(path, defaultPath).distinct().firstNotNullOfOrNull { candidate ->
                imageCache.get(candidate) ?: runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(candidate)))
                        .also { imageCache.put(candidate, it) }
                }.getOrNull()
            }
        }
    }
    val description = if (path != null) "自选日历图片" else when (theme.renderer.image) {
        BuiltInImage.BEAR -> "雪地里的小熊与热可可"
        else -> "日落海边的温暖合影"
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, modifier, contentScale = style.fill.scale)
    else Image(painterResource(if (theme.renderer.image == BuiltInImage.BEAR) R.drawable.winter_bear else R.drawable.sunset_memory),
        description, modifier, contentScale = style.fill.scale)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeStyleSheet(theme: CalendarTheme, style: ThemeStyle, store: ThemeStyleStore,
    onChange: (ThemeStyle) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var dismissing by remember { mutableStateOf(false) }
    val currentStyle by rememberUpdatedState(style)
    val currentChange by rememberUpdatedState(onChange)
    var importing by remember { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            error = null
            try {
                val path = store.importImage(theme, uri)
                val old = currentStyle.imagePath
                currentChange(currentStyle.copy(imagePath = path))
                withContext(Dispatchers.IO) { store.discard(old) }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                error = "无法读取这张图片，请选择其他图片。"
            } finally { importing = false }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!importing) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !importing,
        modifier = Modifier.testTag("theme-style-sheet").semantics { testTagsAsResourceId = true }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
            .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${theme.title.substringBefore(" · ")}样式", style = MaterialTheme.typography.headlineSmall)
            val imageSetting = theme.settings.filterIsInstance<ThemeSetting.Image>().singleOrNull()
            val fillSetting = theme.settings.filterIsInstance<ThemeSetting.Choice>().singleOrNull()
            if (imageSetting != null && fillSetting != null) {
                Text(imageSetting.title, style = MaterialTheme.typography.titleMedium)
                ThemeImage(theme, Modifier.fillMaxWidth().height(128.dp))
                Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !importing, modifier = Modifier.fillMaxWidth().testTag("choose-image")) {
                    Text(if (importing) "正在保存图片…" else "选择本机图片")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                HorizontalDivider()
                Text(fillSetting.title, style = MaterialTheme.typography.titleMedium)
                Column(Modifier.selectableGroup()) {
                    ImageFill.entries.filter { it.name.lowercase() in fillSetting.choices }.forEach { fill ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(
                            selected = style.fill == fill, enabled = !importing, role = Role.RadioButton,
                            onClick = { onChange(style.copy(fill = fill)) }).testTag("fill-${fill.name}"),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = style.fill == fill, onClick = null, enabled = !importing)
                            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                Text(fill.title, style = MaterialTheme.typography.bodyLarge)
                                Text(fill.description, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    val old = style.imagePath
                    onReset()
                    scope.launch(Dispatchers.IO) { store.discard(old) }
                }, enabled = !importing, modifier = Modifier.testTag("restore-image")) {
                    Text("恢复默认图片与填充方式")
                }
            } else {
                Text("此主题暂无可调整的样式。", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                if (!dismissing) {
                    dismissing = true
                    scope.launch {
                        try { sheetState.hide(); if (!sheetState.isVisible) onDismiss() }
                        finally { dismissing = false }
                    }
                }
            }, enabled = !importing && !dismissing,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End).testTag("style-done")) {
                Text("完成")
            }
        }
    }
}
