package com.example.aqi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.aqi.data.database.AqiDatabase
import com.example.aqi.data.database.AqiEntity
import com.example.aqi.data.database.HourlyAqiEntity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class AqiBackgroundWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val NOTIFICATION_ID = 101
    private val CHANNEL_ID = "aqi_recorder_channel"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AQI Background Recorder",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Recording Air Quality")
            .setContentText("Syncing hyperlocal data...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        // Promote to foreground to prevent killing during sleep
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            // Handle if expedited work is not supported or quota exceeded
        }

        val apiService = AqiApiService.create()
        val db = AqiDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val gson = Gson()

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            
            var location = try {
                val locationRequest = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(30 * 60 * 1000)
                    .build()
                fusedLocationClient.getCurrentLocation(locationRequest, null).await()
            } catch (e: Exception) { null }

            if (location == null) {
                location = try {
                    fusedLocationClient.lastLocation.await()
                } catch (e: Exception) { null }
            }

            if (location != null) {
                val response = apiService.getHyperlocalAqi(location.latitude, location.longitude, BuildConfig.API_KEY)
                
                if (response.status == "ok" && response.data.isJsonObject) {
                    val data = gson.fromJson(response.data, AqiData::class.java)
                    val now = Calendar.getInstance()
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
                    val hour = now.get(Calendar.HOUR_OF_DAY)
                    
                    db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = data.city.name, aqi = data.aqi))

                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val hourlyRecord = hashMapOf(
                            "aqi" to data.aqi,
                            "cityName" to data.city.name,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        firestore.collection("users").document(userId)
                            .collection("history").document(dateStr)
                            .collection("hourly").document(hour.toString())
                            .set(hourlyRecord)
                            
                        val dailyRef = firestore.collection("users").document(userId)
                            .collection("history").document(dateStr)
                        
                        firestore.runTransaction { transaction ->
                            val snapshot = transaction.get(dailyRef)
                            val currentMax = snapshot.getLong("aqi") ?: 0L
                            if (data.aqi > currentMax) {
                                transaction.set(dailyRef, hashMapOf("aqi" to data.aqi, "cityName" to data.city.name, "date" to dateStr))
                            }
                        }.await()
                    }

                    val existing = db.aqiDao().getAqiRecordForDateAndCity(dateStr, data.city.name)
                    if (existing == null || data.aqi > existing.aqi) {
                        db.aqiDao().insertAqiRecord(AqiEntity(dateStr, data.city.name, data.aqi))
                    }
                    
                    Result.success()
                } else {
                    Result.retry()
                }
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
