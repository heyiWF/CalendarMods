package com.calendarmods

import android.app.Activity
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
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
    private lateinit var activities: ActivityMonitor
    private var chooserNavigationVisible = true
    private var lastBars = "not measured"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    override fun onStart() {
        val report = Bundle()
        var result = Activity.RESULT_OK
        val checks = mutableListOf<String>()
        val preferences = targetContext.getSharedPreferences("calendar", 0)
        val savedTheme = preferences.getInt("theme", 0)
        val savedImmersive = preferences.getBoolean("immersive", false)
        val savedZone = ZoneId.systemDefault().id
        try {
            activities = addMonitor(MainActivity::class.java.name, null, false)
            preferences.edit().putInt("theme", 0).putBoolean("immersive", false).commit()
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitTheme(CalendarTheme.GOLDEN)
            runOnMainSync {
                chooserNavigationVisible = activities.lastActivity.window.decorView.rootWindowInsets
                    .isVisible(WindowInsets.Type.navigationBars())
            }
            for (rotation in 0..1) {
                uiAutomation.setRotation(rotation)
                await("orientation $rotation") {
                    val bounds = Rect().also { uiAutomation.rootInActiveWindow?.getBoundsInScreen(it) }
                    if (rotation == 0) bounds.height() > bounds.width() else bounds.width() > bounds.height()
                }
                awaitTheme(CalendarTheme.GOLDEN)
                verifyInterruptedEntry(checks, rotation)
                CalendarTheme.entries.forEachIndexed { index, theme ->
                    if (index > 0) swipe(left = true)
                    awaitTheme(theme)
                    capture("${if (rotation == 0) "portrait" else "landscape"}-${index + 1}-${theme.name.lowercase()}")
                    checks += "${theme.name}: rotation=$rotation, date, forward swipe, screenshot PASS"
                    press("art-${theme.name}")
                    awaitImmersive(theme)
                    capture("immersive-${if (rotation == 0) "portrait" else "landscape"}-${index + 1}-${theme.name.lowercase()}")
                    press("immersive-${theme.name}", long = true)
                    awaitTheme(theme)
                    awaitBars(hidden = false)
                    checks += "${theme.name}: rotation=$rotation, tap fullscreen, hidden bars, long press returns to same theme PASS"
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
            verifyImmersiveLifecycle(checks)
            report.putString("stream", "\nPASS ${checks.size} checks\n" + checks.joinToString("\n"))
        } catch (failure: Throwable) {
            result = Activity.RESULT_CANCELED
            report.putString("stream", "\nFAIL after ${checks.size} checks\n" + failure.stackTraceToString())
        } finally {
            shell("cmd alarm set-timezone $savedZone")
            preferences.edit().putInt("theme", savedTheme).putBoolean("immersive", savedImmersive).commit()
            if (::activities.isInitialized) removeMonitor(activities)
            uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            finish(result, report)
        }
    }

    private fun awaitBars(hidden: Boolean) = await("system bars hidden=$hidden") {
        var matches = false
        runOnMainSync {
            val window = activities.lastActivity?.window
            val insets = window?.decorView?.rootWindowInsets
            if (insets != null) {
                val status = insets.isVisible(WindowInsets.Type.statusBars())
                val navigation = insets.isVisible(WindowInsets.Type.navigationBars())
                val keepOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                lastBars = "status=$status navigation=$navigation keepOn=$keepOn chooserNavigation=$chooserNavigationVisible"
                // Hardware-navigation devices do not expose a navigation-bar inset source.
                matches = if (hidden) !status && !navigation && keepOn
                    else status && navigation == chooserNavigationVisible && !keepOn
            }
        }
        matches
    }

    private fun awaitImmersive(theme: CalendarTheme, expectedDate: LocalDate = LocalDate.now()) {
        await("immersive ${theme.name} $expectedDate") {
            nodes().any { it.viewIdResourceName == "immersive-${theme.name}" && it.contentDescription?.contains(expectedDate.toString()) == true }
        }
        awaitBars(hidden = true)
        await("chooser removed after full-screen transition") {
            nodes().none { it.viewIdResourceName == "theme-title" || it.viewIdResourceName == "theme-pager" }
        }
        // Insets visibility changes before the system bar's exit animation has finished.
        SystemClock.sleep(650)
        val tree = nodes()
        check(tree.none { it.viewIdResourceName == "theme-title" || it.viewIdResourceName == "theme-pager" || it.text?.toString() == "CALENDARMODS" })
        val page = tree.first { it.viewIdResourceName == "immersive-${theme.name}" }
        val bounds = Rect().also { page.getBoundsInScreen(it) }
        var screen = Rect()
        runOnMainSync { screen = activities.lastActivity.windowManager.currentWindowMetrics.bounds }
        check(bounds == screen) { "Paper does not fill the screen: $bounds vs $screen" }
        tree.filter { it.viewIdResourceName == "immersive-day" }.forEach { day ->
            val dayBounds = Rect().also { day.getBoundsInScreen(it) }
            check(day.text?.toString() == expectedDate.dayOfMonth.toString().padStart(2, '0'))
            check(bounds.contains(dayBounds)) { "Full-screen date clipped: $dayBounds" }
        }
    }

    private fun press(tag: String, long: Boolean = false) {
        val node = nodes().first { it.viewIdResourceName == tag }
        val area = Rect().also { node.getBoundsInScreen(it) }
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, area.exactCenterX(), area.exactCenterY(), 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN)
        SystemClock.sleep(if (long) ViewConfiguration.getLongPressTimeout().toLong() + 250 else 70)
        inject(MotionEvent.ACTION_UP)
    }

    private fun verifyImmersiveLifecycle(checks: MutableList<String>) {
        val theme = CalendarTheme.HANGING
        press("art-${theme.name}")
        awaitImmersive(theme)
        for (rotation in listOf(1, 3, 0)) {
            uiAutomation.setRotation(rotation)
            await("immersive rotation $rotation") { activities.lastActivity.display?.rotation == rotation }
            awaitImmersive(theme)
        }
        checks += "immersive mode and theme survive both landscape directions and portrait recreation PASS"
        press("immersive-${theme.name}")
        awaitImmersive(theme)
        swipe(left = true, duration = 800, tag = "immersive-${theme.name}")
        awaitImmersive(theme)
        checks += "single tap and ordinary swipe do not exit immersive mode or change theme PASS"
        backgroundToHome()
        // Resume as an external user launch; the app itself must not bypass background-start restrictions.
        shell("am start -W -n com.calendarmods/.MainActivity -f 0x30000000")
        awaitImmersive(theme)
        checks += "immersive resume reapplies hidden bars and current date PASS"
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        awaitTheme(theme)
        awaitBars(hidden = false)
        checks += "system Back returns to chooser and restores bars and screen timeout PASS"
    }

    private fun awaitTheme(theme: CalendarTheme, expectedDate: LocalDate = LocalDate.now()) {
        await("theme ${theme.name}") {
            val tree = nodes()
            tree.any { it.viewIdResourceName == "theme-title" && it.text?.toString() == theme.title } &&
                tree.any { it.viewIdResourceName == "art-${theme.name}" && it.contentDescription?.contains(expectedDate.toString()) == true } &&
                tree.none { it.viewIdResourceName?.startsWith("immersive-") == true }
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

    private fun verifyInterruptedEntry(checks: MutableList<String>, rotation: Int) {
        fun paperBounds() = Rect().also { bounds ->
            nodes().first { it.viewIdResourceName == "art-GOLDEN" }.getBoundsInScreen(bounds)
        }
        val initialBounds = paperBounds()
        press("art-GOLDEN")
        await("entry gesture starts immersive mode") {
            targetContext.getSharedPreferences("calendar", 0).getBoolean("immersive", false)
        }
        SystemClock.sleep(80)
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        awaitTheme(CalendarTheme.GOLDEN)
        awaitBars(hidden = false)
        check(paperBounds() == initialBounds) { "Interrupted entry left the preview at a different size" }
        checks += "rotation=$rotation, Back during entry returns to the same preview bounds PASS"
    }

    private fun verifyLiveDates(checks: MutableList<String>, savedZone: String) {
        val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val clock = AdvancingClock()
        clock.set(ZonedDateTime.of(2026, 12, 31, 23, 59, 52, 0, ZoneId.systemDefault()).toInstant())
        runOnMainSync { activity.setContent { MaterialTheme { CalendarApp(clock) } } }
        awaitTheme(CalendarTheme.GOLDEN, LocalDate.of(2026, 12, 31))
        press("art-GOLDEN")
        awaitImmersive(CalendarTheme.GOLDEN, LocalDate.of(2026, 12, 31))
        awaitImmersive(CalendarTheme.GOLDEN, LocalDate.of(2027, 1, 1))
        check(nodes().any { it.text?.toString() == "JANUARY" })
        check(nodes().any { it.text?.toString() == "2027" })
        press("immersive-GOLDEN", long = true)
        awaitTheme(CalendarTheme.GOLDEN, LocalDate.of(2027, 1, 1))
        checks += "immersive foreground midnight updates day, month and year without reopening PASS"
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

        backgroundToHome()
        await("app backgrounded") { !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        clock.set(ZonedDateTime.of(2028, 2, 29, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant())
        shell("am start -W -n com.calendarmods/.MainActivity -f 0x30000000")
        awaitTheme(CalendarTheme.HANGING, LocalDate.of(2028, 2, 29))
        checks += "resume after background refreshes to leap day with complete calendar data PASS"
        runOnMainSync { activity.setContent { MaterialTheme { CalendarApp() } } }
        awaitTheme(CalendarTheme.HANGING)
        checks += "restored production clock shows actual device date PASS"
    }

    private fun backgroundToHome() {
        check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
        // A paused app does not mean the launcher's transition has finished yet.
        await("home window visible") {
            val root = uiAutomation.rootInActiveWindow
            root != null && root.packageName != targetContext.packageName &&
                root.packageName != "com.android.systemui" && root.childCount > 0
        }
        SystemClock.sleep(1_000)
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
        // AnimatedContent removes and recreates subtrees. Read fresh nodes rather than
        // cached outgoing labels or bounds from an earlier animation frame.
        if (Build.VERSION.SDK_INT >= 34) uiAutomation.clearCache()
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (Build.VERSION.SDK_INT < 34 && !node.refresh()) return
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
        error("Timed out: $label; bars=$lastBars; UI=${nodes().mapNotNull { it.text?.toString() }}")
    }

    private fun swipe(left: Boolean, fraction: Float = .62f, duration: Long = 400, tag: String = "theme-pager") {
        val node = nodes().first { it.viewIdResourceName == tag }
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
