package com.calendarmods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime

fun interface CalendarClock {
    fun now(): ZonedDateTime
}

// Read the current system zone each time, including after a timezone change.
object SystemCalendarClock : CalendarClock {
    override fun now(): ZonedDateTime = ZonedDateTime.now()
}

internal fun millisUntilNextDate(now: ZonedDateTime): Long = Duration.between(
    now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone),
).toMillis().coerceAtLeast(1)

internal data class CalendarDateEvent(val date: LocalDate, val animate: Boolean)
internal data class CalendarDay(val data: CalendarData, val animate: Boolean)

/** A resumed subscription begins with a non-animated refresh, including after a missed midnight. */
internal fun calendarDayEvents(context: Context, clock: CalendarClock) = callbackFlow {
    var ticker: Job? = null
    var previous: ZonedDateTime? = null
    fun refresh(adjusted: Boolean = false) {
        ticker?.cancel()
        ticker = launch {
            var reset = adjusted
            while (isActive) {
                val now = clock.now()
                val before = previous
                if (reset || before == null || before.toLocalDate() != now.toLocalDate()) {
                    trySend(CalendarDateEvent(now.toLocalDate(), !reset && naturalDateBoundary(before, now)))
                }
                previous = now
                reset = false
                delay(millisUntilNextDate(now).coerceAtMost(60_000))
            }
        }
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refresh(intent?.action != Intent.ACTION_DATE_CHANGED)
        }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }, ContextCompat.RECEIVER_NOT_EXPORTED)
    refresh()
    awaitClose { ticker?.cancel(); context.unregisterReceiver(receiver) }
}

internal fun naturalDateBoundary(previous: ZonedDateTime?, now: ZonedDateTime): Boolean =
    previous != null && previous.zone == now.zone && previous.toLocalDate().plusDays(1) == now.toLocalDate() &&
        Duration.between(previous, now).toMillis() in 1..120_000 &&
        Duration.between(now.toLocalDate().atStartOfDay(now.zone), now).toMillis() in 0..120_000

internal fun calendarDates(context: Context, clock: CalendarClock) =
    calendarDayEvents(context, clock).map { it.date }.distinctUntilChanged()
