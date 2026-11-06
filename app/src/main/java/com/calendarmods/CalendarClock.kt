package com.calendarmods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import java.util.Calendar
import java.util.TimeZone

/** A wall-clock reading captured with the zone that produced it. */
internal data class ClockSnapshot(val millis: Long, val zone: TimeZone)

internal fun interface CalendarClock {
    fun now(): ClockSnapshot
}

// Read the current system zone each time, including after a timezone change.
internal object SystemCalendarClock : CalendarClock {
    override fun now(): ClockSnapshot = ClockSnapshot(System.currentTimeMillis(), TimeZone.getDefault())
}

internal fun ClockSnapshot.localDate(): PlainDate {
    val calendar = Calendar.getInstance(zone).apply { timeInMillis = millis }
    return PlainDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH))
}

/** The wall clock the page prints, 24-hour and always two digits. */
internal fun clockText(): String {
    val calendar = Calendar.getInstance()
    return String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
}

/** DST-aware: the next local midnight is not always 24 hours away. */
internal fun millisUntilNextDate(now: ClockSnapshot): Long {
    val next = Calendar.getInstance(now.zone).apply { timeInMillis = now.millis }
    next.set(Calendar.HOUR_OF_DAY, 0)
    next.set(Calendar.MINUTE, 0)
    next.set(Calendar.SECOND, 0)
    next.set(Calendar.MILLISECOND, 0)
    next.add(Calendar.DAY_OF_MONTH, 1)
    return (next.timeInMillis - now.millis).coerceAtLeast(1L)
}

internal fun naturalDateBoundary(previous: ClockSnapshot?, now: ClockSnapshot): Boolean {
    if (previous == null || previous.zone.id != now.zone.id) return false
    val previousDate = previous.localDate()
    if (previousDate.plusDays(1) != now.localDate()) return false
    if (now.millis - previous.millis !in 1..120_000L) return false
    val elapsedToday = now.millis - now.localDate().startOfDayMillis(now.zone)
    return elapsedToday in 0..120_000L
}

internal data class CalendarDateEvent(val date: PlainDate, val animate: Boolean)
internal data class CalendarDay(val data: CalendarData, val animate: Boolean)

/**
 * Date ticks for one resumed screen: a short poll that lands on midnight plus the system
 * broadcasts. The compat branch has no lifecycle library, so the host starts and stops it.
 */
internal class CalendarDayMonitor(
    private val context: Context,
    private val clock: CalendarClock = SystemCalendarClock,
    private val onDate: (CalendarDateEvent) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var previous: ClockSnapshot? = null
    private var started = false

    private val ticker = object : Runnable {
        override fun run() {
            if (!started) return
            val now = clock.now()
            val before = previous
            if (before == null || before.localDate() != now.localDate()) {
                onDate(CalendarDateEvent(now.localDate(), naturalDateBoundary(before, now)))
            }
            previous = now
            handler.postDelayed(this, millisUntilNextDate(now).coerceAtMost(60_000L))
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // A manual date change is not a natural midnight crossing: refresh without animation.
            previous = null
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        handler.post(ticker)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(ticker)
        runCatching { context.unregisterReceiver(receiver) }
    }
}
