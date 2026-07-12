package com.accessible.toolkit.engine

interface TtsEngine {
    fun speak(text: String)
    fun stop()
    fun setCallback(callback: TtsCallback)
    fun isSpeaking(): Boolean
    fun setSpeechRate(rate: Float)
    fun setPitch(pitch: Float)
    fun destroy()
}

interface TtsCallback {
    fun onStart()
    fun onDone()
    fun onError(error: String)
}