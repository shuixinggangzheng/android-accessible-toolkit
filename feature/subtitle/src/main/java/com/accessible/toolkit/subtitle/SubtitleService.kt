package com.accessible.toolkit.subtitle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.accessible.toolkit.engine.AsrCallback
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.model.TranscriptResult
import com.accessible.toolkit.engine.AsrError
import com.accessible.toolkit.vad.EnergyVadDetector
import com.accessible.toolkit.vosk.VoskAsrEngine

class SubtitleService : Service() {

    companion object {
        private const val TAG = "SubtitleService"
        private const val CHANNEL_ID = "subtitle_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.accessible.toolkit.subtitle.PAUSE"
        const val ACTION_RESUME = "com.accessible.toolkit.subtitle.RESUME"
        const val ACTION_STOP = "com.accessible.toolkit.subtitle.STOP"

        var isRunning = false
            private set
        var isPaused = false
            private set

        private var stateListener: ServiceStateListener? = null

        interface ServiceStateListener {
            fun onStateChanged(running: Boolean, paused: Boolean)
        }

        fun setStateListener(listener: ServiceStateListener?) {
            stateListener = listener
        }

        fun start(context: Context) {
            val intent = Intent(context, SubtitleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SubtitleService::class.java)
            context.stopService(intent)
        }
    }

    private var asrEngine: VoskAsrEngine? = null
    private var vadDetector: EnergyVadDetector? = null
    private var floatingView: FloatingSubtitleView? = null
    private var actionReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        floatingView = FloatingSubtitleView(this)
        registerActionReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseService()
            ACTION_RESUME -> resumeService()
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!isRunning) {
                    startForeground(NOTIFICATION_ID, createNotification())
                    startSubtitle()
                } else {
                    updateNotification()
                }
            }
        }
        return START_STICKY
    }

    private fun startSubtitle() {
        asrEngine = VoskAsrEngine(this).apply {
            setCallback(createAsrCallback())
            loadDefaultModel()
        }

        vadDetector = EnergyVadDetector(this).apply {
            setCallback(createVadCallback())
            start()
        }

        floatingView?.show()
        isRunning = true
        isPaused = false
        stateListener?.onStateChanged(true, false)
        Log.d(TAG, "Subtitle service started")
    }

    private fun pauseService() {
        if (!isRunning || isPaused) return

        vadDetector?.stop()
        asrEngine?.stopListening()
        isPaused = true
        updateNotification()
        floatingView?.showPaused()
        stateListener?.onStateChanged(true, true)
        Log.d(TAG, "Subtitle service paused")
    }

    private fun resumeService() {
        if (!isRunning || !isPaused) return

        vadDetector?.start()
        isPaused = false
        updateNotification()
        floatingView?.showResumed()
        stateListener?.onStateChanged(true, false)
        Log.d(TAG, "Subtitle service resumed")
    }

    private fun createAsrCallback(): AsrCallback {
        return object : AsrCallback {
            override fun onPartialResult(result: TranscriptResult) {
                if (!isPaused) {
                    floatingView?.updateSubtitle(result.text, isFinal = false)
                }
            }

            override fun onFinalResult(result: TranscriptResult) {
                if (!isPaused) {
                    floatingView?.updateSubtitle(result.text, isFinal = true)
                }
            }

            override fun onError(error: AsrError) {
                Log.e(TAG, "ASR error: $error")
                val message = when (error) {
                    is AsrError.NotInitialized -> "ASR引擎未初始化"
                    is AsrError.PermissionDenied -> "权限被拒绝"
                    is AsrError.ModelLoadFailed -> "模型加载失败"
                    is AsrError.ModelNotFound -> "模型未找到"
                    is AsrError.RuntimeError -> "错误: ${error.message}"
                }
                floatingView?.showError(message)
            }

            override fun onReady() {
                Log.d(TAG, "ASR engine ready")
            }
        }
    }

    private fun createVadCallback(): VadCallback {
        return object : VadCallback {
            override fun onVoiceStart() {
                if (!isPaused) {
                    asrEngine?.startListening()
                    floatingView?.showVoiceIndicator(isSpeaking = true)
                }
            }

            override fun onVoiceEnd() {
                asrEngine?.stopListening()
                floatingView?.showVoiceIndicator(isSpeaking = false)
            }

            override fun onSilenceDuration(seconds: Int) {
                floatingView?.updateSilenceDuration(seconds)
            }

            override fun onError(error: String) {
                Log.e(TAG, "VAD error: $error")
                floatingView?.showError(error)
            }
        }
    }

    private fun stopSubtitle() {
        vadDetector?.stop()
        asrEngine?.stopListening()
        asrEngine?.destroy()
        floatingView?.hide()

        vadDetector = null
        asrEngine = null
        floatingView = null
        isRunning = false
        isPaused = false
        stateListener?.onStateChanged(false, false)
        Log.d(TAG, "Subtitle service stopped")
    }

    private fun registerActionReceiver() {
        actionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_PAUSE -> pauseService()
                    ACTION_RESUME -> resumeService()
                    ACTION_STOP -> stopSelf()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_PAUSE)
            addAction(ACTION_RESUME)
            addAction(ACTION_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }
    }

    private fun unregisterActionReceiver() {
        actionReceiver?.let { unregisterReceiver(it) }
        actionReceiver = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "字幕服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "实时字幕显示服务"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pauseIntent = createPendingIntent(ACTION_PAUSE)
        val resumeIntent = createPendingIntent(ACTION_RESUME)
        val stopIntent = createPendingIntent(ACTION_STOP)

        val title = if (isPaused) "字幕已暂停" else "字幕监听中"
        val text = if (isPaused) "点击恢复继续监听" else "正在监听语音..."

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(createOpenAppIntent())

        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "恢复", resumeIntent)
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", pauseIntent)
        }
        builder.addAction(android.R.drawable.ic_delete, "退出", stopIntent)

        return builder.build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createOpenAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        unregisterActionReceiver()
        stopSubtitle()
        super.onDestroy()
    }
}