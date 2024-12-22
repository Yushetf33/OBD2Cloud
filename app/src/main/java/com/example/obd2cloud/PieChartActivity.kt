package com.example.obd2cloud

import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate

class PieChartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pie_chart)

        // Obtener los valores enviados desde MainActivity
        val tranquiloCount = intent.getIntExtra("tranquilo", 0)
        val agresivoCount = intent.getIntExtra("agresiva", 0)
        val normalCount = intent.getIntExtra("normal", 0)

        val total = tranquiloCount + agresivoCount + normalCount

        // Verificar si el total es cero, es decir, si no hay datos
        if (total == 0) {
            val consejoTextView: TextView = findViewById(R.id.consejoTextView)
            consejoTextView.text = "" // No mostrar consejo si no hay datos
            return // Salir del método sin continuar con el gráfico y los consejos
        }

        val tranquiloPercentage = if (total > 0) (tranquiloCount.toFloat() / total * 100) else 0f
        val agresivoPercentage = if (total > 0) (agresivoCount.toFloat() / total * 100) else 0f
        val normalPercentage = if (total > 0) (normalCount.toFloat() / total * 100) else 0f

        // Configurar el gráfico circular
        val pieChart: PieChart = findViewById(R.id.pieChart)

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
        legend.textColor = ContextCompat.getColor(this@PieChartActivity, R.color.red)
        // Configurar los márgenes de la leyenda (con espacio entre la leyenda y el gráfico)
        legend.xEntrySpace = 20f  // Espacio entre los elementos de la leyenda
        legend.yEntrySpace = 10f  // Espacio entre las filas de la leyenda
        legend.formToTextSpace = 10f  // Espacio entre el símbolo de la leyenda y el texto
        legend.formSize = 14f // Tamaño de los cuadros de la leyenda


        // Crear la descripción del gráfico
        val description = Description().apply {
            text = ""
            textSize = 16f
            textColor = ContextCompat.getColor(this@PieChartActivity, R.color.red) // Usar ContextCompat para obtener el color
        }

        pieChart.description = description

        // Actualizar el gráfico
        pieChart.invalidate()

        // Determinar el estilo predominante y mostrar el consejo
        val consejoTextView: TextView = findViewById(R.id.consejoTextView)

        val estiloPredominante = when {
            tranquiloPercentage > agresivoPercentage && tranquiloPercentage > normalPercentage -> "tranquilo"
            agresivoPercentage > tranquiloPercentage && agresivoPercentage > normalPercentage -> "agresivo"
            else -> "normal"
        }

        // Mostrar consejo basado en el estilo predominante
        val consejo = when (estiloPredominante) {
            "tranquilo" -> "¡Excelente! Tu estilo de conducción es tranquilo y seguro. Continúa manteniendo una velocidad moderada y respetando las normas de tráfico."
            "agresivo" -> "¡Cuidado! Tu estilo de conducción es agresivo. Trata de reducir la velocidad y evitar aceleraciones bruscas para mejorar la seguridad."
            "normal" -> "Tu estilo de conducción es bastante equilibrado. Mantén un enfoque constante y revisa siempre los límites de velocidad."
            else -> "No hay un estilo predominante. Intenta mantener un ritmo de conducción consistente."
        }

        // Establecer el texto del consejo en el TextView
        consejoTextView.text = consejo
    }
}
