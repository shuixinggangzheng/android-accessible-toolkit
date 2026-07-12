package com.accessible.toolkit.engine.model

data class VadEvent(
    val type: Type,
    val timestamp: Long = System.currentTimeMillis(),
    val energy: Float = 0f
) {
    enum class Type {
        VOICE_START,
        VOICE_END
    }
}