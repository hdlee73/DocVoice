"""화자 구분(diarization) - sherpa-onnx (pyannote 분할 + 3D-Speaker 임베딩, CPU/ONNX).

모델은 최초 1회 GitHub 릴리스에서 내려받아 ``~/.docvoice/models`` 에 저장합니다 (약 46MB).
"""
from __future__ import annotations

import tarfile
import urllib.request
from pathlib import Path
from typing import Callable, Optional

import numpy as np

ProgressCB = Optional[Callable[[float, str], None]]

MODEL_DIR = Path.home() / ".docvoice" / "models"
SEG_URL = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/"
           "sherpa-onnx-pyannote-segmentation-3-0.tar.bz2")
EMB_NAME = "3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
EMB_URL = ("https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/"
           + EMB_NAME)


class DiarizationUnavailable(Exception):
    pass


def _download(url: str, dest: Path) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": "DocVoice"})
    with urllib.request.urlopen(req, timeout=60) as r, open(tmp, "wb") as f:
        while True:
            block = r.read(1 << 20)
            if not block:
                break
            f.write(block)
    tmp.replace(dest)


def _safe_extract(tar_path: Path, dest: Path) -> None:
    with tarfile.open(tar_path, "r:bz2") as tf:
        base = dest.resolve()
        for m in tf.getmembers():
            target = (dest / m.name).resolve()
            if not str(target).startswith(str(base)):
                raise DiarizationUnavailable("모델 압축 파일 경로가 올바르지 않습니다.")
        tf.extractall(dest)


def ensure_models(progress: ProgressCB = None) -> tuple[Path, Path]:
    seg = MODEL_DIR / "sherpa-onnx-pyannote-segmentation-3-0" / "model.onnx"
    emb = MODEL_DIR / EMB_NAME
    try:
        if not seg.exists():
            if progress:
                progress(0.0, "화자 구분 모델(분할) 내려받는 중…")
            tar = MODEL_DIR / "seg.tar.bz2"
            _download(SEG_URL, tar)
            _safe_extract(tar, MODEL_DIR)
            tar.unlink(missing_ok=True)
        if not emb.exists():
            if progress:
                progress(0.0, "화자 구분 모델(임베딩) 내려받는 중…")
            _download(EMB_URL, emb)
    except Exception as e:  # noqa: BLE001
        raise DiarizationUnavailable(f"화자 구분 모델을 내려받지 못했습니다: {e}") from e
    return seg, emb


def diarize(
    samples: np.ndarray,
    num_speakers: int = -1,
    threshold: float = 0.5,
    progress: ProgressCB = None,
) -> list[tuple[float, float, int]]:
    """16kHz mono float32 샘플 → [(start, end, speaker_id)]."""
    try:
        import sherpa_onnx
    except ImportError as e:
        raise DiarizationUnavailable("sherpa-onnx 가 설치되어 있지 않습니다.") from e

    seg_model, emb_model = ensure_models(progress)
    config = sherpa_onnx.OfflineSpeakerDiarizationConfig(
        segmentation=sherpa_onnx.OfflineSpeakerSegmentationModelConfig(
            pyannote=sherpa_onnx.OfflineSpeakerSegmentationPyannoteModelConfig(model=str(seg_model)),
        ),
        embedding=sherpa_onnx.SpeakerEmbeddingExtractorConfig(model=str(emb_model)),
        clustering=sherpa_onnx.FastClusteringConfig(num_clusters=num_speakers, threshold=threshold),
        min_duration_on=0.3,
        min_duration_off=0.5,
    )
    if not config.validate():
        raise DiarizationUnavailable("화자 구분 설정이 올바르지 않습니다.")
    sd = sherpa_onnx.OfflineSpeakerDiarization(config)
    if progress:
        progress(0.0, "화자 구분 분석 중…")
    result = sd.process(samples.astype(np.float32)).sort_by_start_time()
    return [(float(r.start), float(r.end), int(r.speaker)) for r in result]
