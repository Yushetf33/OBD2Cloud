package com.example.obd2cloud

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import java.io.IOException

class HereMapsService(
    private val locationService: LocationService, // Servicio de ubicación
    private val apiKey: String // Clave API de HERE Maps
) {
    private val client = OkHttpClient()

    fun obtenerMaxSpeed(callback: (String?) -> Unit) {
        // Usa LocationService para obtener la ubicación
        locationService.obtenerUbicacionActual { location ->
            if (location != null) {
                val coordenadasInicio = "${location.latitude},${location.longitude}"
                val coordenadasDestino = "${location.latitude},${location.longitude}"

                // Construye la URL para la API de HERE Routing
                val url =
                        "https://routematching.hereapi.com/v8/match/routelinks" +
                        "?apikey=$apiKey" +
                        "&waypoint0=$coordenadasInicio" +
                        "&waypoint1=$coordenadasDestino" +
                        "&mode=fastest;car" +  // Habilita el modo más rápido en coche
                        "&attributes=SPEED_LIMITS_FCn(*)"  // Solicita los límites de velocidad

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        callback(null) // Error en la solicitud
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val responseData = response.body?.string()
                            callback(responseData) // Devuelve la respuesta
                        } else {
                            callback(null) // Error HTTP
                        }
                    }
                })
            } else {
                callback(null) // Ubicación nula
            }
        }
    }
}
