package com.example.aqi

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class PersonalAqiMonitorService : Service() {

    private val MONITOR_CHANNEL_ID = "personal_monitor_service"
    private val ALERT_CHANNEL_ID = "personal_aqi_alerts"
    private val SERVICE_ID = 777
    private val ALERT_ID = 999
    
    private var firestoreListener: ListenerRegistration? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(SERVICE_ID, createServiceNotification())
        startMonitoring()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for the background service itself
            val serviceChannel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Indoor Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            
            // Channel for the high-priority alerts
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Indoor Air Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, MONITOR_CHANNEL_ID)
            .setContentTitle("Personal Air Monitor Active")
            .setContentText("Watching for sudden air quality changes...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("users").document("esp32_device")
            .collection("iot_devices").document("station_01")

        firestoreListener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val ppm = snapshot.getDouble("ppm")?.toFloat() ?: 400f
                val aqi = calculateAqi(ppm)

                if (aqi > 150) {
                    triggerAlert(aqi)
                }
            }
        }
    }

    private fun triggerAlert(aqi: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle("SUDDEN HIGH AQI: $aqi")
            .setContentText("Sensitive: Avoid outdoors. Normal: Limit activity.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
            .build()

        manager.notify(ALERT_ID, notification)

        // Auto-dismiss after 5 minutes
        Handler(Looper.getMainLooper()).postDelayed({
            manager.cancel(ALERT_ID)
        }, 300000)
    }

    private fun calculateAqi(ppm: Float): Int {
        return when {
            ppm <= 600 -> (ppm / 12).toInt()
            ppm <= 1000 -> (51 + (ppm - 600) / 8).toInt()
            else -> (101 + (ppm - 1000) / 10).toInt().coerceAtMost(500)
        }
    }

    override fun onDestroy() {
        firestoreListener?.remove()
        super.onDestroy()
    }
}
