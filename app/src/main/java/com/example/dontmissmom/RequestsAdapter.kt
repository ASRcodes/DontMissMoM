package com.example.dontmissmom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RequestsAdapter(
    private val items: MutableList<RequestItem>,
    private val onAccept: (String, RequestItem) -> Unit,
    private val onReject: (String) -> Unit,
    private val onReport: (RequestItem) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvPhone: TextView = v.findViewById(R.id.tvPhone)
        val btnAccept: Button = v.findViewById(R.id.btnAccept)
        val btnReject: Button = v.findViewById(R.id.btnDecline)
        val btnReport: ImageButton = v.findViewById(R.id.btnReport)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_request, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]

        holder.tvName.text =
            if (r.fromUsername.isNotBlank()) r.fromUsername else "Unknown"

        holder.tvPhone.text =
            if (r.fromPhone.isNotBlank()) r.fromPhone else "Unknown"

        holder.btnAccept.setOnClickListener { onAccept(r.id, r) }
        holder.btnReject.setOnClickListener { onReject(r.id) }
        holder.btnReport.setOnClickListener { onReport(r) }
    }

    override fun getItemCount(): Int = items.size

    fun setData(newList: List<RequestItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
