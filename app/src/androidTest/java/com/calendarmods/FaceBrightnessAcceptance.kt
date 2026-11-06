package com.calendarmods

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.SystemClock

internal fun Instrumentation.verifyFaceBrightness(activity: MainActivity, checks: MutableList<String>) {
    fun main(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    val source = context.assets.open("face-fixture.png").use { checkNotNull(BitmapFactory.decodeStream(it)) }
    val square = Bitmap.createScaledBitmap(source, 320, 320, true)
    val image = Bitmap.createBitmap(square, 0, 0, 320, 240)
    square.recycle()
    source.recycle()
    try {
        for (rotation in listOf(0, 90, 180, 270)) {
            val rotated = Bitmap.createBitmap(image, 0, 0, image.width, image.height,
                Matrix().apply { postRotate(-rotation.toFloat()) }, false)
            val bytes = ByteArray(rotated.width * rotated.height)
            for (y in 0 until rotated.height) for (x in 0 until rotated.width) {
                val color = rotated.getPixel(x, y)
                val gray = (77 * Color.red(color) + 150 * Color.green(color) + 29 * Color.blue(color)) / 256
                bytes[y * rotated.width + x] = (16 + gray * 219 / 255).toByte()
            }
            val detector = LuminanceFaceDetector(rotated.width, rotated.height, rotation)
            try {
                check(detector.detect(bytes)) { "Known face missed at $rotation degrees" }
                bytes.fill(16)
                check(!detector.detect(bytes)) { "Blank frame reported a face" }
            } finally { detector.close(); if (rotated !== image) rotated.recycle() }
        }
        checks += "AOSP detector: known face and blank NV21 frames at all four rotations"
    } finally { image.recycle() }

    val store = ThemeStyleStore(targetContext)
    val before = store.read()
    check(!before.faceBrightness)
    store.save(before.copy(faceBrightness = true))
    check(ThemeStyleStore(targetContext).read().faceBrightness)
    store.save(before)
    checks += "face-presence opt-in persists and defaults to off"

    val apply = MainActivity::class.java.getDeclaredMethod("applyStyle", ThemeStyle::class.java).apply { isAccessible = true }
    val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive").apply { isAccessible = true }
    val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
    val controllerField = MainActivity::class.java.getDeclaredField("faceBrightness").apply { isAccessible = true }
    val controller = controllerField.get(activity)
    val activeField = FaceBrightnessController::class.java.getDeclaredField("active").apply { isAccessible = true }
    val frameField = FaceBrightnessController::class.java.getDeclaredField("lastFrame").apply { isAccessible = true }
    var original = -1f
    val started = SystemClock.elapsedRealtime()
    try {
        main {
            original = activity.window.attributes.screenBrightness
            apply.invoke(activity, before.copy(faceBrightness = true))
            check(!activeField.getBoolean(controller)) { "Camera active outside fullscreen" }
            enter.invoke(activity)
        }
        Thread.sleep(4000)
        main {
            check(activeField.getBoolean(controller)) { "Camera did not start (permission or device unavailable)" }
            check(frameField.getLong(controller) > started + 500) { "Camera is not delivering analysed frames" }
            check(activity.window.attributes.screenBrightness > .8f)
        }
        checks += "camera delivers frames in fullscreen during startup grace"
        main { callActivityOnPause(activity) }
        main {
            check(!activeField.getBoolean(controller)) { "Capture survived activity pause" }
            check(activity.window.attributes.screenBrightness == original)
            callActivityOnResume(activity)
        }
        Thread.sleep(1200)
        main { check(activeField.getBoolean(controller)) { "Capture did not resume in opted-in fullscreen" } }
        checks += "pause restores brightness/releases capture; resume restarts only opted-in fullscreen"
        verifyFaceContinuity(activity, checks)

        main {
            exit.invoke(activity)
        }
        Thread.sleep(400)
        main {
            check(!activeField.getBoolean(controller))
            check(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON == 0)
            check(activity.window.attributes.screenBrightness == original) { "Window brightness not restored" }
        }
        checks += "fullscreen exit releases capture and restores the original brightness override"
    } finally {
        main { exit.invoke(activity); apply.invoke(activity, before) }
        // Instrumentation terminates the process immediately; flush the final asynchronous save.
        targetContext.getSharedPreferences("theme_styles", 0).edit().commit()
    }
}
