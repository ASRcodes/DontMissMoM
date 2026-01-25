package com.example.dontmissmom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {

        // 🚫 Ignore non-emergency messages (optional check, good to keep)
        // if (remoteMessage.data["type"] != "EMERGENCY_CALL") return
        // (Commented out just in case your backend isn't sending "type" yet)

        val data = remoteMessage.data
        if (data.isNotEmpty()) {

            // 1. Extract Data
            val senderName = data["fromName"] ?: "Unknown"
            val senderPhone = data["fromPhone"] ?: ""

            // 🔴 CRITICAL FIX: Extract Location
            // The data comes as Strings from Firebase, so we convert to Double
            val lat = data["latitude"]?.toDoubleOrNull() ?: 0.0
            val lng = data["longitude"]?.toDoubleOrNull() ?: 0.0

            // 🔊 2. Start Siren Service
            val soundIntent = Intent(this, EmergencySoundService::class.java).apply {
                action = EmergencySoundService.ACTION_START
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(soundIntent)
            } else {
                startService(soundIntent)
            }

            // 📱 3. Prepare Popup UI
            val uiIntent = Intent(this, AlertPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

                // Pass ALL data to the popup
                putExtra("senderName", senderName)
                putExtra("senderPhone", senderPhone)
                putExtra("latitude", lat)   // 👈 The missing piece!
                putExtra("longitude", lng)  // 👈 The missing piece!
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                uiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val channelId = "emergency_channel"

            // 🔥 4. Max Importance Channel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    setBypassDnd(true)
                }

                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
            }

            // 🔔 5. Full Screen Notification
            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🚨 Emergency Call")
                .setContentText("$senderName needs your attention")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true) // Launches Popup immediately
                .setOngoing(true)
                .setAutoCancel(true)
                .build()

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(999, notification)

            // 🚀 Force Start Activity (Backup for some phones that ignore FullScreenIntent)
            startActivity(uiIntent)
        }
    }
}