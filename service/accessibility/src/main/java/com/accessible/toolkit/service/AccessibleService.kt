package com.accessible.toolkit.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AccessibleService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibleService"
        var instance: AccessibleService? = null
            private set

        fun isRunning(): Boolean = instance != null
    }

    private var eventListener: AccessibilityEventListener? = null
    private val nodeCache = mutableMapOf<String, AccessibilityNodeInfo>()

    interface AccessibilityEventListener {
        fun onAccessibilityEvent(event: AccessibilityEvent)
        fun onWindowChange(event: AccessibilityEvent)
        fun onNodeInteracted(nodeInfo: AccessibilityNodeInfo, action: String)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
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
        Log.d(TAG, "Missing label detected, supplementary TTS may be needed")

        // 尝试获取有用的描述信息
        val viewId = source.viewIdResourceName
        val className = source.className?.toString()
        val bounds = android.graphics.Rect()
        source.getBoundsInScreen(bounds)

        Log.d(TAG, "Missing label info: viewId=$viewId, class=$className, bounds=$bounds")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearNodeCache()
        instance = null
        Log.d(TAG, "Accessibility service destroyed")
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