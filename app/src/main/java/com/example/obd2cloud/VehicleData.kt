package com.example.obd2cloud

data class VehicleData(
    val touchCount: Int,
    val rpm: Int,
    val fuelTrim: Float,
    val speed: Float,
    val throttlePosition: Float,
    val engineLoad: Float,
    val maxSpeed: Float,
    val gear: Int,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float
)
