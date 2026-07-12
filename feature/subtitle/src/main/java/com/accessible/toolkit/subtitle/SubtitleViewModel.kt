package com.accessible.toolkit.subtitle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.accessible.toolkit.engine.AsrCallback
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.model.TranscriptResult
import com.accessible.toolkit.engine.AsrError
import com.accessible.toolkit.vad.EnergyVadDetector
import com.accessible.toolkit.vosk.VoskAsrEngine

class SubtitleViewModel(application: Application) : AndroidViewModel(application) {

    private val _subtitleText = MutableLiveData<String>()
    val subtitleText: LiveData<String> = _subtitleText

    private val _isListening = MutableLiveData<Boolean>()
    val isListening: LiveData<Boolean> = _isListening

    private val _isPaused = MutableLiveData<Boolean>()
    val isPaused: LiveData<Boolean> = _isPaused

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _silenceDuration = MutableLiveData<Int>()
    val silenceDuration: LiveData<Int> = _silenceDuration

    private val _isVoiceActive = MutableLiveData<Boolean>()
    val isVoiceActive: LiveData<Boolean> = _isVoiceActive

    private val asrEngine = VoskAsrEngine(application)
    private val vadDetector = EnergyVadDetector(application)

    private val subtitleHistory = mutableListOf<TranscriptResult>()
    private var isPausedState = false

    init {
        setupAsrCallback()
        setupVadCallback()
    }

    private fun setupAsrCallback() {
        asrEngine.setCallback(object : AsrCallback {
            override fun onPartialResult(result: TranscriptResult) {
                _subtitleText.postValue(result.text)
            }

            override fun onFinalResult(result: TranscriptResult) {
                subtitleHistory.add(result)
                _subtitleText.postValue(result.text)
            }

            override fun onError(error: AsrError) {
                _error.postValue(when (error) {
                    is AsrError.NotInitialized -> "ASR引擎未初始化"
                    is AsrError.PermissionDenied -> "权限被拒绝"
                    is AsrError.ModelLoadFailed -> "模型加载失败"
                    is AsrError.ModelNotFound -> "模型未找到"
                    is AsrError.RuntimeError -> "运行错误: ${error.message}"
                })
            }

            override fun onReady() {
                _isListening.postValue(true)
            }
        })
    }

    private fun setupVadCallback() {
        vadDetector.setCallback(object : VadCallback {
            override fun onVoiceStart() {
                _isVoiceActive.postValue(true)
                _silenceDuration.postValue(0)
                if (!isPausedState) {
                    asrEngine.startListening()
                }
            }

            override fun onVoiceEnd() {
                _isVoiceActive.postValue(false)
                asrEngine.stopListening()
            }

            override fun onSilenceDuration(seconds: Int) {
                _silenceDuration.postValue(seconds)
            }

            override fun onError(error: String) {
                _error.postValue(error)
            }
        })
    }

    fun startListening(modelPath: String? = null) {
        // If SubtitleService is already running, use it instead of creating duplicate engines
        if (SubtitleService.isRunning) {
            isPausedState = false
            _isPaused.value = false
            _isVoiceActive.value = false
            _silenceDuration.value = 0
            return
        }

        isPausedState = false
        _isPaused.value = false
        _isVoiceActive.value = false
        _silenceDuration.value = 0

        if (modelPath != null) {
            asrEngine.loadModel(modelPath)
        } else {
            asrEngine.loadDefaultModel()
        }
        vadDetector.start()
    }

    fun stopListening() {
        if (SubtitleService.isRunning) return

        vadDetector.stop()
        asrEngine.stopListening()
        _isListening.value = false
        _isVoiceActive.value = false
    }

    fun togglePause() {
        val currentlyPaused = _isPaused.value ?: false
        isPausedState = !currentlyPaused
        _isPaused.value = isPausedState

        if (isPausedState) {
            vadDetector.stop()
            asrEngine.stopListening()
            _isVoiceActive.value = false
        } else {
            vadDetector.start()
        }
    }

    fun clearHistory() {
        subtitleHistory.clear()
        _subtitleText.value = ""
    }

    fun getHistory(): List<TranscriptResult> = subtitleHistory.toList()

    fun recalibrateNoise() {
        vadDetector.recalibrate()
    }

    fun setModelPath(path: String) {
        asrEngine.setModelPath(path)
    }

    override fun onCleared() {
        super.onCleared()
        asrEngine.destroy()
        vadDetector.destroy()
    }
}