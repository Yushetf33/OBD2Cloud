package com.example.obd2cloud

import android.app.Activity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.*

class UpdateUI(
    private val activity: Activity, // Para acceder a findViewById y recursos
    private val bluetoothClient: BluetoothClient,
    private val mainActivity: MainActivity // Agregar referencia a MainActivity
) {
    private lateinit var connectionStatus: TextView
    private lateinit var speedDisplay: TextView
    private lateinit var rpmDisplay: TextView
    private lateinit var throttleDisplay: TextView
    private lateinit var maxspeedDisplay: TextView
    private lateinit var engineLoadDisplay: TextView
    private lateinit var fuelDisplay: TextView
    private lateinit var gearDisplay: TextView
    private lateinit var stopButton: Button

    private var metricsUpdateJob: Job? = null

    fun initializeUI() {
        // Inicializar elementos de la UI
        connectionStatus = activity.findViewById(R.id.connection_indicator)
        speedDisplay = activity.findViewById(R.id.speed_display)
        rpmDisplay = activity.findViewById(R.id.RPM_display)
        fuelDisplay = activity.findViewById(R.id.fuel_display)
        gearDisplay = activity.findViewById(R.id.gear_display)
        throttleDisplay = activity.findViewById(R.id.throttle_display)
        maxspeedDisplay = activity.findViewById(R.id.maxSpeed_display)
        engineLoadDisplay = activity.findViewById(R.id.engine_load_display)
        stopButton = activity.findViewById(R.id.stop)

        stopButton.setOnClickListener {
            handleStopButtonClick()
        }
    }

    private fun handleStopButtonClick() {
        mainActivity.updateLoggingMenuItemText(false) // Indicamos que el logging ha parado
        stopMetricsUpdate() // Detener la actualización de métricas
        updateConnectionStatus("Disconnected")
        resetDisplays() // Restablecer las vistas a su estado inicial

        // Actualizar el estado de la conexión
        stopButton.isEnabled = false

        // Llamar al método para desconectar el cliente Bluetooth
        bluetoothClient.disconnect()

        Log.d("UpdateUI", "Stopped metrics update and disconnected.")

        // Notificar a MainActivity para actualizar el menú
    }

    fun updateConnectionStatus(status: String) {
        activity.runOnUiThread {
            connectionStatus.text = status
            stopButton.isEnabled = status == "Connected"
        }
    }

    private fun startMetricsUpdateJob(readFlag: () -> Boolean) {
        Log.d("UpdateUI", "Iniciando actualización de métricas")
        metricsUpdateJob = CoroutineScope(Dispatchers.IO).launch {
            while (readFlag()) {
                // Consultar y actualizar las métricas secuencialmente
                val rpm = rpm()
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

    fun stopMetricsUpdate() {
        metricsUpdateJob?.cancel()
        metricsUpdateJob = null
    }

    fun handleConnectionStart() {
        updateConnectionStatus("Connected")
        stopButton.isEnabled = true

        // Iniciar actualización de métricas
        startMetricsUpdateJob { true } // O usa un flag para el estado real
    }

    fun resetDisplays() {
        rpmDisplay.text = "_._"
        speedDisplay.text = "_._"
        throttleDisplay.text = "_._"
        maxspeedDisplay.text = "_._"
        fuelDisplay.text = "_._"
        engineLoadDisplay.text = "_._"
        gearDisplay.text = "_._"
    }

    private suspend fun updateMetrics(rpm: String, fuelTrim: String, speed: String, throttle: String, engineLoad: String, gear: String) {
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

    private fun calculateGear(rpm: String, speed: String): Int {
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

    private suspend fun rpm(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askRPM()
        }
    }

    private suspend fun fuelTrim(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askFuelTrimShort()
        }
    }

    private suspend fun speed(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askSpeed()
        }
    }

    private suspend fun throttle(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askThrottlePosition()
        }
    }

    private suspend fun engineLoad(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askEngineLoad()
        }
    }
}