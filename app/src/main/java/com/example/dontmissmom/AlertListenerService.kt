package com.example.dontmissmom

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AlertListenerService : Service() {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AlertService", "Service created")
        startForegroundServiceNotification()
        listenForIncomingAlerts()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        listenerRegistration?.remove()
        super.onDestroy()
        Log.d("AlertService", "Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 🟢 Step 1: Create persistent notification to keep service alive
    private fun startForegroundServiceNotification() {
        val channelId = "alert_listener_channel"
        val channel = NotificationChannel(
            channelId,
            "Alert Listener",
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("DontMissMOM active")
            .setContentText("Listening for emergency alerts")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    // 🟡 Step 2: Start listening for alerts in Firestore
    private fun listenForIncomingAlerts() {
        val user = auth.currentUser
        if (user == null) {
            Log.e("AlertService", "No logged in user")
            stopSelf()
            return
        }

        val uid = user.uid
        Log.d("AlertService", "Listening for alerts for UID: $uid")

        listenerRegistration = firestore.collection("alerts")
            .document(uid)
            .collection("incoming")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("AlertService", "Error listening: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                for (change in snapshot.documentChanges) {
                    if (change.type.name == "ADDED") {
                        val doc = change.document
                        val senderName = doc.getString("senderName") ?: "Unknown"
                        val senderPhone = doc.getString("receiverPhone") ?: "N/A"

                        Log.d("AlertService", "🚨 Incoming alert from $senderName ($senderPhone)")

                        // Wake the screen before launching popup
                        wakeScreen()

                        // Launch popup activity
                        val popupIntent = Intent(this, AlertPopupActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            putExtra("senderName", senderName)
                            putExtra("senderPhone", senderPhone)
                        }
                        startActivity(popupIntent)
                    }
                }
            }
    }

    // 🔔 Step 3: Wake screen for visibility
    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "DontMissMOM::WakeLock"
            )
            wakeLock.acquire(5000) // wake for 5 seconds
        } catch (e: Exception) {
            Log.e("AlertService", "WakeLock failed: ${e.message}")
        }
    }
}
