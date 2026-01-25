package com.example.dontmissmom

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback // 👈 FIXED: Added Import
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient // 👈 FIXED: Added Import
import com.google.android.gms.auth.api.signin.GoogleSignInOptions // 👈 FIXED: Added Import
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PhoneLinkActivity : BaseActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // 👈 FIXED: Declare the variable here
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_link)

        // 👈 FIXED: Initialize the Google Client here!
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etPhone = findViewById<EditText>(R.id.etPhoneLink)
        val btnSave = findViewById<Button>(R.id.btnSavePhone)
        val tvDiffAccount = findViewById<TextView>(R.id.tvDifferentAccount)

        btnSave.setOnClickListener {
            val rawPhone = etPhone.text.toString().trim()

            // Basic Validation
            if (rawPhone.length != 10) {
                etPhone.error = "Please enter valid 10-digit number"
                return@setOnClickListener
            }

            saveCompleteProfile(rawPhone)
        }

        tvDiffAccount.setOnClickListener {
            performLogout()
        }

        // Handle System Back Button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                performLogout()
            }
        })
    }

    private fun performLogout() {
        // Sign out from Firebase
        auth.signOut()

        // Sign out from Google (So the account chooser appears again)
        googleSignInClient.signOut().addOnCompleteListener {
            val intent = Intent(this, LoginActivity::class.java)
            // Clear the stack so they can't go back to this screen
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun saveCompleteProfile(phoneInput: String) {
        val user = auth.currentUser ?: return

        // Format to +91
        val formattedPhone = "+91$phoneInput"

        // Create the Profile Object
        val userProfile = hashMapOf(
            "uid" to user.uid,
            "username" to (user.displayName ?: "User"), // Auto-filled from Google!
            "email" to (user.email ?: ""),       // Verified from Google!
            "phone" to formattedPhone,           // Validated from Input!
            "photoUrl" to (user.photoUrl?.toString() ?: ""),
            "createdAt" to System.currentTimeMillis()
        )

        // Save to Firestore
        db.collection("users").document(user.uid)
            .set(userProfile)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile Completed!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}