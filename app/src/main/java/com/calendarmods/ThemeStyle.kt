package com.calendarmods

import android.content.Context

/** How the page chooses between the light and the dark printing. */
internal enum class DarkMode {
    /** Always the warm paper sheet. */
    OFF,
    /** Always the dark sheet. */
    ON,
    /** The dark sheet inside the night window the user picked. */
    SCHEDULE,
    /** The dark sheet while the ambient light sensor reads a dim room. */
    SENSOR;

    companion object {
        fun fromKey(key: String?): DarkMode = values().firstOrNull { it.name == key } ?: OFF
    }
}

/** User adjustments for the bundled page. */
internal data class ThemeStyle(
    val fontScale: Float = DEFAULT_FONT_SCALE,
    val pageAnimation: Boolean = true,
    val interactive: Boolean = false,
    val faceBrightness: Boolean = false,
    /** Print the clock as 24-hour time; when false it reads 上午9:05 / 下午12:30. */
    val hour24: Boolean = true,
    val darkMode: DarkMode = DarkMode.OFF,
    /** Local hour the night window opens, 0–23. */
    val darkFrom: Int = DEFAULT_DARK_FROM,
    /** Local hour the night window closes, 0–23. */
    val darkTo: Int = DEFAULT_DARK_TO,
) {
    /**
     * Whether [hour] falls inside the night window. A window may cross midnight (19 → 7), and an
     * empty window (both hours equal) never turns the page dark.
     */
    fun scheduledDark(hour: Int): Boolean = when {
        darkFrom == darkTo -> false
        darkFrom < darkTo -> hour in darkFrom until darkTo
        else -> hour >= darkFrom || hour < darkTo
    }

    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.4f
        const val DEFAULT_FONT_SCALE = 1f
        const val DEFAULT_DARK_FROM = 19
        const val DEFAULT_DARK_TO = 7

        /** Slider values and stored preferences both pass through here. */
        fun clampFontScale(value: Float): Float =
            if (value.isNaN()) DEFAULT_FONT_SCALE else value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

        fun clampHour(value: Int): Int = value.coerceIn(0, 23)
    }
}

internal class ThemeStyleStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("theme_styles", Context.MODE_PRIVATE)

    fun read(): ThemeStyle = ThemeStyle(
        fontScale = ThemeStyle.clampFontScale(preferences.getFloat("font_scale", ThemeStyle.DEFAULT_FONT_SCALE)),
        pageAnimation = preferences.getBoolean("page_animation", true),
        interactive = preferences.getBoolean("interactive", false),
        faceBrightness = preferences.getBoolean("face_brightness", false),
        hour24 = preferences.getBoolean("hour24", true),
        darkMode = DarkMode.fromKey(preferences.getString("dark_mode", null)),
        darkFrom = ThemeStyle.clampHour(preferences.getInt("dark_from", ThemeStyle.DEFAULT_DARK_FROM)),
        darkTo = ThemeStyle.clampHour(preferences.getInt("dark_to", ThemeStyle.DEFAULT_DARK_TO)),
    )

    fun save(style: ThemeStyle) {
        preferences.edit()
            .putFloat("font_scale", ThemeStyle.clampFontScale(style.fontScale))
            .putBoolean("page_animation", style.pageAnimation)
            .putBoolean("interactive", style.interactive)
            .putBoolean("face_brightness", style.faceBrightness)
            .putBoolean("hour24", style.hour24)
            .putString("dark_mode", style.darkMode.name)
            .putInt("dark_from", ThemeStyle.clampHour(style.darkFrom))
            .putInt("dark_to", ThemeStyle.clampHour(style.darkTo))
            .apply()
    }
}
