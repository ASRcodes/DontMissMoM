package com.example.dontmissmom

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class RegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPhone: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var etOtp: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnResendOtp: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvGoToLogin: TextView

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUsername = findViewById(R.id.etUsername)
        etPhone = findViewById(R.id.etPhone)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        etOtp = findViewById(R.id.etOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnResendOtp = findViewById(R.id.btnResendOtp)
        progressBar = findViewById(R.id.progressBar)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)

        auth = FirebaseAuth.getInstance()

        btnSendOtp.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isEmpty()) {
                etPhone.error = "Enter phone"
                return@setOnClickListener
            }
            // assume indian +91 if user enters 10 digits
            val phoneWithCode = formatPhone(phone)
            sendOtp(phoneWithCode)
        }

        btnVerifyOtp.setOnClickListener {
            val code = etOtp.text.toString().trim()
            val vId = verificationId
            if (code.isEmpty() || vId == null) {
                Toast.makeText(this, "Enter OTP or request OTP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressBar.visibility = View.VISIBLE
            val credential = PhoneAuthProvider.getCredential(vId, code)
            signInWithCredential(credential)
        }

        btnResendOtp.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            val phoneWithCode = formatPhone(phone)
            resendToken?.let {
                resendOtp(phoneWithCode, it)
            } ?: run {
                // If no token, fall back to normal send
                sendOtp(formatPhone(phone))
            }
        }

        tvGoToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun formatPhone(raw: String): String {
        // quick convenience: if user enters 10 digits assume +91
        val cleaned = raw.replace("\\s".toRegex(), "")
        return if (cleaned.length == 10 && !cleaned.startsWith("+")) "+91$cleaned" else cleaned
    }

    private fun sendOtp(phoneWithCode: String) {
        progressBar.visibility = View.VISIBLE
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneWithCode)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // auto-retrieval or instant verification
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "OTP failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    verificationId = id
                    resendToken = token
                    Toast.makeText(this@RegisterActivity, "OTP sent", Toast.LENGTH_SHORT).show()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun resendOtp(phoneWithCode: String, token: PhoneAuthProvider.ForceResendingToken) {
        progressBar.visibility = View.VISIBLE
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneWithCode)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@RegisterActivity, "Resend failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(id: String, token2: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    verificationId = id
                    resendToken = token2
                    Toast.makeText(this@RegisterActivity, "OTP resent", Toast.LENGTH_SHORT).show()
                }
            })
            .setForceResendingToken(token)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        progressBar.visibility = View.VISIBLE
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val uid = firebaseUser.uid
                        val phone = firebaseUser.phoneNumber ?: etPhone.text.toString().trim()
                        val username = etUsername.text.toString().trim().ifEmpty { "User" }

                        val userMap = hashMapOf(
                            "uid" to uid,
                            "username" to username,
                            "phone" to phone
                        )

                        // Save user to Firestore
                        firestore.collection("users").document(uid)
                            .set(userMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                                navigateToMain()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save user: ${e.message}", Toast.LENGTH_SHORT).show()
                                navigateToMain() // even if save fails, continue
                            }
                    } else {
                        Toast.makeText(this, "User not found after sign-in!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Verification failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}
