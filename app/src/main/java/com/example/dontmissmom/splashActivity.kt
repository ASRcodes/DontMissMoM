package com.example.dontmissmom

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple splash: check login state and redirect
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            // already signed in -> main
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            // not signed in -> open Register (first time)
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        finish()
    }
}
