package com.example.aqi

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// This class is now flexible enough to handle unexpected API responses
data class AqiResponse(
    val status: String,
    val data: JsonElement // Changed from AqiData to JsonElement to prevent crashes
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
    @SerializedName("h") val humidity: Value? = null,
    @SerializedName("w") val wind: Value? = null // Added the missing wind property
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
