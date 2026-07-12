package com.accessible.toolkit.vad

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.accessible.toolkit.engine.VadCallback
import com.accessible.toolkit.engine.VadDetector
import java.util.concurrent.atomic.AtomicBoolean

class EnergyBasedVadDetector(
    private val androidContext: android.content.Context,
    private val silenceTimeoutMs: Long = 2000
) : VadDetector {

    companion object {
        private const val TAG = "EnergyBasedVadDetector"
        private const val SAMPLE_RATE = 16000
        private const val ENERGY_THRESHOLD = 500f
        private const val ZERO_CROSSING_THRESHOLD = 10
        private const val FRAME_SIZE = 320
    }

    private var audioRecord: AudioRecord? = null
    private var callback: VadCallback? = null
    private val running = AtomicBoolean(false)
    private var isVoiceActive = false
    private var lastVoiceTime = 0L
    private var recordingThread: Thread? = null

    override fun start() {
        if (running.get()) return

        if (ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
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
            lastVoiceTime = System.currentTimeMillis()

            recordingThread = Thread { processAudio() }.apply {
                start()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start AudioRecord", e)
        }
    }

    private fun processAudio() {
        val buffer = ShortArray(FRAME_SIZE)

        while (running.get()) {
            val readSize = audioRecord?.read(buffer, 0, FRAME_SIZE) ?: 0
            if (readSize > 0) {
                val energy = calculateEnergy(buffer, readSize)
                val zeroCrossings = calculateZeroCrossings(buffer, readSize)

                val isVoice = energy > ENERGY_THRESHOLD && zeroCrossings > ZERO_CROSSING_THRESHOLD

                if (isVoice && !isVoiceActive) {
                    isVoiceActive = true
                    lastVoiceTime = System.currentTimeMillis()
                    callback?.onVoiceStart()
                } else if (isVoice) {
                    lastVoiceTime = System.currentTimeMillis()
                }

                if (isVoiceActive && !isVoice) {
                    val silenceDuration = System.currentTimeMillis() - lastVoiceTime
                    if (silenceDuration >= silenceTimeoutMs) {
                        isVoiceActive = false
                        callback?.onVoiceEnd()
                    }
                }
            }
        }
    }

    private fun calculateEnergy(buffer: ShortArray, size: Int): Float {
        var sum = 0L
        for (i in 0 until size) {
            sum += buffer[i].toLong() * buffer[i].toLong()
        }
        return (sum / size).toFloat()
    }

    private fun calculateZeroCrossings(buffer: ShortArray, size: Int): Int {
        var count = 0
        for (i in 1 until size) {
            if ((buffer[i] >= 0 && buffer[i - 1] < 0) ||
                (buffer[i] < 0 && buffer[i - 1] >= 0)
            ) {
                count++
            }
        }
        return count
    }

    override fun stop() {
        running.set(false)
        recordingThread?.join(1000)
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    override fun setCallback(callback: VadCallback) {
        this.callback = callback
    }

    override fun isRunning(): Boolean = running.get()

    override fun destroy() {
        stop()
    }
}