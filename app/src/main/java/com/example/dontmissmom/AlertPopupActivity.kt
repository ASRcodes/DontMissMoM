package com.example.dontmissmom

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class AlertPopupActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvSenderName: TextView
    private lateinit var tvSenderPhone: TextView
    private lateinit var tvLocationCoordinates: TextView
    private lateinit var btnMap: Button
    private lateinit var btnCallBack: Button
    private lateinit var btnDismiss: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        showOnLockScreen() // Helper method to handle lock screen logic
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alert_popup)

        // 1. Bind Views
        tvSenderName = findViewById(R.id.tvSenderName)
        tvSenderPhone = findViewById(R.id.tvSenderPhone)
        tvLocationCoordinates = findViewById(R.id.tvLocationCoordinates)
        btnMap = findViewById(R.id.btnMap)
        btnCallBack = findViewById(R.id.btnCallBack)
        btnDismiss = findViewById(R.id.btnDismiss)

        // 2. Get Data from Intent
        val senderName = intent.getStringExtra("senderName") ?: "Unknown User"
        val senderPhone = intent.getStringExtra("senderPhone") ?: ""
        val lat = intent.getDoubleExtra("latitude", 0.0)
        val lng = intent.getDoubleExtra("longitude", 0.0)

        // 3. Update UI
        tvSenderName.text = senderName
        tvSenderPhone.text = senderPhone

        // 4. Handle Location Logic
        if (lat != 0.0 && lng != 0.0) {
            // Location Found
            tvLocationCoordinates.text = String.format("%.5f, %.5f", lat, lng)
            btnMap.visibility = View.VISIBLE

            btnMap.setOnClickListener {
                // Open Google Maps to specific coordinates with a marker
                // "geo:lat,lng?q=lat,lng(Label)"
                val uri = "geo:$lat,$lng?q=$lat,$lng($senderName's Location)"
                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                mapIntent.setPackage("com.google.android.apps.maps")

                // Verify if Maps is installed to prevent crash
                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    // Fallback to browser if no Maps app
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
                    startActivity(browserIntent)
                }
            }
        } else {
            // Location Missing
            tvLocationCoordinates.text = "Location unavailable"
            btnMap.visibility = View.GONE
        }

        // 5. Call Back Button
        btnCallBack.setOnClickListener {
            stopSiren()
            if (senderPhone.isNotEmpty()) {
                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$senderPhone"))
                startActivity(callIntent)
            }
            finish()
        }

        // 6. Dismiss Button
        btnDismiss.setOnClickListener {
            stopSiren()
            finish()
        }
    }

    // --- HELPER FUNCTIONS ---

    private fun showOnLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    private fun stopSiren() {
        val intent = Intent(this, EmergencySoundService::class.java).apply {
            action = EmergencySoundService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onBackPressed() {
        stopSiren()
        super.onBackPressed()
    }

    override fun onDestroy() {
        stopSiren()
        super.onDestroy()
    }
}