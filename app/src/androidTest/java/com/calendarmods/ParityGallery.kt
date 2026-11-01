package com.calendarmods

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalDate

/** Equal pixel size/date/font scale, suitable for side-by-side comparison with framework plates. */
internal fun Instrumentation.verifyParityGallery(activity: MainActivity, checks: MutableList<String>, paletteOnly: Boolean = false) {
    val output = File(targetContext.getExternalFilesDir(null), if (paletteOnly) "palette" else "parity").apply { mkdirs() }
    data class Sample(val theme: CalendarTheme, val date: LocalDate, val style: ThemeStyle, val w: Int, val h: Int)
    val samples = if (paletteOnly) CalendarTheme.entries.filter { it.renderer.isTongSheng }.flatMap { theme ->
        listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 28), LocalDate.of(2026, 10, 1)).flatMap { date ->
            listOf(false, true).map { dark -> Sample(theme, date, theme.defaultStyle().copy(dark = dark), 360, 640) }
        } + Sample(theme, LocalDate.of(2026, 11, 28), theme.defaultStyle().copy(ink = AlmanacInk.BLUE), 360, 640)
    } else CalendarTheme.entries.flatMap { theme -> listOf(360 to 640, 640 to 360).map { (w, h) ->
        Sample(theme, LocalDate.of(2026, 11, 28), theme.defaultStyle(), w, h)
    } }
    for ((theme, date, style, w, h) in samples) {
        val data = CalendarData.create(date)
        val captured = java.util.concurrent.atomic.AtomicReference<Rect>()
        runOnMainSync {
            activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            val surface = androidx.compose.ui.platform.ComposeView(activity)
            activity.setContentView(surface)
            surface.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f),
                        LocalThemeStyles provides mapOf(theme.id to style),
                        LocalAlmanacEnvironment provides AlmanacEnvironment(minute = 720)) {
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.requiredSize(w.dp, h.dp).onGloballyPositioned { coordinates ->
                                val r = coordinates.boundsInWindow()
                                captured.set(Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
                            }.testTag("parity-paper")
                                .semantics { testTagsAsResourceId = true }) {
                                CalendarPaper(theme, data)
                            }
                        }
                    }
                }
            }
        }
        repeat(50) { if (captured.get() == null) SystemClock.sleep(100) }
        SystemClock.sleep(500)
        val area = checkNotNull(captured.get())
        val screen = checkNotNull(uiAutomation.takeScreenshot())
        val image = Bitmap.createBitmap(screen, area.left, area.top, area.width(), area.height())
        if (paletteOnly) {
            val expected = almanacPalette(theme, style, 720, false, almanacDefaultIsRed(data, style)).ink.toInt()
            var inkPixels = 0
            for (y in 0 until image.height step 2) for (x in 0 until image.width step 2) {
                if (image.getPixel(x, y) == expected) inkPixels++
            }
            check(inkPixels > 100) { "${theme.name} $date dark=${style.dark} ink=${style.ink}: expected ink missing ($inkPixels)" }
        }
        val suffix = if (paletteOnly) "-$date-${style.dark}-${style.ink}" else ""
        File(output, "${theme.id}-$w$suffix.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle(); screen.recycle()
        checks += "${theme.id} ${w}x$h $date dark=${style.dark} ink=${style.ink} reference"
    }
}
