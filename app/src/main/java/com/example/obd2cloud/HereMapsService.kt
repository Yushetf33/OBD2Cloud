package com.example.obd2cloud

import android.util.Log
import android.location.Location
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.Callback
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class HereMapsService(
    private val locationService: LocationService,
    private val apiKey: String
) {
    private val client = OkHttpClient()
    private var ultimaUbicacion: Location? = null // Última ubicación registrada
    private var ultimaVelocidadMaxima: String? = null // Última velocidad máxima válida
    private var velocidadActual: Float = 0f

    fun obtenerMaxSpeedSeguido(callback: (String?) -> Unit) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            // Primero, obtener la primera ubicación antes de la consulta
            obtenerMaxSpeed { maxSpeed ->
                callback(maxSpeed) // Actualiza la interfaz con la primera velocidad máxima
            }
            val nuevaUbicacion = locationService.obtenerUbicacionActual()

            if (nuevaUbicacion != null) {
                ultimaUbicacion = nuevaUbicacion // Guardamos la primera ubicación
                // Realizar la primera consulta
                obtenerMaxSpeed { maxSpeed ->
                    callback(maxSpeed) // Actualiza la interfaz con la primera velocidad máxima
                }

                // Ciclo de consulta continua
                while (isActive) {
                    val nuevaUbicacion = locationService.obtenerUbicacionActual()
                    Log.d("HereMapsService", "Ubicación obtenida: $nuevaUbicacion")
                    if (nuevaUbicacion != null) {
                        velocidadActual = nuevaUbicacion.speed * 3.6f

                        if (shouldMakeQuery(nuevaUbicacion)) {
                            ultimaUbicacion = nuevaUbicacion // Actualiza la última ubicación
                            obtenerMaxSpeed { maxSpeed ->
                                callback(maxSpeed) // Actualiza la velocidad máxima
                            }
                        } else {
                            callback(ultimaVelocidadMaxima) // Devuelve la última velocidad válida
                        }
                    } else {
                        callback(ultimaVelocidadMaxima) // En caso de que no haya nueva ubicación
                    }
                    delay(5000) // Realiza la consulta cada 5 segundos
                }
            } else {
                Log.e("HereMapsService", "No se pudo obtener la primera ubicación.")
                callback(ultimaVelocidadMaxima) // Si no se pudo obtener la ubicación inicial
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
            velocidadActual < 60 -> 85f// Si la velocidad es menor de 50 km/h
            velocidadActual <= 100 -> 140f    // Si la velocidad está entre 50 y 100 km/h
            else -> 230f                      // Si la velocidad es mayor de 100 km/h
        }
    }

    private fun shouldMakeQuery(nuevaUbicacion: Location): Boolean {
        val distanciaMinima = calcularDistanciaMinima(velocidadActual)
        val distancia = ultimaUbicacion?.distanceTo(nuevaUbicacion) ?: 0f

        // Log para ver la distancia recorrida y la mínima requerida
        Log.d("HereMapsService", "Velocidad: $velocidadActual km/h, Distancia recorrida: $distancia m, Distancia mínima requerida: $distanciaMinima m")

        return distancia >= distanciaMinima
    }

    private fun obtenerMaxSpeed(callback: (String?) -> Unit) {
        val coordenadas = ultimaUbicacion?.let { "${it.latitude},${it.longitude}" }
            ?: run {
                Log.e("HereMapsService", "Ubicación inválida para obtener velocidad máxima.")
                callback(ultimaVelocidadMaxima)
                return
            }

        val url =
            "https://routematching.hereapi.com/v8/match/routelinks" +
                    "?apikey=$apiKey" +
                    "&waypoint0=$coordenadas" +
                    "&waypoint1=$coordenadas" +
                    "&mode=fastest;car" +
                    "&attributes=SPEED_LIMITS_FCn(*)"

        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("HereMapsService", "Error en la consulta: ${e.message}")
                callback(ultimaVelocidadMaxima)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    Log.d("HereMapsService", "Respuesta de la API: $responseData")
                    val maxSpeed = responseData?.let { parsearMaxSpeed(it) }

                    if (maxSpeed != null) {
                        ultimaVelocidadMaxima = maxSpeed // Actualiza la última velocidad válida
                        callback(maxSpeed)
                    } else {
                        callback(ultimaVelocidadMaxima) // Devuelve el último valor válido si no hay datos nuevos
                    }
                } else {
                    Log.e("HereMapsService", "Error HTTP: ${response.code}")
                    callback(ultimaVelocidadMaxima)
                }
            }
        })
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

