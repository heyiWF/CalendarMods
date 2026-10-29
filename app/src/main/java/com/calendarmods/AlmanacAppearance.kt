package com.calendarmods




internal enum class AlmanacInk(val title: String, val light: Long, val night: Long) {
    DEFAULT("原版", 0, 0), GREEN("松绿", 0xFF246B39, 0xFF9ACAA7),
    RED("朱红", 0xFFB82E24, 0xFFEDA39B), BLUE("靛蓝", 0xFF294F82, 0xFFA8BFE2),
    BROWN("赭棕", 0xFF795337, 0xFFD3B58E), PURPLE("黛紫", 0xFF6D4779, 0xFFCDB1DB),
    BLACK("墨黑", 0xFF292C2A, 0xFFD6D5CC),
}

internal data class AlmanacPalette(val ink: Long, val paper: Long, val backdrop: Long, val dark: Boolean)
internal object AlmanacNightPolicy {
    /** Start inclusive / end exclusive. Equal endpoints mean no scheduled interval. */
    fun scheduled(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }
    fun dark(style: ThemeStyle, minute: Int, ambient: Boolean): Boolean = style.dark ||
        (style.scheduledDark && scheduled(minute, style.darkStart, style.darkEnd)) || (style.ambientDark && ambient)
}

/** Stateful hysteresis, driven by elapsed real time, never by the browsed calendar date. */
internal class AmbientDarkGate {
    var dark = false
        private set
    private var candidate: Boolean? = null
    private var since = 0L
    fun update(lux: Float?, now: Long): Boolean {
        if (lux == null || !lux.isFinite() || lux < 0) { candidate = null; return dark }
        val wanted = when { lux <= 10f -> true; lux >= 40f -> false; else -> null }
        if (wanted == null || wanted == dark) candidate = null
        else {
            if (candidate != wanted) { candidate = wanted; since = now }
            if (now - since >= if (wanted) 30_000L else 10_000L) { dark = wanted; candidate = null }
        }
        return dark
    }
}

internal fun almanacPalette(theme: CalendarTheme, style: ThemeStyle, minute: Int, ambient: Boolean): AlmanacPalette {
    val dark = AlmanacNightPolicy.dark(style, minute, ambient)
    val ink = style.ink.takeUnless { it == AlmanacInk.DEFAULT }
        ?: if (theme.renderer in listOf(ThemeRenderer.TRADITIONAL, ThemeRenderer.PEITAIYI)) AlmanacInk.GREEN else AlmanacInk.RED
    return AlmanacPalette(if (dark) ink.night else if (style.ink == AlmanacInk.DEFAULT) theme.surfaces.chrome else ink.light,
        if (dark) 0xFF161B19 else theme.surfaces.paper, if (dark) 0xFF101512 else theme.surfaces.backdrop, dark)
}
