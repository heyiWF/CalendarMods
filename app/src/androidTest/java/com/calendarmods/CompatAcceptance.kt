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
                    for (scale in scales) {
                        val page = PaperView(host)
                        page.setPage(CalendarTheme.DAILY, data, ThemeStyle(fontScale = scale), false)
                        page.layout(0, 0, w, h)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.draw(Canvas(bitmap))
                        val paper = CalendarTheme.DAILY.surfaces.paper.toInt()
                        var ink = 0
                        for (y in 0 until h step 2) for (x in 0 until w step 2) {
                            if (bitmap.getPixel(x, y) != paper) ink++
                        }
                        check(ink > w * h / 200) {
                            "Page printed almost nothing at $w x $h, scale $scale ($ink pixels)"
                        }
                        // The page keeps its bottom border: nothing is clipped off the screen.
                        for (y in h - 3 until h) for (x in 0 until w) {
                            check(bitmap.getPixel(x, y) == paper) {
                                "Ink reached the bottom edge at $w x $h, scale $scale (row $y)"
                            }
                        }
                        File(output, "daily-$w-${(scale * 100).toInt()}.png").outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                checks += "portrait and landscape pages print date, clock, 宜忌 and the month grid" +
                    " at 85%, 100% and 140% font scale without clipping the page edge"

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
                val slider = descendants(decor).filterIsInstance<android.widget.SeekBar>().single()
                check(slider.max > 0)
                dialog.update(value.copy(fontScale = ThemeStyle.MAX_FONT_SCALE))
                check(slider.progress == slider.max) {
                    "Font slider did not follow the stored scale (${slider.progress} of ${slider.max})"
                }
                dialog.update(value.copy(fontScale = ThemeStyle.MIN_FONT_SCALE))
                check(slider.progress == 0) { "Font slider did not reach its minimum" }
                check(descendants(decor).any { it === label }) { "Font slider rebuilt its controls" }
                dialog.dismiss()
                checks += "style sheet: label/checkbox parity, font slider binding and stable controls"
            }
            val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive").apply { isAccessible = true }
            val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
            val paperField = MainActivity::class.java.getDeclaredField("paper").apply { isAccessible = true }
            val page = paperField.get(host) as PaperView
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
