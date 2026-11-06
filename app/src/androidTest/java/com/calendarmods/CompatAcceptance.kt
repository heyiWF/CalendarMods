package com.calendarmods

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import java.io.File

/** Runs against framework widgets and real font/resource implementations, including API 14. */
class CompatAcceptance : Instrumentation() {
    private var faceOnly = false
    override fun onCreate(arguments: Bundle?) {
        faceOnly = arguments?.getString("face") == "true"
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        val checks = mutableListOf<String>()
        var activity: MainActivity? = null
        try {
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            val host = activity
            val loaded = MainActivity::class.java.getDeclaredField("loaded").apply { isAccessible = true }
            repeat(100) { if (!loaded.getBoolean(host)) Thread.sleep(100) }
            check(loaded.getBoolean(host)) { "Calendar did not load" }
            if (faceOnly) {
                verifyFaceBrightness(host, checks)
                verifyFaceRotation(host, checks)
                finish(android.app.Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: " + checks.joinToString("; ")) })
                return
            }
            val output = File(targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
            val data = CalendarData.create(PlainDate.of(2026, 11, 6))
            checkedMain {
                val scales = listOf(ThemeStyle.MIN_FONT_SCALE, ThemeStyle.DEFAULT_FONT_SCALE,
                    ThemeStyle.MAX_FONT_SCALE)
                for ((w, h) in listOf(360 to 640, 640 to 360)) {
                    for (dark in listOf(false, true)) {
                        val scheme = CalendarTheme.DAILY.scheme(dark)
                        for (scale in scales) {
                            val page = PaperView(host)
                            page.setPage(CalendarTheme.DAILY, data, ThemeStyle(fontScale = scale),
                                false, dark)
                            page.layout(0, 0, w, h)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            page.draw(Canvas(bitmap))
                            val paper = scheme.paper.toInt()
                            var ink = 0
                            for (y in 0 until h step 2) for (x in 0 until w step 2) {
                                if (bitmap.getPixel(x, y) != paper) ink++
                            }
                            check(ink > w * h / 200) {
                                "Page printed almost nothing at $w x $h, scale $scale, dark $dark ($ink pixels)"
                            }
                            // The page keeps its bottom border: nothing is clipped off the screen.
                            for (y in h - 3 until h) for (x in 0 until w) {
                                check(bitmap.getPixel(x, y) == paper) {
                                    "Ink reached the bottom edge at $w x $h, scale $scale, dark $dark (row $y)"
                                }
                            }
                            check(bitmap.getPixel(0, 0) == paper) {
                                "Page did not fill its top-left corner at $w x $h, dark $dark"
                            }
                            // The date line ends inside the page edge instead of running past it.
                            for (y in 2 until 34) for (x in w - 8 until w) {
                                check(bitmap.getPixel(x, y) == paper) {
                                    "The date line ran past its right margin at $w x $h," +
                                        " scale $scale, dark $dark (column $x)"
                                }
                            }
                            File(output, "daily-$w-${(scale * 100).toInt()}-${if (dark) "dark" else "light"}.png")
                                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                }
                checks += "portrait and landscape pages print date, clock, 宜忌 and the month grid" +
                    " at 85%, 100% and 140% on both sheets without clipping the page edge"

                // The 12-hour reading is wider than 24-hour: it must still fit the same column.
                for (hour24 in listOf(true, false)) {
                    val page = PaperView(host)
                    page.setPage(CalendarTheme.DAILY, data, ThemeStyle(hour24 = hour24), false, false)
                    page.layout(0, 0, 360, 640)
                    val bitmap = Bitmap.createBitmap(360, 640, Bitmap.Config.ARGB_8888)
                    page.draw(Canvas(bitmap))
                    val paper = CalendarTheme.DAILY.scheme(false).paper.toInt()
                    for (y in 637 until 640) for (x in 0 until 360) {
                        check(bitmap.getPixel(x, y) == paper) {
                            "The ${if (hour24) "24" else "12"}-hour page was clipped at the bottom (row $y)"
                        }
                    }
                    File(output, "daily-360-${if (hour24) "24h" else "12h"}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                checks += "both clock readings print inside the page"

                var value = ThemeStyle()
                lateinit var dialog: StyleSheetDialog
                dialog = StyleSheetDialog(host, CalendarTheme.DAILY, value, object : StyleSheetDialog.Listener {
                    override fun onChanged(style: ThemeStyle) { value = style; dialog.update(value) }
                })
                dialog.show()
                val decor = dialog.window!!.decorView
                val labels = descendants(decor).filterIsInstance<TextView>()
                val label = labels.first { it.text.toString() == "翻页动画" }
                val row = label.parent.parent as ViewGroup
                val box = descendants(row).filterIsInstance<CheckBox>().single()
                check(box.isChecked)
                row.performClick()
                check(!value.pageAnimation && !box.isChecked)
                check(descendants(decor).any { it === label }) { "Setting rebuilt its controls" }
                box.performClick()
                check(value.pageAnimation && box.isChecked)
                // The font slider mirrors the stored scale and the sheet keeps its controls.
                val sliders = descendants(decor).filterIsInstance<android.widget.SeekBar>()
                val slider = sliders.first { it.tag == "font" }
                check(slider.max > 0)
                dialog.update(value.copy(fontScale = ThemeStyle.MAX_FONT_SCALE))
                check(slider.progress == slider.max) {
                    "Font slider did not follow the stored scale (${slider.progress} of ${slider.max})"
                }
                dialog.update(value.copy(fontScale = ThemeStyle.MIN_FONT_SCALE))
                check(slider.progress == 0) { "Font slider did not reach its minimum" }
                check(descendants(decor).any { it === label }) { "Font slider rebuilt its controls" }

                // 24-hour switch: on by default, bound both ways and persisted through the style.
                val hourLabel = labels.first { it.text.toString() == "24小时制" }
                val hourRow = hourLabel.parent.parent as ViewGroup
                val hourBox = descendants(hourRow).filterIsInstance<CheckBox>().single()
                check(hourBox.isChecked && value.hour24)
                hourRow.performClick()
                check(!value.hour24 && !hourBox.isChecked) { "The 24-hour switch did not reach the style" }
                hourRow.performClick()
                check(value.hour24 && hourBox.isChecked) { "The 24-hour switch did not come back" }
                check(descendants(decor).any { it === hourLabel }) { "The 24-hour switch rebuilt its row" }

                // Dark mode: four exclusive choices, exactly one selected, bound two ways.
                val darkLabel = labels.first { it.text.toString() == "始终深色" }
                val darkRow = darkLabel.parent.parent as ViewGroup
                val darkRadio = descendants(darkRow).filterIsInstance<android.widget.RadioButton>().single()
                check(!darkRadio.isChecked && value.darkMode == DarkMode.OFF)
                darkRadio.performClick()
                check(value.darkMode == DarkMode.ON && darkRadio.isChecked) {
                    "Dark mode radio did not reach the stored style"
                }
                check(descendants(decor).filterIsInstance<android.widget.RadioButton>().count { it.isChecked } == 1) {
                    "Dark mode choices are not exclusive"
                }
                dialog.update(value.copy(darkMode = DarkMode.SCHEDULE))
                val hourBars = sliders.filter { it.tag == "dark-hours" }
                val windowRows = descendants(decor).first { it.tag == "dark-window" }
                check(hourBars.size == 2 && windowRows.visibility == View.VISIBLE) {
                    "The night window only shows while 定时切换 is in use"
                }
                check(hourBars[0].progress == ThemeStyle.DEFAULT_DARK_FROM)
                check(hourBars[1].progress == ThemeStyle.DEFAULT_DARK_TO)
                dialog.update(value.copy(darkMode = DarkMode.ON))
                check(windowRows.visibility == View.GONE) {
                    "The night window stayed visible for a fixed source"
                }
                dialog.update(value.copy(darkMode = DarkMode.OFF))
                dialog.dismiss()
                checks += "style sheet: label/checkbox parity, font slider binding, the 24-hour switch," +
                    " exclusive dark mode choices and the night-window rows"
            }
            // A forced dark style must reach the window, the chooser and the retained page.
            val applyStyle = MainActivity::class.java
                .getDeclaredMethod("applyStyle", ThemeStyle::class.java).apply { isAccessible = true }
            val darkField = MainActivity::class.java.getDeclaredField("dark").apply { isAccessible = true }
            val chooserField = MainActivity::class.java.getDeclaredField("chooser").apply { isAccessible = true }
            val paperField = MainActivity::class.java.getDeclaredField("paper").apply { isAccessible = true }
            val page = paperField.get(host) as PaperView
            checkedMain { applyStyle.invoke(host, ThemeStyle(darkMode = DarkMode.ON)) }
            Thread.sleep(250)
            checkedMain {
                check(darkField.getBoolean(host)) { "Dark mode did not reach the window" }
                check((chooserField.get(host) as ChooserView).dark) { "The chooser kept the light sheet" }
            }
            checkedMain { applyStyle.invoke(host, ThemeStyle()) }
            Thread.sleep(250)
            checkedMain {
                check(!darkField.getBoolean(host)) { "The light sheet did not come back" }
                check(!(chooserField.get(host) as ChooserView).dark)
            }
            checks += "forced dark mode reaches the window and the chooser, and reverts cleanly"
            val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive").apply { isAccessible = true }
            val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
            checkedMain { enter.invoke(host) }
            Thread.sleep(100)
            checkedMain {
                if (MotionSettings.enabled(host)) check(page.alpha > 0f && page.alpha < 1f)
                exit.invoke(host)
                check(page.parent != null) { "Interrupted entry removed page before exit animation" }
            }
            Thread.sleep(400)
            checkedMain { check(page.parent == null) }
            checks += "fullscreen entry/exit animate and interrupted entry retains the outgoing page"
            verifyHistoryRegressions(host, checks)
            val result = Bundle().apply { putString("stream", "PASS: ${checks.joinToString("; ")}\n") }
            finish(android.app.Activity.RESULT_OK, result)
        } catch (failure: Throwable) {
            finish(android.app.Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${android.util.Log.getStackTraceString(failure)}") })
        } finally { activity?.let { checkedMain { it.finish() } } }
    }
    private fun checkedMain(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
