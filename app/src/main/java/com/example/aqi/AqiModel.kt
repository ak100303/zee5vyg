package com.example.aqi

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

// --- WAQI MODELS ---
data class AqiResponse(val status: String, val data: JsonElement)
data class AqiData(val aqi: Int, val idx: Int, val city: City, val iaqi: IaqiMetrics, val forecast: DailyForecasts?)
data class City(val name: String, val geo: List<Double>)
data class IaqiMetrics(
    @SerializedName("pm25") val pm25: Value? = null,
    @SerializedName("pm10") val pm10: Value? = null,
    @SerializedName("o3") val o3: Value? = null,
    @SerializedName("t") val temperature: Value? = null,
    @SerializedName("h") val humidity: Value? = null,
    @SerializedName("w") val wind: Value? = null
)
data class Value(@SerializedName("v") val value: Float)
data class DailyForecasts(val daily: ForecastDetails)
data class ForecastDetails(val o3: List<ForecastDay>? = null, val pm10: List<ForecastDay>? = null, val pm25: List<ForecastDay>? = null)
data class ForecastDay(val avg: Int, val day: String, val max: Int, val min: Int)

// --- OPENWEATHER MODELS ---
data class OpenWeatherResponse(val list: List<OwAirQualityData>)
data class OwAirQualityData(val main: OwMain, val components: OwComponents, val dt: Long)
data class OwMain(val aqi: Int)
data class OwComponents(val pm2_5: Float, val pm10: Float)
data class OwForecastResponse(val list: List<OwForecastItem>)
data class OwForecastItem(val main: OwForecastMain, val wind: OwForecastWind, val dt_txt: String)
data class OwForecastMain(val temp: Float, val humidity: Float, val pressure: Float)
data class OwForecastWind(val speed: Float)

// --- OPENAQ MODELS ---
data class OpenAqiResponse(val results: List<OpenAqiResult>)
data class OpenAqiResult(val location: String, val measurements: List<OpenAqiMeasurement>, val coordinates: OpenAqiCoords)
data class OpenAqiMeasurement(val parameter: String, val value: Float, val unit: String)
data class OpenAqiCoords(val latitude: Double, val longitude: Double)

// --- DATA.GOV.IN MODELS (Indian Official) ---
data class GovIndiaResponse(
    val records: List<GovIndiaRecord>,
    @SerializedName("total") val total: Int
)
data class GovIndiaRecord(
    val city: String,
    val station: String,
    @SerializedName("pollutant_id") val pollutantId: String,
    @SerializedName("pollutant_avg") val pollutantValue: String,
    @SerializedName("last_update") val lastUpdate: String
)
