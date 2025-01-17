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

    private suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (!bluetoothAdapter.isEnabled) {
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
            true
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Connection error: ${e.message}", e)
            false
        }
    }

    fun connectAndNotify(onStatusUpdate: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                onStatusUpdate("Connecting to ${device.address}...")
                val connected = connect() // Ya existente en BluetoothClient
                withContext(Dispatchers.Main) {
                    if (connected) {
                        onStatusUpdate("Connected to ${device.address}")
                    } else {
                        onStatusUpdate("Error al conectar con ${device.address}")
                    }
                }
            } catch (e: Exception) {
                Log.e("BluetoothClient", "Error durante la conexión", e)
                withContext(Dispatchers.Main) {
                    onStatusUpdate("Error durante la conexión: ${e.message}")
                }
            }
        }
    }


    suspend fun askRPM(): String {
        return withContext(Dispatchers.IO) {
            try {
                clearInputStream()
                val aux: ObdResponse = obdConnection.run(RPMCommand(), delayTime = 300)
                if (aux.rawResponse.value.contains("410C")) {
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
                val aux: ObdResponse = obdConnection.run(SpeedCommand(), delayTime = 30)
                if (aux.rawResponse.value.contains("410D")) {
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
                val aux: ObdResponse = obdConnection.run(ThrottlePositionCommand(), delayTime = 30)
                if (aux.rawResponse.value.contains("4111")) {
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
                val aux: ObdResponse = obdConnection.run(LoadCommand(), delayTime = 30)
                if (aux.rawResponse.value.contains("4104")) {
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
                val aux: ObdResponse = obdConnection.run(FuelTrimCommand(FuelTrimCommand.FuelTrimBank.SHORT_TERM_BANK_1), delayTime = 30)

                if (aux.rawResponse.value.contains("4106")) {
                    aux.value
                } else {
                    Log.e("OBD", "Fuel Trim Short response: ${aux.rawResponse}")
                    "!DATA"
                }
            } catch (e: Exception) {
                Log.e("OBD", "Error fetching Fuel Trim Short", e)
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
