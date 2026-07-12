package com.accessible.toolkit.elder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EmergencyContactAdapter(
    private val contacts: MutableList<MedicationReminder.EmergencyContact>,
    private val onCall: (MedicationReminder.EmergencyContact) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<EmergencyContactAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_contact_name)
        val tvPhone: TextView = view.findViewById(R.id.tv_contact_phone)
        val btnCall: ImageButton = view.findViewById(R.id.btn_call)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = contacts[position]

        holder.tvName.text = contact.name
        holder.tvPhone.text = contact.phoneNumber

        holder.btnCall.setOnClickListener {
            onCall(contact)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(contact.id)
        }
    }

    override fun getItemCount() = contacts.size

    fun updateContacts(newContacts: List<MedicationReminder.EmergencyContact>) {
        contacts.clear()
        contacts.addAll(newContacts)
        notifyDataSetChanged()
    }
}
