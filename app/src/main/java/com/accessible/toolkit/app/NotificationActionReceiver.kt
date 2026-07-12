package com.accessible.toolkit.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminder
import com.accessible.toolkit.subtitle.SubtitleService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.accessible.toolkit.NOTIFICATION_TOGGLE" -> {
                val type = intent.getStringExtra("toggle_type")
                when (type) {
                    "subtitle" -> {
                        if (SubtitleService.isRunning) {
                            SubtitleService.stop(context)
                            Toast.makeText(context, "字幕已关闭", Toast.LENGTH_SHORT).show()
                        } else {
                            SubtitleService.start(context)
                            Toast.makeText(context, "字幕已开启", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "bridge" -> {
                        if (BridgeService.isRunning) {
                            BridgeService.stop(context)
                            Toast.makeText(context, "PC 字幕已关闭", Toast.LENGTH_SHORT).show()
                        } else {
                            BridgeService.start(context)
                            val ip = BridgeService.currentLanIp
                            Toast.makeText(context, "PC 字幕已开启: $ip:8766", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                QuickBallService.start(context)
            }

            "com.accessible.toolkit.EMERGENCY" -> {
                val reminder = MedicationReminder(context)
                val contacts = reminder.getEmergencyContacts()
                if (contacts.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:${contacts[0].phoneNumber}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: SecurityException) {
                        Toast.makeText(context, "需要电话权限", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "请先设置紧急联系人", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
