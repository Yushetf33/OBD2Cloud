package com.example.obd2cloud
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class LocationService(private val context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun obtenerUbicacionActual(locationCallback: (Location?) -> Unit) {
        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationCallback(null)
            return
        }

        // Configurar solicitud de ubicación

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
            .setMinUpdateIntervalMillis(0L) // Sin intervalos mínimos entre actualizaciones
            .setMaxUpdateDelayMillis(0L) // Sin retraso máximo
            .build()

        // Configurar callback de ubicación
        this.locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // Llamar al callback con la ubicación obtenida
                locationCallback(locationResult.lastLocation)
                // Detener actualizaciones para evitar recibir ubicaciones repetidas
                stopLocationUpdates()
            }
        }

        // Solicitar una nueva actualización de ubicación
        fusedLocationClient.requestLocationUpdates(locationRequest, this.locationCallback!!, null)
    }

    // Detener actualizaciones de ubicación
    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }
}
