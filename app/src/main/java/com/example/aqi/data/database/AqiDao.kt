package com.example.aqi.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AqiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAqiRecord(record: AqiEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHourlyRecord(record: HourlyAqiEntity)

    @Query("SELECT * FROM aqi_history WHERE date LIKE :monthQuery ORDER BY date ASC")
    fun getAqiRecordsForMonth(monthQuery: String): Flow<List<AqiEntity>>

    @Query("SELECT * FROM aqi_hourly_history WHERE date = :date ORDER BY hour ASC")
    fun getHourlyRecordsForDay(date: String): Flow<List<HourlyAqiEntity>>

    // New: filter by city name for day view
    @Query("SELECT * FROM aqi_hourly_history WHERE date = :date AND cityName = :cityName ORDER BY hour ASC")
    fun getHourlyRecordsForDayByCity(date: String, cityName: String): Flow<List<HourlyAqiEntity>>

    @Query("SELECT * FROM aqi_hourly_history WHERE date LIKE :monthQuery ORDER BY date ASC, aqi DESC")
    suspend fun getHighestHourlyRecordsForMonth(monthQuery: String): List<HourlyAqiEntity>

    // New: filter by city name for month view
    @Query("SELECT * FROM aqi_hourly_history WHERE date LIKE :monthQuery AND cityName = :cityName ORDER BY date ASC, aqi DESC")
    suspend fun getHighestHourlyRecordsForMonthByCity(monthQuery: String, cityName: String): List<HourlyAqiEntity>

    @Query("SELECT * FROM aqi_hourly_history WHERE date = :date ORDER BY hour ASC")
    suspend fun getHourlyRecordsListForDay(date: String): List<HourlyAqiEntity>

    @Query("SELECT * FROM aqi_history WHERE date = :date AND cityName = :cityName LIMIT 1")
    suspend fun getAqiRecordForDateAndCity(date: String, cityName: String): AqiEntity?

    @Query("SELECT * FROM aqi_hourly_history ORDER BY id DESC LIMIT 2")
    suspend fun getLastTwoRecords(): List<HourlyAqiEntity>
}
