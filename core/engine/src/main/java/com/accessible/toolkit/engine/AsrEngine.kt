package com.accessible.toolkit.engine

interface AsrEngine {
    fun startListening()
    fun stopListening()
    fun setCallback(callback: AsrCallback)
    fun isListening(): Boolean
    fun destroy()
}