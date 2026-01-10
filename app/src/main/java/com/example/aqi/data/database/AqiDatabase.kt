package com.example.aqi.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AqiEntity::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
