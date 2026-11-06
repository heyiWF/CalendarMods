package com.calendarmods

import android.content.Context

/** User adjustments for the bundled page. */
internal data class ThemeStyle(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val pageAnimation: Boolean = true,
    val interactive: Boolean = false,
    val faceBrightness: Boolean = false,
) {
    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.4f
        const val DEFAULT_FONT_SCALE = 1f

        /** Slider values and stored preferences both pass through here. */
        fun clampFontScale(value: Float): Float =
            if (value.isNaN()) DEFAULT_FONT_SCALE else value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    }
}

internal class ThemeStyleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("theme_styles", Context.MODE_PRIVATE)

    fun read(): ThemeStyle = ThemeStyle(
        fontScale = ThemeStyle.clampFontScale(preferences.getFloat("font_scale", ThemeStyle.DEFAULT_FONT_SCALE)),
        pageAnimation = preferences.getBoolean("page_animation", true),
        interactive = preferences.getBoolean("interactive", false),
        faceBrightness = preferences.getBoolean("face_brightness", false),
    )

    fun save(style: ThemeStyle) {
        preferences.edit()
            .putFloat("font_scale", ThemeStyle.clampFontScale(style.fontScale))
            .putBoolean("page_animation", style.pageAnimation)
            .putBoolean("interactive", style.interactive)
            .putBoolean("face_brightness", style.faceBrightness)
            .apply()
    }
}
