@file:Suppress("DEPRECATION")

package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import java.text.DecimalFormat
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.Manifest
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.CellType
import org.json.JSONObject
import org.json.JSONException
import java.io.File
import java.util.Date
import java.io.FileWriter
import java.util.Locale
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.FileOutputStream
import java.text.DecimalFormatSymbols

class MainActivity : AppCompatActivity(), View.OnClickListener, SensorEventListener {
    private var menu: Menu? = null
    private lateinit var connection_status: TextView
    private lateinit var speed_display: TextView
    private lateinit var RPM_display: TextView
    private lateinit var throttle_display: TextView
    private lateinit var maxSpeed_display: TextView
    private lateinit var engine_load_display: TextView
    private lateinit var fuel_display: TextView
    private lateinit var gear_display: TextView

    private var address: String = ""
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var updateUI: UpdateUI
    private lateinit var metricsManager: MetricsManager
    private lateinit var bluetoothClient: BluetoothClient
    private var connected: Boolean = false
    private var read: Boolean = true
    private var save: Boolean = false
    private lateinit var stop: Button
    private var bt: MenuItem? = null
    private var job: Job? = null
    private var loggingJob: Job? = null


    private lateinit var locationService: LocationService
    private lateinit var openStreetMapService: OpenStreetMapService
    private lateinit var hereMapsService: HereMapsService
    private lateinit var permisoUbicacionLauncher: ActivityResultLauncher<String>
    private val handler = android.os.Handler(Looper.getMainLooper())

    private var velocidadAnterior: String? = null // Variable para almacenar la velocidad anterior

    private val metricScopes = mutableListOf<Job>()

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    private var touchCount: Int = 0
    private var currentRPM: String = ""
    private var currentFuelTrim: String = ""
    private var currentSpeed: String = ""
    private var currentThrottle: String = ""
    private var currentEngineLoad: String = ""
    private var currentMaxSpeed: String = ""
    private var currentGear: String = ""
    private var currentGyroX: String = ""
    private var currentGyroY: String = ""
    private var currentGyroZ: String = ""
    private var currentAccelX: String = ""
    private var currentAccelY: String = ""
    private var currentAccelZ: String = ""
    private var responseCountMap = mutableMapOf("tranquilo" to 0, "agresiva" to 0, "normal" to 0)


