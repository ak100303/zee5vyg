package com.example.aqi

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// --- WAQI MODELS ---
data class AqiResponse(
    val status: String,
    val data: JsonElement
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
    @SerializedName("pm25") val pm25: Value? = null,
    @SerializedName("pm10") val pm10: Value? = null,
    @SerializedName("o3") val o3: Value? = null,
    @SerializedName("t") val temperature: Value? = null,
    @SerializedName("h") val humidity: Value? = null,
    @SerializedName("w") val wind: Value? = null,
    @SerializedName("p") val pressure: Value? = null
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

// --- OPENWEATHER MODELS ---
data class OpenWeatherResponse(
    val list: List<OwAirQualityData>
)

data class OwAirQualityData(
    val main: OwMain,
    val components: OwComponents,
    val dt: Long
)

data class OwMain(
    val aqi: Int // 1=Good, 2=Fair, 3=Moderate, 4=Poor, 5=Very Poor
)

data class OwComponents(
    val pm2_5: Float,
    val pm10: Float,
    val no2: Float,
    val o3: Float
)
