package com.docvoice.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.docvoice.app.core.AudioDecoder
import com.docvoice.app.core.ModelStore
import com.docvoice.app.core.PcmBuffer
import com.docvoice.app.core.Segment
import com.docvoice.app.core.Storage
import com.docvoice.app.core.Stt
import com.docvoice.app.core.LiveLang
import com.docvoice.app.core.LiveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/** 마이크 녹음 + 실시간 받아쓰기 (Silero VAD 로 말 구간을 잘라 Whisper 로 바로 인식). */
class RecorderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wake: PowerManager.WakeLock? = null

    private class Speech(val samples: FloatArray, val start: Int)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { RecorderHub.stopRequested.set(true); return START_NOT_STICKY }
            ACTION_PAUSE -> { RecorderHub.paused.set(!RecorderHub.paused.get()); return START_NOT_STICKY }
            ACTION_START -> {}
            else -> return START_NOT_STICKY
        }
        if (RecorderHub.job?.isActive == true) return START_NOT_STICKY
        ensureChannel()
        startForeground(NOTIF_ID, notification("준비 중"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DocVoice:rec").apply { acquire(4 * 60 * 60 * 1000L) }
        RecorderHub.stopRequested.set(false)
        RecorderHub.paused.set(false)
        RecorderHub.pcm = null
        RecorderHub.state.value = RecState(phase = RecPhase.Preparing, message = "준비 중")

        RecorderHub.job = scope.launch {
            try {
                record()
            } catch (e: CancellationException) {
                RecorderHub.upd { copy(phase = RecPhase.Failed, message = "녹음을 취소했습니다.") }
            } catch (e: SecurityException) {
                RecorderHub.state.value = RecState(phase = RecPhase.Failed, message = "마이크 권한이 필요합니다.")
            } catch (e: Throwable) {
                RecorderHub.upd { copy(
                    phase = RecPhase.Failed, message = "녹음 중 오류가 발생했습니다: ${e.message ?: e.javaClass.simpleName}",
                ) }
            } finally {
                wake?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        wake?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private suspend fun record() {
        val sr = AudioDecoder.SAMPLE_RATE
        val store = ModelStore(this)
        val lang = LiveLang.of(RecorderHub.language)
        val alive = { scope.isActive && !RecorderHub.stopRequested.get() }

        withContext(Dispatchers.IO) {
            store.ensureLive(lang, alive) { m, f -> prep(m, f) }
        }
        if (RecorderHub.stopRequested.get()) { RecorderHub.reset(); return }
        prep("음성 인식 준비 중", null)
        val recognizer = Stt.newLive(store, lang)
        val stream = recognizer.createStream()

        val pcm = PcmBuffer(sr * 60 * 5)
        RecorderHub.pcm = pcm
        val queue = Channel<FloatArray>(Channel.UNLIMITED)
        val segments = ArrayList<Segment>()

        // 인식 워커: 녹음 스레드와 분리해 오디오가 끊기지 않게 한다.
        var workerError: String? = null
        val worker = scope.launch(Dispatchers.Default) {
          try {
            var fed = 0L
            var segStart = 0.0
            var lastEnd = 0.0
            var active = false
            fun decodeAll() { while (recognizer.isReady(stream)) recognizer.decode(stream) }
            fun finalizeSegment(now: Double) {
                val text = LiveText.format(recognizer.getResult(stream).text, lang.key, true)
                if (text.isNotEmpty()) {
                    segments.add(Segment(segStart, maxOf(now, segStart + 0.3), text))
                    lastEnd = now
                }
                recognizer.reset(stream)
                active = false
                RecorderHub.upd { copy(segments = segments.toList(), partial = "", speaking = false) }
            }
            for (chunk in queue) {
                stream.acceptWaveform(chunk, sr)
                fed += chunk.size
                decodeAll()
                val now = fed / sr.toDouble()
                val raw = recognizer.getResult(stream).text
                if (raw.isNotBlank() && !active) { active = true; segStart = maxOf(lastEnd, now - 0.5) }
                if (recognizer.isEndpoint(stream)) {
                    finalizeSegment(now)
                } else {
                    val part = LiveText.format(raw, lang.key, false)
                    RecorderHub.upd { copy(partial = part, speaking = part.isNotEmpty()) }
                }
            }
            // 마무리: 남은 꼬리 처리
            stream.acceptWaveform(FloatArray(sr / 2), sr)
            stream.inputFinished()
            decodeAll()
            finalizeSegment(fed / sr.toDouble())
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            workerError = "인식 오류: ${e.message ?: e.javaClass.simpleName}"
            for (ignored in queue) { }
          }
        }

        val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, sr * 4),
        )
        // 블루투스 이어폰이 연결돼 있어도 휴대폰 내장 마이크로 녹음 (블루투스 입력은 무음이 되기 쉬움)
        try {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            am.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC }
                ?.let { rec.preferredDevice = it }
        } catch (_: Exception) {}
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); queue.close(); worker.cancel(); stream.release(); recognizer.release()
            throw IllegalStateException("마이크를 시작할 수 없습니다.")
        }
        try {
            rec.startRecording()
            RecorderHub.upd { copy(phase = RecPhase.Recording, message = null, fraction = null) }
            val buf = ShortArray(1600) // 0.1초
            var lastNotify = 0L
            var peak = 0f
            withContext(Dispatchers.IO) {
                while (!RecorderHub.stopRequested.get() && scope.isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (RecorderHub.paused.get()) {
                        RecorderHub.upd { copy(phase = RecPhase.Paused, level = 0f) }
                        continue
                    }
                    var sum = 0.0
                    val f = FloatArray(n)
                    for (i in 0 until n) {
                        pcm.add(buf[i])
                        f[i] = buf[i] / 32768f
                        sum += f[i] * f[i]
                        val a = kotlin.math.abs(f[i])
                        if (a > peak) peak = a
                    }
                    queue.trySend(f)
                    val rms = sqrt(sum / n).toFloat()
                    RecorderHub.upd { copy(phase = RecPhase.Recording, seconds = pcm.seconds, level = (rms * 6f).coerceIn(0f, 1f), peak = peak) }
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 1000) {
                        lastNotify = now
                        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(fmt(pcm.seconds)))
                    }
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }

        RecorderHub.upd { copy(phase = RecPhase.Finishing, message = null, level = 0f) }
        queue.close()
        worker.join()
        stream.release()
        recognizer.release()

        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        RecorderHub.upd { copy(phase = RecPhase.Stopped, seconds = pcm.seconds, segments = segments.toList(), wav = null, message = workerError, title = "녹음_$stamp") }
    }

    private fun prep(msg: String, f: Float?) {
        RecorderHub.upd { copy(phase = RecPhase.Preparing, message = msg, fraction = f) }
    }

    private fun fmt(sec: Double): String {
        val s = sec.toInt()
        return String.format(java.util.Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "녹음", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("녹음 중")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "중지", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.docvoice.app.REC_START"
        const val ACTION_STOP = "com.docvoice.app.REC_STOP"
        const val ACTION_PAUSE = "com.docvoice.app.REC_PAUSE"
        private const val CHANNEL = "rec"
        private const val NOTIF_ID = 3
    }
}
