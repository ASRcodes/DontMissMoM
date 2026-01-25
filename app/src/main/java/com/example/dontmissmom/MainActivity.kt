package com.example.dontmissmom

import android.Manifest
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
class MainActivity : BaseActivity() {

    // UI Components
    private lateinit var switchSiren: MaterialSwitch
    private lateinit var tvSirenStatus: TextView
    private val PREFS_NAME = "DontMissMomPrefs"
    private lateinit var tvWelcome: TextView
    private lateinit var tvPhone: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var btnLogout: ImageView
    private lateinit var btnAddContact: Button
    private lateinit var btnEmergencyAll: Button
    private lateinit var recyclerTrusted: RecyclerView
    private lateinit var requestBadge: View

    // Logic & Firebase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val trustedList = mutableListOf<TrustedContact>()
    private lateinit var adapter: TrustedContactAdapter
    private lateinit var restartReceiver: BroadcastReceiver

    // Location Client
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Google Client (For proper Logout)
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Setup the Siren Logic
        setupSirenSwitch()

        // Initialize Location Services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // --- NEW: Initialize Google Client for Logout ---
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        bindViews()
        setupRecycler()

        // Permissions
        checkAllPermissions()
        requestDndPermission()
        ensureNotificationsEnabled()
        tryRequestIgnoreBatteryOptimizations()

        // Load Data
        checkUserAndLoadData()
        setupButtons()
        saveFcmTokenToFirestore()

        // Start Background Listener
        startBackgroundService()

