package com.calendarmods

import android.app.Instrumentation
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** Regressions found by following the framework rewrite's subsequent fixes. */
internal fun Instrumentation.verifyHistoryRegressions(host: MainActivity, checks: MutableList<String>) {
    fun main(block: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { block() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    main {
        val page = PaperView(host)
        var taps = 0; var exits = 0; var swipes = 0
        page.onTap = { taps++ }; page.onLongPress = { exits++ }; page.onSwipe = { swipes++ }
        fun send(action: Int, x: Float, y: Float) {
            val now = SystemClock.uptimeMillis()
            val event = MotionEvent.obtain(now, now, action, x, y, 0)
            page.onTouchEvent(event); event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 50f, 50f)
        send(MotionEvent.ACTION_MOVE, 50f, 250f)
        send(MotionEvent.ACTION_UP, 50f, 50f)
        send(MotionEvent.ACTION_DOWN, 50f, 50f)
        send(MotionEvent.ACTION_CANCEL, 50f, 50f)
        send(MotionEvent.ACTION_UP, 50f, 50f)
        check(taps == 0 && exits == 0 && swipes == 0)
        send(MotionEvent.ACTION_DOWN, 300f, 50f)
        send(MotionEvent.ACTION_UP, 50f, 50f)
        check(swipes == 1 && exits == 0)
    }
    checks += "moved/cancelled gestures never trigger a tap or fullscreen exit"

    fun field(name: String) = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }
    val enter = MainActivity::class.java.getDeclaredMethod("enterImmersive").apply { isAccessible = true }
    val exit = MainActivity::class.java.getDeclaredMethod("exitImmersive").apply { isAccessible = true }
    val midnight = MainActivity::class.java.getDeclaredMethod("onNewDate", CalendarDateEvent::class.java).apply { isAccessible = true }
    val browse = MainActivity::class.java.getDeclaredMethod("browse", Int::class.javaPrimitiveType).apply { isAccessible = true }
    val applyStyle = MainActivity::class.java.getDeclaredMethod("applyStyle", ThemeStyle::class.java).apply { isAccessible = true }
    val previousStyle = ThemeStyleStore(targetContext).read()
    val original = field("data").get(host) as CalendarData
    val start = PlainDate.of(2026, 1, 31)
    try {
        main {
            field("data").set(host, CalendarData.create(start))
            applyStyle.invoke(host, previousStyle.copy(interactive = true))
            enter.invoke(host)
            midnight.invoke(host, CalendarDateEvent(start.plusDays(1), false))
        }
        repeat(100) { if ((field("data").get(host) as CalendarData).today != start.plusDays(1)) Thread.sleep(50) }
        main { check((field("paging").get(host) as CalendarPaging).origin == start.plusDays(1)) }
        main { browse.invoke(host, 1) }
        Thread.sleep(300)
        main { midnight.invoke(host, CalendarDateEvent(start.plusDays(3), false)) }
        repeat(100) { if ((field("data").get(host) as CalendarData).today != start.plusDays(3)) Thread.sleep(50) }
        main {
            check(field("browsedDate").get(host) == start.plusDays(2))
            (field("todayButton").get(host) as View).performClick()
            check((field("paging").get(host) as CalendarPaging).origin == start.plusDays(3))
            browse.invoke(host, 1)
            check(field("browsedDate").get(host) == start.plusDays(4))
        }
        checks += "midnight preserves browsed date and resets paging origin on live/return-to-today pages"
    } finally { main { exit.invoke(host); field("data").set(host, original); applyStyle.invoke(host, previousStyle) } }
}
