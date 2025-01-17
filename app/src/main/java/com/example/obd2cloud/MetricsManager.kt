
package com.example.obd2cloud

import android.content.Context
import android.util.Log
import android.widget.TextView
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MetricsManager(private val context: Context, private val sensorHelper: SensorManagerHelper) {

    fun logMetricsToExcel(
        fileName: String,
        currentRPM: TextView,
        currentFuelTrim: TextView,
        currentSpeed: TextView,
        currentThrottle: TextView,
        currentEngineLoad: TextView,
        currentMaxSpeed: TextView,
        currentGear: TextView,
        touchCount: Int
    ) {
        Log.d("MetricsManager", "logMetricsToExcel called with fileName: $fileName")

        val dir = File(context.getExternalFilesDir(null), "MyAppData")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)

        try {
            val df = DecimalFormat("#.######", DecimalFormatSymbols(Locale.US))
            val (gyroX, gyroY, gyroZ) = sensorHelper.currentGyro
            val (accelX, accelY, accelZ) = sensorHelper.currentAccel

            val workbook = if (file.exists()) XSSFWorkbook(file.inputStream()) else XSSFWorkbook()
            val sheet = workbook.getSheet("Metrics") ?: workbook.createSheet("Metrics")

            if (sheet.lastRowNum == 0 || sheet.getRow(0) == null) {
                val headerRow = sheet.createRow(0)
                listOf(
                    "Touch Count", "RPM", "Fuel Trim", "Speed", "Throttle Position",
                    "Engine Load", "Max Speed", "Gear",
                    "Gyro X", "Gyro Y", "Gyro Z",
                    "Accel X", "Accel Y", "Accel Z"
                ).forEachIndexed { index, title -> headerRow.createCell(index).setCellValue(title) }
            }

            val currentRow = sheet.createRow(sheet.lastRowNum + 1)
            currentRow.createCell(0).setCellValue(touchCount.toDouble())
            currentRow.createCell(1).setCellValue(currentRPM.text.toString())
            currentRow.createCell(2).setCellValue(currentFuelTrim.text.toString())
            currentRow.createCell(3).setCellValue(currentSpeed.text.toString())
            currentRow.createCell(4).setCellValue(currentThrottle.text.toString())
            currentRow.createCell(5).setCellValue(currentEngineLoad.text.toString())
            currentRow.createCell(6).setCellValue(currentMaxSpeed.text.toString())
            currentRow.createCell(7).setCellValue(currentGear.text.toString())
            currentRow.createCell(8).setCellValue(df.format(gyroX))
            currentRow.createCell(9).setCellValue(df.format(gyroY))
            currentRow.createCell(10).setCellValue(df.format(gyroZ))
            currentRow.createCell(11).setCellValue(df.format(accelX))
            currentRow.createCell(12).setCellValue(df.format(accelY))
            currentRow.createCell(13).setCellValue(df.format(accelZ))

            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun convertExcelToStructuredJson(excelFileName: String): String {
        val dir = File(context.getExternalFilesDir(null), "MyAppData")
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

                headers.forEachIndexed { colIndex, _ ->
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
            val jsonString = gson.toJson(jsonStructure)

            workbook.close()
            return jsonString
        } catch (e: Exception) {
            Log.e("Convert", "Error converting Excel to JSON: ${e.message}")
            return ""
        }
    }


    fun saveJsonToFile(json: String, fileName: String): String {
        return try {
            val dir = File(context.getExternalFilesDir(null), "MyAppData")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            val jsonFile = File(dir, fileName)
            FileWriter(jsonFile).use { it.write(json) }
            jsonFile.absolutePath
        } catch (e: Exception) {
            Log.e("MetricsManager", "Error saving JSON: ${e.message}")
            ""
        }
    }
}