        // Receiver to restart service
        restartReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                startBackgroundService()
            }
        }

        val filter = IntentFilter("RESTART_ALERT_LISTENER")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(restartReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(restartReceiver, filter)
        }
    }


    // ---------------- UI BINDING ----------------

    private fun bindViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvPhone = findViewById(R.id.tvPhone)
        imgProfile = findViewById(R.id.imgProfile)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnEmergencyAll = findViewById(R.id.btnEmergency)
        recyclerTrusted = findViewById(R.id.recyclerTrustedContacts)
        requestBadge = findViewById(R.id.requestBadge)

        findViewById<View>(R.id.btnNotifications).setOnClickListener {
            requestBadge.visibility = View.GONE
            startActivity(Intent(this, RequestsActivity::class.java))
        }
    }

    private fun setupRecycler() {
        adapter = TrustedContactAdapter(
            mutableListOf(),
            onEmergencyClick = { contact -> sendOnlineAlert(contact) },
            onLongPress = { contact -> showContactOptions(contact) }
        )
        recyclerTrusted.layoutManager = LinearLayoutManager(this)
        recyclerTrusted.adapter = adapter
    }

    // ---------------- PERMISSIONS ----------------

    private fun checkAllPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    private fun requestDndPermission() {
        val nm = getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun ensureNotificationsEnabled() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun tryRequestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:$packageName")))
            }
        }
    }

    // ---------------- DATA LOADING ----------------

    private fun saveFcmTokenToFirestore() {
        val user = auth.currentUser ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val data = mapOf("fcmToken" to token, "lastTokenUpdate" to System.currentTimeMillis())
            firestore.collection("users").document(user.uid).update(data)
        }
    }

    private fun checkUserAndLoadData() {
        val user = auth.currentUser ?: return

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val name = doc.getString("username") ?: "User"
                    val phone = doc.getString("phone") ?: "N/A"
                    val photoUrl = doc.getString("photoUrl")

                    tvWelcome.text = "Hi, $name"
                    tvPhone.text = phone

                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .placeholder(R.mipmap.ic_launcher_round)
                            .circleCrop()
                            .into(imgProfile)
                    }
                }
            }

        firestore.collection("users").document(user.uid).collection("trustedContacts")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map {
                    TrustedContact(
                        uid = it.id,
                        username = it.getString("username") ?: "",
                        phone = it.getString("phone") ?: "",
                        blocked = it.getBoolean("blocked") ?: false
                    )
                } ?: emptyList()

                trustedList.clear()
                trustedList.addAll(list)
                adapter.setData(trustedList)
            }

        firestore.collection("requests")
            .whereEqualTo("toUid", user.uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                requestBadge.visibility = if (snap != null && !snap.isEmpty) View.VISIBLE else View.GONE
            }
    }

    // ---------------- BUTTON ACTIONS ----------------

    private fun setupButtons() {
        // --- UPDATED LOGOUT LOGIC ---
        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->

                    // 1. Sign out from Firebase
                    auth.signOut()

                    // 2. Sign out from Google Client (Forces account chooser next time)
                    googleSignInClient.signOut().addOnCompleteListener {
                        val intent = Intent(this, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnAddContact.setOnClickListener { showAddContactDialog() }

        btnEmergencyAll.setOnClickListener {
            if (trustedList.isEmpty()) {
                Toast.makeText(this, "Add contacts first!", Toast.LENGTH_SHORT).show()
            } else {
                trustedList.forEach { sendOnlineAlert(it) }
            }
        }
    }

    // ---------------- CONTACT MANAGEMENT ----------------

    private fun showAddContactDialog() {
        val input = EditText(this)
        input.hint = "Phone Number (+91...)"
        AlertDialog.Builder(this)
            .setTitle("Add Trusted Contact")
            .setView(input)
            .setPositiveButton("Send Request") { _, _ ->
                findUserByPhoneAndSendRequest(formatPhone(input.text.toString()))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatPhone(raw: String): String {
        val clean = raw.replace("\\s".toRegex(), "")
        return if (clean.length == 10 && !clean.startsWith("+")) "+91$clean" else clean
    }

    private fun findUserByPhoneAndSendRequest(phone: String) {
        val me = auth.currentUser ?: return
        firestore.collection("users").whereEqualTo("phone", phone).get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "User not found with this number", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val targetDoc = result.documents[0]

                firestore.collection("users").document(me.uid).get().addOnSuccessListener { myDoc ->
                    val requestData = hashMapOf(
                        "fromUid" to me.uid,
                        "fromUsername" to (myDoc.getString("username") ?: "Unknown"),
                        "fromPhone" to (myDoc.getString("phone") ?: ""),
                        "toUid" to targetDoc.id,
                        "toPhone" to (targetDoc.getString("phone") ?: ""),
                        "status" to "pending",
                        "timestamp" to System.currentTimeMillis()
                    )
                    firestore.collection("requests").add(requestData)
                        .addOnSuccessListener { Toast.makeText(this, "Request sent!", Toast.LENGTH_SHORT).show() }
                }
            }
    }

    private fun showContactOptions(contact: TrustedContact) {
        val actionText = if (contact.blocked) "Unblock" else "Block"
        AlertDialog.Builder(this)
            .setTitle(contact.username)
            .setPositiveButton(actionText) { _, _ -> blockTrusted(contact, !contact.blocked) }
            .setNeutralButton("Delete") { _, _ -> deleteTrusted(contact) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun blockTrusted(contact: TrustedContact, shouldBlock: Boolean) {
        val me = auth.currentUser ?: return
        firestore.collection("users").document(me.uid)
            .collection("trustedContacts").document(contact.uid)
            .update("blocked", shouldBlock)
            .addOnSuccessListener { Toast.makeText(this, "Updated", Toast.LENGTH_SHORT).show() }
    }

    private fun deleteTrusted(contact: TrustedContact) {
        val me = auth.currentUser ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Contact")
            .setMessage("Remove this contact permanently?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("users").document(me.uid).collection("trustedContacts").document(contact.uid).delete()
                firestore.collection("users").document(contact.uid).collection("trustedContacts").document(me.uid).delete()
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------------- EMERGENCY SENDING (WITH LOCATION) ----------------

// ---------------- EMERGENCY SENDING (WITH FRESH LOCATION) ----------------

    private fun sendOnlineAlert(contact: TrustedContact) {
        val me = auth.currentUser ?: return

        firestore.collection("users").document(contact.uid)
            .collection("trustedContacts").document(me.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && (doc.getBoolean("blocked") == true)) {
                    Toast.makeText(this, "You are blocked by ${contact.username}", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Check Permissions
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                    // ⚡ FORCE FRESH LOCATION UPDATE
                    // "lastLocation" is often null or old. "getCurrentLocation" turns on the GPS chip.
                    val cancellationToken = CancellationTokenSource()

                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationToken.token
                    ).addOnSuccessListener { loc ->
                        if (loc != null) {
                            prepareAndSendAlert(contact, loc.latitude, loc.longitude)
                        } else {
                            // If GPS is on but can't get fix inside a building, try last known
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                prepareAndSendAlert(contact, lastLoc?.latitude ?: 0.0, lastLoc?.longitude ?: 0.0)
                            }
                        }
                    }.addOnFailureListener {
                        // GPS Failed entirely (maybe turned off), send alert without location
                        prepareAndSendAlert(contact, 0.0, 0.0)
                    }
                } else {
                    // No Permission granted, send alert without location
                    prepareAndSendAlert(contact, 0.0, 0.0)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to verify contact", Toast.LENGTH_SHORT).show()
            }
    }

    private fun prepareAndSendAlert(contact: TrustedContact, lat: Double, lng: Double) {
        val me = auth.currentUser ?: return
        firestore.collection("users").document(me.uid).get().addOnSuccessListener { myDoc ->

            val payload: HashMap<String, Any> = hashMapOf(
                "toUid" to contact.uid,
                "fromName" to (myDoc.getString("username") ?: "Unknown"),
                "fromPhone" to (myDoc.getString("phone") ?: ""),
                "latitude" to lat,
                "longitude" to lng
            )

            sendEmergencyToBackend(payload)
            Toast.makeText(this, "🚨 SOS sent to ${contact.username}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmergencyToBackend(data: HashMap<String, Any>) {
        val url = "https://dontmissmom-backend.onrender.com/sendEmergency"
        Thread {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.write(JSONObject(data as Map<*, *>).toString().toByteArray())
                conn.responseCode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    // ---------------- SERVICE ----------------

    private fun startBackgroundService() {
        val intent = Intent(this, AlertListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(restartReceiver) } catch (e: Exception) {}
        super.onDestroy()
    }
    override fun onResume() {
        super.onResume()
        // 2. Check if the 6 hours have passed every time user opens app
        checkAutoEnableSiren()
    }

    private fun setupSirenSwitch() {
        switchSiren = findViewById(R.id.switchSiren)
        tvSirenStatus = findViewById(R.id.tvSirenStatus)

        // Load saved state
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isSirenEnabled = prefs.getBoolean("siren_enabled", true)

        updateSirenUI(isSirenEnabled)

        // Handle Click
        switchSiren.setOnClickListener {
            val isChecked = switchSiren.isChecked
            if (!isChecked) {
                // User is trying to turn it OFF -> Show Warning Dialog
                // Reset switch to ON temporarily until they confirm in dialog
                switchSiren.isChecked = true
                showDisableWarningDialog()
            } else {
                // User is turning it ON -> Just enable it
                enableSiren(prefs)
            }
        }
    }

    private fun showDisableWarningDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_disable_siren, null)
        val checkboxPermanent = dialogView.findViewById<CheckBox>(R.id.checkboxPermanent)

        AlertDialog.Builder(this)
            .setTitle("⚠️ Disable Siren Sound?")
            .setView(dialogView)
            .setPositiveButton("Turn Off") { _, _ ->
                val isPermanent = checkboxPermanent.isChecked
                disableSiren(isPermanent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Do nothing, switch remains ON
            }
            .setCancelable(false)
            .show()
    }

    private fun disableSiren(permanent: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putBoolean("siren_enabled", false)
        editor.putBoolean("siren_permanent_off", permanent)

        if (!permanent) {
            // Save current time for the 6-hour timer
            editor.putLong("siren_off_timestamp", System.currentTimeMillis())
        }

        editor.apply()
        updateSirenUI(false)
        Toast.makeText(this, "Siren Disabled. Phone will vibrate only.", Toast.LENGTH_LONG).show()
    }

    private fun enableSiren(prefs: SharedPreferences) {
        val editor = prefs.edit()
        editor.putBoolean("siren_enabled", true)
        editor.putBoolean("siren_permanent_off", false)
        editor.putLong("siren_off_timestamp", 0)
        editor.apply()

        updateSirenUI(true)
    }

    private fun checkAutoEnableSiren() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("siren_enabled", true)
        val isPermanent = prefs.getBoolean("siren_permanent_off", false)
        val offTime = prefs.getLong("siren_off_timestamp", 0)

        // If it's OFF, NOT Permanent, and 6 Hours (21600000 ms) have passed
        if (!isEnabled && !isPermanent) {
            val sixHoursMs = 6 * 60 * 60 * 1000
            if (System.currentTimeMillis() - offTime > sixHoursMs) {
                // Auto-Enable
                enableSiren(prefs)
                Toast.makeText(this, "Safety Timer: Siren automatically re-enabled.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateSirenUI(isEnabled: Boolean) {
        switchSiren.isChecked = isEnabled
        if (isEnabled) {
            tvSirenStatus.text = "Siren Active (Sound + Vibrate)"
            tvSirenStatus.setTextColor(getColor(R.color.white))
        } else {
            tvSirenStatus.text = "Siren OFF (Vibrate Only)"
            tvSirenStatus.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }
}