package com.accessible.toolkit.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Path
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

class AccessibleService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibleService"
        private const val PREFS_NAME = "accessibility_prefs"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_MISSING_LABEL_COUNT = "missing_label_count"

        var instance: AccessibleService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private var eventListener: AccessibilityEventListener? = null
    private val nodeCache = mutableMapOf<String, AccessibilityNodeInfo>()
    private var tts: TextToSpeech? = null
    private var ttsEnabled = true
    private var missingLabelCount = 0L
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    interface AccessibilityEventListener {
        fun onAccessibilityEvent(event: AccessibilityEvent)
        fun onWindowChange(event: AccessibilityEvent)
        fun onNodeInteracted(nodeInfo: AccessibilityNodeInfo, action: String)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        ttsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true)
        missingLabelCount = prefs.getLong(KEY_MISSING_LABEL_COUNT, 0L)
        initTtsIfNeeded()

        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info

        Log.d(TAG, "Accessibility service connected, TTS: $ttsEnabled")
    }

    private fun initTtsIfNeeded() {
        if (!ttsEnabled || tts != null) return
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val currentVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            if (currentVolume == 0) return

            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    Log.d(TAG, "TTS initialized successfully")
                } else {
                    Log.w(TAG, "TTS init failed, status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to init TTS", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        eventListener?.onAccessibilityEvent(event)

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                processViewInteraction(event, "click")
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                processViewInteraction(event, "focus")
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                eventListener?.onWindowChange(event)
                processWindowEvent(event)
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                processTextChange(event)
            }
        }
    }

    private fun processWindowEvent(event: AccessibilityEvent) {
        val source = event.source
        if (source != null) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()
            val text = source.text?.toString()
            val contentDescription = source.contentDescription?.toString()

            Log.d(TAG, "Window event: pkg=$packageName, class=$className")

            // 缓存节点信息
            val key = "$packageName:$className"
            nodeCache[key]?.recycle()
            nodeCache[key] = AccessibilityNodeInfo.obtain(source)

            if (text.isNullOrEmpty() && contentDescription.isNullOrEmpty()) {
                handleMissingLabel(source)
            }
        }
    }

    private fun processViewInteraction(event: AccessibilityEvent, action: String) {
        val source = event.source
        if (source != null) {
            eventListener?.onNodeInteracted(source, action)
        }
    }

    private fun processTextChange(event: AccessibilityEvent) {
        val text = event.text?.joinToString("")
        if (!text.isNullOrEmpty()) {
            Log.d(TAG, "Text changed: $text")
        }
    }

    private fun handleMissingLabel(source: AccessibilityNodeInfo) {
        missingLabelCount++
        prefs.edit().putLong(KEY_MISSING_LABEL_COUNT, missingLabelCount).apply()

        val viewId = source.viewIdResourceName
        val className = source.className?.toString()
        val bounds = android.graphics.Rect()
        source.getBoundsInScreen(bounds)

        Log.d(TAG, "Missing label #$missingLabelCount: viewId=$viewId, class=$className, bounds=$bounds")

        if (ttsEnabled && tts != null && missingLabelCount <= 5) {
            val hint = when {
                source.isClickable -> "未标记的可点击按钮"
                source.isEditable -> "未标记的输入框"
                className?.contains("Image") == true -> "未标记的图片"
                className?.contains("Button") == true -> "未标记的按钮"
                else -> "未标记的界面元素"
            }
            try {
                tts?.speak(hint, TextToSpeech.QUEUE_FLUSH, null, "missing_label_$missingLabelCount")
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearNodeCache()
        tts?.stop()
        tts?.shutdown()
        tts = null
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
    }

    fun setTtsEnabled(enabled: Boolean) {
        ttsEnabled = enabled
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        if (!enabled) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } else if (tts == null) {
            initTtsIfNeeded()
        }
    }

    fun isTtsEnabled(): Boolean = ttsEnabled

    fun getMissingLabelCount(): Long = missingLabelCount

    fun speakText(text: String) {
        if (!ttsEnabled || tts == null) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "speak_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    fun setEventListener(listener: AccessibilityEventListener?) {
        this.eventListener = listener
    }

    fun performClick(nodeId: Int): Boolean {
        val source = findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        return source?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun performLongClick(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun performScrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    fun performScrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    fun performGlobalBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performGlobalHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performGlobalRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun performGlobalNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        } else {
            false
        }
    }

    fun performGlobalQuickSettings(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        } else {
            false
        }
    }

    fun performGesture(x: Float, y: Float, duration: Long = 100): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    fun performSwipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long = 300
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    fun findNodeByViewId(viewId: String): AccessibilityNodeInfo? {
        return rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    }

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

    private fun clearNodeCache() {
        nodeCache.values.forEach { it.recycle() }
        nodeCache.clear()
    }

    fun getNodeInfo(node: AccessibilityNodeInfo): NodeInfo {
        return NodeInfo(
            packageName = node.packageName?.toString(),
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEnabled = node.isEnabled
        )
    }

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