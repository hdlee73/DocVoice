package com.docvoice.app

import android.net.Uri
import com.docvoice.app.core.SttOptions
import com.docvoice.app.core.Storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

data class OutFile(val name: String, val uri: Uri, val mime: String)

sealed interface JobState {
    data object Idle : JobState
    data class Running(val title: String, val stage: String, val fraction: Float?) : JobState
    data class Done(val message: String, val files: List<OutFile>, val note: String?) : JobState
    data class Failed(val message: String) : JobState
}

sealed interface JobRequest {
    data class Tts(
        val doc: Storage.Picked,
        val koVoice: String,
        val enVoice: String,
        val speed: Double,
    ) : JobRequest

    data class Stt(
        val audio: Storage.Picked,
        val format: String,
        val opts: SttOptions,
        val gap: Double,
        val includeTime: Boolean,
        val showSpeaker: Boolean,
    ) : JobRequest
}

/** 서비스(작업 실행)와 화면(상태 표시)이 공유하는 프로세스 단위 상태. */
object JobHub {
    val state = MutableStateFlow<JobState>(JobState.Idle)

    @Volatile var pending: JobRequest? = null
    @Volatile var job: Job? = null

    fun isRunning() = state.value is JobState.Running
}
