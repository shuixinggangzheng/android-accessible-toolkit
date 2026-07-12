package com.accessible.toolkit.engine

interface VadDetector {
    fun start()
    fun stop()
    fun setCallback(callback: VadCallback)
    fun isRunning(): Boolean
    fun destroy()
}

interface VadCallback {
    fun onVoiceStart()
    fun onVoiceEnd()
    fun onSilenceDuration(seconds: Int)
    fun onError(error: String)
}