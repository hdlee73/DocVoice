package com.docvoice.app.core

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

data class SttOptions(
    val language: String = "",          // "" = 자동 감지, "ko", "en"
    val size: WhisperSize = WhisperSize.SMALL,
    val diarize: Boolean = true,
)

class SttResult(
    val segments: List<Segment>,
    val turns: List<Triple<Double, Double, Int>>,
    val diarizationNote: String? = null,
)

/** 온디바이스 받아쓰기 (Silero VAD → Whisper) + 화자 구분 (pyannote 분할 + 3D-Speaker 임베딩). */
object Stt {
    /** 화자 구분은 전체 음성을 메모리에 올리므로 너무 긴 파일에서는 건너뛴다. */
    const val MAX_DIARIZE_SECONDS = 50 * 60

    fun transcribe(
        store: ModelStore,
        pcm: PcmBuffer,
        opts: SttOptions,
        isActive: () -> Boolean,
        onStage: (String, Float) -> Unit,
    ): SttResult {
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val sr = AudioDecoder.SAMPLE_RATE

        // 1) 모델 준비
        store.ensureVad(isActive) { m, f -> onStage(m, f) }
        store.ensureWhisper(opts.size, isActive) { m, f -> onStage(m, f) }
        if (opts.diarize) store.ensureDiar(isActive) { m, f -> onStage(m, f) }

        // 2) 받아쓰기
        val wf = store.whisper(opts.size)
        val recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = wf.encoder.path,
                        decoder = wf.decoder.path,
                        language = opts.language,
                        task = "transcribe",
                        tailPaddings = 1000,
                    ),
                    tokens = wf.tokens.path,
                    numThreads = threads,
                    provider = "cpu",
                ),
            )
        )
        val vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = store.vadFile.path,
                    threshold = 0.5f,
                    minSilenceDuration = 0.4f,
                    minSpeechDuration = 0.25f,
                    windowSize = 512,
                    maxSpeechDuration = 25f,
                ),
                sampleRate = sr,
                numThreads = 1,
                provider = "cpu",
            )
        )
        val segments = ArrayList<Segment>()
        try {
            val total = pcm.size
            fun drain() {
                while (!vad.empty()) {
                    val seg = vad.front()
                    vad.pop()
                    val start = seg.start / sr.toDouble()
                    val dur = seg.samples.size / sr.toDouble()
                    val stream = recognizer.createStream()
                    try {
                        stream.acceptWaveform(seg.samples, sr)
                        recognizer.decode(stream)
                        val text = recognizer.getResult(stream).text.trim()
                        if (text.isNotEmpty() && Segmenter.englishWordCount(text) + (if (Segmenter.hasHangul(text)) 1 else 0) > 0) {
                            segments.add(Segment(start, start + dur, text))
                        }
                    } finally {
                        stream.release()
                    }
                }
            }
            val window = 1600
            var pos = 0
            while (pos < total) {
                if (!isActive()) throw kotlinx.coroutines.CancellationException("취소됨")
                val end = minOf(total, pos + window)
                vad.acceptWaveform(pcm.toFloats(pos, end))
                pos = end
                if (!vad.empty()) {
                    drain()
                    onStage("받아쓰는 중", pos.toFloat() / total)
                }
            }
            vad.flush()
            drain()
            onStage("받아쓰는 중", 1f)
        } finally {
            vad.release()
            recognizer.release()
        }

        // 3) 화자 구분 (선택)
        var turns: List<Triple<Double, Double, Int>> = emptyList()
        var note: String? = null
        if (opts.diarize && segments.isNotEmpty()) {
            if (pcm.seconds > MAX_DIARIZE_SECONDS) {
                note = "녹음이 ${MAX_DIARIZE_SECONDS / 60}분보다 길어 화자 구분은 건너뛰었습니다."
            } else {
                try {
                    onStage("화자 구분 중", 0f)
                    turns = diarize(store, pcm, threads)
                    onStage("화자 구분 중", 1f)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    note = "화자 구분에 실패해 화자 정보 없이 저장합니다. (${e.message})"
                }
            }
        }
        return SttResult(segments, turns, note)
    }

    private fun diarize(store: ModelStore, pcm: PcmBuffer, threads: Int): List<Triple<Double, Double, Int>> {
        val sd = OfflineSpeakerDiarization(
            config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = store.segFile.path),
                    numThreads = threads,
                    provider = "cpu",
                ),
                embedding = SpeakerEmbeddingExtractorConfig(model = store.embFile.path, numThreads = threads, provider = "cpu"),
                clustering = FastClusteringConfig(numClusters = -1, threshold = 0.5f),
                minDurationOn = 0.3f,
                minDurationOff = 0.5f,
            )
        )
        try {
            val result = sd.process(pcm.toFloats())
            return result.map { Triple(it.start.toDouble(), it.end.toDouble(), it.speaker) }
        } finally {
            sd.release()
        }
    }

    /** 받아쓰기 결과 → 문장 → (화자 매핑) → 문단 */
    fun toDocument(r: SttResult, gap: Double): Pair<List<Sentence>, List<Paragraph>> {
        val sentences = Segmenter.buildSentences(r.segments)
        if (r.turns.isNotEmpty()) Segmenter.assignSpeakers(sentences, r.turns)
        val paragraphs = Segmenter.groupParagraphs(sentences, gap = gap, useSpeaker = r.turns.isNotEmpty())
        return sentences to paragraphs
    }
}
