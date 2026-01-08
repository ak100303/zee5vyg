package com.example.aqi

import com.google.gson.annotations.SerializedName

data class AqiData(@SerializedName("aqi") val aqi: Int,
                   @SerializedName("city") val city: City,
                   @SerializedName("time") val time: Time
    // Add other fields from the 'data' object as needed
)

data class City(
    @SerializedName("name") val name: String,
    @SerializedName("geo") val geo: List<Double>,
    @SerializedName("url") val url: String
)

data class Time(
    @SerializedName("s") val s: String,
    @SerializedName("tz") val tz: String,
    @SerializedName("v") val v: Long
)
