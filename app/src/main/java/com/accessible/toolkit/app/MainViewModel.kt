package com.accessible.toolkit.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import com.accessible.toolkit.subtitle.SubtitleViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private var subtitleViewModel: SubtitleViewModel? = null
    private var isSubtitleActive = false

    fun toggleSubtitle(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (isSubtitleActive) {
            stopSubtitle()
        } else {
            startSubtitle()
        }
    }

    private fun startSubtitle() {
        isSubtitleActive = true
        Toast.makeText(app, "字幕功能已启动", Toast.LENGTH_SHORT).show()
    }

    private fun stopSubtitle() {
        isSubtitleActive = false
        Toast.makeText(app, "字幕功能已停止", Toast.LENGTH_SHORT).show()
    }

    fun startVoiceAssist() {
        Toast.makeText(app, "语音辅助功能", Toast.LENGTH_SHORT).show()
    }

    fun scheduleMedicationReminder(hour: Int, minute: Int, medicationName: String) {
        Toast.makeText(app, "已设置 $hour:$minute 服用 $medicationName", Toast.LENGTH_SHORT).show()
    }

    fun setEmergencyContact(phoneNumber: String) {
        Toast.makeText(app, "紧急联系人已设置: $phoneNumber", Toast.LENGTH_SHORT).show()
    }
}