package com.example.aqi.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "aqi_history")
data class AqiEntity(
    @PrimaryKey val date: String, // Format: "yyyy-MM-dd"
    val aqi: Int,
    val cityName: String
)
