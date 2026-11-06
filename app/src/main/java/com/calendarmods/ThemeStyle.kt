package com.calendarmods

import android.content.Context

/** User adjustments for the bundled page. */
internal data class ThemeStyle(
    val pageAnimation: Boolean = true,
    val interactive: Boolean = false,
    val faceBrightness: Boolean = false,
)

internal class ThemeStyleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("theme_styles", Context.MODE_PRIVATE)

    fun read(): ThemeStyle = ThemeStyle(
        preferences.getBoolean("page_animation", true),
        preferences.getBoolean("interactive", false),
        preferences.getBoolean("face_brightness", false),
    )

    fun save(style: ThemeStyle) {
        preferences.edit()
            .putBoolean("page_animation", style.pageAnimation)
            .putBoolean("interactive", style.interactive)
            .putBoolean("face_brightness", style.faceBrightness)
            .apply()
    }
}
