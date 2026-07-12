package com.accessible.toolkit.app

import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminder
import com.accessible.toolkit.subtitle.SubtitleService

class QuickBallService : Service() {

    companion object {
        private const val TAG = "QuickBallService"
        private const val CHANNEL_ID = "quick_ball_channel"
        private const val NOTIFICATION_ID = 1003
        private const val LONG_PRESS_DURATION = 1500L // 1.5s for emergency call
        private const val MIN_PRESS_DURATION = 200L // 200ms minimum to prevent accident

        const val ACTION_START = "com.accessible.toolkit.QUICK_BALL_START"

        fun start(context: Context) {
            val intent = Intent(context, QuickBallService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, QuickBallService::class.java)
            context.stopService(intent)
        }
    }

    private var windowManager: WindowManager? = null
    private var ballView: View? = null
    private var panelView: View? = null
    private var ballLayoutParams: WindowManager.LayoutParams? = null
    private var panelLayoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPanelShowing = false
    private var longPressRunnable: Runnable? = null
    private var pulseAnimator: ValueAnimator? = null
    private var currentBallColor = BallColor.BLUE

    // Long press tracking
    private var pressStartTime = 0L
    private var isLongPressTriggered = false
    private lateinit var notifManager: ToolkitNotificationManager
    private var currentServiceState = ToolkitNotificationManager.ServiceState.IDLE

    enum class BallColor {
        GREEN, BLUE, GRAY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifManager = ToolkitNotificationManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notifManager.buildNotification(currentServiceState))
        createBallView()
        registerStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun registerStateListener() {
        SubtitleService.setStateListener(object : SubtitleService.ServiceStateListener {
            override fun onStateChanged(running: Boolean, paused: Boolean) {
                updateBallColor(running, paused)
                val newState = when {
                    !running -> ToolkitNotificationManager.ServiceState.IDLE
                    paused -> ToolkitNotificationManager.ServiceState.PAUSED
                    else -> ToolkitNotificationManager.ServiceState.LISTENING
                }
                currentServiceState = newState
                notifManager.update(newState)
                if (running && !paused) ToolkitNotificationManager.markAsrActive()
                else ToolkitNotificationManager.resetAsrActive()
            }
        })

        BridgeService.setServiceListener(object : BridgeService.ServiceListener {
            override fun onStateChanged(running: Boolean) {
                ToolkitNotificationManager.setBridgeClientCount(if (running) 1 else 0)
                notifManager.update(currentServiceState)
            }
            override fun onTranscriptUpdate(text: String, isFinal: Boolean) {
                if (isFinal) ToolkitNotificationManager.setLastTranscript(text)
                notifManager.update(currentServiceState)
            }
            override fun onVadStateChange(state: com.accessible.toolkit.bridge.SubtitleWebSocketServer.VadState) {
                when (state) {
                    com.accessible.toolkit.bridge.SubtitleWebSocketServer.VadState.VOICE_START -> {
                        currentServiceState = ToolkitNotificationManager.ServiceState.TRANSCRIBING
                        ToolkitNotificationManager.markAsrActive()
                    }
                    com.accessible.toolkit.bridge.SubtitleWebSocketServer.VadState.VOICE_END,
                    com.accessible.toolkit.bridge.SubtitleWebSocketServer.VadState.SILENCE -> {
                        currentServiceState = ToolkitNotificationManager.ServiceState.LISTENING
                    }
                }
                notifManager.update(currentServiceState)
            }
            override fun onServerAddressChanged(ip: String, httpPort: Int) {}
        })
    }

    private fun updateBallColor(running: Boolean, paused: Boolean) {
        val newColor = when {
            running && !paused -> BallColor.GREEN
            running && paused -> BallColor.BLUE
            else -> BallColor.GRAY
        }
        setBallColor(newColor)
    }

    private fun createBallView() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        ballView = LayoutInflater.from(this).inflate(R.layout.layout_quick_ball, null)
        val ballIcon = ballView?.findViewById<View>(R.id.ball_icon)

        ballLayoutParams = WindowManager.LayoutParams(
            56.dpToPx(),
            56.dpToPx(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 16.dpToPx()
        }

        setupBallTouchListener(ballIcon)
        windowManager?.addView(ballView, ballLayoutParams)
        setBallColor(BallColor.BLUE)
    }

    private fun setupBallTouchListener(ballIcon: View?) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        ballIcon?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = ballLayoutParams?.x ?: 0
                    initialY = ballLayoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isLongPressTriggered = false
                    pressStartTime = System.currentTimeMillis()

