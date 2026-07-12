package com.accessible.toolkit.subtitle

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

class FloatingSubtitleView(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var containerView: View? = null
    private var subtitleTextView: TextView? = null
    private var statusTextView: TextView? = null
    private var voiceIndicator: ImageView? = null
    private var errorTextView: TextView? = null
    private var pausedTextView: TextView? = null
    private val isShowing = AtomicBoolean(false)
    private var layoutParams: WindowManager.LayoutParams? = null
    private var pulseAnimator: ValueAnimator? = null

    fun show() {
        if (isShowing.get()) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        containerView = LayoutInflater.from(context).inflate(R.layout.layout_floating_subtitle, null)
        subtitleTextView = containerView?.findViewById(R.id.tv_subtitle)
        statusTextView = containerView?.findViewById(R.id.tv_status)
        voiceIndicator = containerView?.findViewById(R.id.iv_voice_indicator)
        errorTextView = containerView?.findViewById(R.id.tv_error)
        pausedTextView = containerView?.findViewById(R.id.tv_paused)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
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
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        setupTouchListener()
        windowManager?.addView(containerView, layoutParams)
        isShowing.set(true)
    }

    private fun setupTouchListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        containerView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                    }
                    if (isDragging) {
                        layoutParams?.x = initialX + dx.toInt()
                        layoutParams?.y = initialY - dy.toInt()
                        windowManager?.updateViewLayout(containerView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    !isDragging
                }
                else -> false
            }
        }
    }

    fun updateSubtitle(text: String, isFinal: Boolean = true) {
        containerView?.post {
            subtitleTextView?.text = text
            subtitleTextView?.alpha = if (isFinal) 1.0f else 0.7f
            errorTextView?.visibility = View.GONE
        }
    }

    fun showVoiceIndicator(isSpeaking: Boolean) {
        containerView?.post {
            voiceIndicator?.visibility = View.VISIBLE
            if (isSpeaking) {
                voiceIndicator?.setImageResource(android.R.drawable.ic_btn_speak_now)
                startPulseAnimation()
            } else {
                voiceIndicator?.setImageResource(android.R.drawable.ic_lock_silent_mode)
                stopPulseAnimation()
            }
        }
    }

    fun updateSilenceDuration(seconds: Int) {
        containerView?.post {
            if (seconds > 0) {
                statusTextView?.text = "静音 ${seconds}s"
                statusTextView?.visibility = View.VISIBLE
            } else {
                statusTextView?.visibility = View.GONE
            }
        }
    }

    fun showError(message: String) {
        containerView?.post {
            errorTextView?.text = message
            errorTextView?.visibility = View.VISIBLE
        }
    }

    fun showPaused() {
        containerView?.post {
            pausedTextView?.visibility = View.VISIBLE
            subtitleTextView?.text = "字幕已暂停"
            subtitleTextView?.alpha = 0.5f
            voiceIndicator?.visibility = View.GONE
            statusTextView?.visibility = View.GONE
            errorTextView?.visibility = View.GONE
            containerView?.alpha = 0.7f
        }
    }

    fun showResumed() {
        containerView?.post {
            pausedTextView?.visibility = View.GONE
            subtitleTextView?.text = "正在监听语音..."
            subtitleTextView?.alpha = 1.0f
            containerView?.alpha = 1.0f
        }
    }

    private fun startPulseAnimation() {
        stopPulseAnimation()
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.3f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                voiceIndicator?.scaleX = scale
                voiceIndicator?.scaleY = scale
            }
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        voiceIndicator?.scaleX = 1.0f
        voiceIndicator?.scaleY = 1.0f
    }

    fun hide() {
        if (!isShowing.get()) return

        stopPulseAnimation()
        try {
            windowManager?.removeView(containerView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        containerView = null
        subtitleTextView = null
        statusTextView = null
        voiceIndicator = null
        errorTextView = null
        pausedTextView = null
        isShowing.set(false)
    }

    fun isVisible(): Boolean = isShowing.get()
}