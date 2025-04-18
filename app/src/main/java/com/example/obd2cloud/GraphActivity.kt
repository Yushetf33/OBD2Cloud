package com.example.obd2cloud

import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.ScatterData
import com.github.mikephil.charting.data.ScatterDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import java.io.File

class GraphActivity : AppCompatActivity() {

    private lateinit var rpmChart: LineChart
    private lateinit var speedChart: LineChart
    private lateinit var engineLoadChart: BarChart
    private lateinit var rpmFuelTrimChart: ScatterChart
    private lateinit var fuelTrimChart: BarChart
    private lateinit var throttleChart: LineChart
    private lateinit var gearSpeedChart: ScatterChart


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        rpmChart = findViewById(R.id.rpmChart)
        speedChart = findViewById(R.id.speedChart)
        engineLoadChart = findViewById(R.id.engineLoadChart)
        rpmFuelTrimChart= findViewById(R.id.rpmFuelTrimChart)
        fuelTrimChart = findViewById(R.id.fuelTrimChart)
        throttleChart = findViewById(R.id.throttleChart)
        gearSpeedChart = findViewById(R.id.gearSpeedChart)


        val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        val legendColor = if (!isNightMode) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.black)
        }

        val fileNameJson = intent.getStringExtra("fileNameJson") ?: ""
        val vehicleData = readVehicleData(fileNameJson)

        setupRPMChart(vehicleData, legendColor)
        setupSpeedChart(vehicleData, legendColor)
        setupThrottleChart(vehicleData, legendColor)
        setupEngineLoadChart(vehicleData, legendColor)
        setupRpmFuelTrimChart(vehicleData, legendColor)
        setupFuelTrimChart(vehicleData, legendColor)
        setupGearSpeedChart(vehicleData, legendColor)

    }


    private fun readVehicleData(fileName: String): List<VehicleData> {
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
            val inputData = gson.fromJson(json, InputDataWrapper::class.java).inputData
            if (inputData == null)
                Log.e("GraphActivity", "Error: inputData es NULL")

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
    private fun setupRPMChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val rpmEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            rpmEntries.add(Entry(index.toFloat(), data.rpm.toFloat()))
        }

        val rpmDataSet = LineDataSet(rpmEntries, "RPM")
        rpmDataSet.color = ContextCompat.getColor(this, android.R.color.holo_red_light)
        rpmDataSet.setDrawCircles(false)
        rpmDataSet.setDrawValues(false) // Ocultar los valores en el gráfico

        val lineData = LineData(rpmDataSet)
        rpmChart.data = lineData
        rpmChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        rpmChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
        }
        rpmChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        rpmChart.axisRight.apply {
            textSize = 16f
        }

        rpmChart.xAxis.isEnabled = false
        rpmChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        rpmChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
            textColor = legendColor
        }
        rpmChart.setExtraTopOffset(10f)
    }

    private fun setupThrottleChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val throttleEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            throttleEntries.add(Entry(index.toFloat(), data.throttlePosition))
        }

        val throttleDataSet = LineDataSet(throttleEntries, "Throttle Position (%)")
        throttleDataSet.color = ContextCompat.getColor(this, android.R.color.holo_orange_light)
        throttleDataSet.setDrawCircles(false)
        throttleDataSet.setDrawValues(false)

        val lineData = LineData(throttleDataSet)
        throttleChart.data = lineData
        throttleChart.invalidate()

        throttleChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
        }
        throttleChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        throttleChart.axisRight.apply {
            textSize = 16f
        }
        throttleChart.axisRight.isEnabled = false
        throttleChart.xAxis.isEnabled = false
        throttleChart.legend.apply {
            textSize = 16f
            textColor = legendColor
        }
        throttleChart.setExtraTopOffset(10f)

    }

    private fun setupGearSpeedChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val gearSpeedEntries = ArrayList<Entry>()

        // Añadir puntos al gráfico: (gear, speed)
        vehicleData.forEach { data ->
            gearSpeedEntries.add(Entry(data.gear.toFloat(), data.speed))
        }

        val gearSpeedDataSet = ScatterDataSet(gearSpeedEntries, "Gear vs Speed")
        gearSpeedDataSet.color = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        gearSpeedDataSet.setDrawValues(false)

        val scatterData = ScatterData(gearSpeedDataSet)
        gearSpeedChart.data = scatterData
        gearSpeedChart.invalidate()

        // Configurar eje X con marchas de 1 a 6
        gearSpeedChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
            granularity = 1f  // Forzar pasos de 1
            axisMinimum = 0.5f  // Dejar espacio antes del 1
            axisMaximum = 6.5f  // Dejar espacio después del 6
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val intVal = value.toInt()
                    return if (intVal in 1..6) intVal.toString() else ""
                }
            }
        }

        // Eje Y (velocidad)
        gearSpeedChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }

        gearSpeedChart.axisRight.isEnabled = false

        gearSpeedChart.legend.apply {
            textSize = 16f
            textColor = legendColor
        }

        gearSpeedChart.setExtraTopOffset(10f)
    }



    private fun setupSpeedChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val speedEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { index, data ->
            speedEntries.add(Entry(index.toFloat(), data.speed))
        }

        val speedDataSet = LineDataSet(speedEntries, "Speed")
        speedDataSet.color = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        speedDataSet.setDrawCircles(false)
        speedDataSet.setDrawValues(false)

        val lineData = LineData(speedDataSet)
        speedChart.data = lineData
        speedChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        speedChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
        }
        speedChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        speedChart.axisRight.apply {
            textSize = 16f
        }

        speedChart.xAxis.isEnabled = false
        speedChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        speedChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
            textColor = legendColor
        }
        speedChart.setExtraTopOffset(10f)

    }

    // Gráfico de barras para Engine Load
    private fun setupEngineLoadChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val engineLoadEntries = ArrayList<com.github.mikephil.charting.data.BarEntry>()

        vehicleData.forEachIndexed { index, data ->
            engineLoadEntries.add(com.github.mikephil.charting.data.BarEntry(index.toFloat(), data.engineLoad))
        }

        val engineLoadDataSet = BarDataSet(engineLoadEntries, "Engine Load")
        engineLoadDataSet.color = ContextCompat.getColor(this, android.R.color.holo_green_light)
        engineLoadDataSet.setDrawValues(false)
        val barData = BarData(engineLoadDataSet)
        engineLoadChart.data = barData
        engineLoadChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        engineLoadChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
        }
        engineLoadChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        engineLoadChart.axisRight.apply {
            textSize = 16f
        }

        engineLoadChart.xAxis.isEnabled = false
        engineLoadChart.axisRight.isEnabled = false
        engineLoadChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
            textColor = legendColor
        }
        engineLoadChart.setExtraTopOffset(10f)

    }

    // Gráfico de dispersión para RPM vs Fuel Trim
    private fun setupRpmFuelTrimChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val scatterEntries = ArrayList<Entry>()

        vehicleData.forEachIndexed { _, data ->
            scatterEntries.add(Entry(data.rpm.toFloat(), data.fuelTrim))
        }

        val scatterDataSet = ScatterDataSet(scatterEntries, "RPM vs Fuel Trim")
        scatterDataSet.color = ContextCompat.getColor(this, android.R.color.holo_purple)
        scatterDataSet.setDrawValues(false)  // Desactivar los valores sobre los puntos

        val scatterData = ScatterData(scatterDataSet)
        rpmFuelTrimChart.data = scatterData  // Reutilizar rpmFuelTrimChart para este nuevo gráfico
        rpmFuelTrimChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        rpmFuelTrimChart.xAxis.apply {
            textSize = 14f
            textColor = legendColor
        }
        rpmFuelTrimChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        rpmFuelTrimChart.axisRight.apply {
            textSize = 16f
        }

        rpmFuelTrimChart.xAxis.isEnabled = true  // Habilitar el eje X (RPM)
        rpmFuelTrimChart.axisRight.isEnabled = false  // Deshabilitar el eje Y derecho
        rpmFuelTrimChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
            textColor = legendColor
        }
        rpmFuelTrimChart.setExtraTopOffset(10f)

    }

    // Gráfico de barras para Fuel Trim
    private fun setupFuelTrimChart(vehicleData: List<VehicleData>, legendColor: Int) {
        val fuelTrimEntries = ArrayList<com.github.mikephil.charting.data.BarEntry>()

        vehicleData.forEachIndexed { index, data ->
            fuelTrimEntries.add(com.github.mikephil.charting.data.BarEntry(index.toFloat(), data.fuelTrim))
        }

        val fuelTrimDataSet = BarDataSet(fuelTrimEntries, "Fuel Trim")
        fuelTrimDataSet.color = ContextCompat.getColor(this, android.R.color.holo_blue_light)
        fuelTrimDataSet.setDrawValues(false)

        val barData = BarData(fuelTrimDataSet)
        fuelTrimChart.data = barData
        fuelTrimChart.invalidate()

        // Personalizar el tamaño y color de las etiquetas
        fuelTrimChart.xAxis.apply {
            textSize = 16f
            textColor = legendColor
        }
        fuelTrimChart.axisLeft.apply {
            textSize = 16f
            textColor = legendColor
        }
        fuelTrimChart.axisRight.apply {
            textSize = 16f
        }
        fuelTrimChart.axisRight.isEnabled = false  // Deshabilita el eje Y derecho
        fuelTrimChart.xAxis.isEnabled = false
        fuelTrimChart.legend.apply {
            textSize = 16f  // Tamaño de la fuente
            textColor = legendColor
        }
        fuelTrimChart.setExtraTopOffset(10f)

    }
}
