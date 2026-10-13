package com.calendarmods

/** Host-owned renderers. Their wire IDs, unlike enum names/positions, are permanent. */
enum class ThemeRenderer(val wireId: String, val image: BuiltInImage? = null) {
    GOLDEN("golden.v1"), TEDDY("illustration.v1", BuiltInImage.BEAR),
    PHOTO("photo.v1", BuiltInImage.SUNSET), GOOD_DAY("good-day.v1"),
    VERSE("verse.v1"), ALMANAC("almanac.v1"), HANGING("hanging.v1");

    val settings: List<ThemeSetting> get() = if (image == null) emptyList() else listOf(
        ThemeSetting.Image("image", "日历图片"),
        ThemeSetting.Choice("fill", "图片填充方式", listOf("crop", "fit", "stretch")),
    )
}

enum class BuiltInImage { BEAR, SUNSET }

/** Settings are advertised by the renderer that actually consumes them. */
sealed class ThemeSetting(val key: String, val title: String) {
    class Image(key: String, title: String) : ThemeSetting(key, title)
    class Choice(key: String, title: String, val choices: List<String>) : ThemeSetting(key, title)
}

/** Shared surface tokens. Decorative details remain part of each versioned native template. */
data class ThemeSurfaces(
    val backdrop: Long,
    val paper: Long = 0xFFFFFEF9,
    val chrome: Long = 0xFF51554F,
    val darkSystemBarIcons: Boolean = true,
) {
    init { require(listOf(backdrop, paper, chrome).all { it in 0..0xFFFFFFFF }) }
}

/** Portable, declarative theme contract. No executable code, URLs or device paths. */
data class CalendarTheme(
    val id: String,
    val title: String,
    val renderer: ThemeRenderer,
    val surfaces: ThemeSurfaces,
    val version: Int = 1,
    val schemaVersion: Int = 1,
    val author: String = "CalendarMods",
    val defaultFill: String = "crop",
    val hasImageAsset: Boolean = false,
) {
    init {
        require(id.length <= 120 && id.matches(Regex("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)+"))) { "Invalid theme ID" }
        require(title.isNotBlank() && title.length <= 80 && title.none(Char::isISOControl)) { "Invalid title" }
        require(author.isNotBlank() && author.length <= 80 && author.none(Char::isISOControl)) { "Invalid author" }
        require(schemaVersion == 1) { "Unsupported theme schema" }
        require(version > 0) { "Invalid theme version" }
        require(defaultFill in listOf("crop", "fit", "stretch")) { "Unsupported image fill" }
        require(renderer.image != null || (!hasImageAsset && defaultFill == "crop")) { "Renderer has no image slot" }
    }

    val settings: List<ThemeSetting> get() = renderer.settings
    // Preserve existing accessibility/test tags for bundled themes only.
    val name: String get() = entries.firstOrNull { it.id == id }?.renderer?.name ?: id

    companion object {
        val GOLDEN = CalendarTheme("com.calendarmods.golden", "晴日 · 黄页台历", ThemeRenderer.GOLDEN, ThemeSurfaces(0xFFECEEE9))
        val TEDDY = CalendarTheme("com.calendarmods.teddy", "冬日来信 · 插画月历", ThemeRenderer.TEDDY, ThemeSurfaces(0xFF9CC9DF))
        val PHOTO = CalendarTheme("com.calendarmods.photo", "拾光 · 照片台历", ThemeRenderer.PHOTO, ThemeSurfaces(0xFFD5C6B6, 0xFF232421))
        val GOOD_DAY = CalendarTheme("com.calendarmods.good-day", "好日 · 字体日历", ThemeRenderer.GOOD_DAY, ThemeSurfaces(0xFFF8F7F3))
        val VERSE = CalendarTheme("com.calendarmods.verse", "慢笺 · 竖排文句", ThemeRenderer.VERSE, ThemeSurfaces(0xFFE5E6DE, 0xFFE4E4D5))
        val ALMANAC = CalendarTheme("com.calendarmods.almanac", "岁时 · 复古黄历", ThemeRenderer.ALMANAC,
            ThemeSurfaces(0xFF53534D, 0xFF30312D, 0xFFEAE1C9, false))
        val HANGING = CalendarTheme("com.calendarmods.hanging", "一页 · 悬挂日历", ThemeRenderer.HANGING, ThemeSurfaces(0xFFE6E8E1))
        val entries: List<CalendarTheme> = listOf(GOLDEN, TEDDY, PHOTO, GOOD_DAY, VERSE, ALMANAC, HANGING)
    }
}

/** Pure catalog rules shared by installation, selection and tests. */
object ThemeCatalog {
    fun validateInstall(theme: CalendarTheme, installed: List<CalendarTheme>) {
        require(!theme.id.startsWith("com.calendarmods.")) { "Reserved built-in namespace" }
        installed.firstOrNull { it.id == theme.id }?.let { previous ->
            require(theme.version > previous.version) { "Theme update must increase version" }
            require(theme.renderer == previous.renderer) { "Changing renderer requires a new theme ID" }
        }
    }

    fun selectedId(savedId: String?, legacyIndex: Int, themes: List<CalendarTheme>): String {
        require(themes.isNotEmpty())
        return if (savedId != null) themes.firstOrNull { it.id == savedId }?.id ?: themes.first().id
        else CalendarTheme.entries.getOrNull(legacyIndex)?.id?.takeIf { id -> themes.any { it.id == id } } ?: themes.first().id
    }
}
