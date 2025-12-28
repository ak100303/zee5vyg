package com.example.aqi.data

import com.example.aqi.AqiApiService
import com.example.aqi.AqiResponse

class AqiRepository(private val aqiApiService: AqiApiService) {

    suspend fun getHyperlocalAqi(lat: Double, lon: Double, token: String): Result<AqiResponse> {
        return try {
            val response = aqiApiService.getHyperlocalAqi(lat, lon, token)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}