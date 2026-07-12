package com.accessible.toolkit.engine.model

data class TranscriptResult(
    val text: String,
    val isFinal: Boolean,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)