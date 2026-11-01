package com.calendarmods

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.View

/** Unlike bitmap-only tests, this exercises the actual window's hardware Canvas/display list. */
internal fun Instrumentation.verifyHardwareNumerals(host: MainActivity, checks: MutableList<String>) {
    if (Build.VERSION.SDK_INT < 18) return // UiAutomation screenshots are a test-only API.
    val original = MainActivity::class.java.getDeclaredField("root").apply { isAccessible = true }.get(host) as View
    val data = CalendarData.create(PlainDate.of(2026, 11, 1))
    val almanac = Almanacs.shared.day(data.today)
    val view = object : View(host) {
        var day = "1"
        var accelerated = false
        override fun onDraw(canvas: Canvas) {
            accelerated = canvas.isHardwareAccelerated
            canvas.drawColor(Color.WHITE)
            val w = width / 2f; val h = height / 2f
            for (index in 0..3) {
                val x = index % 2 * w; val y = index / 2 * h
                val checkpoint = canvas.save()
                canvas.clipRect(x, y, x + w, y + h)
                if (index < 2) {
                    Plate(canvas, Color.MAGENTA, Color.WHITE).bigDay(day, x + 20, y + 20, w - 40, h - 40,
                        typeface = if (index == 0) Typeface.DEFAULT_BOLD else Typeface.SERIF)
                } else {
                    canvas.translate(x, y)
                    canvas.scale(w / 1000f, h / 1000f)
                    val plate = TongShengPlate(data, almanac, Color.MAGENTA, Color.WHITE,
                        Typeface.SERIF, weightedFont(Typeface.DEFAULT_BOLD, 1000), ThemeRenderer.TRADITIONAL,
                        AlmanacIcons(host), null, null, true)
                    plate.c = canvas
                    plate.dayNumber(day, 0f, 1000f, 50f, 900f, if (index == 2) 800f else 400f)
                }
                canvas.restoreToCount(checkpoint)
            }
        }
    }
    try {
        runOnMainSync { host.setContentView(view) }
        for (day in listOf("1", "7", "9", "28")) {
            runOnMainSync { view.day = day; view.invalidate() }
            Thread.sleep(350)
            check(view.accelerated) { "Window was not hardware accelerated" }
            val location = IntArray(2)
            runOnMainSync { view.getLocationOnScreen(location) }
            val screen: Bitmap = checkNotNull(uiAutomation.takeScreenshot())
            try {
                for (cell in 0..3) {
                    val left = location[0] + cell % 2 * view.width / 2
                    val top = location[1] + cell / 2 * view.height / 2
                    var ink = 0
                    for (y in top until top + view.height / 2 step 3)
                        for (x in left until left + view.width / 2 step 3)
                            if (screen.getPixel(x, y) == Color.MAGENTA) ink++
                    check(ink > 200) { "Hardware numeral $day in cell $cell vanished ($ink pixels)" }
                }
            } finally { screen.recycle() }
        }
        checks += "16 hardware-window numeral checks, ordinary/serif/traditional at different scales"
        val inventory = ThemeRepository.get(targetContext).inventory
        val output = java.io.File(targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        for (theme in inventory.themes.filter { it.renderer.isTongSheng || it == CalendarTheme.GOLDEN }) {
            runOnMainSync {
                host.setContentView(PaperView(host).apply {
                    setPage(theme, data, theme.defaultStyle(), AlmanacEnvironment(), inventory, false)
                })
            }
            Thread.sleep(350)
            val screen = checkNotNull(uiAutomation.takeScreenshot())
            try {
                java.io.File(output, "${theme.id}-hardware-day1.png").outputStream().use {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            } finally { screen.recycle() }
        }
    } finally { runOnMainSync { host.setContentView(original) } }
}
