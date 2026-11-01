package com.calendarmods

import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock

/** Exercise real configuration changes, not just rotation of a square offline test bitmap. */
internal fun Instrumentation.verifyFaceRotation(initial: MainActivity, checks: MutableList<String>) {
    if (Build.VERSION.SDK_INT < 18) return
    val monitor = addMonitor(MainActivity::class.java.name, null, false)
    val theme = CalendarTheme.GOLDEN
    val store = ThemeStyleStore(targetContext)
    val before = store.read(theme)
    fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    val apply = MainActivity::class.java.getDeclaredMethod("applyStyle", CalendarTheme::class.java, ThemeStyle::class.java).apply { isAccessible = true }
    val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive", CalendarTheme::class.java).apply { isAccessible = true }
    val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
    val active = FaceBrightnessController::class.java.getDeclaredField("active").apply { isAccessible = true }
    val frame = FaceBrightnessController::class.java.getDeclaredField("lastFrame").apply { isAccessible = true }
    var current = initial
    try {
        runOnMainSync { apply.invoke(current, theme, before.copy(faceBrightness = true)); enter.invoke(current, theme) }
        for (rotation in listOf(0, 1, 2, 3, 0, 1, 0)) {
            val started = SystemClock.elapsedRealtime()
            uiAutomation.setRotation(rotation)
            var streaming = false
            var observedController: Any? = null
            var firstFrame = 0L
            val deadline = started + 10_000
            while (!streaming && SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(150)
                monitor.lastActivity?.let { current = it as MainActivity }
                runOnMainSync {
                    if (field("loaded").getBoolean(current) && field("resumed").getBoolean(current)) {
                        val controller = field("faceBrightness").get(current)
                        val timestamp = frame.getLong(controller)
                        if (observedController !== controller) {
                            observedController = controller
                            firstFrame = timestamp
                        } else {
                            streaming = active.getBoolean(controller) && timestamp > firstFrame + 300
                        }
                    }
                }
            }
            check(streaming) { "No analysed camera frames after display rotation $rotation" }
            checks += "real display rotation $rotation: resumed fullscreen camera supplies analysed frames"
        }
    } finally {
        runOnMainSync { exit.invoke(current); apply.invoke(current, theme, before); current.finish() }
        targetContext.getSharedPreferences("theme_styles", 0).edit().commit()
        uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        removeMonitor(monitor)
    }
}
