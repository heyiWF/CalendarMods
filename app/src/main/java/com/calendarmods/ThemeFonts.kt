package com.calendarmods

import android.graphics.Typeface
import java.io.File

/** Validate with the platform font parser; never accept a silently substituted default face. */
internal object ThemeFonts {
    const val MAX_FILE_BYTES = 16 * 1024 * 1024
    const val MAX_PACKAGE_BYTES = 32 * 1024 * 1024

    fun load(file: File): Typeface {
        require(file.length() in 1..MAX_FILE_BYTES.toLong()) { "Font must be at most 16 MiB" }
        val signature = ByteArray(4).also { bytes ->
            java.io.DataInputStream(file.inputStream()).use { it.readFully(bytes) }
        }.toList()
        require(signature == listOf<Byte>(0, 1, 0, 0) || signature == "OTTO".toByteArray().toList()) {
            "Only TTF and OTF fonts are supported"
        }
        return checkNotNull(Typeface.createFromFile(file)) { "Font could not be loaded" }
    }
}
