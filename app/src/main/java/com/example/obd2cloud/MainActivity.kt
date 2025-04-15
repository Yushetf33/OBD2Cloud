@file:Suppress("DEPRECATION")

package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
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
import org.json.JSONArray
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private var menu: Menu? = null
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
    lateinit var hereMapsService: HereMapsService
    private lateinit var permisoUbicacionLauncher: ActivityResultLauncher<String>
    private val handler = android.os.Handler(Looper.getMainLooper())

    private var touchCount: Int = 0
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

    override fun onStart() {
        super.onStart()
        hereMapsService.obtenerMaxSpeedSeguido { maxSpeed ->
            // Actualiza el TextView en el hilo principal
            runOnUiThread {
                maxSpeedDisplay.text = maxSpeed ?: "0"
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
                if (!save && connected) {
                    updateLoggingMenuItemText(true)
                    loggingJob = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            while (save) {
                                try {
                                    // Obtén y convierte las vistas a valores
                                    val currentRPM = findViewById<TextView>(R.id.RPM_display)?.text.toString().toIntOrNull() ?: 0
                                    val currentFuelTrim = findViewById<TextView>(R.id.fuel_display)?.text.toString().toDoubleOrNull() ?: 0.0
                                    val currentSpeed = findViewById<TextView>(R.id.speed_display)?.text.toString().toIntOrNull() ?: 0
                                    val currentThrottle = findViewById<TextView>(R.id.throttle_display)?.text.toString().toDoubleOrNull() ?: 0.0
                                    val currentEngineLoad = findViewById<TextView>(R.id.engine_load_display)?.text.toString().toDoubleOrNull() ?: 0.0
                                    val currentGear = findViewById<TextView>(R.id.gear_display)?.text.toString().toIntOrNull() ?: 0
                                    val speedDifference = currentSpeed - maxSpeedDisplay.text.toString().toInt()

                                    // Llama al método logMetricsToExcel del metricsManager
                                    metricsManager.logMetricsToExcel(
                                        fileName = fileName,
                                        currentRPM = currentRPM,
                                        currentFuelTrim = currentFuelTrim,
                                        currentSpeed = currentSpeed,
                                        currentThrottle = currentThrottle,
                                        currentEngineLoad = currentEngineLoad,
                                        currentMaxSpeed = maxSpeedDisplay,
                                        currentGear = currentGear,
                                        speedDifference = speedDifference,
                                        touchCount = touchCount
                                    )
                                } catch (e: Exception) {
                                    Log.e("LoggingJob", "Error logging metrics: ${e.message}")
                                }
                                delay(400) // Pausa entre iteraciones
                            }
                        } finally {
                            // Código que se ejecuta al salir del bucle, incluso si la corutina fue cancelada
                            try {
                                Log.d("ARRANQUE JSON", "INICIANDO LLAMADA A LOG JSON")
                                val jsonString = metricsManager.convertExcelToStructuredJson(fileName)
                                filePath = metricsManager.saveJsonToFile(jsonString, fileNameJson)

                                val apiDrivingStyle = ApiDrivingStyle(apiUrl = "https://clasificador-estilos-de-conduccion.onrender.com/predict")
                                val response = apiDrivingStyle.sendPostRequest(filePath)
                                Log.d("DEBUG", "Respuesta cruda de la API: $response")
                                countResponses(response, this@MainActivity)
                                //fileNameJson contiene nombre del fichero con extension
                                //jsonString contiene el contenido del JSON

                                // Pasar datos a PieChartActivity
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(this@MainActivity, PieChartActivity::class.java).apply {
                                        putExtra("tranquilo", responseCountMap["tranquilo"] ?: 0)
                                        putExtra("agresiva", responseCountMap["agresiva"] ?: 0)
                                        putExtra("normal", responseCountMap["normal"] ?: 0)
                                        putExtra("fileNameJson", fileNameJson)
                                    }
                                    startActivity(intent)
                                }
                            } catch (e: Exception) {
                                Log.e("LoggingJob", "Error outside loop: ${e.message}")
                            }
                        }
                    }
                } else {
                    updateLoggingMenuItemText(false)
                    cancelLoggingJob()
                }
                return true
            }

            R.id.show_statistics -> {
                val intent = Intent(this, PieChartActivity::class.java)
                intent.putExtra("tranquilo", responseCountMap["tranquilo"] ?: 0)
                intent.putExtra("agresiva", responseCountMap["agresiva"] ?: 0)
                intent.putExtra("normal", responseCountMap["normal"] ?: 0)
                intent.putExtra("fileNameJson", filePath)

                startActivity(intent)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    fun cancelLoggingJob() {
        loggingJob?.cancel() // Cancelar la corutina
        loggingJob = null
    }

    fun updateLoggingMenuItemText(isLogging: Boolean) {
        val menuItem = menu?.findItem(R.id.rec)
        if (isLogging) {
            menuItem?.title = "Stop logging"
            save = true
        } else {
            menuItem?.title = "Start logging"
            save = false
        }
    }

    private suspend fun countResponses(response: String, context: Context) {
        try {
            // Convertir el String en un array JSON
            val jsonArray = JSONArray(response)

            // Recorrer la lista de respuestas y contar las ocurrencias
            for (i in 0 until jsonArray.length()) {
                val res = jsonArray.getString(i)
                when (res) {
                    "tranquilo" -> responseCountMap["tranquilo"] = responseCountMap.getOrDefault("tranquilo", 0) + 1
                    "agresiva" -> responseCountMap["agresiva"] = responseCountMap.getOrDefault("agresiva", 0) + 1
                    "normal" -> responseCountMap["normal"] = responseCountMap.getOrDefault("normal", 0) + 1
                    else -> Log.e("ApiResponse", "Respuesta no válida: $res")
                }
            }
            Log.d("countResponses", "Conteo de respuestas -> Tranquilo: ${responseCountMap["tranquilo"]}, Agresiva: ${responseCountMap["agresiva"]}, Normal: ${responseCountMap["normal"]}")

        } catch (e: Exception) {
            Log.e("countResponses", "Error procesando la respuesta JSON: ${e.message}")
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
                updateUI = UpdateUI(this, bluetoothClient, this)
                updateUI.initializeUI()
                bluetoothClient.connectAndNotify { status ->
                    updateUI.updateConnectionStatus(status)
                    if (status.contains("Connected to")) {
                        connected = true
                        updateUI.handleConnectionStart()
                    }
                    else
                        connected = false
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
