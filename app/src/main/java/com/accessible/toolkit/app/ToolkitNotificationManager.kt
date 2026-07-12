package com.accessible.toolkit.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.accessible.toolkit.bridge.BridgeService

class ToolkitNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_PANEL = "toolkit_panel"
        const val CHANNEL_ALERTS = "toolkit_alerts"
        const val NOTIFICATION_ID = 1003

        const val ACTION_TOGGLE_SUBTITLE = "com.accessible.toolkit.TOGGLE_SUBTITLE"
        const val ACTION_TTS_QUICK = "com.accessible.toolkit.TTS_QUICK"
        const val ACTION_EXIT_ALL = "com.accessible.toolkit.EXIT_ALL"

        enum class ServiceState {
            IDLE, LISTENING, TRANSCRIBING, READING, PAUSED
        }

        @Volatile
        var lastTranscriptText: String = ""
            private set

        @Volatile
        var bridgeClientCount: Int = 0
            private set

        @Volatile
        var asrActiveStartTime: Long = 0L
            private set

        fun setLastTranscript(text: String) { lastTranscriptText = text }
        fun setBridgeClientCount(n: Int) { bridgeClientCount = n }
        fun markAsrActive() { asrActiveStartTime = if (asrActiveStartTime == 0L) System.currentTimeMillis() else asrActiveStartTime }
        fun resetAsrActive() { asrActiveStartTime = 0L }
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val panel = NotificationChannel(
                CHANNEL_PANEL, "无障碍助手", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "常驻通知面板"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val alerts = NotificationChannel(
                CHANNEL_ALERTS, "服务提醒", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "重要状态提醒"
                enableVibration(false)
            }
            nm.createNotificationChannel(panel)
            nm.createNotificationChannel(alerts)
        }
    }

    fun buildNotification(state: ServiceState): Notification {
        val title = "无障碍助手"

        val (contentText, subtitleIcon) = when (state) {
            ServiceState.IDLE ->
                "点击开始字幕监听" to android.R.drawable.ic_lock_silence_mode
            ServiceState.LISTENING ->
                "字幕监听中 · 点击暂停" to android.R.drawable.ic_btn_speak_now
            ServiceState.TRANSCRIBING ->
                "正在转写..." to android.R.drawable.ic_media_play
            ServiceState.READING ->
                "正在朗读..." to android.R.drawable.ic_media_play
            ServiceState.PAUSED ->
                "已暂停" to android.R.drawable.ic_media_pause
        }

        val openIntent = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val toggleIntent = PendingIntent.getBroadcast(context, 1,
            Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_TOGGLE_SUBTITLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val ttsIntent = PendingIntent.getBroadcast(context, 2,
            Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_TTS_QUICK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val exitIntent = PendingIntent.getBroadcast(context, 3,
            Intent(context, NotificationActionReceiver::class.java).apply { action = ACTION_EXIT_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val toggleLabel = if (state == ServiceState.IDLE || state == ServiceState.PAUSED) "开启字幕" else "暂停字幕"

        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_PANEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, toggleLabel, toggleIntent)
            .addAction(android.R.drawable.ic_lock_silence_mode, "语音播报", ttsIntent)
            .addAction(android.R.drawable.ic_delete, "退出", exitIntent)

        // Expanded view with RemoteViews
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            val expandedView = android.widget.RemoteViews(context.packageName,
                R.layout.layout_notification_expanded)

            val lastText = lastTranscriptText
            if (lastText.isNotEmpty()) {
                expandedView.setTextViewText(R.id.tv_last_transcript, lastText)
                expandedView.setViewVisibility(R.id.tv_last_transcript, android.view.View.VISIBLE)
                expandedView.setViewVisibility(R.id.tv_no_transcript, android.view.View.GONE)
            } else {
                expandedView.setTextViewText(R.id.tv_no_transcript, "暂无转写内容")
                expandedView.setViewVisibility(R.id.tv_last_transcript, android.view.View.GONE)
                expandedView.setViewVisibility(R.id.tv_no_transcript, android.view.View.VISIBLE)
            }

            if (BridgeService.isRunning) {
                expandedView.setTextViewText(R.id.tv_bridge_status,
                    if (bridgeClientCount > 0) "$bridgeClientCount 台设备已连接" else "PC字幕已开启，等待连接")
                expandedView.setViewVisibility(R.id.tv_bridge_status, android.view.View.VISIBLE)
            } else {
                expandedView.setViewVisibility(R.id.tv_bridge_status, android.view.View.GONE)
            }

            val asrRunning = asrActiveStartTime > 0
            val asrMinutes = (System.currentTimeMillis() - asrActiveStartTime) / 60_000L
            if (asrRunning && asrMinutes >= 10) {
                expandedView.setTextViewText(R.id.tv_battery_hint, "长时间使用会加快耗电")
                expandedView.setViewVisibility(R.id.tv_battery_hint, android.view.View.VISIBLE)
            } else {
                expandedView.setViewVisibility(R.id.tv_battery_hint, android.view.View.GONE)
            }

            builder.setCustomBigContentView(expandedView)
        }

        return builder.build()
    }

    fun update(state: ServiceState) {
        nm.notify(NOTIFICATION_ID, buildNotification(state))
    }

    fun cancel() {
        nm.cancel(NOTIFICATION_ID)
    }

    fun showAlert(title: String, text: String) {
        val intent = PendingIntent.getActivity(context, 100,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(title.hashCode(), notification)
    }
}
