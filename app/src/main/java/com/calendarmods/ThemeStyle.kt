package com.calendarmods

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal enum class ImageFill(val title: String, val description: String) {
    CROP("等比填充", "铺满图片区域，边缘可能被裁切"),
    FIT("完整显示", "保留完整图片，空余区域留白"),
    STRETCH("拉伸填充", "铺满图片区域，可能改变原图比例"),
}

internal data class ThemeStyle(val imagePath: String? = null, val fill: ImageFill = ImageFill.CROP,
    val fontId: String? = null, val pageAnimation: Boolean = true, val interactive: Boolean = false,
    val ink: AlmanacInk = AlmanacInk.DEFAULT, val dark: Boolean = false,
    val scheduledDark: Boolean = false, val darkStart: Int = 22 * 60, val darkEnd: Int = 7 * 60,
    val ambientDark: Boolean = false, val festive: Boolean = true)

internal fun CalendarTheme.defaultStyle() = ThemeStyle(fill = ImageFill.entries.single { it.name.lowercase() == defaultFill })

internal class ThemeStyleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("theme_styles", Context.MODE_PRIVATE)

    fun read(theme: CalendarTheme): ThemeStyle {
        // Migrate once, including an explicit reset, so old values cannot reappear later.
        val key = theme.id
        val legacy = CalendarTheme.entries.firstOrNull { it.id == key }?.name
        if (!preferences.contains("$key.migrated") && legacy != null) {
            val editor = preferences.edit()
            if (preferences.contains("$legacy.image")) editor.putString("$key.image", preferences.getString("$legacy.image", null))
            if (preferences.contains("$legacy.fill")) editor.putString("$key.fill", preferences.getString("$legacy.fill", null))
            editor.putBoolean("$key.migrated", true)
            editor.apply()
        }
        return ThemeStyle(preferences.getString("$key.image", null),
            ImageFill.entries.firstOrNull { it.name == preferences.getString("$key.fill", null) } ?: theme.defaultStyle().fill,
            preferences.getString("$key.font", null)?.takeIf { it == "template" || theme.fonts.any { font -> font.id == it } },
            preferences.getBoolean("$key.page_animation", true),
            preferences.getBoolean("$key.interactive", false),
            AlmanacInk.entries.firstOrNull { it.name == preferences.getString("$key.ink", null) } ?: AlmanacInk.DEFAULT,
            preferences.getBoolean("$key.dark", false), preferences.getBoolean("$key.dark_schedule", false),
            preferences.getInt("$key.dark_start", 1320).coerceIn(0, 1439),
            preferences.getInt("$key.dark_end", 420).coerceIn(0, 1439),
            preferences.getBoolean("$key.dark_ambient", false), preferences.getBoolean("$key.festive", true))
    }

    fun save(theme: CalendarTheme, style: ThemeStyle) {
        val previous = read(theme)
        val editor = preferences.edit()
        // Editing another property must not turn an inherited default into an override.
        if (style.imagePath != previous.imagePath) {
            if (style.imagePath == null) editor.remove("${theme.id}.image") else editor.putString("${theme.id}.image", style.imagePath)
        }
        if (style.fill != previous.fill) editor.putString("${theme.id}.fill", style.fill.name)
        editor.putString("${theme.id}.font", style.fontId?.takeIf { it == "template" || theme.fonts.any { font -> font.id == it } })
        editor.putBoolean("${theme.id}.migrated", true)
        editor.putBoolean("${theme.id}.page_animation", style.pageAnimation)
        editor.putBoolean("${theme.id}.interactive", style.interactive)
        editor.putString("${theme.id}.ink", style.ink.name)
        editor.putBoolean("${theme.id}.dark", style.dark)
        editor.putBoolean("${theme.id}.dark_schedule", style.scheduledDark)
        editor.putInt("${theme.id}.dark_start", style.darkStart.coerceIn(0, 1439))
        editor.putInt("${theme.id}.dark_end", style.darkEnd.coerceIn(0, 1439))
        editor.putBoolean("${theme.id}.dark_ambient", style.ambientDark)
        editor.putBoolean("${theme.id}.festive", style.festive)
        editor.apply()
    }

    fun resetImage(theme: CalendarTheme) {
        preferences.edit().remove("${theme.id}.image").remove("${theme.id}.fill")
            .putBoolean("${theme.id}.migrated", true).commit()
    }

    fun reset(theme: CalendarTheme) {
        val editor = preferences.edit()
        editor.remove("${theme.id}.image"); editor.remove("${theme.id}.fill")
        editor.remove("${theme.id}.font")
        editor.remove("${theme.id}.page_animation"); editor.remove("${theme.id}.interactive")
        listOf("ink", "dark", "dark_schedule", "dark_start", "dark_end", "dark_ambient", "festive")
            .forEach { editor.remove("${theme.id}.$it") }
        editor.putBoolean("${theme.id}.migrated", true)
        editor.commit()
    }

    /** Decode off the UI thread and cap memory before storing a private copy. */
    fun importImage(theme: CalendarTheme, uri: Uri): String {
        val bitmap = checkNotNull(ThemeImages.decodeUri(context, uri, 2048)) { "Image could not be decoded" }
        val directory = File(context.filesDir, "theme-images").apply { mkdirs() }
        val file = File(directory, "${theme.id}-${UUID.randomUUID()}.png")
        try {
            FileOutputStream(file).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            return file.absolutePath
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
            ThemeImages.discard(path)
            file.delete()
        }
    }
}
