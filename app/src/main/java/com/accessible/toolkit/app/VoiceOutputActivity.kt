package com.accessible.toolkit.app

import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.accessible.toolkit.engine.AndroidTtsEngine
import com.accessible.toolkit.engine.TtsCallback
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.subtitle.SubtitleViewModel

class VoiceOutputActivity : AppCompatActivity() {

    private lateinit var ttsEngine: AndroidTtsEngine
    private lateinit var subtitleViewModel: SubtitleViewModel

    // Normal mode views
    private lateinit var layoutNormal: LinearLayout
    private lateinit var etInput: EditText
    private lateinit var btnSpeak: ImageButton
    private lateinit var btnDialogueMode: Button
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var seekBarPitch: SeekBar
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvPitchValue: TextView
    private lateinit var layoutPhrases: LinearLayout

    // Dialogue mode views
    private lateinit var layoutDialogue: LinearLayout
    private lateinit var btnExitDialogue: Button
    private lateinit var tvAsrText: TextView
    private lateinit var scrollAsr: ScrollView
    private lateinit var tvVoiceActive: TextView
    private lateinit var etDialogueInput: EditText
    private lateinit var btnDialogueSpeak: ImageButton
    private lateinit var layoutDialoguePhrases: LinearLayout

    private val defaultPhrases = listOf("你好", "谢谢", "我需要帮助", "请等一下", "好的", "再见")
    private var isSpeaking = false
    private var isDialogueMode = false

    private val ttsCallback = object : TtsCallback {
        override fun onStart() {
            runOnUiThread {
                isSpeaking = true
                updateSpeakButtons()
            }
        }

        override fun onDone() {
            runOnUiThread {
                isSpeaking = false
                updateSpeakButtons()
            }
        }

        override fun onError(error: String) {
            runOnUiThread {
                isSpeaking = false
                updateSpeakButtons()
                Toast.makeText(this@VoiceOutputActivity, "播报错误: $error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_output)

        ttsEngine = AndroidTtsEngine(this)
        ttsEngine.setCallback(ttsCallback)

        subtitleViewModel = ViewModelProvider(this)[SubtitleViewModel::class.java]

        initViews()
        setupPhrases()
        setupListeners()
        observeSubtitle()
    }

    private fun initViews() {
        layoutNormal = findViewById(R.id.layout_normal)
        etInput = findViewById(R.id.et_input)
        btnSpeak = findViewById(R.id.btn_speak)
        btnDialogueMode = findViewById(R.id.btn_dialogue_mode)
        seekBarSpeed = findViewById(R.id.seekbar_speed)
        seekBarPitch = findViewById(R.id.seekbar_pitch)
        tvSpeedValue = findViewById(R.id.tv_speed_value)
        tvPitchValue = findViewById(R.id.tv_pitch_value)
        layoutPhrases = findViewById(R.id.layout_phrases)

        layoutDialogue = findViewById(R.id.layout_dialogue)
        btnExitDialogue = findViewById(R.id.btn_exit_dialogue)
        tvAsrText = findViewById(R.id.tv_asr_text)
        scrollAsr = findViewById(R.id.scroll_asr)
        tvVoiceActive = findViewById(R.id.tv_voice_active)
        etDialogueInput = findViewById(R.id.et_dialogue_input)
        btnDialogueSpeak = findViewById(R.id.btn_dialogue_speak)
        layoutDialoguePhrases = findViewById(R.id.layout_dialogue_phrases)
    }

    private fun setupPhrases() {
        addPhraseButtons(layoutPhrases, defaultPhrases) { phrase ->
            etInput.setText(phrase)
            etInput.setSelection(phrase.length)
        }
        addPhraseButtons(layoutDialoguePhrases, defaultPhrases) { phrase ->
            etDialogueInput.setText(phrase)
            etDialogueInput.setSelection(phrase.length)
        }
    }

    private fun addPhraseButtons(container: LinearLayout, phrases: List<String>, onClick: (String) -> Unit) {
        container.removeAllViews()
        for (phrase in phrases) {
            val btn = Button(this).apply {
                text = phrase
                textSize = 14f
                setBackgroundColor(0xFFE8F5E9.toInt())
                setTextColor(0xFF333333.toInt())
                typeface = Typeface.DEFAULT
                setPadding(32, 16, 32, 16)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params
                setOnClickListener { onClick(phrase) }
            }
            container.addView(btn)
        }
    }

    private fun setupListeners() {
        btnSpeak.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                if (isSpeaking) {
                    ttsEngine.stop()
                } else {
                    ttsEngine.speak(text)
                }
            }
        }

        btnDialogueSpeak.setOnClickListener {
            val text = etDialogueInput.text.toString().trim()
            if (text.isNotEmpty()) {
                if (isSpeaking) {
                    ttsEngine.stop()
                } else {
                    ttsEngine.speak(text)
                }
            }
        }

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSpeak.isEnabled = !s.isNullOrBlank()
            }
        })

        btnDialogueMode.setOnClickListener {
            isDialogueMode = true
            layoutNormal.visibility = View.GONE
            layoutDialogue.visibility = View.VISIBLE
            startListening()
        }

        btnExitDialogue.setOnClickListener {
            isDialogueMode = false
            ttsEngine.stop()
            isSpeaking = false
            updateSpeakButtons()
            stopListening()
            layoutDialogue.visibility = View.GONE
            layoutNormal.visibility = View.VISIBLE
        }

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress * 1.5f / 100f)
                tvSpeedValue.text = String.format("%.1fx", speed)
                if (fromUser) ttsEngine.setSpeechRate(speed)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitchVal = 0.5f + (progress * 1.5f / 100f)
                tvPitchValue.text = String.format("%.1f", pitchVal)
                if (fromUser) ttsEngine.setPitch(pitchVal)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarSpeed.progress = 50
        seekBarPitch.progress = 50
    }

    private fun observeSubtitle() {
        subtitleViewModel.subtitleText.observe(this) { text ->
            if (text.isNotEmpty() && isDialogueMode) {
                tvAsrText.text = text
                scrollAsr.post { scrollAsr.fullScroll(View.FOCUS_DOWN) }
            }
        }

        subtitleViewModel.isVoiceActive.observe(this) { active ->
            tvVoiceActive.visibility = if (active) View.VISIBLE else View.GONE
        }
    }

    private fun startListening() {
        SubtitleService.start(this)
        subtitleViewModel.startListening()
    }

    private fun stopListening() {
        SubtitleService.stop(this)
        subtitleViewModel.stopListening()
    }

    private fun updateSpeakButtons() {
        if (isSpeaking) {
            btnSpeak.setBackgroundResource(R.drawable.bg_circle_red)
            btnSpeak.setImageResource(android.R.drawable.ic_media_pause)
            btnDialogueSpeak.setBackgroundResource(R.drawable.bg_circle_red)
            btnDialogueSpeak.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnSpeak.setBackgroundResource(R.drawable.bg_circle_green)
            btnSpeak.setImageResource(android.R.drawable.ic_btn_speak_now)
            btnDialogueSpeak.setBackgroundResource(R.drawable.bg_circle_green)
            btnDialogueSpeak.setImageResource(android.R.drawable.ic_btn_speak_now)
        }
    }

    override fun onDestroy() {
        if (isDialogueMode) {
            SubtitleService.stop(this)
            subtitleViewModel.stopListening()
        }
        ttsEngine.destroy()
        super.onDestroy()
    }
}
