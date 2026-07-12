package com.accessible.toolkit.app

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.subtitle.SubtitleViewModel
import com.accessible.toolkit.voice.TtsService

class CommunicationPanelActivity : AppCompatActivity() {

    private lateinit var viewModel: SubtitleViewModel
    private var ttsService: TtsService? = null
    private var isBound = false

    private lateinit var tvReceivedText: TextView
    private lateinit var scrollReceived: ScrollView
    private lateinit var etInputText: EditText
    private lateinit var btnSpeak: Button
    private lateinit var btnClearReceived: Button
    private lateinit var btnClearInput: Button
    private lateinit var btnStartListening: Button
    private lateinit var btnStopListening: Button
    private lateinit var tvListeningStatus: TextView
    private lateinit var tvVoiceActive: TextView

    private val receivedTexts = mutableListOf<String>()
    private var isListening = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TtsService.LocalBinder
            ttsService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_communication_panel)

        viewModel = ViewModelProvider(this)[SubtitleViewModel::class.java]

        initViews()
        setupListeners()
        observeViewModel()
        startTtsService()
    }

    private fun initViews() {
        tvReceivedText = findViewById(R.id.tv_received_text)
        scrollReceived = findViewById(R.id.scroll_received)
        etInputText = findViewById(R.id.et_input_text)
        btnSpeak = findViewById(R.id.btn_speak)
        btnClearReceived = findViewById(R.id.btn_clear_received)
        btnClearInput = findViewById(R.id.btn_clear_input)
        btnStartListening = findViewById(R.id.btn_start_listening)
        btnStopListening = findViewById(R.id.btn_stop_listening)
        tvListeningStatus = findViewById(R.id.tv_listening_status)
        tvVoiceActive = findViewById(R.id.tv_voice_active)
    }

    private fun setupListeners() {
        btnSpeak.setOnClickListener {
            val text = etInputText.text.toString().trim()
            if (text.isNotEmpty()) {
                speakText(text)
                addToReceived("我: $text", isLocal = true)
                etInputText.text.clear()
            }
        }

        btnClearReceived.setOnClickListener {
            receivedTexts.clear()
            tvReceivedText.text = ""
        }

        btnClearInput.setOnClickListener {
            etInputText.text.clear()
        }

        btnStartListening.setOnClickListener {
            startListening()
        }

        btnStopListening.setOnClickListener {
            stopListening()
        }
    }

    private fun observeViewModel() {
        viewModel.subtitleText.observe(this) { text ->
            if (text.isNotEmpty()) {
                addToReceived("对方: $text", isLocal = false)
            }
        }

        viewModel.isVoiceActive.observe(this) { active ->
            tvVoiceActive.visibility = if (active) View.VISIBLE else View.GONE
        }

        viewModel.isListening.observe(this) { listening ->
            isListening = listening
            updateListeningUI()
        }
    }

    private fun startListening() {
        SubtitleService.start(this)
        viewModel.startListening()
        isListening = true
        updateListeningUI()
    }

    private fun stopListening() {
        SubtitleService.stop(this)
        viewModel.stopListening()
        isListening = false
        updateListeningUI()
    }

    private fun updateListeningUI() {
        btnStartListening.visibility = if (isListening) View.GONE else View.VISIBLE
        btnStopListening.visibility = if (isListening) View.VISIBLE else View.GONE
        tvListeningStatus.text = if (isListening) "正在监听..." else "已停止监听"
        tvListeningStatus.setTextColor(
            if (isListening) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt()
        )
    }

    private fun addToReceived(text: String, isLocal: Boolean) {
        receivedTexts.add(text)
        val spannableText = buildString {
            receivedTexts.forEachIndexed { index, line ->
                if (index > 0) append("\n")
                append(line)
            }
        }
        tvReceivedText.text = spannableText
        scrollReceived.post {
            scrollReceived.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun speakText(text: String) {
        ttsService?.speak(text)
    }

    private fun startTtsService() {
        val intent = Intent(this, TtsService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }
}