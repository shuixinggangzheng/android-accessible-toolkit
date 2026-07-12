package com.accessible.toolkit.app

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences(this)

        setupToolbar()
        setupVadSettings()
        setupTtsSettings()
        setupSubtitleSettings()
        setupBridgeSettings()
        setupAlertSettings()
        setupPrivacyButton()
    }

    private fun setupToolbar() {
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            title = "设置"
            setSupportActionBar(this)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            setNavigationOnClickListener { finish() }
        }
    }

    private fun setupVadSettings() {
        val seekBar: SeekBar = findViewById(R.id.seekbar_vad_threshold)
        val valueText: TextView = findViewById(R.id.tv_vad_threshold_value)

        seekBar.max = 100
        seekBar.progress = ((prefs.vadThreshold - 1.0f) / 9.0f * 100).toInt().coerceIn(0, 100)
        valueText.text = String.format("%.1fx", prefs.vadThreshold)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 1.0f + (progress / 100f) * 9.0f
                valueText.text = String.format("%.1fx", value)
                if (fromUser) prefs.vadThreshold = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupTtsSettings() {
        val seekBarSpeed: SeekBar = findViewById(R.id.seekbar_tts_speed)
        val tvSpeed: TextView = findViewById(R.id.tv_tts_speed_value)
        val seekBarPitch: SeekBar = findViewById(R.id.seekbar_tts_pitch)
        val tvPitch: TextView = findViewById(R.id.tv_tts_pitch_value)

        seekBarSpeed.max = 100
        seekBarSpeed.progress = ((prefs.ttsSpeed - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
        tvSpeed.text = String.format("%.1fx", prefs.ttsSpeed)

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress * 1.5f / 100f)
                tvSpeed.text = String.format("%.1fx", value)
                if (fromUser) prefs.ttsSpeed = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarPitch.max = 100
        seekBarPitch.progress = ((prefs.ttsPitch - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
        tvPitch.text = String.format("%.1f", prefs.ttsPitch)

        seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress * 1.5f / 100f)
                tvPitch.text = String.format("%.1f", value)
                if (fromUser) prefs.ttsPitch = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSubtitleSettings() {
        val seekBar: SeekBar = findViewById(R.id.seekbar_subtitle_font)
        val valueText: TextView = findViewById(R.id.tv_subtitle_font_value)

        seekBar.max = 60
        seekBar.progress = prefs.subtitleFontSize
        valueText.text = "${prefs.subtitleFontSize}sp"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress.coerceIn(16, 48)
                valueText.text = "${value}sp"
                if (fromUser) prefs.subtitleFontSize = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupBridgeSettings() {
        val seekBar: SeekBar = findViewById(R.id.seekbar_bridge_port)
        val valueText: TextView = findViewById(R.id.tv_bridge_port_value)

        seekBar.max = 9000
        seekBar.progress = (prefs.bridgePort - 8000).coerceIn(0, 9000)
        valueText.text = "${prefs.bridgePort}"

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 8000 + progress
                valueText.text = "$value"
                if (fromUser) prefs.bridgePort = value
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupAlertSettings() {
        findViewById<Switch>(R.id.switch_vibrate).apply {
            isChecked = prefs.enableVibrate
            setOnCheckedChangeListener { _, isChecked ->
                prefs.enableVibrate = isChecked
            }
        }

        findViewById<Switch>(R.id.switch_tts_alert).apply {
            isChecked = prefs.enableTtsAlert
            setOnCheckedChangeListener { _, isChecked ->
                prefs.enableTtsAlert = isChecked
            }
        }
    }

    private fun setupPrivacyButton() {
        findViewById<android.widget.Button>(R.id.btn_show_privacy).setOnClickListener {
            showPrivacyDialog()
        }
    }

    private fun showPrivacyDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("隐私政策")
            .setMessage(getString(com.accessible.toolkit.service.R.string.privacy_policy_full))
            .setPositiveButton("关闭", null)
            .show()
    }
}
