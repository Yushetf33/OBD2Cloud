package com.example.obd2cloud

import android.content.Context
import android.util.Log
import android.widget.TextView
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class MetricsManager(private val context: Context) {

    fun logMetricsToExcel(
    fileName: String,
    currentRPM: TextView,
    currentFuelTrim: TextView,
    currentSpeed: TextView,
    currentThrottle: TextView,
    currentEngineLoad: TextView,
    currentMaxSpeed: TextView,
    currentGear: TextView,
    currentGyroX: String?,
    currentGyroY: String?,
    currentGyroZ: String?,
    currentAccelX: String?,
    currentAccelY: String?,
    currentAccelZ: String?,
    touchCount: Int
    ) {
        Log.d("MetricsManager", "logMetricsToExcel called with fileName: $fileName")

        val dir = File(context.getExternalFilesDir(null), "MyAppData")
        if (!dir.exists()) {
            Log.d("MetricsManager", "Directory doesn't exist, creating directory")
            val dirCreated = dir.mkdirs()
            Log.d("MetricsManager", "Directory created: $dirCreated")
        }

        val file = File(dir, fileName)
        Log.d("MetricsManager", "File path: ${file.absolutePath}")

        try {
            val df = DecimalFormat("#.######", DecimalFormatSymbols(Locale.US))

            val gyroX = currentGyroX?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val gyroY = currentGyroY?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val gyroZ = currentGyroZ?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelX = currentAccelX?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelY = currentAccelY?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            val accelZ = currentAccelZ?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

            val workbook: Workbook = if (file.exists()) {
                Log.d("MetricsManager", "File exists, loading workbook")
                XSSFWorkbook(file.inputStream())
            } else {
                Log.d("MetricsManager", "File does not exist, creating new workbook")
                XSSFWorkbook()
            }

            val sheet = workbook.getSheet("Metrics") ?: run {
                Log.d("MetricsManager", "Sheet 'Metrics' not found, creating new sheet")
                workbook.createSheet("Metrics")
            }

            if (sheet.lastRowNum == 0 || sheet.getRow(0) == null) {
                Log.d("MetricsManager", "Creating header row")
                val headerRow = sheet.createRow(0)
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

            val currentRow = sheet.createRow(sheet.lastRowNum + 1)
            Log.d("MetricsManager", "Creating new row at position: ${sheet.lastRowNum + 1}")

            currentRow.createCell(0).setCellValue(touchCount.toDouble())
            currentRow.createCell(1).setCellValue(currentRPM.text.toString())
            currentRow.createCell(2).setCellValue(currentFuelTrim.text.toString())
            currentRow.createCell(3).setCellValue(currentSpeed.text.toString())
            currentRow.createCell(4).setCellValue(currentThrottle.text.toString())
            currentRow.createCell(5).setCellValue(currentEngineLoad.text.toString())
            currentRow.createCell(6).setCellValue(currentMaxSpeed.text.toString())
            currentRow.createCell(7).setCellValue(currentGear.text.toString())
            currentRow.createCell(8).setCellValue(df.format(gyroX).toDouble())
            currentRow.createCell(9).setCellValue(df.format(gyroY).toDouble())
            currentRow.createCell(10).setCellValue(df.format(gyroZ).toDouble())
            currentRow.createCell(11).setCellValue(df.format(accelX).toDouble())
            currentRow.createCell(12).setCellValue(df.format(accelY).toDouble())
            currentRow.createCell(13).setCellValue(df.format(accelZ).toDouble())
            currentRow.createCell(14).setCellValue("0")

            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()

            Log.d("MetricsManager", "Excel file written successfully")
        } catch (e: Exception) {
            Log.e("MetricsManager", "Error writing to Excel: ${e.message}")
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
