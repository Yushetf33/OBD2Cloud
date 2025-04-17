package com.example.obd2cloud

import android.content.Intent
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.gson.Gson
import java.io.File

class PieChartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pie_chart)

        // Detectar el modo oscuro o claro
        val isNightMode = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        val textColor = if (!isNightMode) {
            ContextCompat.getColor(this, R.color.white)
        } else {
            ContextCompat.getColor(this, R.color.black)
        }

        // Obtener los valores enviados desde MainActivity
        val tranquiloCount = intent.getIntExtra("tranquilo", 0)
        val agresivoCount = intent.getIntExtra("agresiva", 0)
        val normalCount = intent.getIntExtra("normal", 0)
        val fileNameJson = intent.getStringExtra("fileNameJson") ?: ""
        val total = tranquiloCount + agresivoCount + normalCount

        val button: Button = findViewById(R.id.button)

        // Verificar si hay datos antes de mostrar el botón
        if (total == 0) {
            button.visibility = View.GONE // Ocultar el botón si no hay datos
            val consejoTextView: TextView = findViewById(R.id.consejoTextView)
            consejoTextView.text = "" // No mostrar consejo si no hay datos
            return // Salir del método sin continuar con el gráfico y los consejos
        } else {
            button.visibility = View.VISIBLE // Mostrar el botón si hay datos
        }

        button.setOnClickListener {
            // Crear Intent para abrir GraphActivity
            val intent = Intent(this, GraphActivity::class.java)
            intent.putExtra("fileNameJson", fileNameJson)

            startActivity(intent)
        }


        // Verificar si el total es cero, es decir, si no hay datos
        if (total == 0) {
            val consejoTextView: TextView = findViewById(R.id.consejoTextView)
            consejoTextView.text = "" // No mostrar consejo si no hay datos
            return // Salir del método sin continuar con el gráfico y los consejos
        }

        val tranquiloPercentage = if (total > 0) (tranquiloCount.toFloat() / total * 100) else 0f
        val agresivoPercentage = if (total > 0) (agresivoCount.toFloat() / total * 100) else 0f
        val normalPercentage = if (total > 0) (normalCount.toFloat() / total * 100) else 0f

        // Cargar los datos del archivo JSON
        val vehicleData = readVehicleData(fileNameJson)

       // Log.d("PieChartActivity", "Archivo JSON: $fileNameJson")
        //Log.d("PieChartActivity", "Datos cargados: ${vehicleData.size} registros")

        // Configurar el gráfico circular
        val pieChart: PieChart = findViewById(R.id.pieChart)
        pieChart.setEntryLabelColor(textColor)
        pieChart.legend.textColor = textColor

        // Crear las entradas para el gráfico
        val entries = mutableListOf<PieEntry>()

        if (tranquiloPercentage > 0) entries.add(PieEntry(tranquiloPercentage, "Tranquilo"))
        if (agresivoPercentage > 0) entries.add(PieEntry(agresivoPercentage, "Agresiva"))
        if (normalPercentage > 0) entries.add(PieEntry(normalPercentage, "Normal"))

        // Configurar el dataset
        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.COLORFUL_COLORS.toList()

        // Configurar los datos
        val pieData = PieData(dataSet)
        pieChart.data = pieData

        // Personalizar el tamaño de la letra para las etiquetas
        dataSet.valueTextSize = 16f  // Tamaño de la letra de los valores en el gráfico
        dataSet.sliceSpace = 3f      // Espacio entre los segmentos del gráfico

        // Personalizar la leyenda
        val legend: Legend = pieChart.legend
        legend.textSize = 18f  // Tamaño de la letra de la leyenda

        // Configurar los márgenes de la leyenda (con espacio entre la leyenda y el gráfico)
        legend.xEntrySpace = 20f  // Espacio entre los elementos de la leyenda
        legend.yEntrySpace = 10f  // Espacio entre las filas de la leyenda
        legend.formToTextSpace = 10f  // Espacio entre el símbolo de la leyenda y el texto
        legend.formSize = 14f // Tamaño de los cuadros de la leyenda



        // Actualizar el gráfico
        pieChart.invalidate()

        val estiloPredominante = when {
            tranquiloPercentage > agresivoPercentage && tranquiloPercentage > normalPercentage -> "tranquilo"
            agresivoPercentage > tranquiloPercentage && agresivoPercentage > normalPercentage -> "agresivo"
            else -> "normal"
        }

        val advice = generateCustomAdvice(vehicleData, estiloPredominante)
        val consejoTextView: TextView = findViewById(R.id.consejoTextView)
        consejoTextView.text = advice
    }

    private fun generateCustomAdvice(vehicleData: List<VehicleData>, estiloPredominante: String): String {
        val averageSpeed = vehicleData.map { it.speed }.average()
        val averageRpm = vehicleData.map { it.rpm }.average()
        val averageFuelTrim = vehicleData.map { it.fuelTrim }.average()
        val averageThrottlePosition = vehicleData.map { it.throttlePosition }.average()
        val averageEngineLoad = vehicleData.map { it.engineLoad }.average()
        val averageGear = vehicleData.map { it.gear }.average()


        // Consejos básicos basados en el estilo de conducción
        var advice = ""

        when (estiloPredominante) {
            "tranquilo" -> {
                advice = "¡Excelente! Tu estilo de conducción es tranquilo y seguro. "
            }

            "agresivo" -> {
                advice = "¡Cuidado! Tu estilo de conducción es agresivo. "
                if (averageRpm > 3000) {
                    advice += "\nIntenta reducir las revoluciones del motor para evitar un consumo excesivo de combustible."
                }
                if (averageFuelTrim > 4.8) {
                    advice += "\nTu mezcla de combustible puede estar desequilibrada. Trata de conducir de manera más suave para mejorar la eficiencia."
                }
            }

            "normal" -> {
                advice = "Tu estilo de conducción es bastante equilibrado. "
                if (averageRpm > 2500) {
                    advice += "\nEvita mantener el motor a altas revoluciones durante largos periodos para cuidar la vida útil del motor."
                }
            }
        }

        // Consejos adicionales basados en otros parámetros
        if (averageSpeed > 100) {
            advice += "\n¡Atención! Estás conduciendo a alta velocidad. Redúcela para mejorar la seguridad."
        }

        if (averageFuelTrim > 0.8) {
            advice += "\nTu eficiencia de combustible podría mejorar. Intenta acelerar suavemente."
        }

        if (averageThrottlePosition > 35) {
            advice += "\nTu posición del acelerador es alta. Intenta mantener una aceleración más suave para mejorar la eficiencia."
        }

        if (averageEngineLoad > 75) {
            advice += "\nEl motor está trabajando con una carga elevada. Evita aceleraciones bruscas y mantén velocidades constantes."
        }
        if (averageRpm > 3000 && averageThrottlePosition > 40) {
            advice += "\nEstás acelerando con fuerza frecuentemente. Intenta una conducción más progresiva para ahorrar combustible."
        }

        if (averageGear < 3 && averageRpm > 2500) {
            advice += "\nSueles conducir en marchas bajas con muchas revoluciones. Cambia a marchas superiores para mejorar la eficiencia."
        }

        val idleTime = vehicleData.count { it.rpm > 800 && it.speed < 5 }
        if (idleTime > vehicleData.size * 0.3) {
            advice += "\nTu vehículo ha estado mucho tiempo encendido sin moverse. Esto puede generar consumo innecesario. Apaga el motor si no estás en movimiento."
        }

        return advice
    }


    private fun readVehicleData(fileName: String): List<VehicleData> {
        val file = File(fileName)
        if (!file.exists()) {
            Log.e("PieChartActivity", "Archivo JSON no encontrado: $fileName")
            return emptyList()
        }

        if (file.readText().isEmpty()) {
            Log.e("PieChartActivity", "Archivo JSON vacío: $fileName")
            return emptyList()
        }

        return try {
            val json = file.readText()
            val gson = Gson()

            // Parsear el JSON completo
            val inputDataWrapper = gson.fromJson(json, InputDataWrapper::class.java)
            Log.d("PieChartActivity", "Datos deserializados: ${inputDataWrapper?.inputData?.data?.size} registros")

            val inputData = inputDataWrapper.inputData
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
            Log.e("PieChartActivity", "Error al procesar el archivo JSON: ${e.message}")
            emptyList()
        }
    }

}

