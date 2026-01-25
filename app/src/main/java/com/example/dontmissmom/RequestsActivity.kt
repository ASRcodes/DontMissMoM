package com.example.dontmissmom

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore

class RequestsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: RequestsAdapter
    private val items = mutableListOf<RequestItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_requests)

        recycler = findViewById(R.id.recyclerRequests)

        adapter = RequestsAdapter(
            items,
            onAccept = { id, req -> acceptRequest(id, req) },
            onReject = { id -> rejectRequest(id) },
            onReport = { req -> showReportDialog(req) }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        listenForRequests()
    }

    private fun listenForRequests() {
        val me = auth.currentUser ?: return

        firestore.collection("requests")
            .whereEqualTo("toUid", me.uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snaps, e ->
                if (e != null) {
                    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                snaps?.documentChanges?.forEach { dc ->
                    val d = dc.document
                    val req = RequestItem(
                        id = d.id,
                        fromUid = d.getString("fromUid") ?: "",
                        fromUsername = d.getString("fromUsername") ?: "",
                        fromPhone = d.getString("fromPhone") ?: "",
                        toUid = d.getString("toUid") ?: "",
                        toPhone = d.getString("toPhone") ?: ""
                    )

                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            items.add(0, req)
                            adapter.notifyItemInserted(0)
                        }
                        DocumentChange.Type.REMOVED,
                        DocumentChange.Type.MODIFIED -> {
                            adapter.setData(snaps.documents.map {
                                RequestItem(
                                    id = it.id,
                                    fromUid = it.getString("fromUid") ?: "",
                                    fromUsername = it.getString("fromUsername") ?: "",
                                    fromPhone = it.getString("fromPhone") ?: "",
                                    toUid = it.getString("toUid") ?: "",
                                    toPhone = it.getString("toPhone") ?: ""
                                )
                            })
                        }
                    }
                }
            }
    }

    // ---------------- ACCEPT ----------------

    private fun acceptRequest(id: String, req: RequestItem) {
        val me = auth.currentUser ?: return
        val db = firestore

        val meRef = db.collection("users").document(me.uid)
        val fromRef = db.collection("users").document(req.fromUid)

        meRef.get().addOnSuccessListener { meDoc ->
            val myName = meDoc.getString("username") ?: "User"
            val myPhone = meDoc.getString("phone") ?: ""

            val batch = db.batch()

            batch.set(
                meRef.collection("trustedContacts").document(req.fromUid),
                mapOf(
                    "username" to req.fromUsername,
                    "phone" to req.fromPhone,
                    "addedAt" to System.currentTimeMillis()
                )
            )

            batch.set(
                fromRef.collection("trustedContacts").document(me.uid),
                mapOf(
                    "username" to myName,
                    "phone" to myPhone,
                    "addedAt" to System.currentTimeMillis()
                )
            )

            batch.update(db.collection("requests").document(id), "status", "accepted")

            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "✅ Request accepted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rejectRequest(id: String) {
        firestore.collection("requests").document(id)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------------- REPORT ----------------

    private fun showReportDialog(req: RequestItem) {
        val options = arrayOf(
            "Unknown spam",
            "Fake account",
            "Harassment",
            "Other"
        )

        AlertDialog.Builder(this)
            .setTitle("Report user")
            .setItems(options) { _, which ->
                if (options[which] == "Other") {
                    showCustomReport(req)
                } else {
                    submitReport(req, options[which])
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomReport(req: RequestItem) {
        val input = EditText(this)
        input.hint = "Describe the issue"

        AlertDialog.Builder(this)
            .setTitle("Custom report")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                submitReport(req, input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(req: RequestItem, reason: String) {
        val report = hashMapOf(
            "reportedBy" to (auth.currentUser?.uid ?: ""),
            "reportedUserUid" to req.fromUid,
            "reportedUserName" to req.fromUsername,
            "reportedUserPhone" to req.fromPhone,
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("reports")
            .add(report)
            .addOnSuccessListener {
                Toast.makeText(this, "🚨 Report submitted", Toast.LENGTH_SHORT).show()
            }
    }
}
