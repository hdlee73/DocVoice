"""문서 → 음성(mp3). Microsoft Edge 신경망 음성(edge-tts)을 사용합니다 (인터넷 필요)."""
from __future__ import annotations

import asyncio
import threading
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

from . import Cancelled
from .extract import extract_text
from .segment import is_korean, split_spans

ProgressCB = Optional[Callable[[float, str], None]]

KO_VOICES = {
    "인준 (남성)": "ko-KR-InJoonNeural",
    "현수 (남성, 다국어)": "ko-KR-HyunsuMultilingualNeural",
}
EN_VOICES = {
    "us": {
        "Guy (남성)": "en-US-GuyNeural",
        "Andrew (남성)": "en-US-AndrewNeural",
        "Brian (남성)": "en-US-BrianNeural",
        "Christopher (남성)": "en-US-ChristopherNeural",
        "Eric (남성)": "en-US-EricNeural",
    },
    "uk": {
        "Ryan (남성)": "en-GB-RyanNeural",
        "Thomas (남성)": "en-GB-ThomasNeural",
    },
}
DEFAULT_KO = "ko-KR-InJoonNeural"
DEFAULT_EN = {"us": "en-US-GuyNeural", "uk": "en-GB-RyanNeural"}
MAX_CHUNK_CHARS = 2500
CONCURRENCY = 3


class TTSError(Exception):
    pass


@dataclass
class Chunk:
    text: str
    lang: str  # "ko" | "en"


def build_chunks(text: str, max_chars: int = MAX_CHUNK_CHARS) -> list[Chunk]:
    """문장 단위로 언어를 판별해 같은 언어끼리 묶는다."""
    chunks: list[Chunk] = []
    cur_lang, buf, size = None, [], 0

    def flush() -> None:
        nonlocal buf, size
        if buf:
            chunks.append(Chunk("".join(buf).strip(), cur_lang or "ko"))
        buf, size = [], 0

    lines = text.split("\n")
    for line in lines:
        line_stripped = line.strip()
        if not line_stripped:
            continue
        spans = split_spans(line_stripped)
        for k, (a, b) in enumerate(spans):
            sent = line_stripped[a:b]
            lang = "ko" if is_korean(sent) else "en"
            # 숫자/기호만 있는 문장은 직전 언어를 따른다
            if not any(c.isalpha() for c in sent) and cur_lang:
                lang = cur_lang
            if (lang != cur_lang) or (size + len(sent) > max_chars and buf):
                flush()
                cur_lang = lang
            last = k == len(spans) - 1
            buf.append(sent + ("\n" if last else " "))
            size += len(sent) + 1
    flush()
    return [c for c in chunks if c.text]


def _rate_str(speed: float) -> str:
    pct = round((speed - 1.0) * 100)
    return f"{pct:+d}%"


async def _synth_one(text: str, voice: str, rate: str, fallback_voice: str | None,
                     retries: int = 3) -> bytes:
    import edge_tts

    last: Exception | None = None
    for v in (voice, fallback_voice):
        if v is None or (v == fallback_voice and v == voice):
            continue
        for attempt in range(retries):
            try:
                comm = edge_tts.Communicate(text, v, rate=rate)
                buf = bytearray()
                async for item in comm.stream():
                    if item["type"] == "audio":
                        buf += item["data"]
                if not buf:
                    raise RuntimeError("오디오가 수신되지 않았습니다")
                return bytes(buf)
            except Exception as e:  # noqa: BLE001
                last = e
                await asyncio.sleep(1.5 * (attempt + 1))
    raise TTSError(f"음성 합성에 실패했습니다. 인터넷 연결을 확인하세요. ({last})")


def synthesize_chunks(
    chunks: list[Chunk],
    ko_voice: str,
    en_voice: str,
    speed: float = 1.0,
    progress: ProgressCB = None,
    cancel: threading.Event | None = None,
) -> bytes:
    rate = _rate_str(speed)
    total = len(chunks)
    results: list[bytes | None] = [None] * total
    done = 0

    async def run() -> None:
        sem = asyncio.Semaphore(CONCURRENCY)

        async def task(i: int, ch: Chunk) -> None:
            nonlocal done
            async with sem:
                if cancel is not None and cancel.is_set():
                    raise Cancelled()
                voice = ko_voice if ch.lang == "ko" else en_voice
                fb = DEFAULT_KO if ch.lang == "ko" else None
                results[i] = await _synth_one(ch.text, voice, rate, fb)
                done += 1
                if progress:
                    progress(done / total, f"음성 합성 {done}/{total}")

        await asyncio.gather(*(task(i, c) for i, c in enumerate(chunks)))

    asyncio.run(run())
    return b"".join(r for r in results if r)


def unique_path(path: Path) -> Path:
    if not path.exists():
        return path
    k = 1
    while True:
        cand = path.with_name(f"{path.stem}_{k}{path.suffix}")
        if not cand.exists():
            return cand
        k += 1


def convert_document_to_mp3(
    src: str | Path,
    out_dir: str | Path | None = None,
    accent: str = "us",
    en_voice: str | None = None,
    ko_voice: str | None = None,
    speed: float = 1.0,
    progress: ProgressCB = None,
    cancel: threading.Event | None = None,
) -> Path:
    """문서를 읽어 mp3 파일로 저장하고 경로를 돌려준다."""
    src = Path(src)
    if progress:
        progress(0.0, f"문서 읽는 중: {src.name}")
    text = extract_text(src)
    chunks = build_chunks(text)
    if not chunks:
        raise TTSError("읽을 텍스트가 없습니다.")
    accent = accent if accent in EN_VOICES else "us"
    en_voice = en_voice or DEFAULT_EN[accent]
    ko_voice = ko_voice or DEFAULT_KO
    if progress:
        progress(0.02, f"{len(text):,}자 · {len(chunks)}개 구간 합성 시작")
    audio = synthesize_chunks(chunks, ko_voice, en_voice, speed, progress, cancel)
    out_dir = Path(out_dir) if out_dir else src.parent
    out_dir.mkdir(parents=True, exist_ok=True)
    out = unique_path(out_dir / (src.stem + ".mp3"))
    out.write_bytes(audio)
    if progress:
        progress(1.0, f"완료: {out}")
    return out
