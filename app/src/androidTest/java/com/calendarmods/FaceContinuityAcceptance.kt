package com.calendarmods

import android.app.Instrumentation
import android.hardware.Camera
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Stop a real camera stream repeatedly; the watchdog must recover without user interaction. */
@Suppress("DEPRECATION")
internal fun Instrumentation.verifyFaceContinuity(activity: MainActivity, checks: MutableList<String>) {
    val controller = MainActivity::class.java.getDeclaredField("faceBrightness").apply { isAccessible = true }.get(activity)
    fun field(name: String) = FaceBrightnessController::class.java.getDeclaredField(name).apply { isAccessible = true }
    val frame = field("lastFrame")
    val power = activity.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    val started = SystemClock.elapsedRealtime()
    var interruptions = 0
    var lastInterrupted = 0L
    var latestFrame = 0L
    while (SystemClock.elapsedRealtime() - started < 75_000L) {
        val elapsed = SystemClock.elapsedRealtime() - started
        if (interruptions < 3 && elapsed >= interruptions * 25_000L) {
            val session = field("session").get(controller)
            checkNotNull(session)
            val done = CountDownLatch(1)
            var failure: Throwable? = null
            FaceCameraWorker.handler.post {
                try {
                    val camera = session.javaClass.getDeclaredField("camera").apply { isAccessible = true }.get(session) as Camera
                    camera.stopPreview()
                } catch (error: Throwable) { failure = error }
                finally { done.countDown() }
            }
            check(done.await(5, TimeUnit.SECONDS)) { "Camera worker stopped responding" }
            failure?.let { throw it }
            lastInterrupted = SystemClock.elapsedRealtime()
            interruptions++
        }
        var active = false
        var keptAwake = false
        runOnMainSync {
            active = field("active").getBoolean(controller)
            latestFrame = frame.getLong(controller)
            keptAwake = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        }
        check(active && keptAwake && power.isScreenOn) { "Fullscreen stopped capture or allowed screen sleep" }
        if (SystemClock.elapsedRealtime() - lastInterrupted > 20_000L)
            check(latestFrame > lastInterrupted + 500L) { "Stopped stream did not recover automatically" }
        Thread.sleep(500)
    }
    check(latestFrame > lastInterrupted + 500L)
    checks += "75-second unattended run: three stopped streams automatically recover; fullscreen remains awake"
}
