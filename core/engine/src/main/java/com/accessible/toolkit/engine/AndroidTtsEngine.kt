package com.accessible.toolkit.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AndroidTtsEngine(private val context: Context) : TtsEngine, TextToSpeech.OnInitListener {

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
            val result = tts?.setLanguage(Locale.CHINESE)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }

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
        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
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
        initialized = false
    }
}
