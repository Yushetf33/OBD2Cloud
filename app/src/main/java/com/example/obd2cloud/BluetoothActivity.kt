package com.example.obd2cloud

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_ADMIN
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "DeviceListActivity"
    var EXTRA_DEVICE_ADDRESS = "device_address"

    val REQUEST_ENABLE_BT = 1000
    val BLUETOOTH_CODE = 1003
    val LOCATION_REQUEST_CODE = 1005

    private var bluetoothEnabled = false
    private var locationEnabled = false

    private val mReceiver = BluetoothDeviceReceiver()
    private lateinit var mBtAdapter: BluetoothAdapter
    private lateinit var mNewDevicesArrayAdapter: ArrayAdapter<String>

    private lateinit var progress: ProgressBar

    private lateinit var start_bt: Button
    private lateinit var stop_bt: Button

    // Function to check and request permission.
    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkBtPermissions() {
        val requiredPermissions = arrayOf(
            BLUETOOTH,
            BLUETOOTH_ADMIN,
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION,
            BLUETOOTH_CONNECT,
            BLUETOOTH_SCAN
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                BLUETOOTH_CODE
            )
        } else {
            initBluetooth() // Initialize Bluetooth if permissions are already granted
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }

    private fun isLocationEnabled(): Boolean {
        val locationMode: Int = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.LOCATION_MODE,
            Settings.Secure.LOCATION_MODE_OFF
        )
        return locationMode != Settings.Secure.LOCATION_MODE_OFF
    }

    private fun showLocationAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Enable Location")
            setMessage("Please turn on geolocation to use this app.")
            setPositiveButton("Settings") { _, _ ->
                val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(settingsIntent, LOCATION_REQUEST_CODE)
            }
            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(this@BluetoothActivity, MainActivity::class.java))
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun showBluetoothAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.apply {
            setTitle("Enable Bluetooth")
            setMessage("Please turn on Bluetooth to use this app.")
            setPositiveButton("Turn On") { _, _ ->
                val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(bluetoothIntent, REQUEST_ENABLE_BT)
            }
            setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                startActivity(Intent(this@BluetoothActivity, MainActivity::class.java))
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun initBluetooth() {
        // Set result CANCELED in case the user backs out
        setResult(RESULT_CANCELED)
        start_bt.isEnabled = false
        val pairedDevicesArrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)
        mNewDevicesArrayAdapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1)

        // Find and set up the ListView for paired devices
        val pairedListView = findViewById<ListView>(R.id.bt_list_p)
        pairedListView.adapter = pairedDevicesArrayAdapter
        pairedListView.onItemClickListener = mDeviceClickListener

        // Find and set up the ListView for newly discovered devices
        val newDevicesListView = findViewById<ListView>(R.id.bt_list_d)
        newDevicesListView.adapter = mNewDevicesArrayAdapter
        newDevicesListView.onItemClickListener = mDeviceClickListener

        // Register for broadcasts when a device is discovered
        var filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        this.registerReceiver(mReceiver, filter)

        // Register for broadcasts when discovery has finished
        filter = IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        this.registerReceiver(mReceiver, filter)

        // Get a set of currently paired devices
        val pairedDevices = mBtAdapter.bondedDevices

        // If there are paired devices, add each one to the ArrayAdapter
        if (pairedDevices.size > 0) {
            for (device in pairedDevices) {
                pairedDevicesArrayAdapter.add(
                    """
                    ${device.name}
                    ${device.address}
                    """.trimIndent()
                )
            }
        } else {
            pairedDevicesArrayAdapter.add("No device")
        }
        doDiscovery()
    }

    private fun doDiscovery() {
        progress.visibility = View.VISIBLE

        // If we're already discovering, stop it
        if (mBtAdapter.isDiscovering) {
            mBtAdapter.cancelDiscovery()
            progress.visibility = View.GONE
        }

        // Request discover from BluetoothAdapter
        mBtAdapter.startDiscovery()
        start_bt.isEnabled = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        mBtAdapter = bluetoothManager.adapter

        // Check if Bluetooth is enabled
        if (!isBluetoothEnabled()) {
            showBluetoothAlertDialog()
        } else {
            bluetoothEnabled = true
        }

        // Check if geolocation is enabled
        if (!isLocationEnabled()) {
            showLocationAlertDialog()
        } else {
            locationEnabled = true
        }

        // Set the layout
        setContentView(R.layout.activity_bluetooth)
        progress = findViewById<ProgressBar>(R.id.spinner)
        start_bt = findViewById<Button>(R.id.bt_start)
        stop_bt = findViewById<Button>(R.id.bt_stop)
        start_bt.setOnClickListener(this)
        stop_bt.setOnClickListener(this)

        // Check Bluetooth permissions
        checkBtPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Make sure we're not doing discovery anymore
        mBtAdapter.cancelDiscovery()
        // Unregister broadcast listeners
        unregisterReceiver(mReceiver)
    }

    /**
     * The on-click listener for all devices in the ListViews
     */
    private val mDeviceClickListener =
        OnItemClickListener { _, v, _, _ ->

            mBtAdapter.cancelDiscovery()

            // Get the device MAC address
            val info = (v as TextView).text.toString()
            val address = info.substring(info.length - 17)

            // Create the result Intent and include the MAC address
            val intent = Intent()
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address)

            // Set result and finish this Activity
            setResult(RESULT_OK, intent)
            start_bt.isEnabled = true
            finish()
        }

    inner class BluetoothDeviceReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action!!
            when (action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device
                    val device: BluetoothDevice =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)!!
                    mNewDevicesArrayAdapter.add(
                        """
                        ${device.name}
                        ${device.address}
                        """.trimIndent()
                    )
                    mNewDevicesArrayAdapter.notifyDataSetChanged()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    progress.visibility = View.GONE
                    if (mNewDevicesArrayAdapter.count == 0) {
                        mNewDevicesArrayAdapter.add("No device found")
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == BLUETOOTH_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetooth() // Initialize Bluetooth if permissions are granted
            } else {
                // Show a message indicating that permissions were not granted
                AlertDialog.Builder(this)
                    .setTitle("Permissions Required")
                    .setMessage("This app requires Bluetooth and location permissions to function.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.bt_start -> {
                // Start discovery
                doDiscovery()
            }
            R.id.bt_stop -> {
                // Stop discovery
                mBtAdapter.cancelDiscovery()
            }
        }
    }
}
