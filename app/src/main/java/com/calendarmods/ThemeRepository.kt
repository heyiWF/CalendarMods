package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal data class InstalledTheme(val theme: CalendarTheme, val imageFile: String? = null)
internal data class ThemeInventory(
    val installed: List<InstalledTheme> = emptyList(),
    val loadErrors: List<String> = emptyList(),
) {
    val themes: List<CalendarTheme> get() = CalendarTheme.entries + installed.map { it.theme }
}

/** Single-process durable catalog. Future download/import clients call install after acquisition.
 * Publication changes the observable inventory only after the atomic index is committed.
 */
internal class ThemeRepository private constructor(context: Context) {
    private val directory = File(context.filesDir, "themes-v1").apply { mkdirs() }
    private val index = AtomicFile(File(directory, "index.json"))
    private val mutex = Mutex()
    private val mutableInventory = MutableStateFlow(readInventory())
    val inventory = mutableInventory.asStateFlow()

    fun imagePath(theme: CalendarTheme): String? = inventory.value.installed
        .firstOrNull { it.theme.id == theme.id }?.imageFile?.let { File(directory, it).absolutePath }

    suspend fun install(manifest: String, image: ByteArray? = null): CalendarTheme = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(inventory.value.loadErrors.isEmpty()) { "Resolve theme index errors before modifying the library" }
            val theme = ThemeManifest.decode(manifest)
            val previous = inventory.value.installed
            ThemeCatalog.validateInstall(theme, previous.map { it.theme })
            require(theme.hasImageAsset == (image != null)) { "Image asset does not match manifest" }
            require(previous.any { it.theme.id == theme.id } || previous.size < 200) { "Theme limit reached" }
            val imageFile = image?.let { importAsset(it) }
            try {
                val entry = InstalledTheme(theme, imageFile)
                val updated = if (previous.any { it.theme.id == theme.id }) {
                    previous.map { if (it.theme.id == theme.id) entry else it }
                } else previous + entry
                persist(updated)
                mutableInventory.value = ThemeInventory(updated)
            } catch (failure: Exception) {
                imageFile?.let { File(directory, it).delete() }
                throw failure
            }
            // Old immutable assets may still be rendered during an update. Prune on next startup.
            theme
        }
    }

    suspend fun uninstall(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(inventory.value.loadErrors.isEmpty()) { "Resolve theme index errors before modifying the library" }
            require(CalendarTheme.entries.none { it.id == id }) { "Cannot uninstall a built-in theme" }
            val updated = inventory.value.installed.filterNot { it.theme.id == id }
            persist(updated)
            mutableInventory.value = ThemeInventory(updated)
        }
    }

    private fun importAsset(bytes: ByteArray): String {
        require(bytes.size in 1..8 * 1024 * 1024) { "Image must be at most 8 MiB" }
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            require(info.size.width.toLong() * info.size.height <= 64_000_000) { "Image dimensions are too large" }
            val factor = (2048f / max(info.size.width, info.size.height)).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * factor).roundToInt().coerceAtLeast(1),
                (info.size.height * factor).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file.name
        } catch (failure: Exception) {
            file.delete(); throw failure
        } finally { bitmap.recycle() }
    }

    private fun persist(entries: List<InstalledTheme>) {
        val json = JSONArray().apply {
            entries.forEach { entry -> put(JSONObject().apply {
                put("manifest", JSONObject(ThemeManifest.encode(entry.theme)))
                entry.imageFile?.let { put("imageFile", it) }
            }) }
        }.toString().toByteArray(Charsets.UTF_8)
        val output = index.startWrite()
        try { output.write(json); index.finishWrite(output) }
        catch (failure: Exception) { index.failWrite(output); throw failure }
    }

    private fun readInventory(): ThemeInventory {
        val entries = mutableListOf<InstalledTheme>()
        val errors = mutableListOf<String>()
        try {
            val bytes = index.openRead().use {
                require(it.channel.size() <= 8 * 1024 * 1024) { "Theme index is too large" }
                it.readBytes()
            }
            require(bytes.size <= 8 * 1024 * 1024)
            val json = JSONArray(bytes.toString(Charsets.UTF_8))
            require(json.length() <= 200)
            for (position in 0 until json.length()) {
                try {
                    val record = json.getJSONObject(position)
                    val theme = ThemeManifest.decode(record.getJSONObject("manifest").toString())
                    require(entries.none { it.theme.id == theme.id }) { "Duplicate theme ID" }
                    ThemeCatalog.validateInstall(theme, emptyList())
                    val image = if (record.has("imageFile")) record.getString("imageFile") else null
                    require(theme.hasImageAsset == (image != null))
                    if (image != null) {
                        require(image.matches(Regex("[a-f0-9-]{36}\\.png")))
                        require(File(directory, image).isFile) { "Missing theme image" }
                    }
                    entries += InstalledTheme(theme, image)
                } catch (failure: Exception) { errors += "Theme ${position + 1}: ${failure.message}" }
            }
        } catch (_: java.io.FileNotFoundException) {
            // First launch has no installed themes.
        } catch (failure: Exception) { errors += "Theme index: ${failure.message}" }
        if (errors.isEmpty()) {
            val used = entries.mapNotNull { it.imageFile }.toSet()
            directory.listFiles()?.filter { it.extension == "png" && it.name !in used }?.forEach { it.delete() }
        }
        return ThemeInventory(entries, errors)
    }

    companion object {
        @Volatile private var instance: ThemeRepository? = null
        fun get(context: Context): ThemeRepository = instance ?: synchronized(this) {
            instance ?: ThemeRepository(context.applicationContext).also { instance = it }
        }
        /** Test persistence using a fresh reader; production shares one observable catalog. */
        internal fun reopen(context: Context): ThemeRepository = ThemeRepository(context.applicationContext)
    }
}
