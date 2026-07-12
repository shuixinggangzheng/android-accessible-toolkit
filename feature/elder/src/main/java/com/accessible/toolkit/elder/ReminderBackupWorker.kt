package com.accessible.toolkit.elder

import android.content.Context
import androidx.work.*
import java.util.*

class ReminderBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val reminder = MedicationReminder(applicationContext)
        val reminders = reminder.getReminders()

        // Re-schedule all enabled reminders to ensure alarms are alive
        for (item in reminders) {
            if (item.isEnabled) {
                reminder.scheduleReminder(item)
            }
        }

        return Result.success()
    }
}
