package com.calendarmods

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime

internal data class AlmanacEnvironment(val minute: Int = LocalTime.now().let { it.hour * 60 + it.minute },
    val ambientDark: Boolean = false, val lightAvailable: Boolean = false, val lux: Float? = null)
internal val LocalAlmanacEnvironment = staticCompositionLocalOf { AlmanacEnvironment() }
internal val LocalAlmanacEnvironmentOverride = staticCompositionLocalOf<AlmanacEnvironment?> { null }

/** One listener for the current screen; stopped in the background and never requested by thumbnails. */
@Composable
internal fun rememberAlmanacEnvironment(ambientEnabled: Boolean): AlmanacEnvironment {
    LocalAlmanacEnvironmentOverride.current?.let { return it }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val manager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    val state by produceState(AlmanacEnvironment(lightAvailable = sensor != null), lifecycle, ambientEnabled, sensor) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val gate = AmbientDarkGate()
            var lux: Float? = null
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    lux = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }
                    // Observe every change, including a brief interruption between timer ticks.
                    gate.update(lux, SystemClock.elapsedRealtime())
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            val registered = ambientEnabled && sensor != null && manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            try {
                while (true) {
                    val time = LocalTime.now()
                    value = AlmanacEnvironment(time.hour * 60 + time.minute,
                        registered && gate.update(lux, SystemClock.elapsedRealtime()), sensor != null, lux)
                    // Light sensor is on-change: a timer also completes a stable dark interval without new events.
                    delay(if (registered) 1000 else 15_000)
                }
            } finally {
                if (registered) manager?.unregisterListener(listener)
                value = value.copy(ambientDark = false, lux = null)
            }
        }
    }
    return state
}
