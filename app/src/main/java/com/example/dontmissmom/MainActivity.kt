package com.example.dontmissmom

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var tvWelcome: TextView
    private lateinit var tvPhone: TextView
    private lateinit var btnNotifications: ImageView
    private lateinit var btnLogout: ImageView
    private lateinit var btnAddContact: Button
    private lateinit var btnViewRequests: Button
    private lateinit var btnEmergencyAll: Button
    private lateinit var recyclerTrusted: RecyclerView

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val trustedList = mutableListOf<TrustedContact>()
    private lateinit var adapter: TrustedContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔔 Request DND permission
        requestDndPermission()

        // 🩺 Start listener service for alerts
        startService(Intent(this, AlertListenerService::class.java))

        bindViews()
        setupRecycler()
        checkUserAndLoadData()
        setupButtons()
    }

    // Step 1: DND permission request
    private fun requestDndPermission() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun bindViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvPhone = findViewById(R.id.tvPhone)
        btnNotifications = findViewById(R.id.btnNotifications)
        btnLogout = findViewById(R.id.btnLogout)
        btnAddContact = findViewById(R.id.btnAddContact)
        btnViewRequests = findViewById(R.id.btnViewRequests)
        btnEmergencyAll = findViewById(R.id.btnEmergency)
        recyclerTrusted = findViewById(R.id.recyclerTrustedContacts)
    }

    private fun setupRecycler() {
        adapter = TrustedContactAdapter(mutableListOf(),
            onEmergencyClick = { contact -> sendAlertToContact(contact) },
            onItemClick = { contact ->
                Toast.makeText(this, "Contact: ${contact.username}", Toast.LENGTH_SHORT).show()
            })
        recyclerTrusted.layoutManager = LinearLayoutManager(this)
        recyclerTrusted.adapter = adapter
    }

    private fun checkUserAndLoadData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
            return
        }

        val uid = currentUser.uid
        val phone = currentUser.phoneNumber ?: "N/A"
        tvPhone.text = "Phone: $phone"

        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val username = doc.getString("username") ?: "User"
                tvWelcome.text = "Welcome, $username"
            }
            .addOnFailureListener { tvWelcome.text = "Welcome!" }

        firestore.collection("users").document(uid)
            .collection("trustedContacts")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Toast.makeText(this, "Failed to load trusted contacts", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                val list = mutableListOf<TrustedContact>()
                snap?.documents?.forEach { d ->
                    val cUid = d.id
                    val name = d.getString("username") ?: ""
                    val phoneVal = d.getString("phone") ?: ""
                    list.add(TrustedContact(uid = cUid, username = name, phone = phoneVal))
                }
                trustedList.clear()
                trustedList.addAll(list)
                adapter.setData(trustedList)
            }
    }

    private fun setupButtons() {
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        btnNotifications.setOnClickListener {
            try {
                startActivity(Intent(this, RequestsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Requests screen not implemented yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnAddContact.setOnClickListener { showAddContactDialog() }
        btnViewRequests.setOnClickListener {
            try {
                startActivity(Intent(this, RequestsActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Requests screen not implemented yet", Toast.LENGTH_SHORT).show()
            }
        }

        btnEmergencyAll.setOnClickListener { triggerEmergencyToAll() }
    }

    private fun showAddContactDialog() {
        val input = EditText(this)
        input.hint = "Enter contact phone (E.g. +911234567890)"

        AlertDialog.Builder(this)
            .setTitle("Add Trusted Contact")
            .setView(input)
            .setPositiveButton("Send Request") { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) {
                    Toast.makeText(this, "Enter a phone number", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val phone = formatPhone(raw)
                findUserByPhoneAndSendRequest(phone)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatPhone(raw: String): String {
        val cleaned = raw.replace("\\s".toRegex(), "")
        return if (cleaned.length == 10 && !cleaned.startsWith("+")) "+91$cleaned" else cleaned
    }

    private fun findUserByPhoneAndSendRequest(targetPhone: String) {
        val me = auth.currentUser ?: return
        firestore.collection("users")
            .whereEqualTo("phone", targetPhone)
            .limit(1)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    Toast.makeText(this, "User not found: $targetPhone", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val doc = result.documents[0]
                val targetUid = doc.id
                val meUid = me.uid
                firestore.collection("users").document(meUid).get()
                    .addOnSuccessListener { meDoc ->
                        val myName = meDoc.getString("username") ?: "User"
                        val myPhone = meDoc.getString("phone") ?: me.phoneNumber ?: ""
                        val request = hashMapOf(
                            "fromUid" to meUid,
                            "fromUsername" to myName,
                            "fromPhone" to myPhone,
                            "toUid" to targetUid,
                            "toPhone" to targetPhone,
                            "status" to "pending",
                            "timestamp" to System.currentTimeMillis()
                        )
                        firestore.collection("requests").add(request)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Request sent", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendAlertToContact(contact: TrustedContact) {
        val me = auth.currentUser ?: return
        val meUid = me.uid
        firestore.collection("users").document(meUid).get()
            .addOnSuccessListener { doc ->
                val fromName = doc.getString("username") ?: "User"
                val alert = hashMapOf(
                    "senderUid" to meUid,
                    "senderName" to fromName,
                    "receiverUid" to contact.uid,
                    "receiverPhone" to contact.phone,
                    "timestamp" to System.currentTimeMillis(),
                    "type" to "emergency"
                )
                firestore.collection("alerts").document(contact.uid)
                    .collection("incoming").add(alert)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Alert sent to ${contact.username}", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    private fun triggerEmergencyToAll() {
        val me = auth.currentUser ?: return
        val uid = me.uid
        firestore.collection("users").document(uid)
            .collection("trustedContacts").get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    Toast.makeText(this, "No trusted contacts", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                firestore.collection("users").document(uid).get()
                    .addOnSuccessListener { meDoc ->
                        val fromName = meDoc.getString("username") ?: "User"
                        for (d in snap.documents) {
                            val contactUid = d.id
                            val contactPhone = d.getString("phone") ?: ""
                            val alert = hashMapOf(
                                "senderUid" to uid,
                                "senderName" to fromName,
                                "receiverUid" to contactUid,
                                "receiverPhone" to contactPhone,
                                "timestamp" to System.currentTimeMillis(),
                                "type" to "emergency"
                            )
                            firestore.collection("alerts")
                                .document(contactUid)
                                .collection("incoming").add(alert)
                        }
                        Toast.makeText(this, "🚨 Alerts sent to all trusted contacts", Toast.LENGTH_SHORT).show()
                    }
            }
    }
}
