package com.accessible.toolkit.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.accessible.toolkit.engine.TtsCallback
import com.accessible.toolkit.engine.TtsEngine
import java.util.Locale

class TtsManager(private val context: Context) : TtsEngine, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var callback: TtsCallback? = null
    private var initialized = false
    private var speechRate = 1.0f
    private var pitch = 1.0f

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(speechRate)
            tts?.setPitch(pitch)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    callback?.onStart()
                }

                override fun onDone(utteranceId: String?) {
                    callback?.onDone()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    callback?.onError("TTS播放错误")
                }
            })

            initialized = true
        }
    }

    override fun speak(text: String) {
        if (!initialized) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
    }

    override fun stop() {
        tts?.stop()
    }

    override fun setCallback(callback: TtsCallback) {
        this.callback = callback
    }

    override fun isSpeaking(): Boolean = tts?.isSpeaking ?: false

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch
        tts?.setPitch(pitch)
    }

    override fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}