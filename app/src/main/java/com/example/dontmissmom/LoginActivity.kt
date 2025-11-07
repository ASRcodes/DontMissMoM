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
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var etPhone: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var etOtp: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var btnResendOtp: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvGoToRegister: TextView

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etPhone = findViewById(R.id.etPhone)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        etOtp = findViewById(R.id.etOtp)
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp)
        btnResendOtp = findViewById(R.id.btnResendOtp)
        progressBar = findViewById(R.id.progressBar)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)

        auth = FirebaseAuth.getInstance()

        btnSendOtp.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isEmpty()) {
                etPhone.error = "Enter phone"
                return@setOnClickListener
            }
            sendOtp(formatPhone(phone))
        }

        btnVerifyOtp.setOnClickListener {
            val code = etOtp.text.toString().trim()
            val vId = verificationId
            if (code.isEmpty() || vId == null) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            progressBar.visibility = View.VISIBLE
            val credential = PhoneAuthProvider.getCredential(vId, code)
            signInWithCredential(credential)
        }

        btnResendOtp.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            resendToken?.let {
                resendOtp(formatPhone(phone), it)
            } ?: sendOtp(formatPhone(phone))
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }

    private fun formatPhone(raw: String): String {
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
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "OTP error: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(s: String, token: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    verificationId = s
                    resendToken = token
                    Toast.makeText(this@LoginActivity, "OTP Sent", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@LoginActivity, "Resend failed: ${e.message}", Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(id: String, token2: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    verificationId = id
                    resendToken = token2
                    Toast.makeText(this@LoginActivity, "OTP resent", Toast.LENGTH_SHORT).show()
                }
            })
            .setForceResendingToken(token)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // login success -> go to main
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }
}
