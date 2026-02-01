package com.example.aqi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Incremented version to 4 to support the new 'dataSource' field in HourlyAqiEntity
@Database(entities = [AqiEntity::class, HourlyAqiEntity::class], version = 4, exportSchema = false)
abstract class AqiDatabase : RoomDatabase() {
    abstract fun aqiDao(): AqiDao

    companion object {
        @Volatile
        private var INSTANCE: AqiDatabase? = null

        fun getDatabase(context: Context): AqiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AqiDatabase::class.java,
                    "aqi_database"
                )
                // Use destructive migration to automatically handle the schema change
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
