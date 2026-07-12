package com.accessible.toolkit.elder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminder = MedicationReminder(context)

        when (intent.action) {
            "ACTION_TAKEN" -> {
                // User clicked "已服用"
                val reminderId = intent.getStringExtra("reminder_id") ?: return
                reminder.dismissNotification(reminderId)

                // If ONCE mode, remove the reminder
                val reminderItem = reminder.getReminderById(reminderId)
                if (reminderItem != null && reminderItem.repeatMode == MedicationReminder.RepeatMode.ONCE) {
                    reminder.cancelReminder(reminderItem)
                    reminder.removeReminder(reminderId)
                }
            }
            else -> {
                // AlarmManager triggered
                val reminderId = intent.getStringExtra("reminder_id") ?: return
                val reminderName = intent.getStringExtra("reminder_name") ?: "药物"
                val dosage = intent.getStringExtra("reminder_dosage") ?: ""

                reminder.showReminderNotification(reminderId, reminderName, dosage)
            }
        }
    }
}
