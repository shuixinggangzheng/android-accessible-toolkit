package com.accessible.toolkit.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.accessible.toolkit.bridge.BridgeService
import com.accessible.toolkit.engine.EventBus
import com.accessible.toolkit.subtitle.SubtitleService
import com.accessible.toolkit.vosk.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ToolkitApplication : Application() {

    companion object {
        private const val TAG = "ToolkitApplication"
        lateinit var instance: ToolkitApplication
            private set
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "App initializing")

        preloadVoskModel()
        initTtsEngine()
        registerEventBusCollectors()
        startNotificationPanel()
    }

    private fun preloadVoskModel() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelManager = ModelManager(this@ToolkitApplication)
                if (!modelManager.isDefaultModelExtracted()) {
                    Log.d(TAG, "Preloading Vosk model from assets...")
                    modelManager.extractModelWithProgress().collect { progress ->
                        when (progress) {
                            is ModelManager.ExtractProgress.Starting -> Log.d(TAG, "Model extraction started")
                            is ModelManager.ExtractProgress.Progress -> {}
                            is ModelManager.ExtractProgress.Complete -> Log.d(TAG, "Model ready: ${progress.path}")
                            is ModelManager.ExtractProgress.Error -> Log.w(TAG, "Model preload failed: ${progress.message}")
                        }
                    }
                } else {
                    Log.d(TAG, "Vosk model already extracted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to preload Vosk model", e)
            }
        }
    }

    private fun initTtsEngine() {
        try {
            android.speech.tts.TextToSpeech(this) { status ->
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    Log.d(TAG, "TTS engine initialized")
                } else {
                    Log.w(TAG, "TTS init failed, status=$status")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "TTS engine init failed", e)
        }
    }

    private fun registerEventBusCollectors() {
        appScope.launch {
            EventBus.appState.collectLatest { state ->
                Log.d(TAG, "App state: $state")
            }
        }

        appScope.launch {
            EventBus.bridgeState.collectLatest { state ->
                if (state.running) {
                    Log.d(TAG, "Bridge: ${state.lanIp}, clients=${state.clientCount}")
                }
            }
        }

        appScope.launch {
            EventBus.subtitleState.collectLatest { running ->
                Log.d(TAG, "Subtitle: ${if (running) "running" else "stopped"}")
            }
        }
    }

    private fun startNotificationPanel() {
        handler.postDelayed({
            QuickBallService.start(this)
        }, 500)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning")
    }
}