                    longPressRunnable = Runnable {
                        if (!isDragging && !isLongPressTriggered) {
                            val pressDuration = System.currentTimeMillis() - pressStartTime
                            if (pressDuration >= MIN_PRESS_DURATION) {
                                isLongPressTriggered = true
                                vibrate()
                                onBallLongPress()
                            }
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_DURATION)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (initialTouchX - event.rawX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        handler.removeCallbacks(longPressRunnable!!)
                    }
                    if (isDragging) {
                        ballLayoutParams?.x = initialX + dx
                        ballLayoutParams?.y = initialY + dy
                        windowManager?.updateViewLayout(ballView, ballLayoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable!!)
                    if (!isDragging && !isLongPressTriggered) {
                        val pressDuration = System.currentTimeMillis() - pressStartTime
                        if (pressDuration < MIN_PRESS_DURATION) {
                            // Too short, ignore
                        } else {
                            onBallClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun onBallClick() {
        if (isPanelShowing) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    private fun onBallLongPress() {
        val reminder = MedicationReminder(this)
        val contacts = reminder.getEmergencyContacts()

        if (contacts.isEmpty()) {
            return
        }

        if (contacts.size == 1) {
            showConfirmAndCall(contacts[0].name, contacts[0].phoneNumber)
        } else {
            showContactSelectionDialog(contacts)
        }
    }

    private fun showContactSelectionDialog(contacts: List<MedicationReminder.EmergencyContact>) {
        val names = contacts.map { "${it.name} (${it.phoneNumber})" }.toTypedArray()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle("选择紧急联系人")
            .setItems(names) { _, which ->
                val contact = contacts[which]
                showConfirmAndCall(contact.name, contact.phoneNumber)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showConfirmAndCall(name: String, phoneNumber: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_emergency_call, null)
        dialogView.findViewById<TextView>(R.id.tv_contact_name)?.text = name
        dialogView.findViewById<TextView>(R.id.tv_contact_phone)?.text = phoneNumber

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setView(dialogView)
            .setPositiveButton("拨打") { _, _ ->
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = android.net.Uri.parse("tel:$phoneNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(intent)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Failed to make emergency call", e)
                }
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }

    private fun showPanel() {
        if (isPanelShowing) return

        panelView = LayoutInflater.from(this).inflate(R.layout.layout_quick_ball_panel, null)
        setupPanelButtons()

        panelLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 80.dpToPx()
        }

        windowManager?.addView(panelView, panelLayoutParams)
        isPanelShowing = true
    }

    private fun hidePanel() {
        if (!isPanelShowing) return
        try {
            windowManager?.removeView(panelView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove panel", e)
        }
        panelView = null
        isPanelShowing = false
    }

    private fun setupPanelButtons() {
        panelView?.findViewById<View>(R.id.btn_subtitle)?.setOnClickListener {
            toggleSubtitle()
            hidePanel()
        }

        panelView?.findViewById<View>(R.id.btn_tts)?.setOnClickListener {
            openTtsPanel()
            hidePanel()
        }

        panelView?.findViewById<View>(R.id.btn_bridge)?.setOnClickListener {
            toggleBridge()
            hidePanel()
        }

        panelView?.findViewById<View>(R.id.btn_emergency)?.setOnClickListener {
            onBallLongPress()
            hidePanel()
        }

        panelView?.findViewById<View>(R.id.btn_settings)?.setOnClickListener {
            openSettings()
            hidePanel()
        }
    }

    private fun toggleSubtitle() {
        if (SubtitleService.isRunning) {
            SubtitleService.stop(this)
        } else {
            SubtitleService.start(this)
        }
    }

    private fun toggleBridge() {
        if (BridgeService.isRunning) {
            BridgeService.stop(this)
            Toast.makeText(this, "PC字幕服务已关闭", Toast.LENGTH_SHORT).show()
        } else {
            val lanIp = BridgeService.getDeviceLanIp(this)
            BridgeService.start(this)
            val httpPort = 8766
            val addr = "$lanIp:$httpPort"
            copyToClipboard(addr)
            Toast.makeText(this, "PC字幕已启动\n$addr (已复制)", Toast.LENGTH_LONG).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("server_address", text))
    }

    private fun openTtsPanel() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_tts", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun setBallColor(color: BallColor) {
        val colorValue = when (color) {
            BallColor.GREEN -> 0xFF4CAF50.toInt()
            BallColor.BLUE -> 0xFF2196F3.toInt()
            BallColor.GRAY -> 0xFF9E9E9E.toInt()
        }

        currentBallColor = color
        ballView?.post {
            val ballIcon = ballView?.findViewById<View>(R.id.ball_icon)
            val background = ballIcon?.background as? GradientDrawable
            background?.setColor(colorValue)

            if (color == BallColor.GREEN) {
                startPulseAnimation()
            } else {
                stopPulseAnimation()
            }
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                ballView?.scaleX = scale
                ballView?.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        ballView?.scaleX = 1.0f
        ballView?.scaleY = 1.0f
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            @Suppress("DEPRECATION")
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "快捷悬浮球",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "无障碍助手常驻通知"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        stopPulseAnimation()
        hidePanel()
        try {
            windowManager?.removeView(ballView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove ball view", e)
        }
        ballView = null
        SubtitleService.setStateListener(null)
        super.onDestroy()
    }
}
