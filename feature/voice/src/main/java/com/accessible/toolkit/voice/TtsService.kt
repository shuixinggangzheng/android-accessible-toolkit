package com.accessible.toolkit.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

class TtsService : Service() {

    companion object {
        private const val TAG = "TtsService"
        private const val QUEUE_MAX_SIZE = 10

        fun start(context: Context) {
            val intent = Intent(context, TtsService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TtsService::class.java)
            context.stopService(intent)
        }

        fun speak(context: Context, text: String) {
            val intent = Intent(context, TtsService::class.java).apply {
                action = ACTION_SPEAK
                putExtra(EXTRA_TEXT, text)
            }
            context.startService(intent)
        }

        private const val ACTION_SPEAK = "speak"
        private const val EXTRA_TEXT = "text"
    }

    private val binder = LocalBinder()
    private var ttsManager: TtsManager? = null
    private val speakQueue = ConcurrentLinkedQueue<String>()
    private var isSpeaking = false
    private var listener: TtsServiceListener? = null

    interface TtsServiceListener {
        fun onSpeechStarted()
        fun onSpeechCompleted()
        fun onSpeechError(error: String)
        fun onQueueChanged(queueSize: Int)
    }

    inner class LocalBinder : Binder() {
        fun getService(): TtsService = this@TtsService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ttsManager = TtsManager(this).apply {
            setCallback(object : TtsCallback {
                override fun onStart() {
                    isSpeaking = true
                    listener?.onSpeechStarted()
                }

                override fun onDone() {
                    isSpeaking = false
                    listener?.onSpeechCompleted()
                    processQueue()
                }

                override fun onError(error: String) {
                    isSpeaking = false
                    listener?.onSpeechError(error)
                    processQueue()
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (!text.isNullOrEmpty()) {
                    speak(text)
                }
            }
        }
        return START_STICKY
    }

    fun speak(text: String) {
        if (speakQueue.size >= QUEUE_MAX_SIZE) {
            speakQueue.poll()
        }
        speakQueue.offer(text)
        listener?.onQueueChanged(speakQueue.size)
        processQueue()
    }

    private fun processQueue() {
        if (isSpeaking || speakQueue.isEmpty()) return

        val text = speakQueue.poll()
        if (text != null) {
            listener?.onQueueChanged(speakQueue.size)
            ttsManager?.speak(text)
        }
    }

    fun stopSpeaking() {
        ttsManager?.stop()
        isSpeaking = false
    }

    fun clearQueue() {
        speakQueue.clear()
        listener?.onQueueChanged(0)
    }

    fun setSpeechRate(rate: Float) {
        ttsManager?.setSpeechRate(rate)
    }

    fun setPitch(pitch: Float) {
        ttsManager?.setPitch(pitch)
    }

    fun setListener(listener: TtsServiceListener?) {
        this.listener = listener
    }

    fun isCurrentlySpeaking(): Boolean = isSpeaking

    fun getQueueSize(): Int = speakQueue.size

    override fun onDestroy() {
        ttsManager?.destroy()
        ttsManager = null
        super.onDestroy()
    }
}