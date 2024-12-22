package com.example.obd2cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Callback
import java.io.IOException

class OpenStreetMapService(
    private val locationService: LocationService // Recibe una instancia de LocationService
) {
    private val client = OkHttpClient()

    fun obtenerMaxSpeed(radio: Int, callback: (String?) -> Unit) {
        // Usa LocationService para obtener la ubicación
        locationService.obtenerUbicacionActual { location ->
            if (location != null) {
                val coordenadas = "${location.latitude},${location.longitude}"
                // Consulta para encontrar la carretera más cercana y su maxspeed
                val consulta = """
                [out:json];
                node(around:$radio, $coordenadas);
                way(bn)[highway][maxspeed]; 
                out tags center;
            """.trimIndent()
                val url = "https://overpass-api.de/api/interpreter"


                val request = Request.Builder()
                    .url(url)
                    .post(consulta.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val responseData = response.body?.string()
                            callback(responseData)
                        } else {
                            callback(null)
                        }
                    }
                })
            } else {
                callback(null)
            }
        }
    }

}