package com.example.obd2cloud
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationService(private val context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    suspend fun obtenerUbicacionActual(): Location? {
        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        // Configurar solicitud de ubicación
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
            .setMinUpdateIntervalMillis(0L)
            .setMaxUpdateDelayMillis(0L)
            .build()

        // Suspender la corutina hasta que se obtenga la ubicación
        return suspendCoroutine { cont ->
            // Configurar callback de ubicación
            this.locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    cont.resume(location) // Reanudar la corutina con la ubicación
                    stopLocationUpdates() // Detener las actualizaciones de ubicación
                }
            }

            // Solicitar una nueva actualización de ubicación
            fusedLocationClient.requestLocationUpdates(locationRequest, this.locationCallback!!, null)
        }
    }


    // Detener actualizaciones de ubicación
    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }
}
