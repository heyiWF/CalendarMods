package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

internal data class InstalledTheme(val theme: CalendarTheme, val imageFile: String? = null,
    val fontFiles: Map<String, String> = emptyMap(),
    val fontFamilies: Map<String, Typeface> = emptyMap())
internal data class ThemeInventory(
    val installed: List<InstalledTheme> = emptyList(),
    val loadErrors: List<String> = emptyList(),
    val library: ThemeLibrary = ThemeLibrary(),
) {
    val catalog: List<CalendarTheme> = CalendarTheme.entries + installed.map { it.theme }
    val byId = catalog.associateBy { it.id }
    val themes: List<CalendarTheme> = library.order.mapNotNull(byId::get)
}

/** Single-process durable catalog. Future download/import clients call install after acquisition.
 * Publication changes the observable inventory only after the atomic index is committed.
 * Every mutator is synchronous: the compat host calls them from a worker thread.
 */
internal class ThemeRepository private constructor(context: Context) {
    private val directory = File(context.filesDir, "themes-v1").apply { mkdirs() }
    private val index = File(directory, "index.json")
    private val styleStore = ThemeStyleStore(context.applicationContext)
    private val listeners = CopyOnWriteArrayList<(ThemeInventory) -> Unit>()

    @Volatile private var mutableInventory = readInventory()

    val inventory: ThemeInventory get() = mutableInventory

    fun addListener(listener: (ThemeInventory) -> Unit) {
        listeners += listener
        listener(mutableInventory)
    }

    fun removeListener(listener: (ThemeInventory) -> Unit) {
        listeners -= listener
    }

    fun imagePath(theme: CalendarTheme): String? = inventory.installed
        .firstOrNull { it.theme.id == theme.id }?.imageFile?.let { File(directory, it).absolutePath }

    @Synchronized
    fun install(manifest: String, image: ByteArray? = null,
        fonts: Map<String, ByteArray> = emptyMap()): CalendarTheme {
        check(inventory.loadErrors.isEmpty()) { "Resolve theme index errors before modifying the library" }
        val theme = ThemeManifest.decode(manifest)
        val previous = inventory.installed
        ThemeCatalog.validateInstall(theme, previous.map { it.theme })
        require(theme.hasImageAsset == (image != null)) { "Image asset does not match manifest" }
        require(previous.any { it.theme.id == theme.id } || previous.size < 200) { "Theme limit reached" }
        require(fonts.keys == theme.fonts.map { it.id }.toSet()) { "Font assets do not match manifest" }
        require(fonts.values.all { it.size in 1..ThemeFonts.MAX_FILE_BYTES } &&
            fonts.values.sumOf { it.size.toLong() } <= ThemeFonts.MAX_PACKAGE_BYTES) { "Font assets are too large" }
        val imageFile = image?.let { importAsset(it) }
        val fontFiles = mutableMapOf<String, String>()
        try {
            val families = fonts.mapValues { (id, bytes) ->
                val file = File(directory, "${UUID.randomUUID()}.font")
                fontFiles[id] = file.name
                file.writeBytes(bytes)
                ThemeFonts.load(file)
            }
            val entry = InstalledTheme(theme, imageFile, fontFiles.toMap(), families)
            val updated = if (previous.any { it.theme.id == theme.id }) {
                previous.map { if (it.theme.id == theme.id) entry else it }
            } else previous + entry
            val library = inventory.library.add(theme.id, System.currentTimeMillis())
            publish(inventory.copy(installed = updated, library = library))
        } catch (failure: Exception) {
            imageFile?.let { File(directory, it).delete() }
            fontFiles.values.forEach { File(directory, it).delete() }
            throw failure
        }
        // Old immutable assets may still be rendered during an update. Prune on next startup.
        return theme
    }

    @Synchronized
    fun add(id: String) = modify { current ->
        require(current.catalog.any { it.id == id }) { "主题不存在" }
        current.copy(library = current.library.add(id, System.currentTimeMillis()))
    }

    @Synchronized
    fun uninstall(id: String) = modify { current ->
        current.copy(library = current.library.remove(id))
    }

    @Synchronized
    fun toggleFavorite(id: String) = modify { current ->
        require(current.catalog.any { it.id == id })
        current.copy(library = current.library.favorite(id))
    }

    @Synchronized
    fun setFavorites(values: Map<String, Boolean>) = modify { current ->
        require(values.keys.all { id -> current.catalog.any { it.id == id } }) { "主题不存在" }
        current.copy(library = current.library.setFavorites(values))
    }

    @Synchronized
    fun reorder(ids: List<String>) = modify { current ->
        current.copy(library = current.library.reorder(ids))
    }

    private fun modify(update: (ThemeInventory) -> ThemeInventory) {
        check(inventory.loadErrors.isEmpty()) { "主题记录无法读取，暂时不能修改" }
        val next = update(inventory)
        if (next != inventory) {
            publish(next)
            // Tombstones survive a crash between the atomic membership write and preference cleanup.
            resetRemovedStyles(next)
        }
    }

    private fun publish(state: ThemeInventory) {
        persist(state)
        mutableInventory = state
        listeners.forEach { it(state) }
    }

