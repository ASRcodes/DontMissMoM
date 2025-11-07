package com.example.dontmissmom

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AlertPopupActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var tvSenderName: TextView
    private lateinit var tvSenderPhone: TextView
    private lateinit var tvAlertMessage: TextView
    private lateinit var btnCallBack: Button
    private lateinit var btnDismiss: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_popup)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        tvSenderName = findViewById(R.id.tvSenderName)
        tvSenderPhone = findViewById(R.id.tvSenderPhone)
        tvAlertMessage = findViewById(R.id.tvAlertMessage)
        btnCallBack = findViewById(R.id.btnCallBack)
        btnDismiss = findViewById(R.id.btnDismiss)

        val senderName = intent.getStringExtra("senderName") ?: "Unknown"
        val senderPhone = intent.getStringExtra("senderPhone") ?: "Unknown"

        tvSenderName.text = "From: $senderName"
        tvSenderPhone.text = "Phone: $senderPhone"
        tvAlertMessage.text = "⚠️ $senderName is in an emergency situation. Please call back immediately!"

        // Play loud alarm
        mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_ALARM_ALERT_URI)
        mediaPlayer.isLooping = true
        mediaPlayer.start()

        btnCallBack.setOnClickListener {
            mediaPlayer.stop()
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$senderPhone"))
            startActivity(intent)
            finish()
        }

        btnDismiss.setOnClickListener {
            mediaPlayer.stop()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.release()
        }
    }
}
