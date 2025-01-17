package com.example.obd2cloud

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class HereMapsService(
    private val locationService: LocationService, // Servicio de ubicación
    private val apiKey: String // Clave API de HERE Maps
) {
    private val client = OkHttpClient()
    private var ultimaVelocidadMaxima: String? = null // Variable para almacenar la última velocidad máxima válida

    fun obtenerMaxSpeedSeguido(callback: (String?) -> Unit) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                obtenerMaxSpeed { maxSpeed ->
                    // Aquí se pasa el valor de maxSpeed a través del callback
                    callback(maxSpeed)
                }
                handler.postDelayed(this, 7000) // Repite cada 5 segundos
            }
        })
    }

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
                        callback(ultimaVelocidadMaxima) // Devuelve la última velocidad máxima válida en caso de error
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isSuccessful) {
                            val responseData = response.body?.string()
                            Log.d("HereMapsService", "Respuesta de la API: $responseData") // Log para verificar
                            val maxSpeed = responseData?.let { parsearMaxSpeed(it) }

                            if (maxSpeed != null) {
                                ultimaVelocidadMaxima = maxSpeed // Actualiza la última velocidad máxima válida
                                callback(maxSpeed) // Devuelve la nueva velocidad máxima
                            } else {
                                callback(ultimaVelocidadMaxima) // Devuelve la última velocidad máxima si no hay datos nuevos
                            }
                        } else {
                            Log.e("HereMapsService", "Error HTTP: ${response.code}")
                            callback(ultimaVelocidadMaxima) // Devuelve la última velocidad máxima en caso de error HTTP
                        }
                    }
                })
            } else {
                callback(ultimaVelocidadMaxima) // Si no se obtiene la ubicación, devuelve la última velocidad máxima válida
            }
        }
    }

    private fun parsearMaxSpeed(jsonResponse: String): String? {
        return try {
            val jsonObject = JSONObject(jsonResponse)
            val routeArray = jsonObject.getJSONObject("response").getJSONArray("route")

            if (routeArray.length() > 0) {
                val route = routeArray.getJSONObject(0)
                val legArray = route.getJSONArray("leg")
                val leg = legArray.getJSONObject(0)

                if (leg.has("link")) {
                    val linkArray = leg.getJSONArray("link")
                    val link = linkArray.getJSONObject(0)
                    if (link.has("attributes")) {
                        val attributes = link.getJSONObject("attributes")
                        if (attributes.has("SPEED_LIMITS_FCN")) {
                            val speedLimits = attributes.getJSONArray("SPEED_LIMITS_FCN")
                            for (i in 0 until speedLimits.length()) {
                                val speedLimitObj = speedLimits.getJSONObject(i)
                                val toSpeed = speedLimitObj.getString("TO_REF_SPEED_LIMIT")

                                if (toSpeed != "0") {
                                    return toSpeed
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: JSONException) {
            Log.e("HereMapsService", "Error al parsear la respuesta JSON: ${e.message}")
            null
        }
    }
}

