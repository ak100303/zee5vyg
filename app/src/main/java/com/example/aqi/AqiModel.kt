package com.example.aqi

import com.google.gson.annotations.SerializedName

// This maps the JSON response from the WAQI API
data class AqiResponse(
    val status: String,
    val data: AqiData
)

data class AqiData(
    val aqi: Int,
    val idx: Int,
    val city: City,
    val iaqi: IaqiMetrics,
    val forecast: DailyForecasts?
)

data class City(
    val name: String,
    val geo: List<Double>
)

data class IaqiMetrics(
    val pm25: Value? = null,
    @SerializedName("t") val temperature: Value? = null,
    @SerializedName("h") val humidity: Value? = null
)

data class Value(
    @SerializedName("v") val value: Float
)

data class DailyForecasts(
    val daily: ForecastDetails
)

data class ForecastDetails(
    val o3: List<ForecastDay>? = null,
    val pm10: List<ForecastDay>? = null,
    val pm25: List<ForecastDay>? = null
)

data class ForecastDay(
    val avg: Int,
    val day: String,
    val max: Int,
    val min: Int
)
