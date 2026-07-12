package com.accessible.toolkit.elder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accessible.toolkit.voice.TtsManager

class EmergencyCallManager(private val context: Context) {

    companion object {
        private const val TAG = "EmergencyCallManager"
        private const val CHANNEL_ID = "emergency_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private var phoneNumber: String? = null

    val get: String?
        get() = phoneNumber

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "紧急呼叫",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "紧急呼叫提醒通知"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun setPhoneNumber(number: String) {
        this.phoneNumber = number
    }

    fun makeEmergencyCall() {
        val number = phoneNumber ?: return

        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = android.net.Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to make emergency call", e)
        }
    }

    fun showEmergencyNotification(message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("紧急提醒")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}