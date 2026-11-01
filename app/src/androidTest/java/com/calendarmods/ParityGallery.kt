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
internal fun Instrumentation.verifyParityGallery(activity: MainActivity, checks: MutableList<String>) {
    val output = File(targetContext.getExternalFilesDir(null), "parity").apply { mkdirs() }
    val data = CalendarData.create(LocalDate.of(2026, 11, 28))
    for (theme in CalendarTheme.entries) for ((w, h) in listOf(360 to 640, 640 to 360)) {
        val captured = java.util.concurrent.atomic.AtomicReference<Rect>()
        runOnMainSync {
            activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
            val surface = androidx.compose.ui.platform.ComposeView(activity)
            activity.setContentView(surface)
            surface.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f),
                        LocalThemeStyles provides mapOf(theme.id to theme.defaultStyle()),
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
        File(output, "${theme.id}-$w.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle(); screen.recycle()
        checks += "${theme.id} ${w}x$h parity reference"
    }
}
