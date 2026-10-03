package com.docvoice.app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.docvoice.app.core.Exporter
import com.docvoice.app.core.Storage
import com.docvoice.app.core.SttOptions
import com.docvoice.app.core.TtsVoices
import com.docvoice.app.core.WhisperSize

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("docvoice", 0)

    val job = JobHub.state

    var tab by mutableIntStateOf(0)

    // 문서 → 음성
    var ttsFile by mutableStateOf<Storage.Picked?>(null)
    var koVoice by mutableStateOf(prefs.getString("koVoice", TtsVoices.DEFAULT_KO)!!)
    var accent by mutableStateOf(prefs.getString("accent", "us")!!)
    var enVoiceUs by mutableStateOf(prefs.getString("enVoiceUs", TtsVoices.EN_US.values.first())!!)
    var enVoiceUk by mutableStateOf(prefs.getString("enVoiceUk", TtsVoices.EN_UK.values.first())!!)
    var speed by mutableFloatStateOf(prefs.getFloat("speed", 1.0f))

    // 음성 → 문서
    var sttFile by mutableStateOf<Storage.Picked?>(null)
    var format by mutableStateOf(prefs.getString("format", "xlsx")!!)
    var language by mutableStateOf(prefs.getString("language", "")!!)
    var size by mutableStateOf(runCatching { WhisperSize.valueOf(prefs.getString("size", "SMALL")!!) }.getOrDefault(WhisperSize.SMALL))
    var diarize by mutableStateOf(prefs.getBoolean("diarize", true))
    var includeTime by mutableStateOf(prefs.getBoolean("includeTime", true))
    var showSpeaker by mutableStateOf(prefs.getBoolean("showSpeaker", true))
    var gap by mutableFloatStateOf(prefs.getFloat("gap", 1.5f))

    val enVoice get() = if (accent == "uk") enVoiceUk else enVoiceUs

    fun pickDoc(uri: Uri) { ttsFile = Storage.describe(app, uri) }
    fun pickAudio(uri: Uri) { sttFile = Storage.describe(app, uri) }

    fun save() {
        prefs.edit()
            .putString("koVoice", koVoice).putString("accent", accent).putString("recLang", recLang)
            .putString("enVoiceUs", enVoiceUs).putString("enVoiceUk", enVoiceUk)
            .putFloat("speed", speed)
            .putString("format", format).putString("language", language).putString("size", size.name)
            .putBoolean("diarize", diarize).putBoolean("includeTime", includeTime)
            .putBoolean("showSpeaker", showSpeaker).putFloat("gap", gap)
            .apply()
    }

    private fun launch(req: JobRequest) {
        if (JobHub.isRunning()) return
        save()
        JobHub.pending = req
        JobHub.state.value = JobState.Running("준비 중", "시작하는 중", null)
        ContextCompat.startForegroundService(app, Intent(app, JobService::class.java))
    }

    fun startTts() {
        val f = ttsFile ?: return
        launch(JobRequest.Tts(f, koVoice, enVoice, speed.toDouble()))
    }

    fun startStt() {
        val f = sttFile ?: return
        launch(
            JobRequest.Stt(
                f, format, SttOptions(language, size, diarize), gap.toDouble(),
                includeTime, showSpeaker && diarize,
            )
        )
    }

    fun cancel() {
        app.startService(Intent(app, JobService::class.java).setAction(JobService.ACTION_CANCEL))
    }

    fun dismissResult() {
        if (!JobHub.isRunning()) JobHub.state.value = JobState.Idle
    }

    // ---- 녹음 ----
    val rec = RecorderHub.state
    var recFormat by mutableStateOf(prefs.getString("recFormat", "docx")!!)
    var recDiarize by mutableStateOf(false)
    var recRefine by mutableStateOf(false)
    var recLang by mutableStateOf(prefs.getString("recLang", "en")!!)

    fun startRecording() {
        if (RecorderHub.isActive()) return
        RecorderHub.reset()
        RecorderHub.language = recLang
        save()
        ContextCompat.startForegroundService(app, Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_START))
    }

    fun togglePause() {
        app.startService(Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_PAUSE))
    }

    fun stopRecording() {
        app.startService(Intent(app, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
    }

    fun newRecording() {
        if (!RecorderHub.isActive()) RecorderHub.reset()
    }

    fun exportRecording() {
        val wav = RecorderHub.state.value.wav
        val title = wav?.name?.let { Storage.baseName(it) } ?: "녹음"
        prefs.edit().putString("recFormat", recFormat).putString("recLang", recLang).apply()
        launch(JobRequest.RecExport(title, recFormat, recRefine, recLang, size, recDiarize, gap.toDouble(), includeTime, showSpeaker && recDiarize))
    }

    val formats get() = Exporter.FORMATS
}
