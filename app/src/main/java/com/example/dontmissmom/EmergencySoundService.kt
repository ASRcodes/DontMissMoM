package com.example.dontmissmom

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager // For Android 12+
import androidx.core.app.NotificationCompat
import android.os.VibrationAttributes
class EmergencySoundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_SIREN"
        const val ACTION_STOP = "ACTION_STOP_SIREN"
        const val NOTIFICATION_ID = 1001
        const val PREFS_NAME = "DontMissMomPrefs"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startAlert()
            ACTION_STOP -> {
                stopAlert()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlert() {
        if (mediaPlayer?.isPlaying == true) return

        // 1. Start Foreground Notification immediately
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        // 2. Initialize Vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 3. CHECK TOGGLE STATE 🎚️
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSirenEnabled = prefs.getBoolean("siren_enabled", true)

        if (isSirenEnabled) {
            // ✅ CASE A: Play Sound + Vibrate
            playLoudSiren()
            startHeavyVibration()
        } else {
            // 🔇 CASE B: Vibrate Only (Library Mode)
            startHeavyVibration()
        }
    }

    private fun playLoudSiren() {
        // Max Volume Logic
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0
        )

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(
                this@EmergencySoundService,
                Uri.parse("android.resource://${packageName}/${R.raw.siren}")
            )
            isLooping = true
            prepare()
            start()
        }
    }

    private fun startHeavyVibration() {
        // Pattern: Wait 0ms, Vibrate 500ms, Sleep 200ms, Vibrate 500ms...
        val pattern = longArrayOf(0, 500, 200, 500, 200, 1000, 400)

        if (vibrator?.hasVibrator() == true) {

            // ANDROID 13 (API 33) OR NEWER (Includes Android 14/15)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    vibrationAttributes
                )
            }
            // ANDROID 8 (Oreo) TO ANDROID 12
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Here we use the old way, but AudioAttributes handles the priority
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                vibrator?.vibrate(
                    VibrationEffect.createWaveform(pattern, 0),
                    audioAttributes // 👈 Passing AudioAttributes creates the right VibrationAttributes internally
                )
            }
            // OLDER PHONES
            else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        }
    }

    private fun stopAlert() {
        // Stop Sound
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        // Stop Vibration
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "emergency_sound_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Emergency Siren",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setBypassDnd(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        val deleteIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, StopSirenReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🚨 Emergency Alert")
            .setContentText("Emergency alert active") // Changed text to be generic
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(false)
            .setDeleteIntent(deleteIntent)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopAlert()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stopAlert()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}