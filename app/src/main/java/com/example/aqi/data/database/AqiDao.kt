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

    @Query("SELECT * FROM aqi_history WHERE date LIKE :monthQuery ORDER BY date ASC")
    fun getAqiRecordsForMonth(monthQuery: String): Flow<List<AqiEntity>>

    @Query("SELECT * FROM aqi_history WHERE date = :date LIMIT 1")
    suspend fun getAqiRecordForDate(date: String): AqiEntity?
}
