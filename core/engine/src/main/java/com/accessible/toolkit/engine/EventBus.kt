package com.accessible.toolkit.engine

import com.accessible.toolkit.engine.model.TranscriptResult
import com.accessible.toolkit.engine.model.VadEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object EventBus {

    enum class AppState {
        IDLE, LISTENING, TRANSCRIBING, READING, PAUSED
    }

    data class BridgeState(
        val running: Boolean = false,
        val clientCount: Int = 0,
        val lanIp: String = "127.0.0.1"
    )

    private val _appState = MutableStateFlow(AppState.IDLE)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _asrResults = MutableSharedFlow<TranscriptResult>(replay = 0, extraBufferCapacity = 16)
    val asrResults: SharedFlow<TranscriptResult> = _asrResults.asSharedFlow()

    private val _vadState = MutableSharedFlow<VadEvent>(replay = 0, extraBufferCapacity = 8)
    val vadState: SharedFlow<VadEvent> = _vadState.asSharedFlow()

    private val _bridgeState = MutableStateFlow(BridgeState())
    val bridgeState: StateFlow<BridgeState> = _bridgeState.asStateFlow()

    private val _subtitleState = MutableStateFlow(false)
    val subtitleState: StateFlow<Boolean> = _subtitleState.asStateFlow()

    fun setAppState(state: AppState) { _appState.value = state }

    suspend fun emitAsrResult(result: TranscriptResult) { _asrResults.emit(result) }

    suspend fun emitVadEvent(event: VadEvent) { _vadState.emit(event) }

    fun setBridgeState(state: BridgeState) { _bridgeState.value = state }

    fun setBridgeRunning(running: Boolean, ip: String = "", clients: Int = 0) {
        _bridgeState.value = BridgeState(running, clients, ip)
    }

    fun setSubtitleRunning(running: Boolean) { _subtitleState.value = running }
}
