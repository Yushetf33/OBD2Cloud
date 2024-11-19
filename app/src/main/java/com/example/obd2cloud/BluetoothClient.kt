package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.github.eltonvs.obd.command.AdaptiveTimingMode
import com.github.eltonvs.obd.command.ObdProtocols
import com.github.eltonvs.obd.command.ObdResponse
import com.github.eltonvs.obd.command.Switcher
import com.github.eltonvs.obd.command.at.*
import com.github.eltonvs.obd.command.engine.LoadCommand
import com.github.eltonvs.obd.command.engine.RPMCommand
import com.github.eltonvs.obd.command.engine.SpeedCommand
import com.github.eltonvs.obd.command.engine.ThrottlePositionCommand
import com.github.eltonvs.obd.command.fuel.FuelTrimCommand
import com.github.eltonvs.obd.connection.ObdDeviceConnection
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothClient(private val device: BluetoothDevice) {

    // Standard UUID for SPP (Serial Port Profile)
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream
    private lateinit var obdConnection: ObdDeviceConnection

    // AT Initialization procedure reversed engineered from "Torque" app
    private suspend fun torqueATInit() {
        obdConnection.run(ResetAdapterCommand(), delayTime = 1000)          // ATZ
        obdConnection.run(SetEchoCommand(Switcher.OFF), delayTime = 500)    // ATE0
        obdConnection.run(SetMemoryCommand(Switcher.OFF), delayTime = 500)  // ATM0
        obdConnection.run(SetLineFeedCommand(Switcher.OFF), delayTime = 500)// ATL0
        obdConnection.run(SetSpacesCommand(Switcher.OFF), delayTime = 500)  // ATS0
        obdConnection.run(DeviceDescriptorCommand(), delayTime = 500)       // AT@1
        obdConnection.run(DeviceInfoCommand(), delayTime = 500)             // ATI
        obdConnection.run(HeadersCommand(Switcher.OFF), delayTime = 500)    // ATH0
        obdConnection.run(SetAdaptiveTimingCommand(AdaptiveTimingMode.AUTO_1), delayTime = 500) // ATH0
        obdConnection.run(DisplayProtoNumberCommand(), delayTime = 500)     // ATH0
        obdConnection.run(SelectProtocolCommand(ObdProtocols.AUTO), delayTime = 500) // ATSP0
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!bluetoothAdapter.isEnabled) {
            Log.e("BluetoothClient", "Bluetooth is not enabled")
            return@withContext false
        }

        return@withContext try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket.connect()
            Log.d("BT connection", "BT socket created: ${bluetoothSocket.isConnected}")
            inputStream = bluetoothSocket.inputStream
            outputStream = bluetoothSocket.outputStream
            obdConnection = ObdDeviceConnection(inputStream, outputStream)
            torqueATInit()
            delay(1000)
            Log.d("OBD connection", "OBD connection established")
            true
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Connection error: ${e.message}", e)
            false
        }
    }

    suspend fun askRPM(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                Log.d("OBD", "Sending RPM command")
                val aux: ObdResponse = obdConnection.run(RPMCommand(), delayTime = 100)
                if (aux.rawResponse.value.contains("410C")) {
                    Log.d("OBD", "RPM Response: ${aux.formattedValue}")
                    aux.value
                } else {
                    Log.e("OBD", "Invalid RPM response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching RPM", e)
                "?NaN"
            }
        }
    }

    suspend fun askSpeed(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                Log.d("OBD", "Sending Speed command")
                val aux: ObdResponse = obdConnection.run(SpeedCommand(), delayTime = 100)
                if (aux.rawResponse.value.contains("410D")) {
                    Log.d("OBD", "Speed Response: ${aux.formattedValue}")
                    aux.value
                } else {
                    Log.e("OBD", "Invalid Speed response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching Speed", e)
                "?NaN"
            }
        }
    }

    suspend fun askThrottlePosition(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                Log.d("OBD", "Sending Throttle Position command")
                val aux: ObdResponse = obdConnection.run(ThrottlePositionCommand(), delayTime = 100)
                if (aux.rawResponse.value.contains("4111")) {
                    Log.d("OBD", "Throttle Position Response: ${aux.formattedValue}")
                    aux.value
                } else {
                    Log.e("OBD", "Invalid Throttle Position response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching Throttle Position", e)
                "?NaN"
            }
        }
    }

    suspend fun askEngineLoad(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                Log.d("OBD", "Sending Engine Load command")
                val aux: ObdResponse = obdConnection.run(LoadCommand(), delayTime = 100)
                if (aux.rawResponse.value.contains("4104")) {
                    Log.d("OBD", "Engine Load Response: ${aux.formattedValue}")
                    aux.value
                } else {
                    Log.e("OBD", "Invalid Engine Load response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching Engine Load", e)
                "?NaN"
            }
        }
    }

    suspend fun askFuelTrimShort(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                Log.d("OBD", "Sending Fuel Trim Short command")
                val aux: ObdResponse = obdConnection.run(FuelTrimCommand(FuelTrimCommand.FuelTrimBank.SHORT_TERM_BANK_1), delayTime = 100)
                Log.d("OBD", "Raw Fuel Trim Response: ${aux.rawResponse.value}")

                if (aux.rawResponse.value.contains("4106")) {
                    Log.d("OBD", "Fuel Trim Short Response: ${aux.formattedValue}")
                    aux.value
                } else {
                    Log.e("OBD", "Fuel Trim Short response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching Mass Air Flow", e)
                "?NaN"
            }
        }
    }

    private fun clearInputStream() {
        try {
            while (inputStream.available() > 0) {
                inputStream.read()
            }
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error clearing input stream", e)
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket.close()
            Log.d("BluetoothClient", "Bluetooth socket closed")
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing socket: ${e.message}", e)
        }
    }
}
