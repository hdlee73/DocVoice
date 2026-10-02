"""음성 → 문서. faster-whisper 로 받아쓰기 후 문장/문단 단위로 정리합니다."""
from __future__ import annotations

import threading
from pathlib import Path
from typing import Callable, Optional

from . import Cancelled
from .export import FORMATS, export_all
from .segment import Segment, Sentence, assign_speakers, build_sentences

ProgressCB = Optional[Callable[[float, str], None]]

MODEL_SIZES = ["tiny", "base", "small", "medium", "large-v3"]
LANGUAGES = {"자동 감지": None, "한국어": "ko", "영어": "en"}
AUDIO_EXTS = (".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wma", ".mp4", ".mkv", ".webm", ".mov")


class STTError(Exception):
    pass


def transcribe(
    audio_path: str | Path,
    model_size: str = "small",
    language: str | None = None,
    progress: ProgressCB = None,
    cancel: threading.Event | None = None,
):
    """오디오를 받아쓰기하여 (segments, samples_16k, detected_language) 를 돌려준다."""
    try:
        from faster_whisper import WhisperModel
        from faster_whisper.audio import decode_audio
    except ImportError as e:
        raise STTError("faster-whisper 가 설치되어 있지 않습니다.") from e

    if progress:
        progress(0.0, "오디오 불러오는 중…")
    try:
        samples = decode_audio(str(audio_path), sampling_rate=16000)
    except Exception as e:  # noqa: BLE001
        raise STTError(f"오디오를 읽을 수 없습니다: {e}") from e
    duration = len(samples) / 16000.0
    if duration < 0.5:
        raise STTError("오디오가 너무 짧습니다.")

    if progress:
        progress(0.02, f"음성 인식 모델 준비 중 ({model_size}) — 처음에는 내려받느라 시간이 걸립니다")
    try:
        model = WhisperModel(model_size, device="cpu", compute_type="int8")
    except Exception as e:  # noqa: BLE001
        raise STTError(f"음성 인식 모델을 불러오지 못했습니다. 인터넷 연결을 확인하세요. ({e})") from e

    segments_iter, info = model.transcribe(
        samples,
        language=language,
        vad_filter=True,
        beam_size=5,
        condition_on_previous_text=False,
    )
    segs: list[Segment] = []
    for s in segments_iter:
        if cancel is not None and cancel.is_set():
            raise Cancelled()
        segs.append(Segment(float(s.start), float(s.end), s.text))
        if progress:
            progress(0.05 + 0.65 * min(s.end / duration, 1.0), f"받아쓰기 {int(s.end)}/{int(duration)}초")
    return segs, samples, info.language


def convert_audio_to_documents(
    audio: str | Path,
    out_dir: str | Path | None = None,
    formats: list[str] | None = None,
    language: str | None = None,
    model_size: str = "small",
    gap: float = 1.5,
    diarize_speakers: bool = True,
    num_speakers: int = -1,
    include_time: bool = True,
    show_speaker: bool = False,
    progress: ProgressCB = None,
    cancel: threading.Event | None = None,
    log: Callable[[str], None] | None = None,
) -> list[Path]:
    audio = Path(audio)
    formats = formats or ["xlsx"]
    bad = [f for f in formats if f not in FORMATS]
    if bad:
        raise STTError(f"지원하지 않는 출력 형식: {', '.join(bad)}")

    segs, samples, lang = transcribe(audio, model_size, language, progress, cancel)
    if not segs:
        raise STTError("인식된 음성이 없습니다.")
    if log:
        log(f"인식 언어: {lang} · 구간 {len(segs)}개")

    sentences: list[Sentence] = build_sentences(segs)
    if not sentences:
        raise STTError("문장을 만들지 못했습니다.")

    speakers_known = False
    if diarize_speakers and num_speakers != 1:
        if cancel is not None and cancel.is_set():
            raise Cancelled()
        try:
            from .diarize import diarize

            turns = diarize(samples, num_speakers=num_speakers, progress=progress)
            assign_speakers(sentences, turns)
            speakers_known = any(s.speaker is not None for s in sentences)
            if log:
                log(f"화자 {len({t[2] for t in turns})}명 감지")
        except Exception as e:  # noqa: BLE001
            if log:
                log(f"⚠ 화자 구분을 건너뜁니다: {e}")
    if progress:
        progress(0.9, "문서 저장 중…")

    out_dir = Path(out_dir) if out_dir else audio.parent
    out_dir.mkdir(parents=True, exist_ok=True)
    paths = export_all(
        sentences, out_dir, audio.stem, formats,
        gap=gap, use_speaker=speakers_known, include_time=include_time,
        show_speaker=show_speaker and speakers_known,
    )
    if progress:
        progress(1.0, "완료")
    return paths
