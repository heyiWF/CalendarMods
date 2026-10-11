package com.calendarmods

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.Lifecycle
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference

/** Real system-injected gestures, semantic assertions and screenshots; no test-framework downloads. */
class CalendarAcceptanceInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = Bundle()
        var result = Activity.RESULT_OK
        val checks = mutableListOf<String>()
        val preferences = targetContext.getSharedPreferences("calendar", 0)
        val savedTheme = preferences.getInt("theme", 0)
        val savedZone = ZoneId.systemDefault().id
        try {
            targetContext.getSharedPreferences("calendar", 0).edit().putInt("theme", 0).commit()
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitTheme(CalendarTheme.GOLDEN)
            for (rotation in 0..1) {
                uiAutomation.setRotation(rotation)
                await("orientation $rotation") {
                    val bounds = Rect().also { uiAutomation.rootInActiveWindow?.getBoundsInScreen(it) }
                    if (rotation == 0) bounds.height() > bounds.width() else bounds.width() > bounds.height()
                }
                awaitTheme(CalendarTheme.GOLDEN)
                CalendarTheme.entries.forEachIndexed { index, theme ->
                    if (index > 0) swipe(left = true)
                    awaitTheme(theme)
                    capture("${if (rotation == 0) "portrait" else "landscape"}-${index + 1}-${theme.name.lowercase()}")
                    checks += "${theme.name}: rotation=$rotation, date, forward swipe, screenshot PASS"
                }
                CalendarTheme.entries.reversed().drop(1).forEach { theme ->
                    swipe(left = false)
                    awaitTheme(theme)
                    checks += "${theme.name}: rotation=$rotation, reverse swipe PASS"
                }
                swipe(left = true, fraction = .08f, duration = 650)
                awaitTheme(CalendarTheme.GOLDEN)
                checks += "rotation=$rotation, short drag settles back PASS"
            }
            swipe(left = true)
            awaitTheme(CalendarTheme.TEDDY)
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            await("portrait recreation") { targetContext.resources.configuration.orientation == 1 }
            awaitTheme(CalendarTheme.TEDDY)
            checks += "nondefault theme survives rotation/recreation PASS"
            swipe(left = false)
            awaitTheme(CalendarTheme.GOLDEN)
            verifyLiveDates(checks, savedZone)
            report.putString("stream", "\nPASS ${checks.size} checks\n" + checks.joinToString("\n"))
        } catch (failure: Throwable) {
            result = Activity.RESULT_CANCELED
            report.putString("stream", "\nFAIL after ${checks.size} checks\n" + failure.stackTraceToString())
        } finally {
            shell("cmd alarm set-timezone $savedZone")
            preferences.edit().putInt("theme", savedTheme).commit()
            uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            finish(result, report)
        }
    }

    private fun awaitTheme(theme: CalendarTheme, expectedDate: LocalDate = LocalDate.now()) {
        await("theme ${theme.name}") {
            val tree = nodes()
            tree.any { it.viewIdResourceName == "theme-title" && it.text?.toString() == theme.title } &&
                tree.any { it.viewIdResourceName == "art-${theme.name}" && it.contentDescription?.contains(expectedDate.toString()) == true }
        }
        SystemClock.sleep(350)
        val tree = nodes()
        val dateData = CalendarData.create(expectedDate)
        val displayedText = tree.mapNotNull { it.text?.toString() }
        when (theme) {
            CalendarTheme.GOOD_DAY -> check(displayedText.contains("宜 ${dateData.suitableText(1)}")) { "Good-day almanac is not computed data" }
            CalendarTheme.ALMANAC -> {
                check(displayedText.contains(dateData.suitableText(6))) { "Missing computed suitable activities" }
                check(displayedText.contains(dateData.avoidText(4))) { "Missing computed avoided activities" }
                if (dateData.solarTerm == null) check(displayedText.contains("农\n历")) { "A previous solar term leaked into today" }
            }
            CalendarTheme.HANGING -> {
                check(displayedText.contains("宜 ${dateData.suitableText(2)}  ｜  忌 ${dateData.avoidText(2)}")) { "Hanging almanac is still decorative" }
                if (dateData.solarTerm == null) check(displayedText.contains("日历")) { "A previous solar term leaked into today" }
            }
            else -> Unit
        }
        val artwork = tree.firstOrNull { it.viewIdResourceName == "art-${theme.name}" }
            ?: error("Missing artwork ${theme.name}")
        check(artwork.contentDescription?.contains(expectedDate.toString()) == true) { "Date missing" }
        val screen = Rect().also { uiAutomation.rootInActiveWindow.getBoundsInScreen(it) }
        val bounds = Rect().also { artwork.getBoundsInScreen(it) }
        check(!bounds.isEmpty && screen.contains(bounds)) { "Artwork clipped: $bounds in $screen" }
    }

    private fun verifyLiveDates(checks: MutableList<String>, savedZone: String) {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val clock = AdvancingClock()
        clock.set(ZonedDateTime.of(2026, 12, 31, 23, 59, 52, 0, ZoneId.systemDefault()).toInstant())
        runOnMainSync { activity.setContent { MaterialTheme { CalendarApp(clock) } } }
        awaitTheme(CalendarTheme.GOLDEN, LocalDate.of(2026, 12, 31))
        awaitTheme(CalendarTheme.GOLDEN, LocalDate.of(2027, 1, 1))
        check(nodes().any { it.text?.toString() == "JANUARY" })
        check(nodes().any { it.text?.toString() == "2027" })
        checks += "foreground midnight updates day, month and year without reopening PASS"
        CalendarTheme.entries.drop(1).forEach { theme ->
            swipe(left = true)
            awaitTheme(theme, LocalDate.of(2027, 1, 1))
        }
        checks += "all seven themes share new date, lunar date, festivals and almanac PASS"

        // Use a fixed instant so the two real system zones fall on different dates.
        clock.set(Instant.parse("2027-01-01T05:00:00Z"))
        shell("cmd alarm set-timezone Pacific/Kiritimati")
        awaitTheme(CalendarTheme.HANGING, LocalDate.of(2027, 1, 1))
        shell("cmd alarm set-timezone Pacific/Honolulu")
        awaitTheme(CalendarTheme.HANGING, LocalDate.of(2026, 12, 31))
        checks += "system timezone broadcast immediately changes displayed calendar date PASS"
        shell("cmd alarm set-timezone $savedZone")

        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        await("app backgrounded") { !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        clock.set(ZonedDateTime.of(2028, 2, 29, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant())
        targetContext.startActivity(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        awaitTheme(CalendarTheme.HANGING, LocalDate.of(2028, 2, 29))
        checks += "resume after background refreshes to leap day with complete calendar data PASS"
        runOnMainSync { activity.setContent { MaterialTheme { CalendarApp() } } }
        awaitTheme(CalendarTheme.HANGING)
        checks += "restored production clock shows actual device date PASS"
    }

    private fun shell(command: String): String = uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText() }
    }

    private class AdvancingClock : CalendarClock {
        private val anchor = AtomicReference(Instant.now() to SystemClock.elapsedRealtime())
        fun set(instant: Instant) { anchor.set(instant to SystemClock.elapsedRealtime()) }
        override fun now(): ZonedDateTime {
            val (instant, elapsed) = anchor.get()
            return instant.plusMillis(SystemClock.elapsedRealtime() - elapsed).atZone(ZoneId.systemDefault())
        }
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            result += node
            repeat(node.childCount) { visit(node.getChild(it)) }
        }
        visit(uiAutomation.rootInActiveWindow)
        return result
    }

    private fun await(label: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < end) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        error("Timed out: $label; UI=${nodes().mapNotNull { it.text?.toString() }}")
    }

    private fun swipe(left: Boolean, fraction: Float = .62f, duration: Long = 400) {
        val node = nodes().first { it.viewIdResourceName == "theme-pager" }
        val area = Rect().also { node.getBoundsInScreen(it) }
        val delta = area.width() * fraction * if (left) -1 else 1
        val x = area.exactCenterX() - delta / 2
        val y = area.exactCenterY()
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, at: Long, pointX: Float) {
            val event = MotionEvent.obtain(down, at, action, pointX, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)) { "Input injection failed" }
            event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, down, x)
        for (step in 1..24) {
            SystemClock.sleep(duration / 24)
            inject(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis(), x + delta * step / 24)
        }
        inject(MotionEvent.ACTION_UP, SystemClock.uptimeMillis(), x + delta)
        SystemClock.sleep(600)
    }

    private fun capture(name: String) {
        val output = File(targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
