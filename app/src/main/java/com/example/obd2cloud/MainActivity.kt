package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException
import java.io.File
import java.util.Date
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), View.OnClickListener, SensorEventListener {
    var menu: Menu? = null

    private lateinit var connection_status: TextView
    private lateinit var speed_display: TextView
    private lateinit var RPM_display: TextView
    private lateinit var throttle_display: TextView
    private lateinit var maxSpeed_display: TextView
    private lateinit var engine_load_display: TextView
    private lateinit var gyro_display: TextView
    private lateinit var accel_display: TextView
    private lateinit var fuel_display: TextView


    private var address: String = ""
    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothClient: BluetoothClient
    private var connected: Boolean = false
    private var read: Boolean = true
    private var log: Boolean = false
    private lateinit var stop: Button
    private var bt: MenuItem? = null
    private var job: Job? = null

    private lateinit var locationService: LocationService
    private lateinit var openStreetMapService: OpenStreetMapService
    private lateinit var permisoUbicacionLauncher: ActivityResultLauncher<String>
    private val handler = android.os.Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private var velocidadActual: Float? = null
    private var velocidadAnterior: String? = null // Variable para almacenar la velocidad anterior

    private val metricScopes = mutableListOf<Job>()

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null
    val timestamp = System.currentTimeMillis()
    private var touchCount: Int = 0
    private var currentRPM: String = ""
    private var currentFuelTrim: String = ""
    private var currentSpeed: String = ""
    private var currentThrottle: String = ""
    private var currentEngineLoad: String = ""
    private var currentMaxSpeed: String = ""
    private var currentGear: Int? = null // Variable para almacenar la marcha actual
    private var lastGyroX: String = ""
    private var lastGyroY: String = ""
    private var lastGyroZ: String = ""
    private var lastAccelX: String = ""
    private var lastAccelY: String = ""
    private var lastAccelZ: String = ""

    private var fileName: String = "vehicle_data_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.csv"
    private var isHeaderWritten = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        connection_status = findViewById(R.id.connection_indicator)
        speed_display = findViewById(R.id.speed_display)
        RPM_display = findViewById(R.id.RPM_display)
        fuel_display = findViewById(R.id.fuel_display)
        throttle_display = findViewById(R.id.throttle_display)
        maxSpeed_display = findViewById(R.id.maxSpeed_display)
        engine_load_display = findViewById(R.id.engine_load_display)
        gyro_display = findViewById(R.id.gyro_display)
        accel_display = findViewById(R.id.accel_display)

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
        sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        // Inicializar LocationService y OpenStreetMapService
        locationService = LocationService(this)
        openStreetMapService = OpenStreetMapService(locationService)

        // Inicializar el lanzador de permiso
        permisoUbicacionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                obtenerMaxSpeed()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }

        // Solicitar permisos de ubicación
        solicitarPermisos()
        startMaxSpeedLoop()
    }


    private fun startMaxSpeedLoop() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                while (isActive) { // El bucle continuará mientras la coroutine esté activa
                    obtenerMaxSpeed() // Realiza la consulta
                    delay(3000) // Espera 3 segundos
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error en startMaxSpeedLoop: ${e.message}")
            }
        }
    }

    private fun calcularRadioPorVelocidad(velocidad: Float?): Int {
        return when {
            velocidad == null -> 75  // Radio por defecto
            velocidad < 20 -> 30     // Radio menor para velocidades bajas (ej., zonas urbanas)
            velocidad < 50 -> 50     // Radio para velocidad moderada
            velocidad < 80 -> 100    // Aumenta el radio para carreteras principales
            else -> 150              // Máximo radio para carreteras y autopistas
        }
    }

    private fun obtenerMaxSpeed() {
        Log.d("MainActivity", "obtenerMaxSpeed llamada")
        val locationService = LocationService(this)

        // Obtén la ubicación actual
        locationService.obtenerUbicacionActual { location ->
            if (location != null) {
                val currentLatitude = location.latitude
                val currentLongitude = location.longitude

                val radioDinamico = calcularRadioPorVelocidad(velocidadActual)

                // Realiza la solicitud a OpenStreetMap con el radio ajustado
                openStreetMapService.obtenerMaxSpeed(radioDinamico) { resultado ->
                    if (resultado != null) {
                        // Imprime la respuesta de la API en el log
                        //Log.d("MainActivity", "Respuesta de la API: $resultado")

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
                                    maxSpeed_display.text = mostFrequentMaxSpeed
                                    Log.d("MainActivity", "Velocidad máxima: $mostFrequentMaxSpeed")
                                } else {
                                    maxSpeed_display.text = velocidadAnterior ?: "N/A" // Muestra la velocidad anterior si no hay resultados
                                    Log.d("MainActivity", "No se encontraron valores de maxspeed. Mostrando velocidad anterior: ${velocidadAnterior ?: "N/A"}")
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

    override fun onStart() {
        super.onStart()
        startUpdatingMaxSpeed()
    }

    override fun onStop() {
        super.onStop()
        stopUpdatingMaxSpeed()
    }

    private fun startUpdatingMaxSpeed() {
        runnable = object : Runnable {
            override fun run() {
                obtenerMaxSpeed() // Llama a la función para obtener la velocidad máxima
                handler.postDelayed(this, 3000) // Espera 3 segundos antes de volver a ejecutar
            }
        }
        handler.post(runnable) // Inicia el bucle
    }

    private fun stopUpdatingMaxSpeed() {
        handler.removeCallbacks(runnable) // Detiene el bucle
    }

    private fun solicitarPermisos() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permisoUbicacionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            obtenerMaxSpeed()
        }
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
                if (!log) {
                    Log.d("LOGGING", "Start logging clicked")
                    Toast.makeText(this, "Starting logging...", Toast.LENGTH_SHORT).show()
                    menu?.findItem(R.id.rec)?.title = "Stop logging"
                    log = true

                } else {
                    Log.d("LOGGING", "Stop logging clicked")
                    Toast.makeText(this, "Stopping logging...", Toast.LENGTH_SHORT).show()
                    menu?.findItem(R.id.rec)?.title = "Start logging"
                    log = false
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
                if (read)
                    updateMetricsAndLog()
            }
        }
    }

    private fun calculateGear(rpm: String, speed: String): Int? {
        val rpmValue = rpm.toIntOrNull()  // Convertir rpm a Int
        val speedValue = speed.toFloatOrNull()  // Convertir speed a Float

        // Verificar que las conversiones sean válidas
        if (rpmValue == null || speedValue == null || speedValue <= 0) {
            return null // Si alguno es inválido o el vehículo está detenido
        }

        // Calcular las velocidades estimadas para cada marcha a partir de las RPM
        val speeds = listOf(
            rpmValue * 0.0082258415f,
            rpmValue * 0.01588235263f,
            rpmValue * 0.022352938f,
            rpmValue * 0.02941176463f,
            rpmValue * 0.037058823f,
            rpmValue * 0.046470587f
        )

        // Encontrar el valor mínimo de la diferencia entre las velocidades estimadas y la velocidad actual
        val closestSpeed = speeds.minByOrNull { estimatedSpeed -> kotlin.math.abs(estimatedSpeed - speedValue) }

        // Si encontramos una velocidad más cercana, retornamos el índice (más 1 para la marcha)
        return closestSpeed?.let { speeds.indexOf(it) + 1 }
    }

    private suspend fun updateRPM() {
            val rpm = RPM()
            Log.d("RPM Update", "New RPM: $rpm, Last RPM: $lastRPMValue") // Log para depurar
            if (rpm != lastRPMValue) { // Comparar con el valor previo
                lastRPMValue = rpm
                currentRPM = rpm
                withContext(Dispatchers.Main) { // Actualizar la UI en el hilo principal
                    RPM_display.text = rpm
                }
            }
            //delay(500) // Controlar la frecuencia de actualización

    }              //NUEVOS METODOS

    private suspend fun updateFuelTrim() {
         val fuel = fuelTrim()
            Log.d("Fuel Trim Update", "New Fuel Trim: $fuel, Last Fuel Trim: $lastFuelValue") // Log para depurar
            if (fuel != lastFuelValue) {
                lastFuelValue = fuel
                currentFuelTrim = fuel
                withContext(Dispatchers.Main) {
                    fuel_display.text = fuel
                }
            }
            //delay(500) // Ajusta este valor según la frecuencia necesaria
    }         //NUEVOS METODOS

    private suspend fun updateSpeed() {
            val speed = speed()
            Log.d("Speed Update", "New Speed: $speed, Last Speed: $lastSpeedValue") // Log para depurar
            if (speed != lastSpeedValue) {
                lastSpeedValue = speed
                currentSpeed = speed
                withContext(Dispatchers.Main) {
                    speed_display.text = speed
                }
            }
            //delay(500)
    }            //NUEVOS METODOS

    private suspend fun updateThrottle() {
            val throttle = throttle()
            Log.d("Throttle Update", "New Throttle: $throttle, Last Throttle: $lastThrottleValue") // Log para depurar
            if (throttle != lastThrottleValue) {
                lastThrottleValue = throttle
                currentThrottle = throttle
                withContext(Dispatchers.Main) {
                    throttle_display.text = throttle
                }
            }
            //delay(500)
    }         //NUEVOS METODOS

    private suspend fun updateEngineLoad() {
            val engineLoad = engineLoad()
            Log.d("Engine Load Update", "New Engine Load: $engineLoad, Last Engine Load: $lastEngineLoad") // Log para depurar
            if (engineLoad != lastEngineLoad) {
                lastEngineLoad = engineLoad
                currentEngineLoad = engineLoad
                withContext(Dispatchers.Main) {
                    engine_load_display.text = engineLoad
                }
            }
            //delay(500)
    }       //NUEVOS METODOS


    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun updateMetricsAndLog() {
        while (read) {
            updateRPM()
            updateSpeed()
            updateThrottle()
            updateFuelTrim()
            updateEngineLoad()

            currentGear = calculateGear(currentRPM, currentSpeed)

            // Actualizar la interfaz de usuario con la marcha calculada
            withContext(Dispatchers.Main) {
                if (currentGear != null) {
                    Toast.makeText(this@MainActivity, "Marcha actual: $currentGear", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Vehículo detenido", Toast.LENGTH_SHORT).show()
                }
            }

            if(log)
                logMetricsToCSV(fileName)
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

    private fun logMetricsToCSV(fileName: String) {
        val dir = File(getExternalFilesDir(null), "MyAppData")
        Log.d("CSV", "Directory: ${dir.absolutePath}")
        if (!dir.exists()) {
            Log.d("CSV", "Directory doesn't exist, creating directory")
            val dirCreated = dir.mkdirs()
            Log.d("CSV", "Directory created: $dirCreated")
        }

        val file = File(dir, fileName)
        Log.d("CSV", "File: ${file.absolutePath}")

        try {
            if (!file.exists()) {
                Log.d("CSV", "File doesn't exist, creating new file")
                val fileCreated = file.createNewFile()
                Log.d("CSV", "File created: $fileCreated")
            } else {
                Log.d("CSV", "File already exists")
            }

            val writer = FileWriter(file, true)

            if (!isHeaderWritten) {
                Log.d("CSV", "Writing header to CSV file")
                writer.append("Timestamp,Touch Count,RPM,Fuel Trim,Speed,Throttle Position,Engine Load,Max Speed,Gear,Last Gyro X,Last Gyro Y, Last Gyro Z, Last Accel X, Last Accel Y, Last Accel Z\n")
                isHeaderWritten = true
            }

            val timestamp = System.currentTimeMillis().toString()
            Log.d("CSV", "Logging data: $timestamp,$touchCount,$currentRPM,$currentFuelTrim,$currentEngineLoad,$currentSpeed,$currentThrottle,$currentMaxSpeed,$currentGear,$lastGyroX,$lastGyroY,$lastGyroZ,$lastAccelX,$lastAccelY,$lastAccelZ")
            writer.append("$timestamp,$touchCount,$currentRPM,$currentFuelTrim,$currentSpeed,$currentThrottle,$currentEngineLoad,$currentMaxSpeed,$currentGear,$lastGyroX,$lastGyroY,$lastGyroZ,$lastAccelX,$lastAccelY,$lastAccelZ\n")
            writer.close()

            Log.d("CSV", "Data logged successfully")

        } catch (e: IOException) {
            Log.e("CSV", "Error writing to CSV: ${e.message}")
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
                stopUpdatingMetrics()
                touchCount = 0
            }
        }
    }

    private suspend fun RPM(): String {
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
        gyro_display.text = getString(R.string.default_display)
        accel_display.text = getString(R.string.default_display)
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

    companion object {
        var lastRPMValue = ""
        var lastSpeedValue = ""
        var lastThrottleValue = ""
        var lastFuelValue = ""
        var lastEngineLoad = ""

        var lastGyroX = ""
        var lastGyroY = ""
        var lastGyroZ = ""
        var lastAccelX = ""
        var lastAccelY = ""
        var lastAccelZ = ""
    }
}

