package com.example.aqi.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aqi_hourly_history")
data class HourlyAqiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // "yyyy-MM-dd"
    val hour: Int,   // 0-23
    val cityName: String,
    val aqi: Int
)
