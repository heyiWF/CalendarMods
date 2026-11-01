package com.calendarmods

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
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
                finish(android.app.Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: " + checks.joinToString("; ")) })
                return
            }
            val inventory = ThemeRepository.get(targetContext).inventory
            val output = File(targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
            val data = CalendarData.create(PlainDate.of(2026, 11, 28))
            // Resolve images off the UI thread before rendering the gallery.
            for (theme in inventory.themes) if (theme.renderer.image != null) {
                Ui.themeImage(targetContext, theme, theme.defaultStyle(), inventory, 512)
                Ui.themeImage(targetContext, theme, theme.defaultStyle(), inventory, 768)
            }
            checkedMain {
                for (font in listOf(Typeface.DEFAULT_BOLD, Typeface.SERIF, Typeface.SANS_SERIF,
                    Typeface.create("sans-serif-condensed", Typeface.BOLD))) {
                    for (day in 1..31) {
                        val bitmap = Bitmap.createBitmap(360, 400, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        Plate(Canvas(bitmap), Color.BLACK, Color.WHITE).bigDay(day.toString().padStart(2, '0'), 0f, 0f, 360f, 400f, typeface = font)
                        var ink = 0
                        for (y in 0 until 400 step 2) for (x in 0 until 360 step 2) {
                            if (bitmap.getPixel(x, y) != Color.WHITE) ink++
                        }
                        check(ink > 200) { "Missing numeral $day / $font" }
                        bitmap.eraseColor(Color.WHITE)
                        val traditional = TongShengPlate(data, Almanacs.shared.day(data.today), Color.BLACK, Color.WHITE,
                            font, font, ThemeRenderer.TRADITIONAL, AlmanacIcons(host), null, null, true)
                        traditional.c = Canvas(bitmap)
                        traditional.dayNumber(day.toString(), 0f, 360f, 0f, 400f, 300f)
                        ink = 0
                        for (y in 0 until 400 step 2) for (x in 0 until 360 step 2) {
                            if (bitmap.getPixel(x, y) != Color.WHITE) ink++
                        }
                        check(ink > 200) { "Missing traditional numeral $day / $font" }
                        bitmap.recycle()
                    }
                }
                checks += "248 ordinary/traditional date/font raster checks"
                for (theme in inventory.themes) {
                    for ((w, h) in listOf(360 to 640, 640 to 360)) {
                        val page = PaperView(host)
                        page.setPage(theme, data, theme.defaultStyle(), AlmanacEnvironment(), inventory, false)
                        page.layout(0, 0, w, h)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        page.draw(Canvas(bitmap))
                        File(output, "${theme.id}-$w.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                    }
                }
                checks += "all ${inventory.themes.size} themes in portrait and landscape"
                for (theme in inventory.themes) {
                    var value = theme.defaultStyle()
                    lateinit var dialog: StyleSheetDialog
                    dialog = StyleSheetDialog(host, theme, value, AlmanacEnvironment(), inventory,
                        object : StyleSheetDialog.Listener {
                            override fun onChanged(style: ThemeStyle) { value = style; dialog.update(value, AlmanacEnvironment(), inventory) }
                            override fun onPickImage() = Unit
                            override fun onResetImage() = Unit
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
                    dialog.dismiss()
                }
                checks += "every theme: label/checkbox parity, bound state and stable controls"
            }
            val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive", CalendarTheme::class.java).apply { isAccessible = true }
            val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
            val paperField = MainActivity::class.java.getDeclaredField("paper").apply { isAccessible = true }
            val page = paperField.get(host) as PaperView
            checkedMain { enter.invoke(host, inventory.themes.first()) }
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
