package com.calendarmods

import android.graphics.Typeface
import android.graphics.fonts.Font
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

internal val LocalThemeFonts = staticCompositionLocalOf<Map<String, Map<String, FontFamily>>> { emptyMap() }
internal val LocalCalendarFont = staticCompositionLocalOf<FontFamily?> { null }

/** Validate with Android's font parser; never accept a silently substituted default Typeface. */
internal object ThemeFonts {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_PACKAGE_BYTES = 32 * 1024 * 1024

    fun load(file: File): FontFamily {
        require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Font must be at most 16 MiB" }
        val signature = ByteArray(4).also { bytes ->
            java.io.DataInputStream(file.inputStream()).use { it.readFully(bytes) }
        }.toList()
        require(signature == listOf<Byte>(0, 1, 0, 0) || signature == "OTTO".toByteArray().toList()) {
            "Only TTF and OTF fonts are supported"
        }
        val font = Font.Builder(file).build()
        val family = android.graphics.fonts.FontFamily.Builder(font).build()
        return FontFamily(Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build())
    }
}
