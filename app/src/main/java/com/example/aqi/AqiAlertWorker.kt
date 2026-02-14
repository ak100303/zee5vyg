package com.example.aqi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AqiAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val ALERT_CHANNEL_ID = "personal_aqi_alerts"
    private val NOTIFICATION_ID = 888

    override suspend fun doWork(): Result {
        val firestore = FirebaseFirestore.getInstance()
        
        // Connect to the same path as the app
        val docRef = firestore.collection("users").document("esp32_device")
            .collection("iot_devices").document("station_01")

        try {
            // Get the current snapshot
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val ppm = snapshot.getDouble("ppm")?.toFloat() ?: 400f
                val aqi = calculateAqi(ppm)

                if (aqi > 150) {
                    sendNotification(aqi)
                }
            }
        } catch (e: Exception) {
            return Result.retry()
        }

        return Result.success()
    }

    private fun sendNotification(aqi: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(ALERT_CHANNEL_ID, "Indoor Air Alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, ALERT_CHANNEL_ID)
            .setContentTitle("CRITICAL: Indoor AQI $aqi")
            .setContentText("Dangerous gas levels detected by your ESP32. Ventilate room!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun calculateAqi(ppm: Float): Int {
        return when {
            ppm <= 600 -> (ppm / 12).toInt()
            ppm <= 1000 -> (51 + (ppm - 600) / 8).toInt()
            else -> (101 + (ppm - 1000) / 10).toInt().coerceAtMost(500)
        }
    }
}
