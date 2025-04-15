package com.example.obd2cloud

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class ApiDrivingStyle(private val apiUrl: String) {

    private val client = OkHttpClient()

    // Función para realizar la solicitud POST
    fun sendPostRequest(jsonFilePath: String): String {
        // Leer el contenido del archivo JSON
        val inputJson = File(jsonFilePath).readText()

        // Crear el cuerpo de la solicitud con el JSON
        val body = inputJson.toRequestBody("application/json".toMediaTypeOrNull())

        // Crear la solicitud POST
        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("Content-Type", "application/json") // Solo el Content-Type
            .build()

        return try {
            // Realizar la solicitud
            val response = client.newCall(request).execute()

            // Verificar la respuesta
            if (response.isSuccessful) {
                response.body?.string() ?: "Respuesta vacía"
            } else {
                "Error: ${response.code}"
            }
        } catch (e: IOException) {
            // Manejar posibles errores de red
            "Error en la solicitud: ${e.message}"
        }
    }
}

