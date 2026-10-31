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

internal data class AlmanacEnvironment(val minute: Int = currentMinute(),
    val ambientDark: Boolean = false, val lightAvailable: Boolean = false, val lux: Float? = null)

private fun currentMinute(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}

/**
 * One light listener for the visible screen, stopped in the background. The compat branch has no
 * lifecycle library, so the host calls [start] and [stop] from the activity callbacks.
 */
internal class AlmanacEnvironmentMonitor(
    context: Context,
    private val ambientEnabled: Boolean,
    private val onUpdate: (AlmanacEnvironment) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val gate = AmbientDarkGate()
    private var lux: Float? = null
    private var registered = false
    private var started = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lux = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }
            gate.update(lux, SystemClock.elapsedRealtime())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!started) return
            val dark = registered && gate.update(lux, SystemClock.elapsedRealtime())
            onUpdate(AlmanacEnvironment(currentMinute(), dark, sensor != null, lux))
            // The light sensor reports on change; a timer also completes a stable dark interval.
            handler.postDelayed(this, if (registered) 1_000L else 15_000L)
        }
    }

    fun start() {
        if (started) return
        started = true
        registered = ambientEnabled && sensor != null &&
            manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
        handler.post(ticker)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(ticker)
        if (registered) manager?.unregisterListener(listener)
        registered = false
        lux = null
    }
}
