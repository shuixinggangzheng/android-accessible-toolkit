package com.accessible.toolkit.voice

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VoiceAssistActivity : AppCompatActivity() {

    private var ttsService: TtsService? = null
    private var isBound = false

    private lateinit var etInput: EditText
    private lateinit var btnSpeak: Button
    private lateinit var btnClear: Button
    private lateinit var tvQueueSize: TextView
    private lateinit var tvStatus: TextView
    private lateinit var rvPhrases: RecyclerView
    private lateinit var seekBarSpeed: SeekBar
    private lateinit var seekBarPitch: SeekBar
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvPitchValue: TextView

    private val commonPhrases = listOf(
        "你好",
        "谢谢",
        "不客气",
        "对不起",
        "没关系",
        "请再说一遍",
        "我需要帮助",
        "请稍等",
        "好的",
        "再见",
        "请问这个多少钱？",
        "我要去洗手间",
        "我不太舒服",
        "请帮我叫一下服务员",
        "我听不太清楚"
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.LocalBinder
            ttsService = binder.getService()
            isBound = true
            setupTtsListener()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_assist)

        initViews()
        setupPhrasesRecyclerView()
        setupListeners()
        startTtsService()
    }

    private fun initViews() {
        etInput = findViewById(R.id.et_input)
        btnSpeak = findViewById(R.id.btn_speak)
        btnClear = findViewById(R.id.btn_clear)
        tvQueueSize = findViewById(R.id.tv_queue_size)
        tvStatus = findViewById(R.id.tv_status)
        rvPhrases = findViewById(R.id.rv_phrases)
        seekBarSpeed = findViewById(R.id.seekbar_speed)
        seekBarPitch = findViewById(R.id.seekbar_pitch)
        tvSpeedValue = findViewById(R.id.tv_speed_value)
        tvPitchValue = findViewById(R.id.tv_pitch_value)
    }

    private fun setupPhrasesRecyclerView() {
        val adapter = PhraseAdapter(commonPhrases) { phrase ->
            etInput.setText(phrase)
            speakText(phrase)
        }
        rvPhrases.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPhrases.adapter = adapter
    }

    private fun setupListeners() {
        btnSpeak.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isNotEmpty()) {
                speakText(text)
            }
        }

        btnClear.setOnClickListener {
            etInput.text.clear()
            ttsService?.clearQueue()
            updateStatus("队列已清空")
        }

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnSpeak.isEnabled = !s.isNullOrBlank()
            }
        })

        seekBarSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val speed = 0.5f + (progress * 1.5f / 100f)
                tvSpeedValue.text = String.format("%.1fx", speed)
                if (fromUser) {
                    ttsService?.setSpeechRate(speed)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pitch = 0.5f + (progress * 1.5f / 100f)
                tvPitchValue.text = String.format("%.1f", pitch)
                if (fromUser) {
                    ttsService?.setPitch(pitch)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekBarSpeed.progress = 50
        seekBarPitch.progress = 50
    }

    private fun startTtsService() {
        val intent = Intent(this, TtsService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun setupTtsListener() {
        ttsService?.setListener(object : TtsService.TtsServiceListener {
            override fun onSpeechStarted() {
                runOnUiThread { updateStatus("正在播报...") }
            }

            override fun onSpeechCompleted() {
                runOnUiThread { updateStatus("播报完成") }
            }

            override fun onSpeechError(error: String) {
                runOnUiThread { updateStatus("错误: $error") }
            }

            override fun onQueueChanged(queueSize: Int) {
                runOnUiThread {
                    tvQueueSize.text = "队列: $queueSize"
                }
            }
        })
    }

    private fun speakText(text: String) {
        ttsService?.speak(text)
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}