package com.example.obd2cloud

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.obd2cloud.R
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), View.OnClickListener {
    //used for dynamically change menu entry title
    var menu: Menu? = null

    //displays gauges
    private lateinit var connection_status: TextView
    private lateinit var speed_display: TextView
    private lateinit var RPM_display: TextView
    private lateinit var coolant_display: TextView
    private lateinit var oil_temp_display: TextView
    private lateinit var engine_load_display: TextView

    private var address: String = ""

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothClient: BluetoothClient

    private var lastRPMValue: String = ""
    private var lastSpeedValue: String = ""
    private var lastOilValue: String = ""
    private var lastCoolantValue: String = ""
    private var lastEngineLoad: String = ""

    private var connected: Boolean = false
    private var read: Boolean = true
    private var log: Boolean = false

    private lateinit var stop: Button
    private lateinit var file: CsvLog
    private var bt: MenuItem? = null

    private var job: Job? = null

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

        stop = findViewById<Button>(R.id.stop)
        stop.setOnClickListener(this)
        stop.isEnabled = false

        connection_status.text = getString(R.string.not_connected)
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
                    Intent(this@MainActivity, BluetoothActivity::class.java),
                    1
                )
                return true
            }
            R.id.rec -> {
                if (!log) {
                    menu?.findItem(R.id.rec)?.title = "Stop logging"
                    try {
                        file = CsvLog(
                            "OBDOBC_log_${System.currentTimeMillis() / 1000}.csv",
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
                    Toast.makeText(
                        this, "Stop logging\nFile saved in ${file.path}.",
                        Toast.LENGTH_LONG
                    ).show()
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
                    e.printStackTrace()
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
        // Fetch all parameters concurrently using async
        val rpmRet = CoroutineScope(Dispatchers.IO).async { RPM() }.await()
        val speedRet = CoroutineScope(Dispatchers.IO).async { speed() }.await()
        val coolantRet = CoroutineScope(Dispatchers.IO).async { coolant() }.await()
        val oilRet = CoroutineScope(Dispatchers.IO).async { oiltemp() }.await()
        val engineLoadRet = CoroutineScope(Dispatchers.IO).async { engineLoad() }.await()

        // Update UI only if the values have changed
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
            file.appendRow(
                listOf(
                    (System.currentTimeMillis() / 1000).toString(),
                    rpmRet,
                    speedRet,
                    coolantRet,
                    oilRet,
                    engineLoadRet
                )
            )
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
        lastRPMValue = ""
        lastSpeedValue = ""
        lastCoolantValue = ""
        lastOilValue = ""
        lastEngineLoad = ""
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
            }
        }
    }
}