    private fun resetRemovedStyles(state: ThemeInventory) {
        val styles = styleStore
        state.catalog.filter { it.id in state.library.resetStyles }.forEach { theme ->
            val old = styles.read(theme)
            styles.reset(theme)
            styles.discard(old.imagePath)
        }
    }

    private fun importAsset(bytes: ByteArray): String {
        require(bytes.size in 1..8 * 1024 * 1024) { "Image must be at most 8 MiB" }
        val bitmap = ThemeImages.decodeBytes(bytes, 2048) ?: error("Image could not be decoded")
        val file = File(directory, "${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file.name
        } catch (failure: Exception) {
            file.delete(); throw failure
        } finally { bitmap.recycle() }
    }

    private fun persist(state: ThemeInventory) {
        val packages = JSONArray().apply {
            state.installed.forEach { entry -> put(JSONObject().apply {
                put("manifest", JSONObject(ThemeManifest.encode(entry.theme)))
                entry.imageFile?.let { put("imageFile", it) }
                if (entry.fontFiles.isNotEmpty()) put("fontFiles", JSONObject(entry.fontFiles))
            }) }
        }
        val json = JSONObject().apply {
            put("schemaVersion", 2)
            put("packages", packages)
            put("order", JSONArray(state.library.order))
            put("addedAt", JSONObject(state.library.addedAt))
            put("favorites", JSONArray(state.library.favorites.toList()))
            put("resetStyles", JSONArray(state.library.resetStyles.toList()))
        }.toString().toByteArray(Charsets.UTF_8)
        writeAtomically(index, json)
    }

    private fun readInventory(): ThemeInventory {
        val entries = mutableListOf<InstalledTheme>()
        val errors = mutableListOf<String>()
        var library: ThemeLibrary? = null
        var stored: JSONObject? = null
        try {
            require(index.length() <= 8 * 1024 * 1024) { "Theme index is too large" }
            val text = index.readBytes().toString(Charsets.UTF_8)
            stored = if (text.trimStart().startsWith("{")) JSONObject(text) else null
            stored?.let { require(it.getInt("schemaVersion") == 2) { "Unsupported library version" } }
            val json = stored?.getJSONArray("packages") ?: JSONArray(text)
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
                    val fontRecord = if (record.has("fontFiles")) record.getJSONObject("fontFiles") else JSONObject()
                    require(fontRecord.keys().asSequence().toSet() == theme.fonts.map { it.id }.toSet()) {
                        "Missing or unexpected theme fonts"
                    }
                    val fonts = theme.fonts.associate { font ->
                        val name = fontRecord.get(font.id) as? String ?: error("Invalid font file")
                        require(name.matches(Regex("[a-f0-9-]{36}\\.font"))) { "Invalid font path" }
                        font.id to name
                    }
                    require(fonts.values.sumOf { File(directory, it).length() } <= ThemeFonts.MAX_PACKAGE_BYTES)
                    entries += InstalledTheme(theme, image, fonts, fonts.mapValues { ThemeFonts.load(File(directory, it.value)) })
                } catch (failure: Exception) { errors += "Theme ${position + 1}: ${failure.message}" }
            }
        } catch (_: java.io.FileNotFoundException) {
            // First launch has no installed themes.
        } catch (failure: Exception) { errors += "Theme index: ${failure.message}" }
        if (errors.isEmpty()) {
            try {
                val catalogIds = (CalendarTheme.entries + entries.map { it.theme }).map { it.id }.toSet()
                library = stored?.let { root ->
                    fun ids(key: String): List<String> = root.getJSONArray(key).let { array ->
                        (0 until array.length()).map { array.getString(it) }.also { values ->
                            require(values.distinct().size == values.size && values.all { it in catalogIds })
                        }
                    }
                    val order = ids("order")
                    val times = root.getJSONObject("addedAt")
                    ThemeLibrary(order, order.associateWith { times.getLong(it) }, ids("favorites").toSet(), ids("resetStyles").toSet())
                } ?: ThemeLibrary(order = catalogIds.toList())
            } catch (failure: Exception) { errors += "Theme library: ${failure.message}" }
        }
        val state = ThemeInventory(entries, errors, library ?: ThemeLibrary())
        if (errors.isEmpty()) {
            if (stored == null) try { persist(state) }
            catch (failure: Exception) { return state.copy(loadErrors = listOf("Theme migration: ${failure.message}")) }
            resetRemovedStyles(state)
            val used = entries.flatMap { listOfNotNull(it.imageFile) + it.fontFiles.values }.toSet()
            directory.listFiles()?.filter { it.extension in listOf("png", "font") && it.name !in used }?.forEach { it.delete() }
        }
        return state
    }

    companion object {
        @Volatile private var instance: ThemeRepository? = null
        fun get(context: Context): ThemeRepository = instance ?: synchronized(this) {
            instance ?: ThemeRepository(context.applicationContext).also { instance = it }
        }
        /** Test persistence using a fresh reader; production shares one observable catalog. */
        internal fun reopen(context: Context): ThemeRepository = ThemeRepository(context.applicationContext)

        /** API 14 has no AtomicFile: write a sibling temp file, sync it, then rename over. */
        internal fun writeAtomically(target: File, bytes: ByteArray) {
            val temp = File(target.parentFile, "${target.name}.tmp")
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) {
                    temp.delete()
                    throw java.io.IOException("Could not replace ${target.name}")
                }
            }
        }
    }
}
