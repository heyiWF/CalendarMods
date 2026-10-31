package com.calendarmods

/** Imported themes inherit the page motion of their native renderer. */
internal enum class DayMotion(val millis: Int) {
    BOUND_PAGE(800), ILLUSTRATED_PAGE(760), PHOTO_CARD(650), TYPE_LIFT(650),
    VERSE_REVEAL(780), ALMANAC_PEEL(850), HANGING_PAGE(820);
}

internal val ThemeRenderer.dayMotion: DayMotion get() = when (this) {
    ThemeRenderer.GOLDEN -> DayMotion.BOUND_PAGE
    ThemeRenderer.TEDDY -> DayMotion.ILLUSTRATED_PAGE
    ThemeRenderer.PHOTO -> DayMotion.PHOTO_CARD
    ThemeRenderer.GOOD_DAY -> DayMotion.TYPE_LIFT
    ThemeRenderer.VERSE -> DayMotion.VERSE_REVEAL
    ThemeRenderer.ALMANAC -> DayMotion.ALMANAC_PEEL
    ThemeRenderer.HANGING -> DayMotion.HANGING_PAGE
    ThemeRenderer.TRADITIONAL, ThemeRenderer.CANTON, ThemeRenderer.AUSPICIOUS, ThemeRenderer.PEITAIYI -> DayMotion.ALMANAC_PEEL
}

/** Themes that page the 宜 / 忌 lines instead of printing the whole list at once. */
internal val ThemeRenderer.pagesAlmanacLines: Boolean
    get() = this == ThemeRenderer.HANGING || this == ThemeRenderer.GOOD_DAY
