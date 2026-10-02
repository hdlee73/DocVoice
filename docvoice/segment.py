"""문장 분리 · 화자 매핑 · 문단 묶기 (GUI/네트워크와 무관한 순수 로직)."""
from __future__ import annotations

import bisect
import re
from dataclasses import dataclass, field
from typing import Iterable, Optional

HANGUL_RE = re.compile(r"[ᄀ-ᇿ㄰-㆏가-힯]")
WORD_RE = re.compile(r"[A-Za-z0-9]+(?:['’\-][A-Za-z0-9]+)*")
ALNUM_RE = re.compile(r"[0-9A-Za-zᄀ-ᇿ㄰-㆏가-힯]")
KO_END_RE = re.compile(r"(다|요|죠|까|네|니다)[\"'”’)\]]*$")

TERMINATORS = ".?!。！？…"
_CLOSERS = "\"'”’)]»"
_ABBREV = {
    "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e",
    "no", "inc", "ltd", "co", "corp", "u.s", "u.k", "a.m", "p.m", "fig", "approx",
    "dept", "est", "vol", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep",
    "sept", "oct", "nov", "dec",
}
# 문장 끝에도 올 수 있는 약어 (다음 단어가 대문자면 문장 끝으로 본다)
_CAN_END = {"etc", "inc", "ltd", "co", "corp", "u.s", "u.k", "a.m", "p.m"}


@dataclass
class Segment:
    start: float
    end: float
    text: str


@dataclass
class Sentence:
    start: float
    end: float
    text: str
    speaker: Optional[int] = None


@dataclass
class Paragraph:
    sentences: list[Sentence] = field(default_factory=list)

    @property
    def text(self) -> str:
        return " ".join(s.text for s in self.sentences)

    @property
    def start(self) -> float:
        return self.sentences[0].start

    @property
    def end(self) -> float:
        return self.sentences[-1].end

    @property
    def speaker(self) -> Optional[int]:
        return self.sentences[0].speaker


def has_hangul(text: str) -> bool:
    return bool(HANGUL_RE.search(text))


def is_korean(text: str, ratio: float = 0.15) -> bool:
    """한글 비율이 일정 이상이면 한국어 문장으로 본다."""
    letters = re.findall(r"[A-Za-zᄀ-ᇿ㄰-㆏가-힯]", text)
    if not letters:
        return False
    hangul = sum(1 for c in letters if HANGUL_RE.match(c))
    return hangul / len(letters) >= ratio


def english_word_count(text: str) -> int:
    return len(WORD_RE.findall(text))


def _is_abbrev(text: str, i: int, j: int) -> bool:
    """text[i]가 '.'일 때 문장 끝이 아닌 약어/목록 번호인지 판단."""
    if text[i] != "." or j != i + 1:
        return False
    a = i
    while a > 0 and not text[a - 1].isspace():
        a -= 1
    tok = text[a:i]
    if not tok:
        return False
    low = tok.lower().lstrip("([\"'“‘")
    # 줄 첫머리의 목록 번호 ("1. 개요")
    line_start = a == 0 or text[a - 1] == "\n"
    if line_start and tok.isdigit() and len(tok) <= 3:
        return True
    # 이니셜 (J. Smith)
    if len(tok) == 1 and tok.isalpha() and tok.isupper():
        return True
    if low in _ABBREV:
        if low in _CAN_END:
            k = j
            while k < len(text) and text[k].isspace():
                k += 1
            if k < len(text) and text[k].isupper():
                return False
        return True
    return False


def split_spans(text: str) -> list[tuple[int, int]]:
    """문장 경계를 찾아 (시작, 끝) 오프셋 목록을 돌려준다. 줄바꿈은 항상 경계."""
    spans: list[tuple[int, int]] = []
    n = len(text)
    start = 0

    def flush(a: int, b: int) -> None:
        while a < b and text[a].isspace():
            a += 1
        while b > a and text[b - 1].isspace():
            b -= 1
        if b > a:
            spans.append((a, b))

    i = 0
    while i < n:
        ch = text[i]
        if ch == "\n":
            flush(start, i)
            start = i + 1
            i += 1
            continue
        if ch in TERMINATORS:
            j = i + 1
            while j < n and text[j] in TERMINATORS:
                j += 1
            while j < n and text[j] in _CLOSERS:
                j += 1
            at_end = j >= n or text[j].isspace()
            fullwidth = ch in "。！？"
            if (at_end or fullwidth) and not _is_abbrev(text, i, j):
                flush(start, j)
                start = j
            i = j
            continue
        i += 1
    flush(start, n)
    return spans


def split_sentences(text: str) -> list[str]:
    return [text[a:b] for a, b in split_spans(text)]


# ---------------------------------------------------------------------------
# Whisper 세그먼트 → 문장
# ---------------------------------------------------------------------------

def _ends_sentence(tail: str) -> bool:
    t = tail.rstrip()
    if not t:
        return False
    k = len(t)
    while k > 0 and t[k - 1] in _CLOSERS:
        k -= 1
    return (k > 0 and t[k - 1] in TERMINATORS) or bool(KO_END_RE.search(t))


