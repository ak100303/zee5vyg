package com.example.aqi.ai

import com.example.aqi.AqiData
import java.util.Calendar

class AqiPredictor {

    // Maps hour of the day to a multiplier representing typical AQI fluctuation.
    private val hourlyMultiplier = mapOf(
        0 to 0.9, 1 to 0.85, 2 to 0.8, 3 to 0.8, 4 to 0.85, 5 to 0.9, // Late Night/Early Morning Dip
        6 to 1.0, 7 to 1.1, 8 to 1.15, 9 to 1.1, // Morning Rush Rise
        10 to 1.0, 11 to 0.95, 12 to 0.9, 13 to 0.9, 14 to 0.95, // Midday Dip
        15 to 1.0, 16 to 1.05, 17 to 1.1, 18 to 1.15, 19 to 1.1, // Evening Rush Rise
        20 to 1.05, 21 to 1.0, 22 to 0.95, 23 to 0.9
    )

    fun predictNext3Hours(currentData: AqiData): List<Int> {
        val currentAqi = currentData.aqi
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

        val tomorrowAqi = currentData.forecast?.daily?.pm25?.firstOrNull()?.avg ?: currentAqi
        val trendPerStep = (tomorrowAqi - currentAqi) / 24.0

        return (1..3).map { i ->
            val futureHour = (currentHour + i) % 24
            val basePrediction = currentAqi + (trendPerStep * i)
            val multiplier = hourlyMultiplier[futureHour] ?: 1.0
            (basePrediction * multiplier).toInt().coerceAtLeast(0)
        }
    }
}
