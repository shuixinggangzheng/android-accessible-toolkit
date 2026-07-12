package com.accessible.toolkit.elder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*
import java.util.concurrent.TimeUnit

class MedicationReminder(private val context: Context) {

    companion object {
        private const val TAG = "MedicationReminder"
        private const val CHANNEL_ID = "medication_reminder"
        private const val NOTIFICATION_BASE_ID = 2000
        private const val PREFS_NAME = "medication_reminders"
        private const val KEY_REMINDERS = "reminders_json"
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts_json"
        private const val WORK_TAG = "medication_backup_work"
    }

    enum class RepeatMode {
        DAILY, WEEKLY, ONCE
    }

    data class MedTime(
        val hour: Int,
        val minute: Int
    ) {
        fun toDisplayString(): String {
            return String.format("%02d:%02d", hour, minute)
        }
    }

    data class MedicationReminderItem(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val times: List<MedTime>,
        val dosage: String,
        val repeatMode: RepeatMode = RepeatMode.DAILY,
        val repeatDays: List<Int> = emptyList(), // 1=Mon...7=Sun for weekly
        val isEnabled: Boolean = true
    )

    data class EmergencyContact(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val phoneNumber: String
    )

    private val gson = Gson()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    // ==================== Reminder CRUD ====================

    fun getReminders(): List<MedicationReminderItem> {
        val json = prefs.getString(KEY_REMINDERS, null) ?: return emptyList()
        val type = object : TypeToken<List<MedicationReminderItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveReminders(reminders: List<MedicationReminderItem>) {
        prefs.edit().putString(KEY_REMINDERS, gson.toJson(reminders)).apply()
    }

    fun addReminder(reminder: MedicationReminderItem) {
        val reminders = getReminders().toMutableList()
        reminders.add(reminder)
        saveReminders(reminders)
        scheduleReminder(reminder)
    }

    fun updateReminder(reminder: MedicationReminderItem) {
        val reminders = getReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == reminder.id }
        if (index >= 0) {
            cancelReminder(reminders[index])
            reminders[index] = reminder
            saveReminders(reminders)
            if (reminder.isEnabled) {
                scheduleReminder(reminder)
            }
        }
    }

    fun removeReminder(id: String) {
        val reminders = getReminders().toMutableList()
        val removed = reminders.removeAll { it.id == id }
        if (removed) {
            saveReminders(reminders)
        }
    }

    fun toggleReminder(id: String, enabled: Boolean) {
        val reminders = getReminders().toMutableList()
        val index = reminders.indexOfFirst { it.id == id }
        if (index >= 0) {
            val updated = reminders[index].copy(isEnabled = enabled)
            reminders[index] = updated
            saveReminders(reminders)
            if (enabled) {
                scheduleReminder(updated)
            } else {
                cancelReminder(updated)
            }
        }
    }

    // ==================== Emergency Contacts ====================

    fun getEmergencyContacts(): List<EmergencyContact> {
        val json = prefs.getString(KEY_EMERGENCY_CONTACTS, null) ?: return emptyList()
        val type = object : TypeToken<List<EmergencyContact>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEmergencyContacts(contacts: List<EmergencyContact>) {
        prefs.edit().putString(KEY_EMERGENCY_CONTACTS, gson.toJson(contacts)).apply()
    }

    fun addEmergencyContact(contact: EmergencyContact) {
        val contacts = getEmergencyContacts().toMutableList()
        contacts.add(contact)
        saveEmergencyContacts(contacts)
    }

    fun removeEmergencyContact(id: String) {
        val contacts = getEmergencyContacts().toMutableList()
        contacts.removeAll { it.id == id }
        saveEmergencyContacts(contacts)
    }

    // ==================== Alarm Scheduling ====================

    fun scheduleReminder(reminder: MedicationReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for ((index, time) in reminder.times.withIndex()) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_id", reminder.id)
                putExtra("reminder_name", reminder.name)
                putExtra("reminder_dosage", reminder.dosage)
                putExtra("time_index", index)
                putExtra("total_times", reminder.times.size)
            }

            val requestCode = (reminder.id.hashCode() + index) and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If time already passed today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // For weekly mode, only schedule on repeat days
            if (reminder.repeatMode == RepeatMode.WEEKLY && reminder.repeatDays.isNotEmpty()) {
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                // Convert Java Calendar (1=Sun...7=Sat) to our format (1=Mon...7=Sun)
                val ourDay = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                if (ourDay !in reminder.repeatDays) {
                    // Find next matching day
                    var daysAhead = 0
                    var checkDay = ourDay
                    while (daysAhead < 7) {
                        checkDay = if (checkDay >= 7) 1 else checkDay + 1
                        daysAhead++
                        if (checkDay in reminder.repeatDays) break
                    }
                    calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
                }
            }

            // For ONCE mode, just schedule at the next occurrence
            // For DAILY mode, schedule repeating
            when (reminder.repeatMode) {
                RepeatMode.DAILY -> {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY,
                        pendingIntent
                    )
                }
                RepeatMode.WEEKLY -> {
                    alarmManager.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        AlarmManager.INTERVAL_DAY * 7,
                        pendingIntent
                    )
                }
                RepeatMode.ONCE -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }

        // Schedule WorkManager backup
        scheduleWorkManagerBackup()
    }

    fun cancelReminder(reminder: MedicationReminderItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for ((index, _) in reminder.times.withIndex()) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val requestCode = (reminder.id.hashCode() + index) and 0x7FFFFFFF
            val pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    fun cancelAllReminders() {
        for (reminder in getReminders()) {
            cancelReminder(reminder)
        }
    }

    private fun scheduleWorkManagerBackup() {
        val request = PeriodicWorkRequestBuilder<ReminderBackupWorker>(
            15, TimeUnit.MINUTES
        ).setTags(listOf(WORK_TAG))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
    }

    // ==================== Notification ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "服药提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "药物服用提醒通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(
        reminderId: String,
        reminderName: String,
        dosage: String
    ) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Vibrate
        vibrate()

        // TTS announcement
        val ttsText = "该吃${reminderName}了，${dosage}"
        speakTts(ttsText)

        // Intent to open the app
        val openIntent = Intent(context, MedicationReminderActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "已服用" action
        val takenIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "ACTION_TAKEN"
            putExtra("reminder_id", reminderId)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context, reminderId.hashCode(), takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("服药提醒")
            .setContentText("该吃${reminderName}了，${dosage}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("该吃${reminderName}了，${dosage}\n\n请点击「已服用」确认"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_send, "已服用", takenPendingIntent)
            .build()

        val notificationId = NOTIFICATION_BASE_ID + reminderId.hashCode()
        notificationManager.notify(notificationId, notification)
    }

    fun dismissNotification(reminderId: String) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notificationId = NOTIFICATION_BASE_ID + reminderId.hashCode()
        notificationManager.cancel(notificationId)
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500), -1
            ))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 500, 200, 500, 200, 500), -1
            ))
        }
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun speakTts(text: String) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.language = Locale.CHINESE
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medication_reminder")
                }
            }
        } else if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "medication_reminder")
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    // ==================== Helper ====================

    fun getReminderById(id: String): MedicationReminderItem? {
        return getReminders().find { it.id == id }
    }
}
