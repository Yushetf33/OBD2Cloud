
@file:Suppress("DEPRECATION")

package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONException
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {
    private var menu: Menu? = null
    private lateinit var speedDisplay: TextView
    private lateinit var maxSpeedDisplay: TextView

    private var address: String = ""
    private lateinit var updateUI: UpdateUI
    private lateinit var metricsManager: MetricsManager
    private lateinit var sensorManagerHelper: SensorManagerHelper
    private lateinit var bluetoothClient: BluetoothClient
    private var connected: Boolean = false
    private var save: Boolean = false
    private var bt: MenuItem? = null
    private var loggingJob: Job? = null


    private lateinit var locationService: LocationService
    private lateinit var openStreetMapService: OpenStreetMapService
    private lateinit var hereMapsService: HereMapsService
    private lateinit var permisoUbicacionLauncher: ActivityResultLauncher<String>
    private val handler = android.os.Handler(Looper.getMainLooper())

    private var velocidadAnterior: String? = null   // Variable para almacenar la velocidad anterior en velocidad maxima de openStreetMaps

    private var touchCount: Int = 0
    private var currentMaxSpeed: String = ""
    private var responseCountMap = mutableMapOf("tranquilo" to 0, "agresiva" to 0, "normal" to 0)


    private var fileName: String = "vehicle_data_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.xlsx" //cambiar extension para json o csv
    private var fileNameJson: String = "vehicle_data_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())}.json" //cambiar extension para json o csv
    private var filePath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        maxSpeedDisplay = findViewById(R.id.maxSpeed_display)
        setupToolbar()
        initializeServices()
        initializePermissionLauncher()
        sensorManagerHelper = SensorManagerHelper(this)
        metricsManager = MetricsManager(this, sensorManagerHelper)
    }

    // Configurar la barra de herramientas
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

    // Inicializar servicios de ubicación y mapas
    private fun initializeServices() {
        locationService = LocationService(this)
        //openStreetMapService = OpenStreetMapService(locationService)
        hereMapsService = HereMapsService(locationService, "j0X98rWu-6X7IndOqpQl5b5pFuwuC8BxM4oQlEWAI_4")
    }

    //Inicializa los permisos
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

                val radioDinamico = calcularRadioPorVelocidad(speedDisplay.toString())

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
                                    maxSpeedDisplay.text = mostFrequentMaxSpeed
                                } else {
                                    maxSpeedDisplay.text = velocidadAnterior ?: "N/A" // Muestra la velocidad anterior si no hay resultados
                                }
                            }

                        } catch (e: JSONException) {
                            Log.e("MainActivity", "Error al parsear la respuesta JSON: ${e.message}")
                        }
                    } else {
                        Log.d("MainActivity", "La respuesta fue nula.")
                        // Muestra la velocidad anterior si la respuesta es nula
                        runOnUiThread {
                            maxSpeedDisplay.text = velocidadAnterior ?: "N/A"
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
        hereMapsService.obtenerMaxSpeedSeguido { maxSpeed ->
            // Actualiza el TextView en el hilo principal
            runOnUiThread {
                maxSpeedDisplay.text = maxSpeed ?: "Error o sin datos"
            }
        }
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
    }

    // Crea el menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        this.menu = menu
        bt = menu?.findItem(R.id.bt_connect)
        return true
    }

    //Gestiona las diferentes opciones del menu
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
                                    currentRPM = findViewById(R.id.RPM_display),
                                    currentFuelTrim = findViewById(R.id.fuel_display),
                                    currentSpeed = findViewById(R.id.speed_display),
                                    currentThrottle = findViewById(R.id.throttle_display),
                                    currentEngineLoad = findViewById(R.id.engine_load_display),
                                    currentMaxSpeed = maxSpeedDisplay,
                                    currentGear = findViewById(R.id.RPM_display),
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

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (save && event?.action == MotionEvent.ACTION_DOWN) {
            touchCount++ // Incrementar contador de toques si está guardando en excel
        }
        return super.onTouchEvent(event)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val extras = data?.extras
            address = extras?.getString("device_address").orEmpty()


            if (BluetoothAdapter.checkBluetoothAddress(address)) {
                val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
                bluetoothClient = BluetoothClient(device)
                updateUI = UpdateUI(this, bluetoothClient, metricsManager)
                updateUI.initializeUI()
                bluetoothClient.connectAndNotify { status ->
                    updateUI.updateConnectionStatus(status)
                    if (status.contains("Connected to")) {
                        connected = true
                        updateUI.handleConnectionStart()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManagerHelper.registerListeners()
    }

    override fun onPause() {
        super.onPause()
        sensorManagerHelper.unregisterListeners()
    }
}
