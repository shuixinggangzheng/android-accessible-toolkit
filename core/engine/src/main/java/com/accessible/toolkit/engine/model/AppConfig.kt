package com.accessible.toolkit.engine.model

data class AppConfig(
    val asrLanguage: String = "zh",
    val voskModelPath: String? = null,
    val ttsSpeechRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val vadSilenceTimeoutMs: Long = 2000,
    val subtitleFontSize: Int = 24,
    val emergencyContact: String? = null,
    val emergencyPhoneNumber: String? = null
)