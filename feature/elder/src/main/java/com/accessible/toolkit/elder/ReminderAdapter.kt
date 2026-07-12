package com.accessible.toolkit.elder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ReminderAdapter(
    private val reminders: MutableList<MedicationReminder.MedicationReminderItem>,
    private val onToggle: (String, Boolean) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_medication_name)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvDosage: TextView = view.findViewById(R.id.tv_dosage)
        val tvRepeatMode: TextView = view.findViewById(R.id.tv_repeat_mode)
        val toggleEnabled: android.widget.Switch = view.findViewById(R.id.toggle_enabled)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = reminders[position]

        holder.tvName.text = item.name
        holder.tvTime.text = item.times.joinToString("、") { it.toDisplayString() }
        holder.tvDosage.text = item.dosage
        holder.tvRepeatMode.text = when (item.repeatMode) {
            MedicationReminder.RepeatMode.DAILY -> "每天"
            MedicationReminder.RepeatMode.WEEKLY -> {
                val dayNames = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val daysStr = item.repeatDays.sorted().joinToString(" ") { dayNames[it] }
                "每周 $daysStr"
            }
            MedicationReminder.RepeatMode.ONCE -> "仅一次"
        }

        holder.toggleEnabled.isChecked = item.isEnabled
        holder.toggleEnabled.setOnCheckedChangeListener { _, isChecked ->
            onToggle(item.id, isChecked)
        }

        holder.btnDelete.setOnClickListener {
            onDelete(item.id)
        }
    }

    override fun getItemCount() = reminders.size

    fun updateReminders(newReminders: List<MedicationReminder.MedicationReminderItem>) {
        reminders.clear()
        reminders.addAll(newReminders)
        notifyDataSetChanged()
    }
}
