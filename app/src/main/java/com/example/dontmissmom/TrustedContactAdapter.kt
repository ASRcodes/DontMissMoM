package com.example.dontmissmom

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TrustedContactAdapter(
    private val items: MutableList<TrustedContact>,
    private val onEmergencyClick: (TrustedContact) -> Unit,
    private val onLongPress: (TrustedContact) -> Unit
) : RecyclerView.Adapter<TrustedContactAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvContactName)
        val tvPhone: TextView = view.findViewById(R.id.tvContactPhone)
        val btnEmergency: Button = view.findViewById(R.id.btnEmergencyItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trusted_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = items[position]

        holder.tvName.text = if (c.username.isNotBlank()) c.username else "Unknown"
        holder.tvPhone.text = c.phone

        // 🚫 BLOCKED STATE UI
        if (c.blocked) {
            holder.btnEmergency.isEnabled = false
            holder.btnEmergency.text = "BLOCKED"
            holder.btnEmergency.setBackgroundColor(Color.parseColor("#9E9E9E"))
        } else {
            holder.btnEmergency.isEnabled = true
            holder.btnEmergency.text = "EMERGENCY"
            holder.btnEmergency.setBackgroundColor(Color.parseColor("#D32F2F"))

            holder.btnEmergency.setOnClickListener { onEmergencyClick(c) }
        }

        // 👇 Long press will show Block/Delete dialog (handled in MainActivity)
        holder.itemView.setOnLongClickListener {
            onLongPress(c)
            true
        }
    }

    override fun getItemCount(): Int = items.size

    fun setData(newList: List<TrustedContact>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun add(contact: TrustedContact) {
        items.add(contact)
        notifyItemInserted(items.size - 1)
    }
}
