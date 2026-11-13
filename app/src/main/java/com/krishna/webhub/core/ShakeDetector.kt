package com.krishna.webhub.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.krishna.webhub.WebHubApp
import com.krishna.webhub.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

/** Two threshold spikes within 500 ms, then 1.5 s cooldown. */
class ShakeDetector(
    context: Context,
    private val threshold: () -> Float,
    private val onForce: ((Float) -> Unit)?,
    private val onShake: () -> Unit
) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var above = false
    private var spikes = 0
    private var firstSpikeAt = 0L
    private var lastShakeAt = 0L
    private var lastForceAt = 0L

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        sm.unregisterListener(this)
        above = false
        spikes = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val x = e.values[0]
        val y = e.values[1]
        val z = e.values[2]
        val f = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()

        if (now - lastForceAt >= 33) {
            lastForceAt = now
            onForce?.invoke(f)
        }

        val th = threshold()
        if (f > th) {
            if (!above) {
                above = true
                if (spikes == 0 || now - firstSpikeAt > 500) {
                    spikes = 1
                    firstSpikeAt = now
                } else {
                    spikes++
                }
                if (spikes >= 2) {
                    spikes = 0
                    if (now - lastShakeAt >= 1500) {
                        lastShakeAt = now
                        onShake()
                    }
                }
            }
        } else if (f < th * 0.85f) {
            above = false
        }
    }
}

/** One shared detector. Owners (main, runner, svc) acquire and release it. */
object ShakeHub {
    val force = MutableStateFlow(0f)
    val lastShake = MutableStateFlow(0L)

    @Volatile var meterActive = false

    private var detector: ShakeDetector? = null
    private val owners = mutableSetOf<String>()

    fun acquire(owner: String) {
        synchronized(this) {
            owners.add(owner)
            if (detector == null) {
                detector = ShakeDetector(
                    WebHubApp.instance,
                    threshold = { Prefs.sensitivity.value },
                    onForce = { if (meterActive) force.value = it },
                    onShake = { handleShake() }
                ).also { it.start() }
            }
        }
    }

    fun release(owner: String) {
        synchronized(this) {
            owners.remove(owner)
            if (owners.isEmpty()) {
                detector?.stop()
                detector = null
            }
        }
    }

    private fun handleShake() {
        val app = WebHubApp.instance
        Haptics.tick(app)
        lastShake.value = SystemClock.elapsedRealtime()
        if (meterActive) return
        Actions.dispatch(app)
    }
}
