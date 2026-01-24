package com.example.aqi.data.database

import androidx.room.Entity

// We remove the PrimaryKey from 'date' and create a composite primary key
// This allows storing different AQI values for different cities on the same day.
@Entity(tableName = "aqi_history", primaryKeys = ["date", "cityName"])
data class AqiEntity(
    val date: String, // Format: "yyyy-MM-dd"
    val cityName: String,
    val aqi: Int
)
