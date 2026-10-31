package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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

/** Reads existing theme packages and their migration state. The compat UI has a fixed library:
 * store mutation, favorite and reorder operations are deliberately absent. */
internal class ThemeRepository private constructor(context: Context) {
    private val directory = File(context.filesDir, "themes-v1").apply { mkdirs() }
    private val index = File(directory, "index.json")
    private val styleStore = ThemeStyleStore(context.applicationContext)

    private val mutableInventory = readInventory()

    val inventory: ThemeInventory get() = mutableInventory

    private fun resetRemovedStyles(state: ThemeInventory) {
        val styles = styleStore
        state.catalog.filter { it.id in state.library.resetStyles }.forEach { theme ->
            val old = styles.read(theme)
            styles.reset(theme)
            styles.discard(old.imagePath)
        }
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
