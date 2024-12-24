package com.example.obd2cloud

import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.google.gson.Gson
import java.io.File

class GraphActivity : AppCompatActivity() {

    private lateinit var rpmChart: LineChart
    private lateinit var speedChart: LineChart
    private lateinit var engineLoadChart: BarChart
    private lateinit var accelRpmChart: ScatterChart
    private lateinit var fuelTrimChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        rpmChart = findViewById(R.id.rpmChart)
        speedChart = findViewById(R.id.speedChart)
        engineLoadChart = findViewById(R.id.engineLoadChart)
        accelRpmChart = findViewById(R.id.accelRpmChart)
        fuelTrimChart = findViewById(R.id.fuelTrimChart)

        val fileNameJson = intent.getStringExtra("fileNameJson") ?: ""
        val vehicleData = readVehicleData(fileNameJson)

        setupRPMChart(vehicleData)
        setupSpeedChart(vehicleData)
        setupEngineLoadChart(vehicleData)
        setupAccelRpmChart(vehicleData)
        setupFuelTrimChart(vehicleData)
    }


    fun readVehicleData(fileName: String): List<VehicleData> {
        val file = File(fileName)
        if (!file.exists()) {
            Log.e("GraphActivity", "Archivo JSON no encontrado: $fileName")
            return emptyList()
        }

        if (file.readText().isEmpty()) {
            Log.e("GraphActivity", "Archivo JSON vacío: $fileName")
            return emptyList()
        }

        return try {
            val json = file.readText()
            val gson = Gson()

            // Parsear el JSON completo
            val inputData = gson.fromJson(json, InputDataWrapper::class.java).input_data

            // Mapear los datos de cada fila a la clase VehicleData
            inputData.data.map { row ->
                VehicleData(
                    touchCount = row[0].toInt(),
                    rpm = row[1].toInt(),
                    fuelTrim = row[2].toFloat(),
                    speed = row[3].toFloat(),
                    throttlePosition = row[4].toFloat(),
                    engineLoad = row[5].toFloat(),
                    maxSpeed = row[6].toFloat(),
                    gear = row[7].toInt(),
                    gyroX = row[8].toFloat(),
                    gyroY = row[9].toFloat(),
                    gyroZ = row[10].toFloat(),
                    accelX = row[11].toFloat(),
                    accelY = row[12].toFloat(),
                    accelZ = row[13].toFloat()
                )
            }
        } catch (e: Exception) {
            Log.e("GraphActivity", "Error al procesar el archivo JSON: ${e.message}")
            emptyList()
        }
    }

    // Gráfico de líneas para RPM
    private fun setupRPMChart(vehicleData: List<VehicleData>) {
        val rpmEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            rpmEntries.add(Entry(index.toFloat(), data.rpm.toFloat()))
        }

        val rpmDataSet = LineDataSet(rpmEntries, "RPM")
        rpmDataSet.color = resources.getColor(android.R.color.holo_red_light)
        rpmDataSet.setDrawCircles(false)
        rpmDataSet.setDrawValues(false) // Ocultar los valores en el gráfico

        val lineData = LineData(rpmDataSet)
        rpmChart.data = lineData
        rpmChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        rpmChart.xAxis.apply {
            textSize = 16f
        }
        rpmChart.axisLeft.apply {
            textSize = 16f
        }
        rpmChart.axisRight.apply {
            textSize = 16f
        }

        rpmChart.xAxis.isEnabled = false
        rpmChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        rpmChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
        }
    }

    private fun setupSpeedChart(vehicleData: List<VehicleData>) {
        val speedEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            speedEntries.add(Entry(index.toFloat(), data.speed))
        }

        val speedDataSet = LineDataSet(speedEntries, "Speed")
        speedDataSet.color = resources.getColor(android.R.color.holo_blue_light)
        speedDataSet.setDrawCircles(false)
        speedDataSet.setDrawValues(false)

        val lineData = LineData(speedDataSet)
        speedChart.data = lineData
        speedChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        speedChart.xAxis.apply {
            textSize = 16f
        }
        speedChart.axisLeft.apply {
            textSize = 16f
        }
        speedChart.axisRight.apply {
            textSize = 16f
        }

        speedChart.xAxis.isEnabled = false
        speedChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        speedChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
        }
    }

    // Gráfico de barras para Engine Load
    private fun setupEngineLoadChart(vehicleData: List<VehicleData>) {
        val engineLoadEntries = ArrayList<com.github.mikephil.charting.data.BarEntry>()

        vehicleData.forEachIndexed { index, data ->
            engineLoadEntries.add(com.github.mikephil.charting.data.BarEntry(index.toFloat(), data.engineLoad))
        }

        val engineLoadDataSet = BarDataSet(engineLoadEntries, "Engine Load")
        engineLoadDataSet.color = resources.getColor(android.R.color.holo_green_light)
        engineLoadDataSet.setDrawValues(false)
        val barData = BarData(engineLoadDataSet)
        engineLoadChart.data = barData
        engineLoadChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        engineLoadChart.xAxis.apply {
            textSize = 16f
        }
        engineLoadChart.axisLeft.apply {
            textSize = 16f
        }
        engineLoadChart.axisRight.apply {
            textSize = 16f
        }

        engineLoadChart.xAxis.isEnabled = false
        engineLoadChart.axisRight.isEnabled = false
        engineLoadChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
        }
    }

    // Gráfico de dispersión para la posición del acelerador vs RPM
    private fun setupAccelRpmChart(vehicleData: List<VehicleData>) {
        val scatterEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            scatterEntries.add(Entry(data.throttlePosition, data.rpm.toFloat()))
        }

        val scatterDataSet = ScatterDataSet(scatterEntries, "Throttle vs RPM")
        scatterDataSet.color = resources.getColor(android.R.color.holo_orange_light)
        scatterDataSet.setDrawValues(false)

        val scatterData = ScatterData(scatterDataSet)
        accelRpmChart.data = scatterData
        accelRpmChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        accelRpmChart.xAxis.apply {
            textSize = 16f
        }
        accelRpmChart.axisLeft.apply {
            textSize = 16f
        }
        accelRpmChart.axisRight.apply {
            textSize = 16f
        }

        accelRpmChart.xAxis.isEnabled = false
        accelRpmChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        accelRpmChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
        }
    }

    // Gráfico de barras para Fuel Trim
    private fun setupFuelTrimChart(vehicleData: List<VehicleData>) {
        val fuelTrimEntries = ArrayList<com.github.mikephil.charting.data.BarEntry>()

        vehicleData.forEachIndexed { index, data ->
            fuelTrimEntries.add(com.github.mikephil.charting.data.BarEntry(index.toFloat(), data.fuelTrim))
        }

        val fuelTrimDataSet = BarDataSet(fuelTrimEntries, "Fuel Trim")
        fuelTrimDataSet.color = resources.getColor(android.R.color.holo_blue_light)
        fuelTrimDataSet.setDrawValues(false)

        val barData = BarData(fuelTrimDataSet)
        fuelTrimChart.data = barData
        fuelTrimChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        fuelTrimChart.xAxis.apply {
            textSize = 16f
        }
        fuelTrimChart.axisLeft.apply {
            textSize = 16f
        }
        fuelTrimChart.axisRight.apply {
            textSize = 16f
        }
        fuelTrimChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        fuelTrimChart.xAxis.isEnabled = false
        fuelTrimChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
        }
    }
}
