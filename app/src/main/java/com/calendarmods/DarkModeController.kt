package com.calendarmods

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.Calendar

/**
 * Owns the light/dark decision for one resumed screen.
 *
 * Three sources feed the same switch: a stored preference, a night window checked on the minute,
 * and — when the device has one — the ambient light sensor, read at its own pace and filtered by
 * [LuxDarkGate]. The host starts and stops it with the screen, so no sensor is registered while
 * the calendar is in the background.
 */
internal class DarkModeController(
    context: Context,
    private val onChanged: (Boolean) -> Unit,
) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor: Sensor? = try {
        manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    } catch (_: RuntimeException) {
        null
    }
    private val handler = Handler(Looper.getMainLooper())
    private val gate = LuxDarkGate()

    private var style = ThemeStyle()
    private var started = false
    private var lux = Float.NaN

    /** The printing currently in use. */
    var current = false
        private set

    /** False when the device has no ambient light sensor, which disables that source. */
    val hasSensor: Boolean get() = lightSensor != null

    fun start(style: ThemeStyle) {
        this.style = style
        started = true
        gate.reset(current)
        listen()
        rearm()
        settle()
    }

    fun stop() {
        started = false
        listen()
        handler.removeCallbacks(tick)
    }

    /** Called when the style sheet changes; re-reads the source without dropping the sensor. */
    fun setStyle(style: ThemeStyle) {
        val previous = this.style
        this.style = style
        if (!started) return
        if (previous.darkMode != style.darkMode) {
            if (style.darkMode == DarkMode.SENSOR) {
                lux = Float.NaN
                gate.reset(current)
            }
            listen()
        }
        rearm()
        settle()
    }

    /** The printing [style] asks for right now, without touching the sensor registration. */
    fun resolve(style: ThemeStyle): Boolean = when (style.darkMode) {
        DarkMode.OFF -> false
        DarkMode.ON -> true
        DarkMode.SCHEDULE -> style.scheduledDark(localHour())
        DarkMode.SENSOR -> if (lux.isNaN()) current else gate.update(lux, SystemClock.uptimeMillis())
    }

    private fun settle() {
        val wanted = resolve(style)
        if (wanted == current) return
        current = wanted
        onChanged(wanted)
    }

    private fun apply(value: Boolean) {
        if (value == current) return
        current = value
        onChanged(value)
    }

    private fun localHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    // ------------------------------------------------------------------ sources

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_LIGHT || style.darkMode != DarkMode.SENSOR) return
            lux = event.values[0]
            apply(gate.update(lux, SystemClock.uptimeMillis()))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun listen() {
        val wanted = started && style.darkMode == DarkMode.SENSOR && lightSensor != null
        try {
            if (wanted) manager?.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            else manager?.unregisterListener(sensorListener)
        } catch (_: RuntimeException) {
            // A vendor sensor service that refuses the listener simply leaves the page on the
            // light sheet; the preference and the night window keep working.
        }
    }

    /**
     * One timer for both timed sources: the night window is checked on the local minute, the light
     * sensor is re-read often enough for [LuxDarkGate] to confirm a change it has already seen.
     */
    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            if (style.darkMode == DarkMode.SENSOR) {
                if (!lux.isNaN()) apply(gate.update(lux, SystemClock.uptimeMillis()))
            } else {
                apply(resolve(style))
            }
            rearm()
        }
    }

    private fun rearm() {
        handler.removeCallbacks(tick)
        if (!started) return
        val delay = when (style.darkMode) {
            DarkMode.SCHEDULE -> 60_000L - System.currentTimeMillis() % 60_000L + 50L
            DarkMode.SENSOR -> SENSOR_POLL_MS
            else -> return
        }
        handler.postDelayed(tick, delay.coerceAtLeast(16L))
    }

    companion object {
        /** Only used to let the gate time out a reading that has stopped arriving. */
        private const val SENSOR_POLL_MS = 600L

        /** Whether this device can feed the light-sensor source at all. */
        fun hasLightSensor(context: Context): Boolean = try {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            manager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        } catch (_: RuntimeException) {
            false
        }
    }
}