    private var fileName: String = "vehicle_data_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.xlsx" //cambiar extension para json o csv
    private var fileNameJson: String = "vehicle_data_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.json" //cambiar extension para json o csv
    private var filePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupToolbar()
        initializeUI()
        initializeSensors()
        initializeServices()
        initializePermissionLauncher()
        metricsManager = MetricsManager(this)
    }

    // Configurar la barra de herramientas
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    // Inicializar los elementos de la interfaz de usuario
    private fun initializeUI() {
        connection_status = findViewById(R.id.connection_indicator)
        speed_display = findViewById(R.id.speed_display)
        RPM_display = findViewById(R.id.RPM_display)
        fuel_display = findViewById(R.id.fuel_display)
        gear_display = findViewById(R.id.gear_display)
        throttle_display = findViewById(R.id.throttle_display)
        maxSpeed_display = findViewById(R.id.maxSpeed_display)
        engine_load_display = findViewById(R.id.engine_load_display)
        stop = findViewById(R.id.stop)

        stop.setOnClickListener(this)
        stop.isEnabled = false
        connection_status.text = getString(R.string.not_connected)
    }

    // Inicializar y registrar sensores
    private fun initializeSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    // Inicializar servicios de ubicación y mapas
    private fun initializeServices() {
        locationService = LocationService(this)
        openStreetMapService = OpenStreetMapService(locationService)
        hereMapsService = HereMapsService(locationService, "j0X98rWu-6X7IndOqpQl5b5pFuwuC8BxM4oQlEWAI_4")
    }

    private fun initializePermissionLauncher() {
        permisoUbicacionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
                }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    permisoUbicacionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
    }

    private fun calcularRadioPorVelocidad(velocidad: String?): Int {
        val velocidadAux = velocidad?.toIntOrNull()
        return when {
            velocidadAux == null -> 75  // Radio por defecto
            velocidadAux < 20 -> 30     // Radio menor para velocidades bajas (ej., zonas urbanas)
            velocidadAux < 50 -> 50     // Radio para velocidad moderada
            velocidadAux < 80 -> 100    // Aumenta el radio para carreteras principales
            else -> 150              // Máximo radio para carreteras y autopistas
        }
    }

    private fun obtenerMaxSpeed() {
        val locationService = LocationService(this)

        // Obtén la ubicación actual
        locationService.obtenerUbicacionActual { location ->
            if (location != null) {

                val radioDinamico = calcularRadioPorVelocidad(currentSpeed)

                // Realiza la solicitud a OpenStreetMap con el radio ajustado
                openStreetMapService.obtenerMaxSpeed(radioDinamico) { resultado ->
                    if (resultado != null) {

                        try {
                            val jsonObject = JSONObject(resultado)
                            val elementsArray = jsonObject.getJSONArray("elements")

                            // Lista para almacenar los valores de maxspeed encontrados
                            val maxSpeedValues = mutableListOf<String>()

                            for (i in 0 until elementsArray.length()) {
                                val element = elementsArray.getJSONObject(i)
                                val tagsObject = element.optJSONObject("tags")

                                if (tagsObject != null && tagsObject.has("maxspeed")) {
                                    val maxSpeedValue = tagsObject.getString("maxspeed")
                                    maxSpeedValues.add(maxSpeedValue)
                                }
                            }

                            // Encuentra el valor de maxspeed más frecuente
                            val mostFrequentMaxSpeed = maxSpeedValues.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

                            // Actualiza la UI en el hilo principal
                            runOnUiThread {
                                if (mostFrequentMaxSpeed != null) {
                                    velocidadAnterior = mostFrequentMaxSpeed // Actualiza la velocidad anterior
                                    currentMaxSpeed = velocidadAnterior.toString()
                                    maxSpeed_display.text = mostFrequentMaxSpeed
                                } else {
                                    maxSpeed_display.text = velocidadAnterior ?: "N/A" // Muestra la velocidad anterior si no hay resultados
                                }
                            }

                        } catch (e: JSONException) {
                            Log.e("MainActivity", "Error al parsear la respuesta JSON: ${e.message}")
                        }
                    } else {
                        Log.d("MainActivity", "La respuesta fue nula.")
                        // Muestra la velocidad anterior si la respuesta es nula
                        runOnUiThread {
                            maxSpeed_display.text = velocidadAnterior ?: "N/A"
                        }
                    }
                }
            } else {
                Log.d("MainActivity", "No se pudo obtener la ubicación actual.")
            }
        }
    }

    private fun obtenerMaxSpeedFromHereMaps() {
        handler.post(object : Runnable {
            override fun run() {
                hereMapsService.obtenerMaxSpeed { maxSpeed ->
                    runOnUiThread {
                        maxSpeed_display.text = maxSpeed ?: "Error o sin datos"
                    }
                }
                handler.postDelayed(this, 5000) // Repite cada 5 segundos
            }
        })
    }


    override fun onStart() {
        super.onStart()
        obtenerMaxSpeedFromHereMaps()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
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
                startActivityForResult(
                    Intent(this@MainActivity, BluetoothActivity::class.java), 1
                )
                return true
            }

            R.id.rec -> {
                if (!save) {
                    Log.d("LOGGING", "Start logging clicked")
                    Toast.makeText(this, "Starting logging...", Toast.LENGTH_SHORT).show()
                    menu?.findItem(R.id.rec)?.title = "Stop logging"
                    save = true

                    // Iniciar la corutina para guardar los datos
                    loggingJob = CoroutineScope(Dispatchers.IO).launch {
                        Log.d("LoggingJob", "Started logging metrics")
                        while (save) {
                            try {
                                metricsManager.logMetricsToExcel(
                                    fileName = fileName,
                                    currentRPM = RPM_display,
                                    currentFuelTrim = fuel_display,
                                    currentSpeed = speed_display,
                                    currentThrottle = throttle_display,
                                    currentEngineLoad = engine_load_display,
                                    currentMaxSpeed = maxSpeed_display,
                                    currentGear = gear_display,
                                    currentGyroX = currentGyroX,
                                    currentGyroY = currentGyroY,
                                    currentGyroZ = currentGyroZ,
                                    currentAccelX = currentAccelX,
                                    currentAccelY = currentAccelY,
                                    currentAccelZ = currentAccelZ,
                                    touchCount = touchCount
                                )
                            } catch (e: Exception) {
                                Log.e("LoggingJob", "Error in logMetricsToExcel: ${e.message}")
                            }
                            delay(400)
                            // Si deseas convertir a JSON después
                            //val json = metricsManager.convertExcelToStructuredJson(fileName)
                            //filePath = metricsManager.saveJsonToFile(json, fileNameJson)
                            //Log.d("MetricsManager", "JSON saved at: ${filePath}")
                        }
                        Log.d("LoggingJob", "Logging job stopped")
                    }

                } else {
                    Log.d("LOGGING", "Stop logging clicked")
                    Toast.makeText(this, "Stopping logging...", Toast.LENGTH_SHORT).show()
                    menu?.findItem(R.id.rec)?.title = "Start logging"
                    save = false

                    // Detener la corutina de guardado
                    loggingJob?.cancel() // Cancelar la corutina
                    loggingJob = null
                }
            }


            R.id.show_statistics -> {
                // Iniciar la actividad de gráficos
                val intent = Intent(this, PieChartActivity::class.java)
                intent.putExtra("tranquilo", responseCountMap["tranquilo"] ?: 0)
                intent.putExtra("agresiva", responseCountMap["agresiva"] ?: 0)
                intent.putExtra("normal", responseCountMap["normal"] ?: 0)
                intent.putExtra("fileNameJson", filePath)

                startActivity(intent)
                return true
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

                updateUI = UpdateUI(
                        rpmDisplay = RPM_display,
                        fuelDisplay = fuel_display,
                        speedDisplay = speed_display,
                        throttleDisplay = throttle_display,
                        engineLoadDisplay = engine_load_display,
                        gearDisplay = gear_display,
                        bluetoothClient = bluetoothClient
                    )
                    updateUI.startMetricsUpdateJob { read }
            }
        }
    }


    suspend fun countResponses(response: String, responseCountMap: MutableMap<String, Int>) {
        // Dividir el String de respuestas en una lista de palabras
        val responseList = response.split(",").map { it.trim().trim('"') }

        // Recorrer la lista de respuestas y contar las ocurrencias
        for (res in responseList) {
            when (res) {
                "tranquilo" -> responseCountMap["tranquilo"] = responseCountMap.getOrDefault("tranquilo", 0) + 1
                "agresiva" -> responseCountMap["agresiva"] = responseCountMap.getOrDefault("agresiva", 0) + 1
                "normal" -> responseCountMap["normal"] = responseCountMap.getOrDefault("normal", 0) + 1
                else -> Log.e("ApiResponse", "Respuesta no válida: $res")
            }
        }

        // Obtener los resultados desde el mapa de respuestas
        val tranquiloCount = responseCountMap["tranquilo"] ?: 0
        val agresivaCount = responseCountMap["agresiva"] ?: 0
        val normalCount = responseCountMap["normal"] ?: 0

        // Mostrar los resultados en Toast
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, "Tranquilo: $tranquiloCount", Toast.LENGTH_SHORT).show()
            Toast.makeText(applicationContext, "Agresiva: $agresivaCount", Toast.LENGTH_SHORT).show()
            Toast.makeText(applicationContext, "Normal: $normalCount", Toast.LENGTH_SHORT).show()
        }
    }


    private fun stopUpdatingMetrics() {
        // Detener cada métrica y cancelar sus jobs
        metricScopes.forEach { it.cancel() }
        metricScopes.clear() // Limpiar la lista de métricas activas
        connected = false // Marca como desconectado
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar todas las coroutines cuando la actividad se destruya
        metricScopes.forEach { it.cancel() }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {
                    currentGyroX = event.values[0].toString()
                    currentGyroY = event.values[1].toString()
                    currentGyroZ = event.values[2].toString()
                }

                Sensor.TYPE_ACCELEROMETER -> {
                    currentAccelX = event.values[0].toString()
                    currentAccelY = event.values[1].toString()
                    currentAccelZ = event.values[2].toString()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (save && event?.action == MotionEvent.ACTION_DOWN) {
            touchCount++ // Incrementar contador de toques si está guardando en excel
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
                stopUpdatingMetrics()
                touchCount = 0
            }
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
        throttle_display.text = getString(R.string.default_display)
        maxSpeed_display.text = getString(R.string.default_display)
        fuel_display.text = getString(R.string.default_display)
        engine_load_display.text = getString(R.string.default_display)
        gear_display.text = getString(R.string.default_display)
    }

    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}

