package com.accessible.toolkit.app

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "app_preferences"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
        private const val KEY_PRIVACY_INTRO_SEEN = "privacy_intro_seen"
        private const val KEY_PRIVACY_INTRO_VERSION = "privacy_intro_version"
        private const val KEY_VAD_THRESHOLD = "vad_threshold"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size"
        private const val KEY_BRIDGE_PORT = "bridge_port"
        private const val KEY_ENABLE_VIBRATE = "enable_vibrate"
        private const val KEY_ENABLE_TTS_ALERT = "enable_tts_alert"
        private const val KEY_SUBTITLE_OPACITY = "subtitle_opacity"
        private const val KEY_SUBTITLE_MAX_LINES = "subtitle_max_lines"
        private const val KEY_SUBTITLE_HISTORY_ENABLED = "subtitle_history_enabled"
        private const val KEY_VAD_SILENCE_TIMEOUT = "vad_silence_timeout"
        private const val KEY_ENABLE_ONE_TAP_CALL = "enable_one_tap_call"
        private const val KEY_TALKBACK_SYNC = "talkback_sync"

        const val DEFAULT_VAD_THRESHOLD = 3.0f
        const val DEFAULT_TTS_SPEED = 1.0f
        const val DEFAULT_TTS_PITCH = 1.0f
        const val DEFAULT_SUBTITLE_FONT_SIZE = 24
        const val DEFAULT_BRIDGE_PORT = 8765
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var isPrivacyAccepted: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_ACCEPTED, value).apply()

    var vadThreshold: Float
        get() = prefs.getFloat(KEY_VAD_THRESHOLD, DEFAULT_VAD_THRESHOLD)
        set(value) = prefs.edit().putFloat(KEY_VAD_THRESHOLD, value).apply()

    var ttsSpeed: Float
        get() = prefs.getFloat(KEY_TTS_SPEED, DEFAULT_TTS_SPEED)
        set(value) = prefs.edit().putFloat(KEY_TTS_SPEED, value).apply()

    var ttsPitch: Float
        get() = prefs.getFloat(KEY_TTS_PITCH, DEFAULT_TTS_PITCH)
        set(value) = prefs.edit().putFloat(KEY_TTS_PITCH, value).apply()

    var subtitleFontSize: Int
        get() = prefs.getInt(KEY_SUBTITLE_FONT_SIZE, DEFAULT_SUBTITLE_FONT_SIZE)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_FONT_SIZE, value).apply()

    var bridgePort: Int
        get() = prefs.getInt(KEY_BRIDGE_PORT, DEFAULT_BRIDGE_PORT)
        set(value) = prefs.edit().putInt(KEY_BRIDGE_PORT, value).apply()

    var enableVibrate: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_VIBRATE, value).apply()

    var enableTtsAlert: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_TTS_ALERT, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_TTS_ALERT, value).apply()

    var hasSeenPrivacyIntro: Boolean
        get() = prefs.getBoolean(KEY_PRIVACY_INTRO_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_PRIVACY_INTRO_SEEN, value).apply()

    var privacyIntroVersion: Int
        get() = prefs.getInt(KEY_PRIVACY_INTRO_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_PRIVACY_INTRO_VERSION, value).apply()

    var subtitleOpacity: Int
        get() = prefs.getInt(KEY_SUBTITLE_OPACITY, 90)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_OPACITY, value).apply()

    var subtitleMaxLines: Int
        get() = prefs.getInt(KEY_SUBTITLE_MAX_LINES, 3)
        set(value) = prefs.edit().putInt(KEY_SUBTITLE_MAX_LINES, value).apply()

    var subtitleHistoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_HISTORY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_HISTORY_ENABLED, value).apply()

    var vadSilenceTimeoutMs: Long
        get() = prefs.getLong(KEY_VAD_SILENCE_TIMEOUT, 2000L)
        set(value) = prefs.edit().putLong(KEY_VAD_SILENCE_TIMEOUT, value).apply()

    var enableOneTapCall: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_ONE_TAP_CALL, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_ONE_TAP_CALL, value).apply()

    var enableTalkBackSync: Boolean
        get() = prefs.getBoolean(KEY_TALKBACK_SYNC, true)
        set(value) = prefs.edit().putBoolean(KEY_TALKBACK_SYNC, value).apply()
}
