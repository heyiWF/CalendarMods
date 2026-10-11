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

internal fun calendarDates(context: Context, clock: CalendarClock) = callbackFlow {
    var ticker: Job? = null
    fun refresh() {
        ticker?.cancel()
        ticker = launch {
            while (isActive) {
                val now = clock.now()
                trySend(now.toLocalDate())
                // Wake at midnight; the minute fallback also handles missed broadcasts.
                delay(millisUntilNextDate(now).coerceAtMost(60_000))
            }
        }
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
        addAction(Intent.ACTION_DATE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }, ContextCompat.RECEIVER_NOT_EXPORTED)
    refresh()
    awaitClose {
        ticker?.cancel()
        context.unregisterReceiver(receiver)
    }
}.distinctUntilChanged()
