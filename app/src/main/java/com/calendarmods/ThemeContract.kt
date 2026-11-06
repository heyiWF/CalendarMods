package com.calendarmods

/**
 * Shared surface tokens. The page itself is a native template, so only the colours that the
 * window, the preview backdrop and the system bars share live here.
 */
data class ThemeSurfaces(
    val backdrop: Long,
    val paper: Long = 0xFFFFFEF9,
    val chrome: Long = 0xFF51554F,
) {
    init { require(listOf(backdrop, paper, chrome).all { it in 0..0xFFFFFFFF }) }
}

/** The one bundled calendar page. */
data class CalendarTheme(
    val id: String,
    val title: String,
    val surfaces: ThemeSurfaces,
) {
    init {
        require(id.length <= 120 && id.matches(Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"))) { "Invalid theme ID" }
        require(title.isNotBlank() && title.length <= 80 && title.none(Char::isISOControl)) { "Invalid title" }
    }

    companion object {
        val DAILY = CalendarTheme("com.calendarmods.daily", "今朝 · 每日一页",
            ThemeSurfaces(0xFFDDC79C, 0xFFF7EBCE, 0xFF443B2B))

        val entries: List<CalendarTheme> = listOf(DAILY)
    }
}
