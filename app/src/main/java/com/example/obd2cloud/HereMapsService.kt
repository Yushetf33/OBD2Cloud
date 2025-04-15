package com.example.obd2cloud

import android.location.Location
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class HereMapsService(
    private val locationService: LocationService,
    private val apiKey: String
) {
    private val client = OkHttpClient()
    private var ultimaUbicacion: Location? = null
    private var ultimaVelocidadMaxima: String? = null
    private var velocidadActual: Float = 0f

    fun obtenerMaxSpeedSeguido(callback: (String?) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val nuevaUbicacion = locationService.obtenerUbicacionActual()
            if (nuevaUbicacion != null) {
                ultimaUbicacion = nuevaUbicacion
                obtenerMaxSpeed { maxSpeed -> callback(maxSpeed) }

                while (isActive) {
                    val ubicacion = locationService.obtenerUbicacionActual()
                    Log.d("HereMapsService", "Ubicación obtenida: $ubicacion")

                    if (ubicacion != null) {
                        velocidadActual = ubicacion.speed * 3.6f

                        if (shouldMakeQuery(ubicacion)) {
                            ultimaUbicacion = ubicacion
                            obtenerMaxSpeed { maxSpeed -> callback(maxSpeed) }
                        } else {
                            callback(ultimaVelocidadMaxima)
                        }
                    } else {
                        callback(ultimaVelocidadMaxima)
                    }
                    delay(5000)
                }
            } else {
                Log.e("HereMapsService", "No se pudo obtener la ubicación inicial.")
                callback(ultimaVelocidadMaxima)
            }
        }
    }

    fun actualizarVelocidad(nuevaVelocidad: String) {
        velocidadActual = nuevaVelocidad.toFloat()
    }

    private fun calcularDistanciaMinima(velocidadActual: Float): Float {
        return when {
            velocidadActual < 30 -> 45f
            velocidadActual < 50 -> 70f
            velocidadActual < 60 -> 85f
            velocidadActual <= 100 -> 140f
            else -> 230f
        }
    }

    private fun shouldMakeQuery(nuevaUbicacion: Location): Boolean {
        val distanciaMinima = calcularDistanciaMinima(velocidadActual)
        val distancia = ultimaUbicacion?.distanceTo(nuevaUbicacion) ?: 0f

        Log.d("HereMapsService", "Velocidad: $velocidadActual km/h, Distancia recorrida: $distancia m, Requerida: $distanciaMinima m")
        return distancia >= distanciaMinima
    }

    private fun obtenerMaxSpeed(callback: (String?) -> Unit) {
        val ubicacion = ultimaUbicacion
        if (ubicacion == null) {
            Log.e("HereMapsService", "Ubicación inválida.")
            callback(ultimaVelocidadMaxima)
            return
        }

        val lat = ubicacion.latitude
        val lon = ubicacion.longitude

        val url =
            "https://routemaps.hereapi.com/v8/attributes" +
                    "?in=circle:$lat,$lon;r=50" +
                    "&layers=SPEED_LIMITS_FCN" +
                    "&apiKey=$apiKey"  // Parámetro actualizado

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HereMapsService", "Error en la consulta: ${e.message}")
                callback(ultimaVelocidadMaxima)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        Log.d("HereMapsService", "Respuesta API: $body")
                        val maxSpeed = body?.let { parsearMaxSpeed(it) }

                        if (maxSpeed != null) {
                            ultimaVelocidadMaxima = maxSpeed
                            callback(maxSpeed)
                        } else {
                            callback(ultimaVelocidadMaxima)
                        }
                    } else {
                        val errorBody = response.body?.string()
                        Log.e("HereMapsService", "Error HTTP: ${response.code} - $errorBody")
                        callback(ultimaVelocidadMaxima)
                    }
                } catch (e: Exception) {
                    Log.e("HereMapsService", "Error procesando respuesta: ${e.message}")
                    callback(ultimaVelocidadMaxima)
                }
            }
        })
    }

    private fun parsearMaxSpeed(jsonResponse: String): String? {
        return try {
            val jsonObject = JSONObject(jsonResponse)
            val layers = jsonObject.getJSONObject("data").getJSONArray("layers")

            if (layers.length() > 0) {
                val features = layers.getJSONObject(0).getJSONArray("features")
                for (i in 0 until features.length()) {
                    val attributes = features.getJSONObject(i)
                        .getJSONObject("attributes")
                        .getJSONObject("dynamic_speed_info")  // Nueva estructura

                    val speed = attributes.optInt("speed_limit", 0)
                    if (speed > 0) return speed.toString()
                }
            }
            null
        } catch (e: Exception) {
            Log.e("HereMapsService", "Error al parsear JSON: ${e.message}")
            null
        }
    }
}