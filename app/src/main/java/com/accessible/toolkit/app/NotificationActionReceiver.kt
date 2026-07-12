package com.accessible.toolkit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminder
import com.accessible.toolkit.subtitle.SubtitleService

class NotificationActionReceiver : BroadcastReceiver() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Legacy toggle type (notification panel buttons)
            "com.accessible.toolkit.NOTIFICATION_TOGGLE" -> {
                val type = intent.getStringExtra("toggle_type")
                when (type) {
                    "subtitle" -> toggleSubtitle(context)
                    "bridge" -> toggleBridge(context)
                }
                QuickBallService.start(context)
            }

            // New ToolkitNotificationManager actions
            ToolkitNotificationManager.ACTION_TOGGLE_SUBTITLE -> toggleSubtitle(context)

            ToolkitNotificationManager.ACTION_TTS_QUICK -> {
                val launchIntent = Intent(context, VoiceOutputActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
                Toast.makeText(context, "语音播报面板已打开", Toast.LENGTH_SHORT).show()
            }

            ToolkitNotificationManager.ACTION_EXIT_ALL -> {
                SubtitleService.stop(context)
                BridgeService.stop(context)
                QuickBallService.stop(context)
                Toast.makeText(context, "所有服务已停止", Toast.LENGTH_SHORT).show()
            }

            // Legacy emergency
            "com.accessible.toolkit.EMERGENCY" -> handleEmergency(context)
        }
    }

    private fun toggleSubtitle(context: Context) {
        if (SubtitleService.isRunning) {
            SubtitleService.stop(context)
            Toast.makeText(context, "字幕已关闭", Toast.LENGTH_SHORT).show()
            ToolkitNotificationManager.resetAsrActive()
        } else {
            SubtitleService.start(context)
            Toast.makeText(context, "字幕已开启", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBridge(context: Context) {
        if (BridgeService.isRunning) {
            BridgeService.stop(context)
            Toast.makeText(context, "PC 字幕已关闭", Toast.LENGTH_SHORT).show()
        } else {
            BridgeService.start(context)
            val ip = BridgeService.currentLanIp
            Toast.makeText(context, "PC 字幕已开启: $ip:8766", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleEmergency(context: Context) {
        val reminder = MedicationReminder(context)
        val contacts = reminder.getEmergencyContacts()
        if (contacts.isNotEmpty()) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contacts[0].phoneNumber}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(callIntent)
            } catch (_: SecurityException) {
                Toast.makeText(context, "需要电话权限", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
        }
    }
}
