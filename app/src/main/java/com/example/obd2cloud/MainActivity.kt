package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), View.OnClickListener, SensorEventListener {
    var menu: Menu? = null

    private lateinit var connection_status: TextView
    private lateinit var speed_display: TextView
    private lateinit var RPM_display: TextView
    private lateinit var coolant_display: TextView
    private lateinit var oil_temp_display: TextView
    private lateinit var engine_load_display: TextView
    private lateinit var gyro_display: TextView
    private lateinit var accel_display: TextView

    private var address: String = ""
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothClient: BluetoothClient
    private var connected: Boolean = false
    private var read: Boolean = true
    private var log: Boolean = false
    private lateinit var stop: Button
    private lateinit var file: CsvLog
    private var bt: MenuItem? = null
    private var job: Job? = null

    private lateinit var sensorManager: SensorManager
    private lateinit var gyroscope: Sensor
    private lateinit var accelerometer: Sensor

    private var touchCount: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        connection_status = findViewById(R.id.connection_indicator)
        speed_display = findViewById(R.id.speed_display)
        RPM_display = findViewById(R.id.RPM_display)
        coolant_display = findViewById(R.id.coolant_display)
        oil_temp_display = findViewById(R.id.oil_temp_display)
        engine_load_display = findViewById(R.id.engine_load_display)
        gyro_display = findViewById(R.id.gyro_display)
        accel_display = findViewById(R.id.accel_display)  // Inicializar la variable




        stop = findViewById(R.id.stop)
        stop.setOnClickListener(this)
        stop.isEnabled = false

        connection_status.text = getString(R.string.not_connected)

        // Inicializar sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Registrar listeners de los sensores
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        this.menu = menu
        bt = menu?.findItem(R.id.bt_connect)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.bt_connect -> {
                startActivityForResult(Intent(this@MainActivity, BluetoothActivity::class.java), 1)
                return true
            }
            R.id.rec -> {
                if (!log) {
                    menu?.findItem(R.id.rec)?.title = "Stop logging"
                    try {
                        file = CsvLog(
                            "OBD2Cloud_log_${System.currentTimeMillis() / 1000}.csv",
                            applicationContext
                        )
                        file.makeHeader()
                        log = true
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error: $e", Toast.LENGTH_LONG).show()
                    }
                } else {
                    menu?.findItem(R.id.rec)?.title = "Start logging"
                    log = false
                    Toast.makeText(this, "Stop logging\nFile saved in ${file.path}.", Toast.LENGTH_LONG).show()
                }
            }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connect() {
        if (address.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                    val device: BluetoothDevice = mBluetoothAdapter.getRemoteDevice(address)
                    bluetoothClient = BluetoothClient(device)

                    runOnUiThread {
                        connection_status.text = getString(R.string.connecting, address)
                    }

                    Log.d("BluetoothConnection", "Attempting to connect to $address")

                    connected = bluetoothClient.connect()

                    if (connected) {
                        Log.d("BluetoothConnection", "Connected to $address")
                        read = true
                        display()
                    } else {
                        Log.d("BluetoothConnection", "Failed to connect to $address")
                        runOnUiThread {
                            connection_status.text = getString(R.string.connection_error)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BluetoothConnection", "Connection error: ${e.message}")
                    runOnUiThread {
                        connection_status.text = getString(R.string.connection_error)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun display() {
        if (connected) {
            withContext(Dispatchers.Main) {
                connection_status.text = getString(R.string.connected)
                stop.isEnabled = true
                bt?.isEnabled = false
                bt?.icon?.alpha = 120
            }
            job = CoroutineScope(Dispatchers.IO).launch {
                while (read) {
                    updateUI()
                    delay(200)
                }
            }
        }
    }

    private suspend fun updateUI() {
        val rpmRet = CoroutineScope(Dispatchers.IO).async { RPM() }.await()
        val speedRet = CoroutineScope(Dispatchers.IO).async { speed() }.await()
        val coolantRet = CoroutineScope(Dispatchers.IO).async { coolant() }.await()
        val oilRet = CoroutineScope(Dispatchers.IO).async { oiltemp() }.await()
        val engineLoadRet = CoroutineScope(Dispatchers.IO).async { engineLoad() }.await()

        // Obtén los valores del giroscopio y acelerómetro
        val gyroX = lastGyroX
        val gyroY = lastGyroY
        val gyroZ = lastGyroZ
        val accelX = lastAccelX
        val accelY = lastAccelY
        val accelZ = lastAccelZ

        // Actualiza la UI solo si hay cambios
        if (rpmRet != lastRPMValue) {
            lastRPMValue = rpmRet
            withContext(Dispatchers.Main) {
                RPM_display.text = rpmRet
            }
        }

        if (speedRet != lastSpeedValue) {
            lastSpeedValue = speedRet
            withContext(Dispatchers.Main) {
                speed_display.text = speedRet
            }
        }

        if (coolantRet != lastCoolantValue) {
            lastCoolantValue = coolantRet
            withContext(Dispatchers.Main) {
                coolant_display.text = coolantRet
            }
        }

        if (oilRet != lastOilValue) {
            lastOilValue = oilRet
            withContext(Dispatchers.Main) {
                oil_temp_display.text = oilRet
            }
        }

        if (engineLoadRet != lastEngineLoad) {
            lastEngineLoad = engineLoadRet
            withContext(Dispatchers.Main) {
                engine_load_display.text = engineLoadRet
            }
        }

        if (log) {
            // Agrega los valores de los sensores al archivo CSV
            file.appendRow(
                listOf(
                    (System.currentTimeMillis() / 1000).toString(),
                    rpmRet,
                    speedRet,
                    coolantRet,
                    oilRet,
                    engineLoadRet,
                    gyroX,
                    gyroY,
                    gyroZ,
                    accelX,
                    accelY,
                    accelZ,
                    touchCount.toString()
                )
            )
        }
    }


    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyroX = event.values[0].toString()
                    lastGyroY = event.values[1].toString()
                    lastGyroZ = event.values[2].toString()
                    gyro_display.text = getString(
                        R.string.gyro_values,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccelX = event.values[0].toString()
                    lastAccelY = event.values[1].toString()
                    lastAccelZ = event.values[2].toString()
                    accel_display.text = getString(
                        R.string.accel_values,
                        event.values[0],
                        event.values[1],
                        event.values[2]
                    )
                }
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (log && event?.action == MotionEvent.ACTION_DOWN) {
            touchCount++ // Incrementar contador de toques si está loggeando
        }
        return super.onTouchEvent(event)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.stop -> {
                read = false
                resetDisplays()
                connected = false
                bluetoothClient.disconnect()
                connection_status.text = getString(R.string.not_connected)
                stop.isEnabled = false
                bt?.isEnabled = true
                bt?.icon?.alpha = 255

                touchCount = 0
            }
        }
    }

    private suspend fun RPM(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askRPM()
        }
    }

    private suspend fun speed(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askSpeed()
        }
    }

    private suspend fun coolant(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askCoolantTemp()
        }
    }

    private suspend fun oiltemp(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askOilTemp()
        }
    }

    private suspend fun engineLoad(): String {
        return withContext(Dispatchers.IO) {
            bluetoothClient.askEngineLoad()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val extras = data!!.extras
                address = extras?.getString("device_address").toString()
                Log.e("address", address)
                connection_status.text = getString(R.string.connecting_address, address)
                connect()
            }
        }
    }

    private fun resetDisplays() {
        RPM_display.text = getString(R.string.default_display)
        speed_display.text = getString(R.string.default_display)
        coolant_display.text = getString(R.string.default_display)
        oil_temp_display.text = getString(R.string.default_display)
        engine_load_display.text = getString(R.string.default_display)
        gyro_display.text =getString(R.string.default_display)
        accel_display.text = getString(R.string.default_display)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    companion object {
        var lastRPMValue = ""
        var lastSpeedValue = ""
        var lastCoolantValue = ""
        var lastOilValue = ""
        var lastEngineLoad = ""

        var lastGyroX = ""
        var lastGyroY = ""
        var lastGyroZ = ""
        var lastAccelX = ""
        var lastAccelY = ""
        var lastAccelZ = ""
    }
}