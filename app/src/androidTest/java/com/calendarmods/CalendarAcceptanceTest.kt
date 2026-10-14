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
import androidx.compose.runtime.*
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
    private var refinementsOnly = false
    private var contractOnly = false
    private var storeOnly = false
    private var storeMotionOnly = false
    private val dayMotion = AtomicReference<Pair<LocalDate, Boolean>>()
    private var performanceOnly = false
    private var interruptionsOnly = false
    private var midnightOnly = false
    private var denseOnly = false
    private var centeredRotation: Int? = null
    private var anchoredRotation: Int? = null
    override fun onCreate(arguments: Bundle?) {
        refinementsOnly = arguments?.getString("refinements") == "true"
        performanceOnly = arguments?.getString("performance") == "true"
        storeOnly = arguments?.getString("store") == "true"
        storeMotionOnly = arguments?.getString("storeMotion") == "true"
        interruptionsOnly = arguments?.getString("interruptions") == "true"
        midnightOnly = arguments?.getString("midnight") == "true"
        contractOnly = arguments?.getString("contract") == "true"
        denseOnly = arguments?.getString("dense") == "true"
        centeredRotation = arguments?.getString("centered")?.toIntOrNull()
        anchoredRotation = arguments?.getString("anchored")?.toIntOrNull()
        super.onCreate(arguments); start()
    }

    override fun onStart() {
        val report = Bundle()
        var result = Activity.RESULT_OK
        val checks = mutableListOf<String>()
        val preferences = targetContext.getSharedPreferences("calendar", 0)
        val savedTheme = preferences.getInt("theme", 0)
        val savedThemeId = preferences.getString("theme_id", null)
        val savedImmersive = preferences.getBoolean("immersive", false)
        val savedZone = ZoneId.systemDefault().id
        try {
            activities = addMonitor(MainActivity::class.java.name, null, false)
            preferences.edit().remove("theme_id").putInt("theme", 0).putBoolean("immersive", false).commit()
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            awaitTheme(CalendarTheme.GOLDEN)
            runOnMainSync {
                chooserNavigationVisible = activities.lastActivity.window.decorView.rootWindowInsets
                    .isVisible(WindowInsets.Type.navigationBars())
            }
            if (storeMotionOnly) {
                verifyStoreMotion(checks)
            } else if (performanceOnly) {
                verifyStorePerformance(checks)
            } else if (storeOnly) {
                verifyStore(checks)
            } else if (interruptionsOnly) {
                verifyMotionInterruptions(checks)
            } else if (midnightOnly) {
                verifyMidnightMotions(checks)
                verifyMotionInterruptions(checks)
            } else if (contractOnly) {
                verifyThemeContract(checks)
            } else if (anchoredRotation != null) {
                verifyAnchoredCarousel(checks, anchoredRotation!!)
            } else if (centeredRotation != null) {
                verifyCenteredCarousel(checks, centeredRotation!!)
            } else if (denseOnly) {
                verifyDenseContent(checks)
            } else if (refinementsOnly) {
                verifyRefinements(checks)
            } else {
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
            }
            report.putString("stream", "\nPASS ${checks.size} checks\n" + checks.joinToString("\n"))
        } catch (failure: Throwable) {
            result = Activity.RESULT_CANCELED
            report.putString("stream", "\nFAIL after ${checks.size} checks\n" + failure.stackTraceToString())
        } finally {
            shell("cmd alarm set-timezone $savedZone")
            preferences.edit().putString("theme_id", savedThemeId).putInt("theme", savedTheme).putBoolean("immersive", savedImmersive).commit()
            if (::activities.isInitialized) removeMonitor(activities)
            uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            finish(result, report)
        }
    }

    private fun verifyStore(checks: MutableList<String>) = kotlinx.coroutines.runBlocking {
        val root = File(targetContext.cacheDir, "library-test").apply { deleteRecursively(); mkdirs() }
        val testContext = object : android.content.ContextWrapper(targetContext) {
            override fun getFilesDir() = root
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("store-test-$name", mode)
        }
        val testIndex = File(root, "themes-v1/index.json")
        val fixture = CalendarTheme.PHOTO.copy(id = "org.example.migration", author = "test")
        testIndex.parentFile!!.mkdirs()
        testIndex.writeText(org.json.JSONArray().put(org.json.JSONObject().put("manifest", org.json.JSONObject(ThemeManifest.encode(fixture)))).toString())
        val isolated = ThemeRepository.reopen(testContext)
        check(isolated.inventory.value.themes.size == 8)
        val teddy = CalendarTheme.TEDDY
        val styles = ThemeStyleStore(testContext)
        val image = File(root, "theme-images/custom.png").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        styles.save(teddy, ThemeStyle(image.absolutePath, ImageFill.FIT))
        isolated.toggleFavorite(teddy.id)
        isolated.uninstall(teddy.id)
        check(!image.exists() && styles.read(teddy) == teddy.defaultStyle())
        val reopened = ThemeRepository.reopen(testContext)
        check(teddy.id !in reopened.inventory.value.library.order && teddy.id in reopened.inventory.value.library.favorites)
        isolated.add(teddy.id)
        check(isolated.inventory.value.themes.last() == teddy)
        isolated.reorder(isolated.inventory.value.library.order.reversed())
        check(ThemeRepository.reopen(testContext).inventory.value.library == isolated.inventory.value.library)
        checks += "v1 migration, builtin deletion, favorite retention, image cleanup, default re-add and durable ordering PASS"
        val before = isolated.inventory.value
        val obstruction = File(testIndex.path + ".new").apply { mkdirs() }
        File(obstruction, "block").writeText("test")
        check(runCatching { isolated.setFavorites(mapOf(CalendarTheme.GOLDEN.id to true, teddy.id to false)) }.isFailure)
        check(before == isolated.inventory.value)
        obstruction.deleteRecursively()
        isolated.inventory.value.library.order.drop(1).forEach { isolated.uninstall(it) }
        check(runCatching { isolated.uninstall(isolated.inventory.value.library.order.first()) }.isFailure)
        check(ThemeRepository.reopen(testContext).inventory.value.themes.size == 1)
        checks += "atomic write failure leaves published state unchanged; repository prevents removing final theme PASS"
        root.deleteRecursively()
        testContext.getSharedPreferences("theme_styles", 0).edit().clear().commit()

        val repository = ThemeRepository.get(targetContext)
        val index = File(targetContext.filesDir, "themes-v1/index.json")
        val original = index.takeIf { it.exists() }?.readBytes()
        val originalOrder = repository.inventory.value.library.order
        try {
            repeat(originalOrder.size) { swipe(left = true) }
            await("shop entry") { nodes().any { it.viewIdResourceName == "store-entry" } }
            capture("store-entry-portrait")
            press("store-entry")
            await("shop opens") { nodes().any { it.viewIdResourceName == "store-tab-0" } }
            SystemClock.sleep(700)
            for (rotation in 0..1) {
                uiAutomation.setRotation(rotation)
                await("store orientation") { targetContext.resources.configuration.orientation == if (rotation == 0) 1 else 2 }
                SystemClock.sleep(800)
                val firstRow = listOf("GOLDEN", "TEDDY", "PHOTO").map { name ->
                    Rect().also { bounds -> nodes().first { it.viewIdResourceName == "store-card-$name" }.getBoundsInScreen(bounds) }
                }
                check(firstRow.map { it.top }.distinct().size == 1)
                check(firstRow.zipWithNext().all { (a, b) -> a.right <= b.left })
                val title = Rect().also { b -> nodes().first { it.viewIdResourceName == "store-title-GOLDEN" }.getBoundsInScreen(b) }
                val button = Rect().also { b -> nodes().first { it.viewIdResourceName == "add-use-GOLDEN" }.getBoundsInScreen(b) }
                check(title.bottom <= button.top && kotlin.math.abs(title.centerX() - button.centerX()) <= 2)
                check(kotlin.math.abs(button.centerX() - firstRow.first().centerX()) <= 2)
                SystemClock.sleep(1500) // Cold orientation-specific thumbnails settle before the visual sample.
                capture("store-recommended-${if (rotation == 0) "portrait" else "landscape"}")
            }
            uiAutomation.setRotation(0); SystemClock.sleep(800)
            val wasFavorite = CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites
            if (!wasFavorite) press("favorite-GOLDEN")
            await("favorite persisted") { CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites }
            press("store-tab-1")
            await("favorites") { nodes().any { it.viewIdResourceName == "store-card-GOLDEN" } }
            capture("store-favorites")
            press("favorite-GOLDEN")
            check(CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites)
            check(nodes().any { it.viewIdResourceName == "store-card-GOLDEN" })
            check(nodes().any { it.contentDescription?.toString() == "收藏${CalendarTheme.GOLDEN.title}" })
            uiAutomation.setRotation(1); SystemClock.sleep(800)
            check(nodes().any { it.contentDescription?.toString() == "收藏${CalendarTheme.GOLDEN.title}" })
            check(CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites)
            press("favorite-GOLDEN") // undo pending removal after rotation
            uiAutomation.setRotation(0); SystemClock.sleep(800)
            check(nodes().any { it.contentDescription?.toString() == "取消收藏${CalendarTheme.GOLDEN.title}" })
            press("favorite-GOLDEN")
            val failedWrite = File(index.path + ".new").apply { mkdirs() }
            try {
                File(failedWrite, "block").writeText("failure fixture")
                press("store-tab-0")
                await("failed favorite save restores solid heart") {
                    nodes().any { it.contentDescription?.toString() == "取消收藏${CalendarTheme.GOLDEN.title}" }
                }
                check(CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites)
                check(nodes().any { it.viewIdResourceName == "store-grid-1" })
            } finally { failedWrite.deleteRecursively() }
            press("favorite-GOLDEN")
            backgroundToHome()
            shell("am start -W -n com.calendarmods/.MainActivity -f 0x30000000")
            await("favorites restored from background") { nodes().any { it.viewIdResourceName == "store-grid-1" } }
            check(CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites)
            press("store-tab-0")
            await("unfavorite batch committed on leave") { CalendarTheme.GOLDEN.id !in repository.inventory.value.library.favorites }
            press("favorite-GOLDEN")
            await("refavorite persisted") { CalendarTheme.GOLDEN.id in repository.inventory.value.library.favorites }
            checks += "deferred unfavorite remains visible, survives rotation, undo and atomic commit on tab leave PASS"
            press("store-tab-2"); SystemClock.sleep(400)
            press("store-edit"); SystemClock.sleep(400)
            val a = Rect().also { bounds -> nodes().first { it.viewIdResourceName == "drag-GOLDEN" }.getBoundsInScreen(bounds) }
            val b = Rect().also { bounds -> nodes().first { it.viewIdResourceName == "drag-PHOTO" }.getBoundsInScreen(bounds) }
            dragPoints(a.exactCenterX(), a.exactCenterY(), b.exactCenterX(), b.exactCenterY(), captureAtEnd = "store-drag-shadow")
            await("drag order saved") { repository.inventory.value.library.order != originalOrder }
            check(ThemeRepository.reopen(targetContext).inventory.value.library.order == repository.inventory.value.library.order)
            capture("store-installed-edit")
            press("delete-GOLDEN")
            await("delete confirmation") { nodes().any { it.viewIdResourceName == "confirm-delete" } }
            press("confirm-delete")
            await("golden removed") { CalendarTheme.GOLDEN.id !in repository.inventory.value.library.order }
            press("store-tab-1"); SystemClock.sleep(400)
            check(nodes().any { it.viewIdResourceName == "store-card-GOLDEN" })
            press("add-use-GOLDEN")
            await("readd") { repository.inventory.value.library.order.last() == CalendarTheme.GOLDEN.id &&
                nodes().any { it.viewIdResourceName == "add-use-GOLDEN" && it.isClickable } }
            check(nodes().any { it.viewIdResourceName == "theme-store" })
            checks += "three-column portrait/landscape grids, favorite tab, real drag reorder, delete confirmation and re-add in place PASS"
            press("add-use-GOLDEN")
            await("installed button confirms deletion") { nodes().any { it.viewIdResourceName == "confirm-delete" } }
            check(nodes().any { it.text?.toString() == "将从已安装主题中移除“${CalendarTheme.GOLDEN.title}”" })
            uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            press("store-tab-2")
            // Re-added theme is last; scroll the grid until its use action is visible.
            repeat(3) {
                if (nodes().none { it.viewIdResourceName == "add-use-GOLDEN" }) {
                    val bounds = Rect().also { b -> nodes().first { it.viewIdResourceName == "store-grid-2" }.getBoundsInScreen(b) }
                    dragPoints(bounds.exactCenterX(), bounds.bottom - 60f, bounds.exactCenterX(), bounds.top + 60f)
                }
            }
            await("use action ready") { nodes().any { it.text?.toString() == "使用" } }
            press("add-use-GOLDEN")
            SystemClock.sleep(100)
            capture("store-use-middle")
            awaitTheme(CalendarTheme.GOLDEN)
            val art = Rect().also { bounds -> nodes().first { it.viewIdResourceName == "art-GOLDEN" }.getBoundsInScreen(bounds) }
            val style = Rect().also { bounds -> nodes().first { it.viewIdResourceName == "style-GOLDEN" }.getBoundsInScreen(bounds) }
            check(style.top >= art.bottom)
            check(kotlin.math.abs(style.right - art.right) <= 2) { "style right=${style.right}, art right=${art.right}" }
            dragPoints(art.exactCenterX(), art.exactCenterY(), art.exactCenterX(), art.exactCenterY() - 25)
            check(nodes().none { it.viewIdResourceName == "confirm-delete" })
            dragPoints(art.exactCenterX(), art.exactCenterY(), art.exactCenterX(), art.exactCenterY() - art.height() * .4f, captureAtEnd = "chooser-delete-threshold")
            await("swipe deletion dialog") { nodes().any { it.viewIdResourceName == "confirm-delete" } }
            uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            awaitTheme(CalendarTheme.GOLDEN)
            checks += "use returns to selected preview with transition; style alignment and swipe deletion PASS"
            repeat(repository.inventory.value.themes.size) { swipe(left = true) }
            press("store-entry")
            await("fresh store entry") { nodes().any { it.viewIdResourceName == "store-tab-0" && it.isSelected } }
            SystemClock.sleep(500)
            check(nodes().any { it.viewIdResourceName == "store-grid-0" })
            checks += "reopening the store always selects recommended PASS"
        } finally {
            if (original == null) index.delete() else index.writeBytes(original)
        }
    }

    private fun verifyChooserMotion(checks: MutableList<String>) {
        press("style-GOLDEN")
        await("style sheet") { nodes().any { it.viewIdResourceName == "style-done" } }
        SystemClock.sleep(900)
        capture("style-done-before")
        press("style-done")
        SystemClock.sleep(100)
        check(nodes().any { it.viewIdResourceName == "theme-style-sheet" }) { "Done removed the sheet before its exit animation" }
        capture("style-done-middle")
        await("style dismissed") { nodes().none { it.viewIdResourceName == "theme-style-sheet" } }
        checks += "style Done retains and animates the sheet before removing it PASS"
        val art = Rect().also { b -> nodes().first { it.viewIdResourceName == "art-GOLDEN" }.getBoundsInScreen(b) }
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        val x = art.exactCenterX(); val y = art.exactCenterY()
        inject(MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..20) {
            inject(MotionEvent.ACTION_MOVE, x, y - art.height() * .35f * step / 20)
            SystemClock.sleep(16)
        }
        for (step in 1..20) {
            inject(MotionEvent.ACTION_MOVE, x - art.width() * .4f * step / 20, y - art.height() * .35f)
            SystemClock.sleep(16)
        }
        capture("chooser-vertical-lock")
        check(nodes().any { it.viewIdResourceName == "art-GOLDEN" })
        inject(MotionEvent.ACTION_UP, x - art.width() * .4f, y - art.height() * .35f)
        await("locked swipe delete") { nodes().any { it.viewIdResourceName == "confirm-delete" } }
        check(nodes().any { it.text?.toString() == "将从已安装主题中移除“${CalendarTheme.GOLDEN.title}”" })
        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        awaitTheme(CalendarTheme.GOLDEN)
        swipe(left = true); awaitTheme(CalendarTheme.TEDDY)
        swipe(left = false); awaitTheme(CalendarTheme.GOLDEN)
        checks += "vertical deletion stays locked through sideways drift, then releases horizontal paging PASS"
    }

    private fun assertPhotoPreviewsVisible() {
        val bitmap = checkNotNull(uiAutomation.takeScreenshot())
        try {
            for (name in listOf("PHOTO", "TEDDY")) {
                val bounds = Rect().also { b -> nodes().first { it.viewIdResourceName == "store-card-$name" }.getBoundsInScreen(b) }
                val colors = mutableSetOf<Int>()
                for (x in 3..12) for (y in 4..9) {
                    val px = (bounds.left + bounds.width() * x / 16).coerceIn(0, bitmap.width - 1)
                    val py = (bounds.top + bounds.height() * y / 20).coerceIn(0, bitmap.height - 1)
                    colors += bitmap.getPixel(px, py)
                }
                check(colors.size > 25) { "$name preview disappeared during reorder (${colors.size} colors)" }
            }
        } finally { bitmap.recycle() }
    }

    private fun verifyStoreMotion(checks: MutableList<String>) {
        val activity = activities.lastActivity as MainActivity
        val gate = AtomicReference(kotlinx.coroutines.CompletableDeferred<Unit>())
        val progress = AtomicReference<(Float?) -> Unit>()
        val motionFrames = java.util.Collections.synchronizedList(mutableListOf<Triple<Float, Int, Int>>())
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val error = AtomicReference<String>()
        val failure = java.util.concurrent.atomic.AtomicBoolean()
        val reset = AtomicReference<() -> Unit>()
        val originalScale = shell("settings get global animator_duration_scale").trim()
        try {
            shell("settings put global animator_duration_scale 2")
            verifyChooserMotion(checks)
            runOnMainSync {
                activity.setContent {
                    var fixture by remember { mutableStateOf(ThemeInventory(library = ThemeLibrary(
                        favorites = CalendarTheme.entries.map { it.id }.toSet()).remove(CalendarTheme.GOLDEN.id))) }
                    reset.set { fixture = fixture.copy(library = fixture.library.remove(CalendarTheme.GOLDEN.id)) }
                    MaterialTheme {
                        CompositionLocalProvider(LocalStoreTabMotionObserver provides { alpha, displayed, target ->
                            motionFrames.add(Triple(alpha, displayed, target))
                        }) {
                        ThemeStore(fixture, CalendarData.create(LocalDate.of(2026, 10, 14)), false,
                            androidx.compose.ui.Modifier, onBack = {}, onAdd = { id, notify ->
                                calls.incrementAndGet(); progress.set(notify)
                                gate.get().await()
                                if (failure.get()) throw java.io.IOException("fixture install failure")
                                fixture = fixture.copy(library = fixture.library.add(id, 1L))
                            }, onFavorite = {}, onSaveFavorites = {}, onError = { error.set(it) },
                            onDelete = {}, onReorder = { fixture = fixture.copy(library = fixture.library.reorder(it)) }, onUse = {})
                        }
                    }
                }
            }
            await("motion fixture") { nodes().any { it.viewIdResourceName == "store-grid-0" } }
            SystemClock.sleep(2500)
            press("store-tab-1")
            SystemClock.sleep(160)
            capture("store-tab-transition-middle")
            SystemClock.sleep(850)
            val sampled = synchronized(motionFrames) { motionFrames.toList() }
            check(sampled.any { it.second == 0 && it.third == 1 && it.first in .05f.. .7f }) { "Missing outgoing fade frames" }
            check(sampled.any { it.second == 1 && it.third == 1 && it.first in .05f.. .9f }) { "Missing incoming fade frames" }
            press("store-tab-2"); press("store-tab-0")
            SystemClock.sleep(900)
            check(nodes().count { it.viewIdResourceName?.startsWith("store-grid-") == true } == 1)
            checks += "tab fade-through retains outgoing content during exit; rapid reversal settles to one page PASS"
            press("add-use-GOLDEN")
            await("install starts") { calls.get() == 1 && nodes().any { it.stateDescription?.toString() == "正在安装" } }
            capture("store-install-indeterminate")
            check(nodes().filter { it.viewIdResourceName?.startsWith("store-tab-") == true }.all { !it.isEnabled })
            runOnMainSync { progress.get().invoke(.42f) }
            await("real progress") { nodes().any { it.stateDescription?.toString() == "42%" } }
            capture("store-install-progress")
            gate.get().complete(Unit)
            await("installed result") { nodes().any { it.viewIdResourceName == "add-use-GOLDEN" && it.isClickable } }
            check(calls.get() == 1)
            checks += "application shows circular indeterminate and determinate progress, prevents duplicate actions and settles installed PASS"
            runOnMainSync { reset.get().invoke() }
            gate.set(kotlinx.coroutines.CompletableDeferred())
            failure.set(true)
            press("add-use-GOLDEN")
            await("second install starts") { calls.get() == 2 }
            gate.get().complete(Unit)
            await("failure restores action") { error.get() != null && nodes().any { it.viewIdResourceName == "add-use-GOLDEN" && it.isClickable } }
            check(nodes().any { it.text?.toString() == "应用" })
            checks += "failed acquisition leaves theme uninstalled and restores retry action PASS"
        } finally { shell("settings put global animator_duration_scale $originalScale") }
    }

    private fun verifyStorePerformance(checks: MutableList<String>) {
        val activity = activities.lastActivity as MainActivity
        val frames = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val sampling = java.util.concurrent.atomic.AtomicBoolean(true)
        val peakPss = java.util.concurrent.atomic.AtomicInteger(0)
        val sampler = kotlin.concurrent.thread(name = "store-memory-sampler") {
            while (sampling.get()) {
                val memory = android.os.Debug.MemoryInfo().also(android.os.Debug::getMemoryInfo).totalPss
                peakPss.updateAndGet { maxOf(it, memory) }
                SystemClock.sleep(250)
            }
        }
        val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames.add(metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION))
        }
        runOnMainSync { activity.window.addOnFrameMetricsAvailableListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) }
        try {
            for (count in listOf(7, 70, 200)) {
                val extras = (7 until count).map { n -> InstalledTheme(CalendarTheme.entries[n % 7].copy(id = "org.benchmark.theme$n", title = "主题 $n")) }
                val all = CalendarTheme.entries + extras.map { it.theme }
                val fixture = ThemeInventory(installed = extras, library = ThemeLibrary(order = all.map { it.id },
                    addedAt = all.associate { it.id to 0L }, favorites = all.map { it.id }.toSet()))
                ThemeThumbnails.clear()
                peakPss.set(0)
                frames.clear()
                val start = SystemClock.elapsedRealtime()
                runOnMainSync {
                    activity.setContent {
                        MaterialTheme {
                            androidx.compose.runtime.key(count) {
                                ThemeStore(fixture, CalendarData.create(LocalDate.of(2026, 10, 14)), false,
                                    androidx.compose.ui.Modifier, {}, { _, _ -> }, {}, {}, {}, {}, {}, {})
                            }
                        }
                    }
                }
                await("benchmark grid $count") { nodes().any { it.viewIdResourceName == "store-grid-0" } }
                SystemClock.sleep(1800)
                fun summary(label: String): String {
                    val samples = synchronized(frames) { frames.toList().sorted() }
                    val info = android.os.Debug.MemoryInfo().also(android.os.Debug::getMemoryInfo)
                    fun percentile(p: Double) = if (samples.isEmpty()) 0 else samples[((samples.size - 1) * p).toInt()] / 1_000_000
                    return "$count themes $label: frames=${samples.size}, p50=${percentile(.5)}ms p95=${percentile(.95)}ms max=${percentile(1.0)}ms over100ms=${samples.count { it > 100_000_000 }}, pss=${info.totalPss / 1024}MiB peakPss=${peakPss.get() / 1024}MiB cache=${ThemeThumbnails.bytes() / 1024}KiB"
                }
                checks += summary("cold (${SystemClock.elapsedRealtime() - start}ms including 1800ms settle)")
                press("store-tab-1"); SystemClock.sleep(1000)
                press("store-tab-2"); SystemClock.sleep(1000)
                frames.clear()
                repeat(3) {
                    press("store-tab-0"); press("store-tab-1"); press("store-tab-2")
                }
                checks += summary("warm tab switches")
                press("store-edit")
                val first = Rect().also { b -> nodes().first { it.viewIdResourceName == "drag-GOLDEN" }.getBoundsInScreen(b) }
                val second = Rect().also { b -> nodes().first { it.viewIdResourceName == "drag-TEDDY" }.getBoundsInScreen(b) }
                frames.clear()
                dragPoints(first.exactCenterX(), first.exactCenterY(), second.exactCenterX(), second.exactCenterY())
                checks += summary("drag")
                press("store-edit")
                frames.clear()
                val bounds = Rect().also { b -> nodes().first { it.viewIdResourceName == "store-grid-2" }.getBoundsInScreen(b) }
                repeat(3) { dragPoints(bounds.exactCenterX(), bounds.bottom - 70f, bounds.exactCenterX(), bounds.top + 70f, 450) }
                check(ThemeThumbnails.bytes() <= ThemeThumbnails.MAX_BYTES)
                checks += summary("scroll")
                capture("store-performance-$count")
            }
        } finally {
            sampling.set(false); sampler.join(1000)
            runOnMainSync { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        }
    }

    private fun dragPoints(x: Float, y: Float, endX: Float, endY: Float, duration: Long = 700, captureAtEnd: String? = null) {
        val down = SystemClock.uptimeMillis()
        fun inject(action: Int, px: Float, py: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(uiAutomation.injectInputEvent(event, true)); event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..30) {
            SystemClock.sleep(duration / 30)
            inject(MotionEvent.ACTION_MOVE, x + (endX - x) * step / 30, y + (endY - y) * step / 30)
        }
        captureAtEnd?.let(::capture)
        if (captureAtEnd == "store-drag-shadow") {
            assertPhotoPreviewsVisible()
        }
        inject(MotionEvent.ACTION_UP, endX, endY)
        SystemClock.sleep(700)
    }

    private fun verifyMidnightMotions(checks: MutableList<String>) {
        val preferences = targetContext.getSharedPreferences("calendar", 0)
        for (rotation in 0..1) {
            uiAutomation.setRotation(rotation)
            SystemClock.sleep(700)
            CalendarTheme.entries.forEach { theme ->
                preferences.edit().putString("theme_id", theme.id).putBoolean("immersive", true).commit()
                val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
                val clock = AdvancingClock().apply {
                    set(ZonedDateTime.of(2026, 12, 31, 23, 59, 50, 0, ZoneId.systemDefault()).toInstant())
                }
                runOnMainSync { activity.setContent { MaterialTheme {
                    androidx.compose.runtime.CompositionLocalProvider(LocalDayMotionObserver provides { date, moving -> dayMotion.set(date to moving) }) {
                        CalendarApp(clock)
                    }
                } } }
                awaitImmersive(theme, LocalDate.of(2026, 12, 31))
                await("midnight animation ${theme.name}") { dayMotion.get() == (LocalDate.of(2027, 1, 1) to true) }
                SystemClock.sleep(120)
                capture("midnight-$rotation-${theme.name.lowercase()}-middle")
                await("midnight settled") { dayMotion.get() == (LocalDate.of(2027, 1, 1) to false) }
                awaitImmersive(theme, LocalDate.of(2027, 1, 1))
                capture("midnight-$rotation-${theme.name.lowercase()}-after")
                val result = "${theme.name}: rotation=$rotation natural year boundary animates and settles to complete new date PASS"
                checks += result
                sendStatus(0, Bundle().apply { putString("stream", result + "\n") })
            }
        }
    }

    private fun verifyMotionInterruptions(checks: MutableList<String>) {
        uiAutomation.setRotation(0); SystemClock.sleep(700)
        val prefs = targetContext.getSharedPreferences("calendar", 0)
        fun start(): Pair<MainActivity, AdvancingClock> {
            prefs.edit().putString("theme_id", CalendarTheme.GOLDEN.id).putBoolean("immersive", true).commit()
            val activity = startActivitySync(Intent(targetContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            val clock = AdvancingClock().apply {
                set(ZonedDateTime.of(2028, 2, 28, 23, 59, 50, 0, ZoneId.systemDefault()).toInstant())
            }
            runOnMainSync { activity.setContent { MaterialTheme {
                    androidx.compose.runtime.CompositionLocalProvider(LocalDayMotionObserver provides { date, moving -> dayMotion.set(date to moving) }) {
                        CalendarApp(clock)
                    }
                } } }
            awaitImmersive(CalendarTheme.GOLDEN, LocalDate.of(2028, 2, 28))
            return activity to clock
        }
        fun moving() = dayMotion.get()?.second == true

        start()
        await("leap-day motion starts") { moving() }
        uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        awaitTheme(CalendarTheme.GOLDEN, LocalDate.of(2028, 2, 29))
        checks += "Back during midnight converges to leap-day chooser without stale paper PASS"
        start()
        await("rotation motion starts") { moving() }
        uiAutomation.setRotation(1)
        await("rotation settled") { targetContext.resources.configuration.orientation == 2 }
        awaitImmersive(CalendarTheme.GOLDEN)
        // Recreation intentionally replaces the injected content/observer with production content.
        check(nodes().any { it.viewIdResourceName == "midnight-paper" && it.stateDescription == "静止：${LocalDate.now()}" })
        checks += "rotation during midnight discards outgoing page and recreates latest production date PASS"
        uiAutomation.setRotation(0); SystemClock.sleep(700)
        val (_, clock) = start()
        backgroundToHome()
        clock.set(ZonedDateTime.of(2028, 2, 29, 12, 0, 0, 0, ZoneId.systemDefault()).toInstant())
        shell("am start -W -n com.calendarmods/.MainActivity -f 0x30000000")
        awaitImmersive(CalendarTheme.GOLDEN, LocalDate.of(2028, 2, 29))
        check(!moving())
        checks += "background across midnight resumes at current date without replay PASS"
        val scale = shell("settings get global animator_duration_scale").trim()
        try {
            shell("settings put global animator_duration_scale 0")
            start()
            awaitImmersive(CalendarTheme.GOLDEN, LocalDate.of(2028, 2, 29))
            check(!moving())
            checks += "disabled system animation displays latest date without retained transition PASS"
        } finally { shell("settings put global animator_duration_scale $scale") }
    }

    private fun verifyThemeContract(checks: MutableList<String>) = kotlinx.coroutines.runBlocking {
        val repository = ThemeRepository.get(targetContext)
        val catalogFile = File(targetContext.filesDir, "themes-v1/index.json")
        val originalCatalog = catalogFile.readBytes()
        val store = ThemeStyleStore(targetContext)
        val fixture = CalendarTheme.TEDDY.copy(id = "org.example.contract-fixture", title = "契约测试主题",
            author = "Contract test", defaultFill = "fit", hasImageAsset = true)
        val neighbor = fixture.copy(id = "org.example.contract-neighbor", title = "契约相邻主题", hasImageAsset = false)
        check(repository.inventory.value.themes.none { it.id == fixture.id || it.id == neighbor.id })
        val preferences = targetContext.getSharedPreferences("theme_styles", 0)
        val originalStyles = preferences.all.toMap()
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF609DBB.toInt()) }
        val image = java.io.ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
        bitmap.recycle()
        try {
            // Test the real Android JSON decoder, not android.jar's JVM stubs.
            CalendarTheme.entries.forEach { check(ThemeManifest.decode(ThemeManifest.encode(it)) == it) }
            val invalid = listOf(
                ThemeManifest.encode(fixture).replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                ThemeManifest.encode(fixture).replace("illustration.v1", "unknown.v1"),
                ThemeManifest.encode(fixture).replace("\"version\":1", "\"version\":1.5"),
                ThemeManifest.encode(fixture).replace("\"fill\":\"fit\"", "\"fill\":\"invalid\""),
                ThemeManifest.encode(fixture).dropLast(1) + ",\"script\":\"bad\"}",
            )
            invalid.forEach { check(runCatching { repository.install(it, image) }.isFailure) }
            check(runCatching { repository.install(ThemeManifest.encode(fixture)) }.isFailure)
            check(runCatching { repository.install(ThemeManifest.encode(fixture), byteArrayOf(1, 2, 3)) }.isFailure)
            checks += "manifest round trips; unknown schema/renderer/property, wrong types, missing/invalid asset rejected PASS"

            preferences.edit().remove("${CalendarTheme.TEDDY.id}.migrated").remove("${CalendarTheme.TEDDY.id}.fill")
                .putString("TEDDY.fill", "STRETCH").commit()
            check(store.read(CalendarTheme.TEDDY).fill == ImageFill.STRETCH)
            store.reset(CalendarTheme.TEDDY)
            check(store.read(CalendarTheme.TEDDY).fill == ImageFill.CROP)
            check(targetContext.getSharedPreferences("calendar", 0).getString("theme_id", null) == CalendarTheme.GOLDEN.id)
            checks += "legacy ordinal selection and image settings migrate once; reset cannot resurrect old values PASS"

            repository.install(ThemeManifest.encode(neighbor))
            repository.install(ThemeManifest.encode(fixture), image)
            awaitTheme(CalendarTheme.GOLDEN)
            check(repository.imagePath(fixture)?.let { File(it).isFile } == true)
            repeat(repository.inventory.value.themes.lastIndex) { swipe(left = true) }
            awaitTheme(fixture)
            check(nodes().any { it.contentDescription?.toString() == "自选日历图片" })
            val total = repository.inventory.value.themes.size.toString().padStart(2, '0')
            check(nodes().any { it.text?.toString()?.endsWith(" / $total") == true })
            capture("contract-installed-theme")
            press("style-${fixture.name}")
            await("external theme settings") { nodes().any { it.viewIdResourceName == "fill-FIT" } }
            // Semantics exist before the sheet reaches its final touch coordinates.
            var previousBounds: Rect? = null
            var stableSince = SystemClock.uptimeMillis()
            await("external settings sheet settled") {
                val node = nodes().firstOrNull { it.viewIdResourceName == "fill-STRETCH" }
                val bounds = node?.let { Rect().also(it::getBoundsInScreen) }
                if (bounds != previousBounds) {
                    previousBounds = bounds
                    stableSince = SystemClock.uptimeMillis()
                }
                bounds != null && !bounds.isEmpty && SystemClock.uptimeMillis() - stableSince >= 500
            }
            check(store.read(fixture).fill == ImageFill.FIT)
            press("fill-STRETCH")
            await("external override") { store.read(fixture).fill == ImageFill.STRETCH }
            check(store.read(CalendarTheme.TEDDY).fill == ImageFill.CROP)
            check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            awaitTheme(fixture)
            press("art-${fixture.name}")
            awaitImmersive(fixture)
            press("immersive-${fixture.name}", long = true)
            awaitTheme(fixture)
            checks += "installed asset appears live in chooser; dynamic total, settings isolation and fullscreen interaction PASS"

            repository.uninstall(neighbor.id)
            awaitTheme(fixture)
            val updated = fixture.copy(version = 2, title = "契约测试主题 · 更新", defaultFill = "crop")
            repository.install(ThemeManifest.encode(updated), image)
            awaitTheme(updated)
            check(store.read(updated).fill == ImageFill.STRETCH)
            check(runCatching { repository.install(ThemeManifest.encode(fixture), image) }.isFailure)
            val reloaded = ThemeRepository.reopen(targetContext)
            check(reloaded.inventory.value.loadErrors.isEmpty())
            check(reloaded.inventory.value.themes.last() == updated)
            runOnMainSync { activities.lastActivity.recreate() }
            awaitTheme(updated)
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            await("contract landscape") { targetContext.resources.configuration.orientation == 2 }
            awaitTheme(updated)
            capture("contract-installed-landscape")
            repository.uninstall(updated.id)
            awaitTheme(CalendarTheme.HANGING)
            check(ThemeRepository.reopen(targetContext).inventory.value.themes.none { it.id == fixture.id })
            checks += "neighbor removal preserves selected ID; version update retains overrides; durable reload, rotation and uninstall fallback PASS"
        } finally {
            repository.uninstall(fixture.id)
            repository.uninstall(neighbor.id)
            catalogFile.writeBytes(originalCatalog)
            val restore = preferences.edit().clear()
            originalStyles.forEach { (key, value) -> when (value) {
                is String -> restore.putString(key, value)
                is Boolean -> restore.putBoolean(key, value)
            } }
            check(restore.commit())
        }
    }

    private fun verifyCenteredCarousel(checks: MutableList<String>, rotation: Int) {
        uiAutomation.setRotation(rotation)
        await("centered orientation") { targetContext.resources.configuration.orientation == if (rotation == 0) 1 else 2 }
        SystemClock.sleep(600)
        val data = CalendarData.create(LocalDate.now()).copy(recommends = listOf("嫁娶", "入人口", "祭祀", "修饰垣墙"))
        lateinit var cycle: AlmanacCycle
        runOnMainSync {
            val activity = activities.lastActivity as MainActivity
            activity.setContent {
                MaterialTheme {
                    cycle = rememberAlmanacCycle(data, centered = true)
                    androidx.compose.runtime.CompositionLocalProvider(LocalGoodDayCycle provides cycle) {
                        ImmersiveCalendar(CalendarTheme.GOOD_DAY, data, onExit = {})
                    }
                }
            }
        }
        await("centered fixture composed") { nodes().any { it.viewIdResourceName == "good-day-almanac" } }
        runOnMainSync {
            val activity = activities.lastActivity as MainActivity
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        awaitImmersive(CalendarTheme.GOOD_DAY)
        val positions = mutableMapOf<Int, Rect>()
        val samples = mutableListOf("millis,step,target,opacity,widthProgress,labelLeft,labelRight,itemLeft,itemRight")
        val started = SystemClock.uptimeMillis()
        val until = started + 17_000
        var lastMovingStep = -1
        var lastMovingX = 0
        var movingSamples = 0
        var overlappingSamples = 0
        while (SystemClock.uptimeMillis() < until && positions.size < 5) {
            var step = 0
            var opacity = 0f
            var progress = 0f
            var target = 0
            runOnMainSync { step = cycle.step; target = cycle.targetStep; opacity = cycle.opacity.value; progress = cycle.widthProgress.value }
            val tree = nodes()
            val labelNode = tree.single { it.viewIdResourceName == "almanac-label-宜" }
            val itemNode = tree.single { it.viewIdResourceName == "almanac-item-宜" }
            val rowNode = tree.single { it.viewIdResourceName == "good-day-almanac" }
            val label = Rect().also(labelNode::getBoundsInScreen)
            val item = Rect().also(itemNode::getBoundsInScreen)
            val row = Rect().also(rowNode::getBoundsInScreen)
            samples += "${SystemClock.uptimeMillis() - started},$step,$target,$opacity,$progress,${label.left},${label.right},${item.left},${item.right}"
            if (progress > .05f && progress < .95f) {
                if (opacity > .05f && opacity < .95f) overlappingSamples++
                if (lastMovingStep == target) {
                    val direction = if (target % 4 == 1 || target % 4 == 3) -1 else 1
                    check((label.left - lastMovingX) * direction >= -3) { "Label reverses or jumps during travel" }
                }
                lastMovingStep = target
                lastMovingX = label.left
                movingSamples++
            }
            if (opacity == 1f && progress == 1f && step !in positions) {
                check(itemNode.text?.toString() == data.recommends[step % 4])
                check(kotlin.math.abs((label.left + item.right) / 2f - row.exactCenterX()) <= 2f) {
                    "Group not centered: label=$label item=$item row=$row"
                }
                positions[step] = label
                capture("centered-$rotation-step-$step")
                checks += "rotation=$rotation, step=$step, ${data.recommends[step % 4]}: label and item centered PASS"
            }
            SystemClock.sleep(16)
        }
        check(positions.keys.containsAll((0..4).toList())) { "Incomplete carousel: ${positions.keys}" }
        check(positions.getValue(1).left < positions.getValue(0).left)
        check(positions.getValue(3).left < positions.getValue(1).left)
        check(kotlin.math.abs(positions.getValue(2).left - positions.getValue(0).left) <= 2)
        check(kotlin.math.abs(positions.getValue(4).left - positions.getValue(0).left) <= 2)
        check(movingSamples > 0)
        check(overlappingSamples > 0) { "Motion and fade never overlap" }
        val output = File(targetContext.getExternalFilesDir(null), "acceptance")
        File(output, "centered-$rotation.csv").writeText(samples.joinToString("\n"))
        checks += "rotation=$rotation: three/four-character offsets, return to origin, overlapping fade and monotonic travel PASS"
    }

    private fun verifyAnchoredCarousel(checks: MutableList<String>, rotation: Int) {
        uiAutomation.setRotation(rotation)
        await("anchored orientation") { targetContext.resources.configuration.orientation == if (rotation == 0) 1 else 2 }
        SystemClock.sleep(600)
        val data = CalendarData.create(LocalDate.now()).copy(
            recommends = listOf("嫁娶", "祭祀", "修饰垣墙", "入人口", "扫舍宇"),
            avoids = listOf("安葬", "开市", "解除", "扫舍宇", "祭祀"))
        lateinit var cycle: AlmanacCycle
        runOnMainSync {
            (activities.lastActivity as MainActivity).setContent {
                MaterialTheme {
                    cycle = rememberAlmanacCycle(data)
                    androidx.compose.runtime.CompositionLocalProvider(LocalAlmanacCycle provides cycle) {
                        ImmersiveCalendar(CalendarTheme.HANGING, data, onExit = {})
                    }
                }
            }
        }
        await("anchored fixture composed") { nodes().any { it.viewIdResourceName == "hanging-almanac-宜" } }
        runOnMainSync {
            val activity = activities.lastActivity as MainActivity
            androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        awaitImmersive(CalendarTheme.HANGING)
        val labels = mutableMapOf<String, Rect>()
        val slots = mutableMapOf<String, Rect>()
        val seen = mutableSetOf<Int>()
        val until = SystemClock.uptimeMillis() + 13_000
        while (SystemClock.uptimeMillis() < until && seen.size < 4) {
            var step = 0
            var opacity = 0f
            runOnMainSync { step = cycle.step; opacity = cycle.opacity.value }
            val tree = nodes()
            for (label in listOf("宜", "忌")) {
                fun bounds(tag: String) = Rect().also { area -> tree.single { it.viewIdResourceName == tag }.getBoundsInScreen(area) }
                val mark = bounds("almanac-label-$label")
                labels[label]?.let { check(mark == it) { "$label label moved: $mark vs $it" } }
                if (opacity == 1f && step !in seen) {
                    val item = bounds("almanac-item-$label")
                    val row = bounds("hanging-almanac-$label")
                    val expected = almanacPages(if (label == "宜") data.recommends else data.avoids, 2)[step % 3]
                    check(tree.single { it.viewIdResourceName == "almanac-item-$label" }.text?.toString() == expected)
                    when (step % 3) {
                        0 -> {
                            check(kotlin.math.abs((mark.left + item.right) / 2f - row.exactCenterX()) <= 2f)
                            labels[label] = mark
                            slots[label] = item
                        }
                        1 -> {
                            check(kotlin.math.abs(item.left - slots.getValue(label).left) <= 1)
                            check(item.right > slots.getValue(label).right) { "Long items did not extend right" }
                            check(row.contains(item)) { "Long items clipped" }
                        }
                        2 -> check(kotlin.math.abs(item.left - slots.getValue(label).left) <= 1)
                    }
                }
            }
            if (opacity == 1f && seen.add(step)) {
                capture("anchored-$rotation-step-$step")
                checks += "rotation=$rotation, page=$step: fixed labels, centered baseline / rightward extension / left-aligned single PASS"
            }
            SystemClock.sleep(25)
        }
        check(seen.containsAll((0..3).toList())) { "Incomplete anchored carousel: $seen" }
        check(labels.getValue("宜").left == labels.getValue("忌").left)
        checks += "rotation=$rotation: Yi/Ji aligned and fixed throughout the full cycle PASS"
    }

    private fun verifyDenseContent(checks: MutableList<String>) {
        val preferences = targetContext.getSharedPreferences("calendar", 0)
        fun show(theme: CalendarTheme, date: LocalDate) {
            preferences.edit().putString("theme_id", theme.id).putBoolean("immersive", true).commit()
            val clock = object : CalendarClock {
                override fun now(): ZonedDateTime = date.atTime(12, 0).atZone(ZoneId.systemDefault())
            }
            runOnMainSync {
                (activities.lastActivity as MainActivity).setContent {
                    MaterialTheme { androidx.compose.runtime.key(theme, date) { CalendarApp(clock) } }
                }
            }
            awaitImmersive(theme, date)
        }
        val dense = LocalDate.of(2026, 1, 22)
        val longest = (0..365).map { LocalDate.of(2026, 1, 1).plusDays(it.toLong()) }
            .maxBy { DailyWords.motto(it).length }
        for (rotation in 0..1) {
            uiAutomation.setRotation(rotation)
            await("dense orientation") { targetContext.resources.configuration.orientation == if (rotation == 0) 1 else 2 }
            SystemClock.sleep(600)
            show(CalendarTheme.ALMANAC, dense)
            val data = CalendarData.create(dense)
            check(data.recommends.size == 29)
            val tree = nodes()
            check(tree.any { it.text?.toString() == data.suitableText(Int.MAX_VALUE) })
            check(tree.any { it.text?.toString() == data.avoidText(Int.MAX_VALUE) })
            data.cells.forEach { cell ->
                val node = tree.first { it.contentDescription?.startsWith("${cell.date}，") == true }
                val bounds = Rect().also(node::getBoundsInScreen)
                check(bounds.width() > 0 && bounds.height() > 0)
            }
            capture("dense-almanac-$rotation")
            checks += "29 almanac activities and all 42 date cells visible, rotation=$rotation PASS"
            show(CalendarTheme.VERSE, longest)
            check(nodes().any { it.text?.toString() == DailyWords.motto(longest).toCharArray().joinToString("\n") })
            check(nodes().any { it.text?.toString() == DailyWords.heartWord(longest) })
            capture("longest-motto-$rotation")
            checks += "longest 14-character motto and daily two-character word, rotation=$rotation PASS"
        }
    }

    private fun verifyRefinements(checks: MutableList<String>) {
        val store = ThemeStyleStore(targetContext)
        val originals = CalendarTheme.entries.associateWith(store::read)
        val imported = mutableListOf<String>()
        try {
            CalendarTheme.entries.forEach { store.save(it, ThemeStyle()) }
            // Load a device-local image through the same bounded decoder and private-copy path.
            val fixture = android.graphics.Bitmap.createBitmap(3000, 600, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(fixture).apply {
                drawColor(android.graphics.Color.rgb(90, 155, 192))
                drawRect(900f, 0f, 2100f, 600f, android.graphics.Paint().apply { color = android.graphics.Color.rgb(245, 184, 72) })
            }
            val sourceFile = File(targetContext.cacheDir, "style-fixture.png")
            sourceFile.outputStream().use { fixture.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            fixture.recycle()
            val path = kotlinx.coroutines.runBlocking {
                store.importImage(CalendarTheme.TEDDY, android.net.Uri.fromFile(sourceFile))
            }
            imported += path
            sourceFile.delete()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            check(bounds.outWidth == 2048 && bounds.outHeight in 409..410)
            store.save(CalendarTheme.TEDDY, ThemeStyle(path, ImageFill.FIT))
            check(ThemeStyleStore(targetContext).read(CalendarTheme.TEDDY) == ThemeStyle(path, ImageFill.FIT))
            check(store.read(CalendarTheme.PHOTO) == ThemeStyle())
            checks += "local image copy bounded to 2048 px, aspect ratio and independent persisted settings PASS"
            runOnMainSync { activities.lastActivity.recreate() }
            awaitTheme(CalendarTheme.GOLDEN)
            CalendarTheme.entries.forEachIndexed { index, theme ->
                if (index > 0) swipe(left = true)
                awaitTheme(theme)
                val pageBounds = Rect().also { nodes().first { it.viewIdResourceName == "art-${theme.name}" }.getBoundsInScreen(it) }
                var screen = Rect()
                runOnMainSync { screen = activities.lastActivity.windowManager.currentWindowMetrics.bounds }
                check(kotlin.math.abs(pageBounds.width().toFloat() / pageBounds.height() - screen.width().toFloat() / screen.height()) < .01f)
                press("style-${theme.name}")
                await("style panel ${theme.name}") { nodes().any { it.viewIdResourceName == "theme-style-sheet" } }
                capture("style-${theme.name.lowercase()}")
                if (theme == CalendarTheme.TEDDY || theme == CalendarTheme.PHOTO) {
                    val desired = if (theme == CalendarTheme.TEDDY) ImageFill.STRETCH else ImageFill.FIT
                    press("fill-${desired.name}")
                    await("saved fill ${theme.name}") { store.read(theme).fill == desired }
                    check(store.read(if (theme == CalendarTheme.TEDDY) CalendarTheme.PHOTO else CalendarTheme.TEDDY).fill ==
                        if (theme == CalendarTheme.TEDDY) ImageFill.CROP else ImageFill.STRETCH)
                    press("choose-image")
                    await("system photo picker") { uiAutomation.rootInActiveWindow?.packageName?.toString()?.let { it != targetContext.packageName } == true }
                    capture("picker-${theme.name.lowercase()}")
                    check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                    await("picker cancelled") { nodes().any { it.viewIdResourceName == "theme-style-sheet" } }
                    check(store.read(theme).fill == desired)
                    checks += "${theme.name}: independent fill, native image picker and cancellation PASS"
                } else {
                    check(nodes().any { it.text?.toString() == "此主题暂无可调整的样式。" })
                }
                check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
                awaitTheme(theme)
                checks += "${theme.name}: preview aspect ratio and style entry PASS"
            }
            // Watch every paired entry, including the final unpaired entry, on a real rendered page.
            val data = CalendarData.create(LocalDate.now())
            val expected = almanacPages(data.recommends, 2).toSet()
            val seen = mutableSetOf<String>()
            var labelBounds: Rect? = null
            val until = SystemClock.uptimeMillis() + expected.size * 2440L + 3000L
            while (SystemClock.uptimeMillis() < until && !seen.containsAll(expected)) {
                val tree = nodes()
                tree.firstOrNull { it.viewIdResourceName == "almanac-item-宜" }?.text?.toString()?.let { seen += it }
                tree.firstOrNull { it.viewIdResourceName == "almanac-label-宜" }?.let {
                    val current = Rect().also(it::getBoundsInScreen)
                    if (labelBounds == null) labelBounds = current else check(labelBounds == current)
                }
                SystemClock.sleep(100)
            }
            check(seen.containsAll(expected)) { "Missing almanac pages: ${expected - seen}" }
            checks += "paired almanac carousel covers every item with stationary label PASS"
            // Explicitly exercise import rendering after activity recreation and sheet layout in landscape.
            repeat(5) { swipe(left = false) }
            awaitTheme(CalendarTheme.TEDDY)
            runOnMainSync { activities.lastActivity.recreate() }
            awaitTheme(CalendarTheme.TEDDY)
            check(nodes().any { it.contentDescription?.toString() == "自选日历图片" })
            capture("custom-image-portrait")
            uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            await("landscape style") { targetContext.resources.configuration.orientation == 2 }
            awaitTheme(CalendarTheme.TEDDY)
            capture("custom-image-landscape")
            press("style-TEDDY")
            await("landscape sheet") { nodes().any { it.viewIdResourceName == "theme-style-sheet" } }
            capture("style-landscape")
            check(uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            awaitTheme(CalendarTheme.TEDDY)
            checks += "custom image survives recreation; landscape preview and scrollable settings PASS"
        } finally {
            // Instrumentation can terminate before apply() reaches disk; restore synchronously.
            val restore = targetContext.getSharedPreferences("theme_styles", 0).edit()
            originals.forEach { (theme, style) ->
                restore.putString("${theme.id}.image", style.imagePath)
                restore.putString("${theme.id}.fill", style.fill.name)
            }
            check(restore.commit())
            imported.forEach(store::discard)
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
        await("press target $tag") { nodes().any { it.viewIdResourceName == tag && it.isEnabled } }
        SystemClock.sleep(250)
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
                tree.none { node -> CalendarTheme.entries.any { node.viewIdResourceName == "immersive-${it.name}" } }
        }
        SystemClock.sleep(350)
        val tree = nodes()
        val dateData = CalendarData.create(expectedDate)
        val displayedText = tree.mapNotNull { it.text?.toString() }
        when (theme) {
            CalendarTheme.GOOD_DAY -> check(displayedText.any { it in almanacPages(dateData.recommends, 1) }) { "Good-day almanac is not computed data" }
            CalendarTheme.ALMANAC -> {
                check(displayedText.contains(dateData.suitableText(Int.MAX_VALUE))) { "Missing computed suitable activities" }
                check(displayedText.contains(dateData.avoidText(Int.MAX_VALUE))) { "Missing computed avoided activities" }
                if (dateData.festivals.isEmpty()) check(displayedText.none { it == "农历" }) { "Unexpected fallback festival badge" }
            }
            CalendarTheme.HANGING -> {
                check(displayedText.any { it in almanacPages(dateData.recommends, 2) }) { "Hanging suitable activities missing" }
                check(displayedText.any { it in almanacPages(dateData.avoids, 2) }) { "Hanging avoided activities missing" }
                check(displayedText.none { it == "日历" }) { "Obsolete calendar heading" }
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
        runOnMainSync { activity.setContent { MaterialTheme {
                    androidx.compose.runtime.CompositionLocalProvider(LocalDayMotionObserver provides { date, moving -> dayMotion.set(date to moving) }) {
                        CalendarApp(clock)
                    }
                } } }
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
        val art = nodes().firstOrNull { it.viewIdResourceName?.startsWith("art-") == true }
        if (art != null) {
            val bounds = Rect().also { art.getBoundsInScreen(it) }
            File(output, "$name.bounds").writeText("${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}")
        }
    }
}