def _interp(pos: int, spans: list[tuple[int, int, float, float]], starts: list[int],
            as_end: bool) -> float:
    """문자 위치를 시간으로 선형 보간."""
    if as_end:
        idx = bisect.bisect_left([s[1] for s in spans], pos)
        idx = min(idx, len(spans) - 1)
        c0, c1, t0, t1 = spans[idx]
        pos = min(max(pos, c0), c1)
    else:
        idx = bisect.bisect_right(starts, pos) - 1
        idx = max(idx, 0)
        c0, c1, t0, t1 = spans[idx]
        if pos > c1 and idx + 1 < len(spans):  # 구분 문자 위치 → 다음 세그먼트 시작
            c0, c1, t0, t1 = spans[idx + 1]
        pos = min(max(pos, c0), c1)
    if c1 == c0:
        return t0
    return t0 + (pos - c0) / (c1 - c0) * (t1 - t0)


def build_sentences(
    segments: Iterable[Segment],
    min_en_words: int = 3,
    pause_boundary: float = 0.8,
    max_pending_chars: int = 160,
) -> list[Sentence]:
    """Whisper 세그먼트를 문장 단위로 재구성한다.

    - 구두점 기준으로 문장을 나누고, 구두점이 없을 때는 긴 쉼/한국어 종결어미로 보완.
    - 영어 문장은 단어가 ``min_en_words``개 미만이면 이웃 문장에 붙인다.
    """
    segs = [s for s in segments if s.text and s.text.strip()]
    if not segs:
        return []

    full = ""
    spans: list[tuple[int, int, float, float]] = []
    prev: Optional[Segment] = None
    for seg in segs:
        t = seg.text.strip()
        if prev is not None:
            tail = full.rsplit("\n", 1)[-1]
            gap = seg.start - prev.end
            boundary = (
                gap >= pause_boundary
                or len(tail) >= max_pending_chars
                or (_ends_sentence(tail) and has_hangul(tail))
            )
            full += "\n" if boundary else " "
        c0 = len(full)
        full += t
        spans.append((c0, len(full), seg.start, max(seg.end, seg.start)))
        prev = seg

    starts = [s[0] for s in spans]
    raw: list[Sentence] = []
    for a, b in split_spans(full):
        text = full[a:b]
        if not ALNUM_RE.search(text):
            continue
        t0 = _interp(a, spans, starts, as_end=False)
        t1 = _interp(b, spans, starts, as_end=True)
        raw.append(Sentence(t0, max(t1, t0), text))
    return merge_short_english(raw, min_en_words)


def merge_short_english(sentences: list[Sentence], min_words: int = 3) -> list[Sentence]:
    """단어 수가 min_words 미만인 영어 조각은 독립 문장으로 보지 않고 이웃에 붙인다."""
    out: list[Sentence] = []
    pending: Optional[Sentence] = None  # 앞에 붙일 대상이 없을 때 다음 문장에 붙임
    for s in sentences:
        cur = s
        if pending is not None:
            cur = Sentence(pending.start, s.end, pending.text + " " + s.text, s.speaker)
            pending = None
        short = (not has_hangul(cur.text)) and english_word_count(cur.text) < min_words
        if short and s is cur and out:
            prev = out[-1]
            out[-1] = Sentence(prev.start, cur.end, prev.text + " " + cur.text, prev.speaker)
        elif short and not out:
            pending = cur
        else:
            out.append(cur)
    if pending is not None:  # 문서 전체가 너무 짧은 경우에도 내용은 보존
        out.append(pending)
    return out


# ---------------------------------------------------------------------------
# 화자 · 문단
# ---------------------------------------------------------------------------

def assign_speakers(sentences: list[Sentence],
                    turns: Iterable[tuple[float, float, int]]) -> None:
    """겹치는 시간이 가장 긴 화자를 각 문장에 지정한다 (제자리 수정)."""
    turns = list(turns)
    for s in sentences:
        best, best_ov = None, 0.0
        acc: dict[int, float] = {}
        for t0, t1, spk in turns:
            ov = min(s.end, t1) - max(s.start, t0)
            if ov > 0:
                acc[spk] = acc.get(spk, 0.0) + ov
        for spk, ov in acc.items():
            if ov > best_ov:
                best, best_ov = spk, ov
        s.speaker = best


def group_paragraphs(
    sentences: list[Sentence],
    gap: float = 1.5,
    max_chars: int = 500,
    use_speaker: bool = True,
) -> list[Paragraph]:
    """화자 변경 · 발언 간격(gap초 이상) · 길이 초과 시 줄(문단)을 나눈다."""
    paragraphs: list[Paragraph] = []
    cur: Optional[Paragraph] = None
    chars = 0
    prev: Optional[Sentence] = None
    for s in sentences:
        new = cur is None
        if not new and prev is not None:
            if use_speaker and s.speaker is not None and prev.speaker is not None \
                    and s.speaker != prev.speaker:
                new = True
            elif s.start - prev.end >= gap:
                new = True
            elif chars >= max_chars:
                new = True
        if new:
            cur = Paragraph()
            paragraphs.append(cur)
            chars = 0
        cur.sentences.append(s)
        chars += len(s.text)
        prev = s
    return paragraphs


def fmt_time(sec: float) -> str:
    sec = max(sec, 0.0)
    h, rem = divmod(int(sec), 3600)
    m, s = divmod(rem, 60)
    frac = int((sec - int(sec)) * 10)
    return f"{h:02d}:{m:02d}:{s:02d}.{frac}"
