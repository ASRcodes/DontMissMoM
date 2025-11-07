package com.example.dontmissmom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RequestsAdapter(
    private val items: MutableList<RequestItem>,
    private val onAccept: (String, RequestItem) -> Unit,
    private val onReject: (String) -> Unit
) : RecyclerView.Adapter<RequestsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tvFromName)
        val tvPhone: TextView = v.findViewById(R.id.tvFromPhone)
        val btnAccept: Button = v.findViewById(R.id.btnAccept)
        val btnReject: Button = v.findViewById(R.id.btnReject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_request, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val r = items[position]
        holder.tvName.text = r.fromUsername
        holder.tvPhone.text = r.fromPhone
        holder.btnAccept.setOnClickListener { onAccept(r.id, r) }
        holder.btnReject.setOnClickListener { onReject(r.id) }
    }

    override fun getItemCount(): Int = items.size

    fun setData(newList: List<RequestItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
