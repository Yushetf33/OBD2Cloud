package com.example.obd2cloud

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.File
import java.io.IOException

class ApiDrivingStyle(private val token: String, private val apiUrl: String) {

    private val client = OkHttpClient()

    // Función para realizar la solicitud POST
    fun sendPostRequest(jsonFilePath: String): String {
        // Leer el contenido del archivo JSON
        val inputJson = File(jsonFilePath).readText()

        // Crear el cuerpo de la solicitud con el JSON
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), inputJson)

        // Crear la solicitud POST
        val request = Request.Builder()
            .url(apiUrl)
            .post(body)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
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
