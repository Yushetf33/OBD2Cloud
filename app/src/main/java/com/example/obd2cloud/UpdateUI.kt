package com.example.obd2cloud

import android.util.Log
import android.widget.TextView
import kotlinx.coroutines.*

class UpdateUI(
    private var rpmDisplay: TextView,
    private var fuelDisplay: TextView,
    private var speedDisplay: TextView,
    private var throttleDisplay: TextView,
    private var engineLoadDisplay: TextView,
    private var gearDisplay: TextView,
    private val bluetoothClient: BluetoothClient,
) {

    fun startMetricsUpdateJob(readFlag: () -> Boolean): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            while (readFlag()) {
                // Consultar y actualizar las métricas secuencialmente
                val rpm = RPM()
                val speed = speed()
                val gear = calculateGear(rpm, speed).toString()
                val fuelTrim = fuelTrim()
                val throttle = throttle()
                val engineLoad = engineLoad()

                // Actualizar la interfaz de usuario
                withContext(Dispatchers.Main) {
                    updateMetrics(rpm, fuelTrim, speed, throttle, engineLoad, gear)
                }
            }
        }
    }

    suspend fun updateMetrics(rpm: String, fuelTrim: String, speed: String, throttle: String, engineLoad: String, gear: String) {
        withContext(Dispatchers.Main) {
            Log.d("UpdateUI", "Updating UI: RPM=$rpm, FuelTrim=$fuelTrim, Speed=$speed, Throttle=$throttle, EngineLoad=$engineLoad, Gear=$gear")

            rpmDisplay.text = rpm
            fuelDisplay.text = fuelTrim
            speedDisplay.text = speed
            throttleDisplay.text = throttle
            engineLoadDisplay.text = engineLoad
            gearDisplay.text = gear
        }
    }

    fun calculateGear(rpm: String, speed: String): Int {
        val rpmValue = rpm.toIntOrNull() ?: return 0
        val speedValue = speed.toFloatOrNull() ?: return 0

        if (speedValue <= 0) return 0

        val speeds = listOf(
            rpmValue * 0.007368421f,
            rpmValue * 0.014736842f,
            rpmValue * 0.019607844f,
            rpmValue * 0.026041666f,
            rpmValue * 0.0327529f,
            rpmValue * 0.04043478f
        )

        val closestSpeed = speeds.minByOrNull { kotlin.math.abs(it - speedValue) }
        return speeds.indexOf(closestSpeed ?: 0) + 1
    }

    suspend fun RPM(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askRPM()
        }
    }

    suspend fun fuelTrim(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askFuelTrimShort()
        }
    }

    suspend fun speed(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askSpeed()
        }
    }

    suspend fun throttle(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askThrottlePosition()
        }
    }

    suspend fun engineLoad(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askEngineLoad()
        }
    }
}
