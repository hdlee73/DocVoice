"""명령행 / GUI 진입점.

    python -m docvoice                      # GUI
    python -m docvoice tts 문서.pdf --accent uk
    python -m docvoice stt 녹음.mp3 --formats xlsx docx pdf
"""
from __future__ import annotations

import argparse
import sys
import threading


def _cli_progress(frac: float, msg: str) -> None:
    print(f"[{frac * 100:5.1f}%] {msg}", flush=True)


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    if not argv or argv[0] == "gui":
        from .gui import main as gui_main

        gui_main()
        return 0

    ap = argparse.ArgumentParser(prog="docvoice", description="문서 ↔ 음성 변환")
    sub = ap.add_subparsers(dest="cmd", required=True)

    t = sub.add_parser("tts", help="문서 → mp3")
    t.add_argument("files", nargs="+")
    t.add_argument("--out", help="저장 폴더")
    t.add_argument("--accent", choices=["us", "uk"], default="us", help="영어 발음 (미국식/영국식)")
    t.add_argument("--en-voice", help="예: en-US-AndrewNeural")
    t.add_argument("--ko-voice", help="예: ko-KR-InJoonNeural")
    t.add_argument("--speed", type=float, default=1.0)

    s = sub.add_parser("stt", help="음성 → 문서")
    s.add_argument("audio")
    s.add_argument("--out", help="저장 폴더")
    s.add_argument("--formats", nargs="+", default=["xlsx"], choices=["xlsx", "docx", "pdf", "txt"])
    s.add_argument("--lang", choices=["ko", "en"], default=None)
    s.add_argument("--model", default="small")
    s.add_argument("--gap", type=float, default=1.5, help="이 간격(초) 이상 쉬면 줄 바꿈")
    s.add_argument("--speakers", type=int, default=-1, help="화자 수 (-1=자동, 1=구분 안 함)")
    s.add_argument("--no-diarize", action="store_true")
    s.add_argument("--no-time", action="store_true")
    s.add_argument("--show-speaker", action="store_true")

    args = ap.parse_args(argv)
    try:
        if args.cmd == "tts":
            from .tts import convert_document_to_mp3

            for f in args.files:
                out = convert_document_to_mp3(f, args.out, args.accent, args.en_voice, args.ko_voice,
                                              args.speed, _cli_progress, threading.Event())
                print("저장됨:", out)
        else:
            from .stt import convert_audio_to_documents

            outs = convert_audio_to_documents(
                args.audio, args.out, args.formats, args.lang, args.model, args.gap,
                diarize_speakers=not args.no_diarize, num_speakers=args.speakers,
                include_time=not args.no_time, show_speaker=args.show_speaker,
                progress=_cli_progress, cancel=threading.Event(), log=print,
            )
            for p in outs:
                print("저장됨:", p)
    except Exception as e:  # noqa: BLE001
        print(f"오류: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
