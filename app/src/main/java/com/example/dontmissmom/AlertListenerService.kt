package com.example.dontmissmom

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AlertListenerService : Service() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        listenForIncomingAlerts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        listenerRegistration?.remove()
        sendBroadcast(Intent("RESTART_ALERT_LISTENER"))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        val channelId = "alert_listener_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency Listener",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setSound(null, null)
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("DontMissMOM active")
            .setContentText("Listening for emergency alerts")
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    private fun listenForIncomingAlerts() {
        val uid = auth.currentUser?.uid
            ?: getSharedPreferences("app_prefs", MODE_PRIVATE)
                .getString("saved_uid", null)
            ?: return

        listenerRegistration = firestore.collection("alerts")
            .whereEqualTo("toUid", uid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type.name != "ADDED") return@forEach

                    val doc = change.document
                    if (doc.getBoolean("handled") == true) return@forEach

                    val senderName = doc.getString("fromName") ?: "Unknown"
                    val senderPhone = doc.getString("fromPhone") ?: ""

                    wakeScreen()
                    showEmergencyNotification(senderName, senderPhone)

                    doc.reference.update("handled", true)
                }
            }
    }

    private fun showEmergencyNotification(senderName: String, senderPhone: String) {

        // 🔊 START SIREN
        ContextCompat.startForegroundService(
            this,
            Intent(this, EmergencySoundService::class.java).apply {
                action = EmergencySoundService.ACTION_START
            }
        )

        val openIntent = Intent(this, AlertPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("senderName", senderName)
            putExtra("senderPhone", senderPhone)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ❌ SWIPE → STOP SIREN
        val deleteIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EmergencySoundService::class.java).apply {
                action = EmergencySoundService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "emergency_alert_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Emergency Alert")
            .setContentText("Emergency triggered by $senderName")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setDeleteIntent(deleteIntent) // 👈 swipe stops siren
            .setOngoing(false)
            .build()

        nm.notify(999, notification)
    }

    private fun wakeScreen() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "DontMissMOM:Wake"
        )
        wakeLock.acquire(8000)
    }
}
