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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
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
    private lateinit var bluetoothClient: BluetoothClient
    private var connected: Boolean = false
    private var read: Boolean = true
    private var log: Boolean = false
    private lateinit var stop: Button
    private var bt: MenuItem? = null
    private var job: Job? = null

    private lateinit var locationService: LocationService
    private lateinit var openStreetMapService: OpenStreetMapService
    private lateinit var hereMapsService: HereMapsService
    private lateinit var permisoUbicacionLauncher: ActivityResultLauncher<String>
    private val handler = android.os.Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

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
    private var currentGear: Int? = null // Variable para almacenar la marcha actual
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
                solicitarPermisos()
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
        hereMapsService.obtenerMaxSpeed { responseData ->
            runOnUiThread {
                if (responseData != null) {
                    val maxSpeed = parsearMaxSpeed(responseData)
                    maxSpeed_display.text = maxSpeed ?: "Sin datos"
                } else {
                    maxSpeed_display.text = "Error al obtener datos"
                }
            }
        }
    }

    private fun parsearMaxSpeed(jsonResponse: String): String? {
        return try {
            val jsonObject = JSONObject(jsonResponse)
            val routeArray = jsonObject.getJSONObject("response").getJSONArray("route")

            if (routeArray.length() > 0) {
                val route = routeArray.getJSONObject(0)
                val legArray = route.getJSONArray("leg")
                val leg = legArray.getJSONObject(0)

                if (leg.has("link")) {
                    val linkArray = leg.getJSONArray("link")
                    val link = linkArray.getJSONObject(0)
                    if (link.has("attributes")) {
                        val attributes = link.getJSONObject("attributes")
                        if (attributes.has("SPEED_LIMITS_FCN")) {
                            val speedLimits = attributes.getJSONArray("SPEED_LIMITS_FCN")
                            for (i in 0 until speedLimits.length()) {
                                val speedLimitObj = speedLimits.getJSONObject(i)
                                val toSpeed = speedLimitObj.getString("TO_REF_SPEED_LIMIT")

                                if (toSpeed != "0") {
                                    currentMaxSpeed = toSpeed
                                    return toSpeed
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e("MainActivity", "No se encontraron rutas en la respuesta.")
            }
            null
        } catch (e: JSONException) {
            Log.e("MainActivity", "Error al parsear la respuesta JSON: ${e.message}")
            null
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
                obtenerMaxSpeedFromHereMaps() // Llama a la función para obtener la velocidad máxima
                handler.postDelayed(this, 5000) // Espera 5 segundos antes de volver a ejecutar
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
                currentGear = 0
                gear_display.text = currentGear.toString()
                return currentGear // Si alguno es inválido o el vehículo está detenido
            }

            // Calcular las velocidades estimadas para cada marcha a partir de las RPM
            val speeds = listOf(
                rpmValue * 0.007368421f,
                rpmValue * 0.014736842f,
                rpmValue * 0.019607844f,
                rpmValue * 0.026041666f,
                rpmValue * 0.0327529f,
                rpmValue * 0.04043478f
            )

            // Encontrar el valor mínimo de la diferencia entre las velocidades estimadas y la velocidad actual
            val closestSpeed =
                speeds.minByOrNull { estimatedSpeed -> kotlin.math.abs(estimatedSpeed - speedValue) }
            currentGear = closestSpeed?.let { speeds.indexOf(it) + 1 }
            gear_display.text = currentGear.toString()
            return currentGear
    }

    private suspend fun updateRPM() {
        currentRPM = RPM()
        withContext(Dispatchers.Main) { // Actualizar la UI en el hilo principal
                RPM_display.text = currentRPM
            }
    }

    private suspend fun updateFuelTrim() {
        currentFuelTrim = fuelTrim()
        withContext(Dispatchers.Main) {
            fuel_display.text = currentFuelTrim
        }
    }

    private suspend fun updateSpeed() {
        currentSpeed = speed()
        withContext(Dispatchers.Main) {
            speed_display.text = currentSpeed
        }
    }

    private suspend fun updateThrottle() {
        currentThrottle = throttle()
        withContext(Dispatchers.Main) {
            throttle_display.text = currentThrottle
        }
    }

    private suspend fun updateEngineLoad() {
        currentEngineLoad = engineLoad()
        withContext(Dispatchers.Main) {
            engine_load_display.text = currentEngineLoad
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun updateMetricsAndLog() {
        // Iniciar las tareas de actualización de métricas y el registro de datos
        val updateJob = launchMetricsUpdateJob()
        val logJob = launchLoggingJob()

        // Esperar a que ambas tareas finalicen
        updateJob.join()
        logJob.join()

        val json = withContext(Dispatchers.IO) {
            convertExcelToStructuredJson(fileName)
        }

        processApiResponse(json, fileNameJson)
    }

    suspend fun processApiResponse(json: String, fileNameJson: String) {
        if (json.isNotEmpty()) {
            // Guardar el JSON en un archivo
            filePath = saveJsonToFile(json, fileNameJson)
            Log.d("MainActivity", "Archivo JSON guardado en: $filePath")
            try {
                val api = ApiDrivingStyle(
                    "8mxnjCT74badfewKW4JtGZbs39skW3BD",
                    "https://recomendacionesdeconducci-stscl.spaincentral.inference.ml.azure.com/score"
                )
                val result = api.sendPostRequest(filePath)

                // Contar las respuestas
                countResponses(result, responseCountMap)

            } catch (e: Exception) {
                // Manejar errores de la solicitud
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Error en la solicitud: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            Log.e("ExcelToJson", "Hubo un error al convertir el Excel a JSON.")
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

    private fun launchMetricsUpdateJob(): Job {
        return lifecycleScope.launch {
            // Lanzar calculateGear en una corutina separada para que no bloquee el flujo
            launch {
                while (read) {
                    calculateGear(currentRPM, currentSpeed)  // Mantiene el cálculo de marcha en segundo plano
                    delay(100) // Controla la frecuencia del cálculo de marcha (ajustable según necesidades)
                }
            }

            while (read) {
                // Actualizar métricas de alta prioridad
                updateRPM()
                updateSpeed()

                // Actualizar métricas de baja prioridad periódicamente
                updateThrottle()
                updateFuelTrim()

                updateRPM()
                updateSpeed()

                updateEngineLoad()

                delay(50) // Controla la frecuencia de actualización de las métricas
            }
        }
    }

    private fun launchLoggingJob(): Job {
        return lifecycleScope.launch {
            while (read) {
                if (log) {
                    logMetricsToExcel(fileName) // Llamar a logMetricsToExcel  manera independiente
                }
                delay(300) // Controla la frecuencia de registro
            }
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

    private fun logMetricsToExcel(fileName: String) {
        val gearAux = currentGear.toString()
        val column22 = "0"
        val dir = File(getExternalFilesDir(null), "MyAppData")
        if (!dir.exists()) {
            Log.d("Excel", "Directory doesn't exist, creating directory")
            val dirCreated = dir.mkdirs()
            Log.d("Excel", "Directory created: $dirCreated")
        }

        val file = File(dir, fileName)

        try {
            val df = DecimalFormat("#.######", DecimalFormatSymbols(Locale.US)) // Limitar a 6 decimales

            val gyroX = currentGyroX?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val gyroY = currentGyroY?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val gyroZ = currentGyroZ?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelX = currentAccelX?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelY = currentAccelY?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelZ = currentAccelZ?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            // Crear o cargar el archivo Excel
            val workbook: Workbook = if (file.exists()) {
                XSSFWorkbook(file.inputStream()) // Cargar el archivo existente
            } else {
                XSSFWorkbook() // Crear un nuevo archivo
            }

            // Obtener o crear la hoja de cálculo
            val sheet = workbook.getSheet("Metrics") ?: workbook.createSheet("Metrics")

            // Crear encabezados si no existen
            if (sheet.lastRowNum == 0 || sheet.getRow(0) == null) {
                val headerRow = sheet.createRow(0)
                //headerRow.createCell(0).setCellValue("Current Time")
                headerRow.createCell(0).setCellValue("Touch Count")
                headerRow.createCell(1).setCellValue("RPM")
                headerRow.createCell(2).setCellValue("Fuel Trim")
                headerRow.createCell(3).setCellValue("Speed")
                headerRow.createCell(4).setCellValue("Throttle Position")
                headerRow.createCell(5).setCellValue("Engine Load")
                headerRow.createCell(6).setCellValue("Max Speed")
                headerRow.createCell(7).setCellValue("Gear")
                headerRow.createCell(8).setCellValue("Gyro X")
                headerRow.createCell(9).setCellValue("Gyro Y")
                headerRow.createCell(10).setCellValue("Gyro Z")
                headerRow.createCell(11).setCellValue("Accel X")
                headerRow.createCell(12).setCellValue("Accel Y")
                headerRow.createCell(13).setCellValue("Accel Z")
                headerRow.createCell(14).setCellValue("Column22")
            }

            // Crear nueva fila para los datos
            val currentRow = sheet.createRow(sheet.lastRowNum + 1)
            //val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            // Rellenar las celdas con los datos
            //currentRow.createCell(0).setCellValue(currentTime)
            currentRow.createCell(0).setCellValue(touchCount.toDouble())
            currentRow.createCell(1).setCellValue(currentRPM)
            currentRow.createCell(2).setCellValue(currentFuelTrim)
            currentRow.createCell(3).setCellValue(currentSpeed)
            currentRow.createCell(4).setCellValue(currentThrottle)
            currentRow.createCell(5).setCellValue(currentEngineLoad)
            currentRow.createCell(6).setCellValue(currentMaxSpeed)
            currentRow.createCell(7).setCellValue(gearAux)
            currentRow.createCell(8).setCellValue(df.format(gyroX).toDouble())
            currentRow.createCell(9).setCellValue(df.format(gyroY).toDouble())
            currentRow.createCell(10).setCellValue(df.format(gyroZ).toDouble())
            currentRow.createCell(11).setCellValue(df.format(accelX).toDouble())
            currentRow.createCell(12).setCellValue(df.format(accelY).toDouble())
            currentRow.createCell(13).setCellValue(df.format(accelZ).toDouble())
            currentRow.createCell(14).setCellValue(column22)


            // Guardar el archivo Excel
            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()


        } catch (e: Exception) {
            Log.e("Excel", "Error writing to Excel: ${e.message}")
        }
    }

    private fun convertExcelToStructuredJson(excelFileName: String): String {
        val dir = File(getExternalFilesDir(null), "MyAppData")
        val excelFile = File(dir, excelFileName)

        try {
            if (!excelFile.exists()) {
                Log.e("Convert", "Excel file does not exist: ${excelFile.absolutePath}")
                return ""
            }

            // Cargar el archivo Excel
            val workbook = XSSFWorkbook(excelFile.inputStream())
            val sheet = workbook.getSheet("Metrics") ?: run {
                Log.e("Convert", "Sheet 'Metrics' not found in Excel file")
                return ""
            }

            // Leer encabezados de la primera fila
            val headers = sheet.getRow(0)?.map { it.stringCellValue } ?: emptyList()

            // Crear listas para almacenar los datos
            val data = mutableListOf<List<Any>>()
            val index = mutableListOf<Int>()

            // Iterar sobre las filas, empezando desde la segunda fila (índice 1)
            for (rowIndex in 1..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex)
                val rowData = mutableListOf<Any>()

                headers.forEachIndexed { colIndex, header ->
                    val cell = row.getCell(colIndex)
                    val value = when (cell?.cellType) {
                        CellType.NUMERIC -> cell.numericCellValue
                        CellType.STRING -> {
                            val stringValue = cell.stringCellValue
                            // Verificar si el valor es "NaN" y manejarlo
                            if (stringValue.equals("NaN", ignoreCase = true)) {
                                ""  // O cualquier valor que desees usar en lugar de "NaN"
                            } else if (stringValue.contains(",")) {
                                // Reemplazar comas por puntos en números representados como cadenas
                                val modifiedValue = stringValue.replace(",", ".")
                                try {
                                    modifiedValue.toDouble()
                                } catch (e: NumberFormatException) {
                                    stringValue // Si no se puede convertir, dejar el valor original
                                }
                            } else {
                                stringValue
                            }
                        }
                        CellType.BOOLEAN -> cell.booleanCellValue
                        else -> null
                    }
                    rowData.add(value ?: "")
                }

                // Agregar la fila a los datos y el índice
                data.add(rowData)
                index.add(rowIndex)
            }

            // Crear el JSON con las columnas, los índices y los datos
            val jsonStructure = mapOf(
                "input_data" to mapOf(
                    "columns" to headers,
                    "index" to index,
                    "data" to data
                )
            )

            // Convertir a JSON utilizando Gson
            val gson = com.google.gson.GsonBuilder()
                .disableHtmlEscaping()
                .serializeNulls()
                .setPrettyPrinting()
                .create()
            var jsonString = gson.toJson(jsonStructure)

            workbook.close()
            return jsonString
        } catch (e: Exception) {
            Log.e("Convert", "Error converting Excel to JSON: ${e.message}")
            return ""
        }
    }

    private fun saveJsonToFile(json: String, fileName: String): String {
        return try {
            val dir = File(getExternalFilesDir(null), "MyAppData")
            if (!dir.exists()) {
                dir.mkdir() // Crear directorio si no existe
            }

            val jsonFile = File(dir, fileName)  // Aquí se asigna el nombre del archivo
            val writer = FileWriter(jsonFile)
            writer.write(json)
            writer.flush()
            writer.close()

            jsonFile.absolutePath // Retorna la ruta completa del archivo
        } catch (e: Exception) {
            Log.e("SaveJson", "Error al guardar JSON: ${e.message}")
            ""
        }
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

