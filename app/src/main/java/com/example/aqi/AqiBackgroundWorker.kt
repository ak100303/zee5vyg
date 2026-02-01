package com.example.aqi

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
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
        return ForegroundInfo(
            NOTIFICATION_ID,
            createNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else { 0 }
        )
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AQI Background Recorder", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Recording Air Quality")
            .setContentText("Syncing hyperlocal data...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build()
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val apiService = AqiApiService.create()
        val db = AqiDatabase.getDatabase(applicationContext)
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val gson = Gson()

        return try {
            val userId = auth.currentUser?.uid
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = fusedLocationClient.lastLocation.await() ?:
                fusedLocationClient.getCurrentLocation(CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY).build(), null).await()

            val istOffset = 5.5 * 60 * 60 * 1000
            val nowIST = Date(System.currentTimeMillis() + istOffset.toLong())
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(nowIST)
            val hour = SimpleDateFormat("H", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(nowIST).toInt()

            var recordedAqi: Int? = null
            var recordedCity: String? = null
            var dataSource: String = "unknown"

            if (location != null) {
                // TIER 1: WAQI
                try {
                    val response = apiService.getHyperlocalAqi(location.latitude, location.longitude, BuildConfig.API_KEY)
                    if (response.status == "ok" && response.data.isJsonObject) {
                        val data = gson.fromJson(response.data, AqiData::class.java)
                        recordedAqi = data.aqi
                        recordedCity = data.city.name
                        dataSource = "waqi"
                    }
                } catch (e: Exception) { Log.e("AQI_WORKER", "WAQI Failed: ${e.message}") }

                // TIER 2: OPENWEATHER FAILOVER
                if (recordedAqi == null) {
                    val owKey = BuildConfig.OPEN_WEATHER_KEY
                    if (owKey.isNotEmpty()) {
                        try {
                            val owData = apiService.getOpenWeatherAqi(location.latitude, location.longitude, owKey)
                            if (owData.list.isNotEmpty()) {
                                recordedAqi = calculateUsAqi(owData.list[0].components.pm2_5)
                                recordedCity = "Failover: OpenWeather"
                                dataSource = "openweather"
                            }
                        } catch (e: Exception) { Log.e("AQI_WORKER", "OpenWeather Failed: ${e.message}") }
                    }
                }
            }

            // TIER 3: PREDICTION
            if (recordedAqi == null) {
                val lastRecords = db.aqiDao().getLastTwoRecords()
                if (lastRecords.size >= 2) {
                    val trend = lastRecords[0].aqi - lastRecords[1].aqi
                    recordedAqi = (lastRecords[0].aqi + trend.coerceIn(-25, 25)).coerceIn(0, 500)
                    recordedCity = lastRecords[0].cityName
                    dataSource = "trend_prediction"
                }
            }

            if (recordedAqi != null && recordedCity != null) {
                // Save Local
                db.aqiDao().insertHourlyRecord(HourlyAqiEntity(date = dateStr, hour = hour, cityName = recordedCity, aqi = recordedAqi))
                db.aqiDao().insertAqiRecord(AqiEntity(dateStr, recordedCity, recordedAqi))

                // Save Cloud with Label
                if (userId != null) {
                    val record = hashMapOf(
                        "aqi" to recordedAqi,
                        "cityName" to recordedCity,
                        "dataSource" to dataSource,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    firestore.collection("users").document(userId).collection("history").document(dateStr).collection("hourly").document(hour.toString()).set(record)
                }
            }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
