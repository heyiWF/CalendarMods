package com.calendarmods

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** Exercise real font metrics and the gesture/lifecycle paths added with month paging and idle mode. */
internal fun Instrumentation.verifyIdleAndMonth(host: MainActivity, checks: MutableList<String>) {
    var failure: Throwable? = null
    runOnMainSync {
        try {
            val saver = ScreenSaverView(host)
            if (Build.VERSION.SDK_INT <= 22) check(saver.layerType == View.LAYER_TYPE_SOFTWARE)
            for ((w, h) in listOf(1920 to 1200, 1200 to 1920, 640 to 360)) {
                saver.layout(0, 0, w, h)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                try {
                    for ((hour, minute) in listOf(0 to 0, 11 to 11, 12 to 34, 16 to 57, 20 to 28, 23 to 59)) {
                        saver.drawClock(Canvas(bitmap), formatClock(true, hour, minute))
                        fun ink(x: Int, y: Int) = bitmap.getPixel(x, y) != Color.BLACK
                        fun rowInk(y: Int) = (0 until w).any { x -> ink(x, y) }
                        fun firstColumn(from: Int, to: Int) = (0 until w).firstOrNull { x ->
                            (from..to).any { y -> ink(x, y) }
                        } ?: -1
                        fun lastColumn(from: Int, to: Int) = (w - 1 downTo 0).firstOrNull { x ->
                            (from..to).any { y -> ink(x, y) }
                        } ?: -1
                        // The date line sits under the reading: walk up over it to isolate the
                        // rows the reading itself occupies.
                        var row = h - 1
                        while (row >= 0 && !rowInk(row)) row--
                        val dateBottom = row
                        while (row >= 0 && rowInk(row)) row--
                        val dateTop = row + 1
                        while (row >= 0 && !rowInk(row)) row--
                        val readingBottom = row
                        // Every digit and the colon must leave its own ink band. A blank glyph
                        // makes this fail even when the rest of the reading still looks plausible.
                        var bands = 0
                        var previousInk = false
                        for (x in 0 until w) {
                            val columnInk = (0..readingBottom).any { y -> ink(x, y) }
                            if (columnInk && !previousInk) bands++
                            previousInk = columnInk
                        }
                        check(bands == 5) { "Clock $hour:$minute has $bands ink bands at $w x $h" }
                        // The date prints under the reading, its numerals flush with the reading's
                        // left ink and its weekday flush with the reading's right edge. Digits and
                        // Chinese characters carry different side bearings, so the two edges meet
                        // within a share of the width rather than on the pixel.
                        val tolerance = (w / 100).coerceAtLeast(2)
                        val edge = w - 1
                        check(firstColumn(dateTop, dateBottom) in
                            (firstColumn(0, readingBottom) - tolerance)..(firstColumn(0, readingBottom) + tolerance)) {
                            "The date line did not start on the reading's left edge at $w x $h"
                        }
                        check(lastColumn(dateTop, dateBottom) in
                            (lastColumn(0, readingBottom) - tolerance)..(lastColumn(0, readingBottom) + tolerance)) {
                            "The weekday did not end on the reading's right edge at $w x $h"
                        }
                        for (x in 0 until w) {
                            check(bitmap.getPixel(x, 0) == Color.BLACK)
                            check(bitmap.getPixel(x, h - 1) == Color.BLACK)
                        }
                        for (y in 0 until h) {
                            check(bitmap.getPixel(0, y) == Color.BLACK)
                            check(bitmap.getPixel(edge, y) == Color.BLACK)
                        }
                    }
                    saver.drawClock(Canvas(bitmap), formatClock(false, 12, 34))
                    check((0 until h / 2).any { y ->
                        (0 until w).any { x -> bitmap.getPixel(x, y) != Color.BLACK }
                    }) { "12-hour period/reading did not render" }
                } finally { bitmap.recycle() }
            }
            checks += "large clocks retain all four digits and colon in portrait/landscape with margins"

            val page = PaperView(host)
            val date = PlainDate.of(2026, 11, 14)
            val data = CalendarData.create(date)
            val next = CalendarData.create(PlainDate.of(2026, 12, 1))
            page.layout(0, 0, 640, 360)
            val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
            fun load(animated: Boolean) {
                page.setPage(CalendarTheme.DAILY, data,
                    ThemeStyle(interactive = true, pageAnimation = animated), false, false)
                page.draw(Canvas(bitmap))
            }
            fun value(name: String): Float = PaperView::class.java.getDeclaredField(name)
                .apply { isAccessible = true }.getFloat(page)
            fun field(name: String) = PaperView::class.java.getDeclaredField(name).apply { isAccessible = true }
            val now = SystemClock.uptimeMillis()
            fun send(action: Int, x: Float, y: Float) {
                MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0).let {
                    page.onTouchEvent(it)
                    it.recycle()
                }
            }
            try {
                load(false)
                var picks = 0
                var steps = 0
                page.onPickDate = { picks++ }
                page.onMonthStep = { steps++ }
                page.monthPage = { next }
                val x = value("gridLeft") + value("gridCellW") * 3.5f
                val headerY = value("gridTop") + value("gridHeaderH") / 2f
                val y = value("gridTop") + value("gridHeaderH") + value("gridCellH") / 2f
                send(MotionEvent.ACTION_DOWN, x, headerY)
                send(MotionEvent.ACTION_UP, x, headerY)
                check(picks == 0) { "Weekday header selected a date" }
                send(MotionEvent.ACTION_DOWN, x, y)
                send(MotionEvent.ACTION_UP, x, y)
                check(picks == 1) { "First row could not be selected" }
                send(MotionEvent.ACTION_DOWN, x, y)
                send(MotionEvent.ACTION_MOVE, x - 200f, y)
                check(field("sheet").get(page) == null) { "Disabled animations still allocated a turning sheet" }
                send(MotionEvent.ACTION_UP, x - 200f, y)
                check(steps == 1)

                if (MotionSettings.enabled(host)) {
                    load(true)
                    send(MotionEvent.ACTION_DOWN, x, y)
                    send(MotionEvent.ACTION_MOVE, x - 260f, y)
                    check(field("turned").get(page) == next)
                    send(MotionEvent.ACTION_CANCEL, x - 260f, y)
                    (field("turnAnimator").get(page) as? android.animation.ValueAnimator)?.end()
                    check(page.data?.today == date) { "Cancelled drag changed month" }
                    page.draw(Canvas(bitmap))
                    page.monthPage = { CalendarData.create(PlainDate.of(2027, 1, 1)) }
                    send(MotionEvent.ACTION_DOWN, x, y)
                    send(MotionEvent.ACTION_MOVE, x - 260f, y)
                    check(field("turned").get(page) == null) { "Stale neighbour cache skipped a month" }
                    send(MotionEvent.ACTION_CANCEL, x - 260f, y)
                }
            } finally { bitmap.recycle() }
            checks += "grid header taps, disabled animations, cancelled drags and stale neighbours are safe"

            fun hostField(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
            fun call(name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(host)
            val oldStyle = hostField("style").get(host)
            val wasResumed = hostField("resumed").getBoolean(host)
            try {
                hostField("style").set(host, ThemeStyle(screensaver = true, interactive = true))
                call("enterImmersive")
                hostField("resumed").setBoolean(host, false)
                call("showScreensaver")
                check(hostField("screenSaver").get(host) == null) { "Background activity opened idle clock" }
                hostField("resumed").setBoolean(host, true)
                hostField("touching").setBoolean(host, true)
                call("showScreensaver")
                check(hostField("screenSaver").get(host) == null) { "Clock interrupted a held gesture" }
                hostField("touching").setBoolean(host, false)
                call("showScreensaver")
                check(hostField("screenSaver").get(host) != null)
                check((hostField("paper").get(host) as View).visibility == View.INVISIBLE)
                call("dismissScreensaver")
                check((hostField("paper").get(host) as View).visibility == View.VISIBLE)
            } finally {
                call("exitImmersive")
                hostField("style").set(host, oldStyle)
                hostField("resumed").setBoolean(host, wasResumed)
                hostField("touching").setBoolean(host, false)
            }
            checks += "idle clock respects foreground/touch state and suspends the covered calendar"
        } catch (error: Throwable) { failure = error }
    }
    failure?.let { throw it }
}
