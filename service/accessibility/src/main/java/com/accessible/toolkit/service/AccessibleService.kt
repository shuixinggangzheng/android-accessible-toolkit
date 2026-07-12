package com.accessible.toolkit.service

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AccessibleService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibleService"

        var instance: AccessibleService? = null
            private set

        fun isRunning(): Boolean = instance != null

        const val GESTURE_SWIPE_DOWN = "swipe_down"
        const val GESTURE_SWIPE_UP = "swipe_up"
        const val GESTURE_DOUBLE_TAP = "double_tap"
    }

    // === Core state ===
    private var eventListener: AccessibilityEventListener? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())

    // TalkBack tracking
    private var isTalkBackRunning = false
    private var talkBackCheckPending = true

    // Audio capture
    private var audioRecord: AudioRecord? = null
    private var audioCaptureThread: Thread? = null

    // Gesture state
    private val gestureSwipeThreshold = 300f
    private var lastGestureTime = 0L
    private var lastGestureType = ""
    private var gestureStartY = 0f

    // === Public interface ===

    interface AccessibilityEventListener {
        fun onAccessibilityEvent(event: AccessibilityEvent) {}
        fun onWindowChange(event: AccessibilityEvent) {}
        fun onNodeInteracted(nodeInfo: AccessibilityNodeInfo, action: String) {}
        fun onMediaPlaybackDetected(packageName: String) {}
    }

    /** Gesture action listener for the 3-finger shortcuts */
    interface GestureActionListener {
        fun onSubtitleToggle()
        fun onTtsPanelOpen()
        fun onEmergencyCall()
    }

    private var gestureActionListener: GestureActionListener? = null

    fun setEventListener(listener: AccessibilityEventListener?) {
        this.eventListener = listener
    }

    fun setGestureActionListener(listener: GestureActionListener?) {
        this.gestureActionListener = listener
    }

    // === Lifecycle ===

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Enable gesture detection (3-finger shortcuts)
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Allow detecting media playback
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            }
        }
        serviceInfo = info

        initTts()
        checkTalkBackState()
        Log.d(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        eventListener?.onAccessibilityEvent(event)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                processViewInteraction(event)

            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED ->
                processFocusEvent(event)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                processWindowEvent(event)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                eventListener?.onWindowChange(event)

            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ->
                processTextChange(event)

            AccessibilityEvent.TYPE_ANNOUNCEMENT -> {
                // TalkBack announcement — track TalkBack state
                if (talkBackCheckPending) {
                    checkTalkBackState()
                }
            }
        }
    }

    // === Gesture Handling (3-finger) ===

    override fun onGesture(gestureEvent: AccessibilityGestureEvent?): Boolean {
        gestureEvent ?: return false

        val now = System.currentTimeMillis()

        when {
            // 3-finger swipe down → toggle subtitle
            gestureEvent.gestureId == AccessibilityGestureEvent.GESTURE_SWIPE_DOWN &&
            gestureEvent.displayLocation != null -> {
                Log.d(TAG, "3-finger swipe DOWN detected")
                gestureActionListener?.onSubtitleToggle()
                speakFeedback("字幕")
                return true
            }

            // 3-finger swipe up → open TTS panel
            gestureEvent.gestureId == AccessibilityGestureEvent.GESTURE_SWIPE_UP &&
            gestureEvent.displayLocation != null -> {
                Log.d(TAG, "3-finger swipe UP detected")
                gestureActionListener?.onTtsPanelOpen()
                speakFeedback("语音播报")
                return true
            }

            // 3-finger double-tap → emergency call
            gestureEvent.gestureId == AccessibilityGestureEvent.GESTURE_DOUBLE_TAP &&
            gestureEvent.displayLocation != null -> {
                Log.d(TAG, "3-finger DOUBLE-TAP detected")
                // Double-tap debounce
                val dt = now - lastGestureTime
                if (dt < 600 && lastGestureType == GESTURE_DOUBLE_TAP) {
                    gestureActionListener?.onEmergencyCall()
                    speakFeedback("紧急呼叫")
                    lastGestureTime = 0L
                    return true
                }
                lastGestureTime = now
                lastGestureType = GESTURE_DOUBLE_TAP
                return true
            }
        }

        // Fallback: 3-finger swipe using raw motion (API < R)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && gestureEvent.displayLocation != null) {
            val y = gestureEvent.displayLocation!!.y
            val timeSinceLast = now - lastGestureTime

            if (gestureEvent.gestureId == AccessibilityGestureEvent.GESTURE_SWIPE_DOWN) {
                gestureStartY = y
                lastGestureTime = now
                lastGestureType = GESTURE_SWIPE_DOWN
                return true
            }

            if (gestureEvent.gestureId == AccessibilityGestureEvent.GESTURE_SWIPE_UP) {
                val dy = gestureStartY - y
                if (dy > gestureSwipeThreshold && lastGestureType == GESTURE_SWIPE_DOWN) {
                    // Actually a downward swipe completed
                    gestureActionListener?.onSubtitleToggle()
                    speakFeedback("字幕")
                } else if (dy < -gestureSwipeThreshold && lastGestureType == GESTURE_SWIPE_UP) {
                    gestureActionListener?.onTtsPanelOpen()
                    speakFeedback("语音播报")
                }
                lastGestureTime = 0L
                return true
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted by user")
        stopAudioCapture()
        tts?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioCapture()
        tts?.stop()
        tts?.shutdown()
        tts = null
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    // === TalkBack Coordination ===

    private fun checkTalkBackState() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
        val enabledServices = try {
            val settingValue = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            settingValue ?: ""
        } catch (e: Exception) {
            ""
        }
        val wasRunning = isTalkBackRunning
        isTalkBackRunning = enabledServices.contains("talkback", ignoreCase = true) ||
                enabledServices.contains("TalkBack", ignoreCase = true)
        talkBackCheckPending = false

        if (wasRunning != isTalkBackRunning) {
            Log.d(TAG, "TalkBack state changed: $wasRunning → $isTalkBackRunning")
        }
    }

    // === Unlabeled Control Detection ===

    private fun processFocusEvent(event: AccessibilityEvent) {
        val source = event.source ?: return

        // If TalkBack is running, only supplement — never replace
        if (isTalkBackRunning) {
            supplementTalkBack(source)
        } else {
            // TalkBack disabled — we could provide basic reading in the future
            // For now, just log
        }

        eventListener?.onNodeInteracted(source, "focus")
    }

    private fun supplementTalkBack(node: AccessibilityNodeInfo) {
        val contentDesc = node.contentDescription?.toString()?.trim()
        val text = node.text?.toString()?.trim()
        val hint = node.hintText?.toString()?.trim()

        // If has contentDescription, TalkBack handles it — don't interfere
        if (!contentDesc.isNullOrEmpty()) return

        // Try fallback sources in order: text → hint → nothing
        val supplementaryText = when {
            !text.isNullOrEmpty() -> text
            !hint.isNullOrEmpty() -> hint
            else -> return // Nothing useful, don't speak garbage
        }

        // Only speak if TTS is initialized
        if (tts == null) return

        try {
            tts?.speak(
                supplementaryText,
                TextToSpeech.QUEUE_ADD, // QUEUE_ADD to not interrupt TalkBack
                null,
                "supplement_${System.currentTimeMillis()}"
            )
            Log.d(TAG, "TalkBack supplement: '$supplementaryText' for ${node.className}")
        } catch (_: Exception) {}
    }

    // === AudioPlaybackCapture ===

    fun tryCaptureAudioPlayback(projection: MediaProjection? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (projection == null) {
            Log.d(TAG, "AudioCapture: no MediaProjection available, skip")
            return
        }

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .excludeUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(
                16000,
                android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(android.media.AudioFormat.Builder()
                    .setSampleRate(16000)
                    .setChannelMask(android.media.AudioFormat.CHANNEL_IN_MONO)
                    .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                    .build())
                .setBufferSizeInBytes(bufferSize * 2)
                .build()

            audioRecord?.startRecording()
            Log.d(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.w(TAG, "Audio capture not allowed, target app denied it — silent fail", e)
            audioRecord = null
        }
    }

    fun stopAudioCapture() {
        audioCaptureThread?.interrupt()
        audioCaptureThread = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
    }

    // === Process events ===

    private fun processViewInteraction(event: AccessibilityEvent) {
        val source = event.source ?: return
        val action = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "click"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "long_click"
            else -> "interact"
        }
        eventListener?.onNodeInteracted(source, action)

        // Detect media playback start
        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        if (className.contains("Media") || className.contains("Player") ||
            className.contains("Video") || className.contains("Audio")) {
            eventListener?.onMediaPlaybackDetected(packageName)
        }
    }

    private fun processWindowEvent(event: AccessibilityEvent) {
        eventListener?.onWindowChange(event)
        checkTalkBackState()
    }

    private fun processTextChange(event: AccessibilityEvent) {
        val text = event.text?.joinToString("") ?: return
        Log.d(TAG, "Text changed: ${text.take(80)}")
    }

    // === TTS helpers ===

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                Log.d(TAG, "TTS ready")
            } else {
                Log.w(TAG, "TTS init failed, status=$status")
                tts = null
            }
        }
    }

    fun speakFeedback(message: String) {
        if (tts == null) return
        try {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "feedback_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun speakText(text: String) {
        if (tts == null) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "speak_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    // === Node manipulation utilities ===

    fun performClickOnNode(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    fun performLongClick(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

    fun performScrollForward(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

    fun performScrollBackward(node: AccessibilityNodeInfo): Boolean =
        node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

    fun performGlobalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun performGlobalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun performGlobalRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun performGlobalNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        } else false
    }

    fun performGlobalQuickSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        } else false
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? =
        rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()

    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? =
        rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()

    fun getAllNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { traverseNodes(it, nodes) }
        return nodes
    }

    private fun traverseNodes(node: AccessibilityNodeInfo, result: MutableList<AccessibilityNodeInfo>) {
        result.add(AccessibilityNodeInfo.obtain(node))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNodes(child, result)
            child.recycle()
        }
    }

    fun getNodeInfo(node: AccessibilityNodeInfo): NodeInfo = NodeInfo(
        packageName = node.packageName?.toString(),
        className = node.className?.toString(),
        text = node.text?.toString(),
        contentDescription = node.contentDescription?.toString(),
        viewId = node.viewIdResourceName,
        isClickable = node.isClickable,
        isScrollable = node.isScrollable,
        isEnabled = node.isEnabled
    )

    data class NodeInfo(
        val packageName: String?,
        val className: String?,
        val text: String?,
        val contentDescription: String?,
        val viewId: String?,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isEnabled: Boolean
    )
}
