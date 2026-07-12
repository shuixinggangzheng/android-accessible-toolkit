package com.accessible.toolkit.vad

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.VadDetector

class EnergyVadDetector(
    private val context: Context,
    private val calibrationDurationMs: Long = 3000,
    private val voiceThresholdMultiplier: Float = 3.0f,
    private val voiceStartFrames: Int = 5,
    private val voiceEndFrames: Int = 30
) : VadDetector {

    companion object {
        private const val TAG = "EnergyVadDetector"
        private const val SAMPLE_RATE = 16000
        private const val FRAME_SIZE = 640
        private const val FRAME_DURATION_MS = 40L
    }

    private var audioRecord: AudioRecord? = null
    private var callback: VadCallback? = null
    private val running = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    // 状态
    private var isVoiceActive = false
    private var consecutiveVoiceFrames = 0
    private var consecutiveSilenceFrames = 0
    private var silenceStartTime = 0L
    private var lastSilenceDurationReport = -1

    // 噪声校准
    private var isCalibrating = true
    private var calibrationFrames = 0
    private var noiseFloorSum = 0.0
    private var noiseFloor = 0.0
    private var voiceThreshold = 0.0

    private val atomicBooleanClass = AtomicBoolean::class.java

    override fun start() {
        if (running.get()) return

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback?.onError("需要麦克风权限")
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            running.set(true)

            // 重置状态
            isVoiceActive = false
            consecutiveVoiceFrames = 0
            consecutiveSilenceFrames = 0
            isCalibrating = true
            calibrationFrames = 0
            noiseFloorSum = 0.0
            lastSilenceDurationReport = -1

            recordingThread = Thread { processAudio() }.apply {
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(TAG, "Started VAD detection")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start AudioRecord", e)
            callback?.onError("无法启动录音: ${e.message}")
        }
    }

    private fun processAudio() {
        val buffer = ShortArray(FRAME_SIZE)

        while (running.get()) {
            val readSize = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0
            if (readSize > 0) {
                val energy = calculateEnergy(buffer, readSize)
                processFrame(energy, buffer, readSize)
            }
        }
    }

    private fun processFrame(energy: Double, buffer: ShortArray, size: Int) {
        // 噪声校准阶段
        if (isCalibrating) {
            calibrationFrames++
            noiseFloorSum += energy
            val calibrationFrameCount = (calibrationDurationMs / FRAME_DURATION_MS).toInt()

            if (calibrationFrames >= calibrationFrameCount) {
                noiseFloor = noiseFloorSum / calibrationFrames
                voiceThreshold = noiseFloor * voiceThresholdMultiplier
                isCalibrating = false
                Log.d(TAG, "Calibration complete: noiseFloor=$noiseFloor, threshold=$voiceThreshold")
            }
            return
        }

        // 正常检测阶段
        val isAboveThreshold = energy > voiceThreshold

        if (isAboveThreshold) {
            consecutiveVoiceFrames++
            consecutiveSilenceFrames = 0

            if (!isVoiceActive && consecutiveVoiceFrames >= voiceStartFrames) {
                isVoiceActive = true
                silenceStartTime = System.currentTimeMillis()
                lastSilenceDurationReport = -1
                callback?.onVoiceStart()
                Log.d(TAG, "Voice started (energy=$energy, threshold=$voiceThreshold)")
            }
        } else {
            consecutiveSilenceFrames++
            consecutiveVoiceFrames = 0

            if (isVoiceActive) {
                if (consecutiveSilenceFrames == 1) {
                    silenceStartTime = System.currentTimeMillis()
                }

                val silenceDurationMs = System.currentTimeMillis() - silenceStartTime
                val silenceDurationSec = (silenceDurationMs / 1000).toInt()

                if (silenceDurationSec > lastSilenceDurationReport) {
                    lastSilenceDurationReport = silenceDurationSec
                    callback?.onSilenceDuration(silenceDurationSec)
                }

                if (consecutiveSilenceFrames >= voiceEndFrames) {
                    isVoiceActive = false
                    consecutiveSilenceFrames = 0
                    callback?.onVoiceEnd()
                    Log.d(TAG, "Voice ended (silence=${silenceDurationMs}ms)")
                }
            }
        }
    }

    private fun calculateEnergy(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sum / size
    }

    override fun stop() {
        if (!running.get()) return

        running.set(false)
        recordingThread?.join(2000)
        recordingThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null

        Log.d(TAG, "Stopped VAD detection")
    }

    override fun setCallback(callback: VadCallback) {
        this.callback = callback
    }

    override fun isRunning(): Boolean = running.get()

    override fun destroy() {
        stop()
    }

    /**
     * 重置噪声校准
     */
    fun recalibrate() {
        isCalibrating = true
        calibrationFrames = 0
        noiseFloorSum = 0.0
        Log.d(TAG, "Recalibration requested")
    }

    /**
     * 手动设置噪声底（跳过校准）
     */
    fun setNoiseFloor(floor: Double) {
        noiseFloor = floor
        voiceThreshold = floor * voiceThresholdMultiplier
        isCalibrating = false
        Log.d(TAG, "Manual noise floor set: $floor, threshold=$voiceThreshold")
    }

    /**
     * 获取当前噪声底
     */
    fun getNoiseFloor(): Double = noiseFloor

    /**
     * 获取当前语音阈值
     */
    fun getVoiceThreshold(): Double = voiceThreshold
}

private class AtomicBoolean(initialValue: Boolean) {
    @Volatile
    private var value = initialValue

    fun get(): Boolean = value

    fun set(newValue: Boolean) {
        value = newValue
    }

    fun compareAndSet(expected: Boolean, new: Boolean): Boolean {
        if (value == expected) {
            value = new
            return true
        }
        return false
    }
}