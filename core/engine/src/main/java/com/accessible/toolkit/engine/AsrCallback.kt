package com.accessible.toolkit.engine

import com.accessible.toolkit.engine.model.TranscriptResult

interface AsrCallback {
    fun onPartialResult(result: TranscriptResult)
    fun onFinalResult(result: TranscriptResult)
    fun onError(error: AsrError)
    fun onReady()
}

sealed class AsrError {
    object NotInitialized : AsrError()
    object PermissionDenied : AsrError()
    object ModelLoadFailed : AsrError()
    object ModelNotFound : AsrError()
    data class RuntimeError(val message: String) : AsrError()
}