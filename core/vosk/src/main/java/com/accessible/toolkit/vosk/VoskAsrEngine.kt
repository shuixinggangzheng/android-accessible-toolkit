package com.accessible.toolkit.vosk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.accessible.toolkit.engine.AsrCallback
import com.accessible.toolkit.engine.AsrEngine
import com.accessible.toolkit.engine.AsrError
import com.accessible.toolkit.engine.model.TranscriptResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VoskAsrEngine(private val context: Context) : AsrEngine {

    companion object {
        private const val TAG = "VoskAsrEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val PARTIAL_THROTTLE_MS = 300L
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    private val modelManager = ModelManager(context)
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var callback: AsrCallback? = null
    private val listening = AtomicBoolean(false)
    private val lastPartialTime = AtomicLong(0)
    private var retryCount = 0
    private var customModelPath: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recognitionJob: Job? = null

    override fun startListening() {
        if (listening.get()) {
            Log.w(TAG, "Already listening")
            return
        }

        // 检查权限
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback?.onError(AsrError.PermissionDenied)
            return
        }

        // 检查模型
        val currentModel = model
        if (currentModel == null) {
            callback?.onError(AsrError.NotInitialized)
            return
        }

        try {
            // 先释放旧的实例防止泄漏
            speechService?.shutdown()
            recognizer?.close()

            recognizer = Recognizer(currentModel, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)

            speechService?.startListening(createRecognitionListener())
            listening.set(true)
            retryCount = 0
            Log.d(TAG, "Started listening")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start listening", e)
            callback?.onError(AsrError.RuntimeError("启动识别失败: ${e.message}"))
            attemptRetry()
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onPartialResult(hypothesis: String?) {
                hypothesis?.let {
                    val result = parseResult(it, isFinal = false)
                    if (result != null && result.text.isNotEmpty()) {
                        throttlePartialResult(result)
                    }
                }
            }

            override fun onResult(hypothesis: String?) {
                hypothesis?.let {
                    val result = parseResult(it, isFinal = true)
                    if (result != null && result.text.isNotEmpty()) {
                        callback?.onFinalResult(result)
                    }
                }
            }

            override fun onFinalResult(hypothesis: String?) {
                hypothesis?.let {
                    val result = parseResult(it, isFinal = true)
                    if (result != null && result.text.isNotEmpty()) {
                        callback?.onFinalResult(result)
                    }
                }
                // 识别完成，停止监听
                listening.set(false)
            }

            override fun onError(exception: Exception?) {
                Log.e(TAG, "Recognition error", exception)
                callback?.onError(
                    AsrError.RuntimeError(exception?.message ?: "未知识别错误")
                )
                listening.set(false)
                attemptRetry()
            }

            override fun onTimeout() {
                Log.d(TAG, "Recognition timeout")
                listening.set(false)
            }
        }
    }

    /**
     * 节流 partial 结果回调（每 300ms 最多一次）
     */
    private fun throttlePartialResult(result: TranscriptResult) {
        val now = System.currentTimeMillis()
        val lastTime = lastPartialTime.get()
        if (now - lastTime >= PARTIAL_THROTTLE_MS) {
            lastPartialTime.set(now)
            callback?.onPartialResult(result)
        }
    }

    /**
     * 解析 Vosk 返回的 JSON 结果
     */
    private fun parseResult(json: String, isFinal: Boolean): TranscriptResult? {
        return try {
            val obj = JSONObject(json)
            val text = obj.optString("text", "")
            if (text.isEmpty()) return null

            val result = TranscriptResult(
                text = text,
                isFinal = isFinal,
                confidence = if (isFinal) 1.0f else 0.8f,
                timestamp = System.currentTimeMillis()
            )
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse result JSON: $json", e)
            null
        }
    }

    /**
     * 尝试重试识别
     */
    private fun attemptRetry() {
        if (retryCount >= MAX_RETRY_COUNT) {
            Log.w(TAG, "Max retry count reached")
            callback?.onError(AsrError.RuntimeError("识别失败，已达最大重试次数"))
            return
        }

        retryCount++
        Log.d(TAG, "Attempting retry $retryCount/$MAX_RETRY_COUNT")

        recognitionJob?.cancel()
        recognitionJob = scope.launch {
            delay(RETRY_DELAY_MS)
            if (listening.get()) {
                startListening()
            }
        }
    }

    override fun stopListening() {
        if (!listening.get()) return

        speechService?.stop()
        listening.set(false)
        retryCount = 0
        Log.d(TAG, "Stopped listening")
    }

    override fun setCallback(callback: AsrCallback) {
        this.callback = callback
    }

    override fun isListening(): Boolean = listening.get()

    override fun destroy() {
        stopListening()
        recognitionJob?.cancel()
        scope.cancel()

        speechService?.shutdown()
        recognizer?.close()
        model?.close()

        speechService = null
        recognizer = null
        model = null
    }

    /**
     * 加载模型（从路径）
     */
    fun loadModel(modelPath: String) {
        try {
            model?.close()
            model = Model(modelPath)
            customModelPath = modelPath
            callback?.onReady()
            Log.d(TAG, "Model loaded from: $modelPath")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load model from $modelPath", e)
            callback?.onError(AsrError.ModelLoadFailed)
        }
    }

    /**
     * 从 assets 加载模型（带解压进度）
     */
    fun loadModelFromAssets(
        onProgress: ((String) -> Unit)? = null
    ) {
        scope.launch {
            modelManager.extractModelWithProgress().collect { progress ->
                when (progress) {
                    is ModelManager.ExtractProgress.Starting -> {
                        onProgress?.invoke("正在准备模型...")
                    }
                    is ModelManager.ExtractProgress.Progress -> {
                        onProgress?.invoke("解压进度: ${progress.percent}%")
                    }
                    is ModelManager.ExtractProgress.Complete -> {
                        loadModel(progress.path)
                    }
                    is ModelManager.ExtractProgress.Error -> {
                        callback?.onError(AsrError.ModelNotFound)
                        onProgress?.invoke(progress.message)
                    }
                }
            }
        }
    }

    /**
     * 加载默认模型（assets 中的中文小模型）
     */
    fun loadDefaultModel() {
        if (modelManager.isDefaultModelExtracted()) {
            loadModel(modelManager.getDefaultModelPath())
        } else {
            loadModelFromAssets()
        }
    }

    /**
     * 设置自定义模型路径
     */
    fun setModelPath(path: String) {
        customModelPath = path
        loadModel(path)
    }

    /**
     * 导入并加载自定义模型
     */
    fun importAndLoadModel(sourcePath: String, modelName: String) {
        scope.launch {
            val result = modelManager.importCustomModel(sourcePath, modelName)
            result.onSuccess { modelPath ->
                loadModel(modelPath)
            }.onFailure { e ->
                callback?.onError(AsrError.RuntimeError("导入模型失败: ${e.message}"))
            }
        }
    }

    /**
     * 获取所有可用模型
     */
    fun getAvailableModels(): List<ModelManager.ModelInfo> {
        return modelManager.getAvailableModels()
    }

    /**
     * 删除模型
     */
    fun deleteModel(modelName: String): Boolean {
        return modelManager.deleteModel(modelName)
    }

    /**
     * 获取模型管理器
     */
    fun getModelManager(): ModelManager = modelManager
}