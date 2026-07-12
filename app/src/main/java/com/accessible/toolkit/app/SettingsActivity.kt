package com.accessible.toolkit.app

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.elder.MedicationReminderActivity
import com.accessible.toolkit.service.AccessibleService
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private val phrasePrefsName = "quick_phrases"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences(this)

        setupToolbar()
        setupSubtitleSettings()
        setupVadSettings()
        setupTtsSettings()
        setupBridgeSettings()
        setupElderSettings()
        setupPhraseEditor()
        setupAlertSettings()
        setupAccessibilitySettings()
        setupAboutSection()
    }

    private fun setupToolbar() {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            title = "设置"
            setSupportActionBar(this)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            setNavigationOnClickListener { finish() }
        }
    }

    // ==================== 字幕设置 ====================

    private fun setupSubtitleSettings() {
        val seekFont: SeekBar = findViewById(R.id.seekbar_subtitle_font)
        val tvFont: TextView = findViewById(R.id.tv_subtitle_font_value)

        seekFont.progress = prefs.subtitleFontSize.coerceIn(16, 36)
        tvFont.text = "${prefs.subtitleFontSize}sp"

        seekFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p.coerceIn(16, 36)
                tvFont.text = "${v}sp"
                if (fromUser) prefs.subtitleFontSize = v
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val seekOpacity: SeekBar = findViewById(R.id.seekbar_subtitle_opacity)
        val tvOpacity: TextView = findViewById(R.id.tv_subtitle_opacity_value)
        seekOpacity.progress = prefs.subtitleOpacity
        tvOpacity.text = "${prefs.subtitleOpacity}%"
        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = p.coerceIn(50, 100)
                tvOpacity.text = "${v}%"
                if (fromUser) prefs.subtitleOpacity = v
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val spinnerMaxLines: Spinner = findViewById(R.id.spinner_max_lines)
        val maxLinesOptions = arrayOf("3 行", "5 行", "10 行", "不限制")
        spinnerMaxLines.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, maxLinesOptions)
        spinnerMaxLines.setSelection(
            when (prefs.subtitleMaxLines) {
                3 -> 0; 5 -> 1; 10 -> 2; else -> 3
            }
        )
        spinnerMaxLines.onItemSelectedListener = null
        spinnerMaxLines.setSelection(when (prefs.subtitleMaxLines) { 3->0; 5->1; 10->2; else->3 })
        spinnerMaxLines.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                prefs.subtitleMaxLines = when (p2) { 0->3; 1->5; 2->10; else->0 }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }

        findViewById<Switch>(R.id.switch_history).apply {
            isChecked = prefs.subtitleHistoryEnabled
            setOnCheckedChangeListener { _, v -> prefs.subtitleHistoryEnabled = v }
        }

        val langSpinner: Spinner = findViewById(R.id.spinner_language)
        langSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("中文", "English (coming soon)", "日本語 (coming soon)"))
    }

    // ==================== VAD 设置 ====================

    private fun setupVadSettings() {
        val seekBar: SeekBar = findViewById(R.id.seekbar_vad_threshold)
        val valueText: TextView = findViewById(R.id.tv_vad_threshold_value)

        seekBar.max = 100
        seekBar.progress = ((prefs.vadThreshold - 1.0f) / 9.0f * 100).toInt().coerceIn(0, 100)
        valueText.text = String.format(Locale.US, "%.1fx", prefs.vadThreshold)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 1.0f + (p / 100f) * 9.0f
                valueText.text = String.format(Locale.US, "%.1fx", v)
                if (fromUser) prefs.vadThreshold = v
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        val timeoutSpinner: Spinner = findViewById(R.id.spinner_silence_timeout)
        timeoutSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("1 秒", "2 秒（默认）", "3 秒"))
        timeoutSpinner.setSelection(
            when (prefs.vadSilenceTimeoutMs) {
                1000L -> 0; 2000L -> 1; else -> 2
            }
        )
        timeoutSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: android.view.View?, p2: Int, p3: Long) {
                prefs.vadSilenceTimeoutMs = when (p2) { 0->1000L; 1->2000L; else->3000L }
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        }
    }

    // ==================== TTS 设置 ====================

    private fun setupTtsSettings() {
        setupSeekBar(R.id.seekbar_tts_speed, R.id.tv_tts_speed_value,
            prefs.ttsSpeed, 0.5f, 2.0f, "%.1fx") { prefs.ttsSpeed = it }
        setupSeekBar(R.id.seekbar_tts_pitch, R.id.tv_tts_pitch_value,
            prefs.ttsPitch, 0.5f, 2.0f, "%.1f") { prefs.ttsPitch = it }

        val engineSpinner: Spinner = findViewById(R.id.spinner_tts_engine)
        val engines = mutableListOf("系统默认")
        try {
            val tts = TextToSpeech(this, null)
            tts.engines?.let { e -> engines.addAll(e.map { it.label }) }
            tts.shutdown()
        } catch (_: Exception) {}
        engineSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, engines)
    }

    // ==================== 跨设备 ====================

    private fun setupBridgeSettings() {
        findViewById<Switch>(R.id.switch_bridge).apply {
            isChecked = BridgeService.isRunning
            setOnCheckedChangeListener { _, v ->
                if (v) BridgeService.start(this@SettingsActivity)
                else BridgeService.stop(this@SettingsActivity)
                isChecked = BridgeService.isRunning
            }
        }

        val seekBar: SeekBar = findViewById(R.id.seekbar_bridge_port)
        val valueText: TextView = findViewById(R.id.tv_bridge_port_value)
        seekBar.max = 9000
        seekBar.progress = (prefs.bridgePort - 8000).coerceIn(0, 9000)
        valueText.text = "${prefs.bridgePort}"
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = 8000 + p
                valueText.text = "$v"
                if (fromUser) prefs.bridgePort = v
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        findViewById<TextView>(R.id.tv_current_ip).text =
            BridgeService.currentLanIp + ":" + (prefs.bridgePort + 1)
    }

    // ==================== 老年辅助 ====================

    private fun setupElderSettings() {
        findViewById<Button>(R.id.btn_medication_manager).setOnClickListener {
            startActivity(Intent(this, MedicationReminderActivity::class.java))
        }
        findViewById<Button>(R.id.btn_emergency_contacts).setOnClickListener {
            startActivity(Intent(this, MedicationReminderActivity::class.java))
        }
        findViewById<Switch>(R.id.switch_one_tap_call).apply {
            isChecked = prefs.enableOneTapCall
            setOnCheckedChangeListener { _, v -> prefs.enableOneTapCall = v }
        }
    }

    // ==================== 快捷短语 ====================

    private fun setupPhraseEditor() {
        findViewById<Button>(R.id.btn_edit_phrases).setOnClickListener { showPhraseEditDialog() }
        updatePhrasePreview()
    }

    private fun showPhraseEditDialog() {
        val phrases = getPhrases()
        val editText = EditText(this).apply {
            setText(phrases.joinToString("\n"))
            setLines(8)
            gravity = android.view.Gravity.TOP
            setPadding(24, 16, 24, 16)
            textSize = 15f
        }

        AlertDialog.Builder(this)
            .setTitle("编辑常用短语（每行一个）")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val lines = editText.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
                savePhrases(lines)
                updatePhrasePreview()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getPhrases(): List<String> {
        val saved = getSharedPreferences(phrasePrefsName, MODE_PRIVATE)
            .getString("phrases", null)
        return if (saved.isNullOrEmpty()) {
            listOf("你好", "谢谢", "不客气", "对不起", "我需要帮助", "请稍等", "好的", "再见")
        } else {
            saved.split("|||")
        }
    }

    private fun savePhrases(list: List<String>) {
        getSharedPreferences(phrasePrefsName, MODE_PRIVATE)
            .edit().putString("phrases", list.joinToString("|||")).apply()
    }

    private fun updatePhrasePreview() {
        val phrases = getPhrases()
        findViewById<TextView>(R.id.tv_phrases_preview).text =
            phrases.take(5).joinToString(" · ") + if (phrases.size > 5) " · +${phrases.size - 5}" else ""
    }

    // ==================== 提醒 ====================

    private fun setupAlertSettings() {
        findViewById<Switch>(R.id.switch_vibrate).apply {
            isChecked = prefs.enableVibrate
            setOnCheckedChangeListener { _, v -> prefs.enableVibrate = v }
        }
        findViewById<Switch>(R.id.switch_tts_alert).apply {
            isChecked = prefs.enableTtsAlert
            setOnCheckedChangeListener { _, v -> prefs.enableTtsAlert = v }
        }
    }

    // ==================== 无障碍 ====================

    private fun setupAccessibilitySettings() {
        findViewById<Button>(R.id.btn_accessibility_settings).setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
        findViewById<Switch>(R.id.switch_talkback_sync).apply {
            isChecked = prefs.enableTalkBackSync
            setOnCheckedChangeListener { _, v ->
                prefs.enableTalkBackSync = v
                AccessibleService.instance?.setTtsEnabled(v)
            }
        }
    }

    // ==================== 关于 ====================

    private fun setupAboutSection() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
        findViewById<TextView>(R.id.tv_version).text = "无障碍辅助工具 v$versionName"

        findViewById<TextView>(R.id.tv_license).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("开源协议")
                .setMessage("Apache License 2.0\n\nCopyright (c) 2024\n\nLicensed under the Apache License, Version 2.0\n\nVosk Speech Recognition - Apache 2.0\nJava-WebSocket - MIT\nNanoHTTPD - Modified BSD")
                .setPositiveButton("关闭", null)
                .show()
        }

        findViewById<Button>(R.id.btn_show_privacy).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("隐私政策")
                .setMessage(getString(com.accessible.toolkit.service.R.string.privacy_policy_full))
                .setPositiveButton("关闭", null)
                .show()
        }
    }

    // ==================== Helper ====================

    private fun setupSeekBar(
        seekBarId: Int, textViewId: Int,
        initialValue: Float, min: Float, max: Float, format: String,
        onChanged: (Float) -> Unit
    ) {
        val seekBar: SeekBar = findViewById(seekBarId)
        val tv: TextView = findViewById(textViewId)
        seekBar.max = 100
        seekBar.progress = ((initialValue - min) / (max - min) * 100).toInt().coerceIn(0, 100)
        tv.text = String.format(Locale.US, format, initialValue)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val v = min + (p * (max - min) / 100f)
                tv.text = String.format(Locale.US, format, v)
                if (fromUser) onChanged(v)
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
    }
}
