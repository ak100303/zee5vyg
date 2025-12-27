package com.example.aqi

// This maps the JSON response from the WAQI API
data class AqiResponse(
    val status: String,
    val data: AqiData
)

data class AqiData(
    val aqi: Int,
    val idx: Int,
    val city: City,
    val iaqi: IaqiMetrics
)

data class City(val name: String)

data class IaqiMetrics(
    val pm25: Value? = null,
    val t: Value? = null,    // Temperature
    val h: Value? = null     // Humidity
)

data class Value(val v: Float)