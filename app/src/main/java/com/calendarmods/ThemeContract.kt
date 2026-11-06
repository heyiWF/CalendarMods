package com.calendarmods

/**
 * Colours of one printed page: the window backdrop, the sheet itself, and the inks used on it.
 *
 * The page has two printings — a warm paper sheet and a dark sheet — and both are described here,
 * so switching at runtime only swaps this value; there is no second renderer.
 */
data class PageScheme(
    val backdrop: Long,
    val paper: Long,
    val chrome: Long,
    val ink: Long,
    /** 忌：the red entries and the 忌 chip. */
    val accent: Long,
    /** 宜：the green entries and the 宜 chip. */
    val herb: Long,
    /** Type printed inside a filled chip, so it stays legible on either sheet. */
    val onAccent: Long,
) {
    init { require(listOf(backdrop, paper, chrome, ink, accent, herb, onAccent).all { it in 0..0xFFFFFFFF }) }
}

/** The one bundled calendar page. */
data class CalendarTheme(
    val id: String,
    val title: String,
    val light: PageScheme,
    val dark: PageScheme,
) {
    init {
        require(id.length <= 120 && id.matches(Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"))) { "Invalid theme ID" }
        require(title.isNotBlank() && title.length <= 80 && title.none(Char::isISOControl)) { "Invalid title" }
    }

    fun scheme(dark: Boolean): PageScheme = if (dark) this.dark else light

    companion object {
        /** Warm, low-glare paper with letterpress ink and two muted seals. */
        val LIGHT = PageScheme(
            backdrop = 0xFFDDC79C,
            paper = 0xFFF7EBCE,
            chrome = 0xFF443B2B,
            ink = 0xFF14110C,
            accent = 0xFFB3362B,
            herb = 0xFF2E6F3E,
            onAccent = 0xFFF7EBCE,
        )

        /** The same sheet after dark: the paper becomes warm charcoal and the inks invert. */
        val DARK = PageScheme(
            backdrop = 0xFF0E0D0A,
            paper = 0xFF1B1913,
            chrome = 0xFFC8BCA0,
            ink = 0xFFF3EAD4,
            accent = 0xFFE1715C,
            herb = 0xFF7CC08D,
            onAccent = 0xFF14120C,
        )

        val DAILY = CalendarTheme("com.calendarmods.daily", "今朝 · 每日一页", LIGHT, DARK)

        val entries: List<CalendarTheme> = listOf(DAILY)
    }
}
