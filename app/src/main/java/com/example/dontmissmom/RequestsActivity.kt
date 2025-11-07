package com.example.dontmissmom

import android.os.Bundle
import android.widget.Toast
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
        adapter = RequestsAdapter(items,
            onAccept = { requestDocId, req -> acceptRequest(requestDocId, req) },
            onReject = { requestDocId -> rejectRequest(requestDocId) }
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
                    Toast.makeText(this, "Failed to load requests: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                snaps?.documentChanges?.forEach { dc ->
                    when (dc.type) {
                        DocumentChange.Type.ADDED -> {
                            val d = dc.document
                            val req = RequestItem(
                                id = d.id,
                                fromUid = d.getString("fromUid") ?: "",
                                fromUsername = d.getString("fromUsername") ?: "",
                                fromPhone = d.getString("fromPhone") ?: "",
                                toUid = d.getString("toUid") ?: "",
                                toPhone = d.getString("toPhone") ?: ""
                            )
                            items.add(0, req)
                            adapter.notifyItemInserted(0)
                        }
                        DocumentChange.Type.REMOVED, DocumentChange.Type.MODIFIED -> {
                            // reload list simply
                            adapter.setData(snaps.documents.map { doc ->
                                RequestItem(
                                    id = doc.id,
                                    fromUid = doc.getString("fromUid") ?: "",
                                    fromUsername = doc.getString("fromUsername") ?: "",
                                    fromPhone = doc.getString("fromPhone") ?: "",
                                    toUid = doc.getString("toUid") ?: "",
                                    toPhone = doc.getString("toPhone") ?: ""
                                )
                            })
                        }
                    }
                }
            }
    }

    private fun acceptRequest(requestDocId: String, req: RequestItem) {
        val me = auth.currentUser ?: return
        val db = firestore

        // 1) Add from user as my trusted contact
        val contactForMe = hashMapOf(
            "uid" to req.fromUid,
            "username" to req.fromUsername,
            "phone" to req.fromPhone,
            "addedAt" to System.currentTimeMillis()
        )
        val meRef = db.collection("users").document(me.uid)
        val fromRef = db.collection("users").document(req.fromUid)

        // get my info to add reciprocal entry for sender
        meRef.get().addOnSuccessListener { meDoc ->
            val myName = meDoc.getString("username") ?: "User"
            val myPhone = meDoc.getString("phone") ?: me.phoneNumber ?: ""

            val contactForFrom = hashMapOf(
                "uid" to me.uid,
                "username" to myName,
                "phone" to myPhone,
                "addedAt" to System.currentTimeMillis()
            )

            // Write both entries (could be batched)
            val batch = db.batch()
            val meContactDoc = meRef.collection("trustedContacts").document(req.fromUid)
            val fromContactDoc = fromRef.collection("trustedContacts").document(me.uid)
            batch.set(meContactDoc, contactForMe)
            batch.set(fromContactDoc, contactForFrom)
            batch.update(db.collection("requests").document(requestDocId), "status", "accepted")
            batch.commit().addOnSuccessListener {
                Toast.makeText(this, "Request accepted. Trust link created.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to accept: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rejectRequest(requestDocId: String) {
        firestore.collection("requests").document(requestDocId)
            .update("status", "rejected")
            .addOnSuccessListener { Toast.makeText(this, "Request rejected", Toast.LENGTH_SHORT).show() }
            .addOnFailureListener { e -> Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show() }
    }
}
