package com.example.obd2cloud

data class InputData(
    val columns: List<String>,
    val index: List<Int>,
    val data: List<List<Double>>
)
