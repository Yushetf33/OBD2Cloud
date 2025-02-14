package com.example.obd2cloud
import com.google.gson.annotations.SerializedName

data class InputDataWrapper(
    @SerializedName("input_data") val inputData: InputData
)
