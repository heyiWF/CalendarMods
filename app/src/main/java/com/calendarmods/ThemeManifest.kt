package com.calendarmods

import org.json.JSONObject

/** Strict v1 wire format. Unknown fields fail instead of silently changing a design. */
internal object ThemeManifest {
    const val MAX_BYTES = 32 * 1024

    fun decode(json: String): CalendarTheme {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Theme manifest is too large" }
        val root = JSONObject(json)
        root.only("schemaVersion", "id", "version", "title", "author", "renderer", "surfaces", "defaults", "imageAsset")
        val rendererId = root.string("renderer")
        val renderer = ThemeRenderer.entries.singleOrNull { it.wireId == rendererId }
            ?: error("Unsupported renderer: $rendererId")
        val base = CalendarTheme.entries.single { it.renderer == renderer }
        val surfaces = root.optJSONObject("surfaces") ?: run {
            require(!root.has("surfaces")); JSONObject()
        }
        surfaces.only("backdrop", "paper", "chrome", "darkSystemBarIcons")
        val defaults = root.optJSONObject("defaults") ?: run {
            require(!root.has("defaults")); JSONObject()
        }
        defaults.only("fill")
        require(renderer.image != null || !defaults.has("fill")) { "Renderer does not support fill" }
        return CalendarTheme(
            id = root.string("id"), title = root.string("title"), renderer = renderer,
            surfaces = ThemeSurfaces(
                surfaces.color("backdrop", base.surfaces.backdrop),
                surfaces.color("paper", base.surfaces.paper),
                surfaces.color("chrome", base.surfaces.chrome),
                surfaces.boolean("darkSystemBarIcons", base.surfaces.darkSystemBarIcons),
            ),
            schemaVersion = root.integer("schemaVersion"), version = root.integer("version"),
            author = root.string("author"),
            defaultFill = if (defaults.has("fill")) defaults.string("fill") else "crop",
            hasImageAsset = root.boolean("imageAsset", false),
        )
    }

    fun encode(theme: CalendarTheme): String = JSONObject().apply {
        put("schemaVersion", theme.schemaVersion); put("id", theme.id); put("version", theme.version)
        put("title", theme.title); put("author", theme.author); put("renderer", theme.renderer.wireId)
        put("imageAsset", theme.hasImageAsset)
        put("surfaces", JSONObject().apply {
            put("backdrop", theme.surfaces.backdrop.hex()); put("paper", theme.surfaces.paper.hex())
            put("chrome", theme.surfaces.chrome.hex()); put("darkSystemBarIcons", theme.surfaces.darkSystemBarIcons)
        })
        if (theme.renderer.image != null) put("defaults", JSONObject().put("fill", theme.defaultFill))
    }.toString()

    private fun Long.hex() = "#" + toString(16).padStart(8, '0').uppercase()
    private fun JSONObject.only(vararg allowed: String) {
        require(keys().asSequence().all { it in allowed }) { "Unknown theme property" }
    }
    private fun JSONObject.string(key: String): String = (get(key) as? String) ?: error("$key must be a string")
    private fun JSONObject.integer(key: String): Int = (get(key) as? Int) ?: error("$key must be an integer")
    private fun JSONObject.boolean(key: String, fallback: Boolean): Boolean =
        if (!has(key)) fallback else (get(key) as? Boolean) ?: error("$key must be a boolean")
    private fun JSONObject.color(key: String, fallback: Long): Long {
        if (!has(key)) return fallback
        val value = string(key)
        require(value.matches(Regex("#[0-9a-fA-F]{8}"))) { "$key must be #AARRGGBB" }
        return value.drop(1).toLong(16)
    }
}
