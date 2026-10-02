"""DocVoice GUI - 파스텔 톤 CustomTkinter 인터페이스."""
from __future__ import annotations

import os
import platform
import queue
import subprocess
import threading
import traceback
from pathlib import Path
from tkinter import filedialog

import customtkinter as ctk

from . import APP_NAME, Cancelled, __version__
from .extract import SUPPORTED_EXTS
from .export import FORMATS
from .stt import AUDIO_EXTS, LANGUAGES, MODEL_SIZES, convert_audio_to_documents
from .tts import DEFAULT_EN, DEFAULT_KO, EN_VOICES, KO_VOICES, convert_document_to_mp3

ASSETS = Path(__file__).parent / "assets"

# ── 파스텔 팔레트 ──────────────────────────────────────────────
BG = "#F7F5FF"
CARD = "#FFFFFF"
BORDER = "#E6E1F5"
INK = "#4A4560"
MUTED = "#8E88A8"
LAVENDER = "#A594F0"
LAVENDER_HOVER = "#9381E6"
LAVENDER_SOFT = "#ECE8FC"
MINT = "#8FD5B8"
MINT_HOVER = "#78C7A7"
MINT_SOFT = "#E4F6EE"
ROSE = "#FFC9DE"
ROSE_HOVER = "#FFB5D0"
PEACH_SOFT = "#FFF1E8"

SYSTEM = platform.system()
FONT = {"Windows": "Malgun Gothic", "Darwin": "Apple SD Gothic Neo"}.get(SYSTEM, "Noto Sans CJK KR")


def font(size: int = 13, weight: str = "normal") -> ctk.CTkFont:
    return ctk.CTkFont(family=FONT, size=size, weight=weight)


def open_folder(path: Path) -> None:
    target = path if path.is_dir() else path.parent
    try:
        if SYSTEM == "Windows":
            os.startfile(str(target))  # type: ignore[attr-defined]
        elif SYSTEM == "Darwin":
            subprocess.Popen(["open", str(target)])
        else:
            subprocess.Popen(["xdg-open", str(target)])
    except Exception:  # noqa: BLE001
        pass


class Card(ctk.CTkFrame):
    def __init__(self, master, title: str | None = None, **kw):
        super().__init__(master, fg_color=CARD, corner_radius=18, border_width=1,
                         border_color=BORDER, **kw)
        if title:
            ctk.CTkLabel(self, text=title, font=font(14, "bold"), text_color=INK,
                         anchor="w").pack(fill="x", padx=18, pady=(14, 4))


class Runner:
    """백그라운드 작업 + 진행률/로그 표시를 담당하는 하단 패널."""

    def __init__(self, master, start_label: str):
        self.frame = Card(master)
        self.q: queue.Queue = queue.Queue()
        self.cancel = threading.Event()
        self.thread: threading.Thread | None = None
        self.last_result: list[Path] = []

        row = ctk.CTkFrame(self.frame, fg_color="transparent")
        row.pack(fill="x", padx=18, pady=(16, 8))
        self.start_btn = ctk.CTkButton(row, text=start_label, font=font(14, "bold"), height=42,
                                       corner_radius=21, fg_color=LAVENDER, hover_color=LAVENDER_HOVER,
                                       text_color="white")
        self.start_btn.pack(side="left", fill="x", expand=True)
        self.cancel_btn = ctk.CTkButton(row, text="취소", font=font(13), height=42, width=80, corner_radius=21,
                                        fg_color=ROSE, hover_color=ROSE_HOVER, text_color=INK,
                                        state="disabled", command=self.cancel.set)
        self.cancel_btn.pack(side="left", padx=(10, 0))
        self.open_btn = ctk.CTkButton(row, text="폴더 열기", font=font(13), height=42, width=100,
                                      corner_radius=21, fg_color=MINT, hover_color=MINT_HOVER,
                                      text_color=INK, state="disabled", command=self._open)
        self.open_btn.pack(side="left", padx=(10, 0))

        self.bar = ctk.CTkProgressBar(self.frame, height=10, corner_radius=5, fg_color=LAVENDER_SOFT,
                                      progress_color=MINT)
        self.bar.set(0)
        self.bar.pack(fill="x", padx=20, pady=(4, 6))
        self.status = ctk.CTkLabel(self.frame, text="대기 중", font=font(12), text_color=MUTED, anchor="w")
        self.status.pack(fill="x", padx=20)
        self.log = ctk.CTkTextbox(self.frame, height=110, font=font(12), fg_color=BG, text_color=INK,
                                  corner_radius=12, border_width=1, border_color=BORDER)
        self.log.pack(fill="both", expand=True, padx=18, pady=(8, 16))
        self.log.configure(state="disabled")

    # ---- UI helpers -------------------------------------------------
    def write(self, msg: str) -> None:
        self.log.configure(state="normal")
        self.log.insert("end", msg + "\n")
        self.log.see("end")
        self.log.configure(state="disabled")

    def _open(self) -> None:
        if self.last_result:
            open_folder(self.last_result[0])

    def busy(self) -> bool:
        return self.thread is not None and self.thread.is_alive()

    # ---- 실행 -------------------------------------------------------
    def run(self, job) -> None:
        """job(progress, cancel, log) -> list[Path]"""
        if self.busy():
            return
        self.cancel.clear()
        self.last_result = []
        self.bar.set(0)
        self.start_btn.configure(state="disabled")
        self.cancel_btn.configure(state="normal")
        self.open_btn.configure(state="disabled")

        def progress(frac: float, msg: str) -> None:
            self.q.put(("progress", frac, msg))

        def work() -> None:
            try:
                result = job(progress, self.cancel, lambda m: self.q.put(("log", m)))
                self.q.put(("done", result))
            except Cancelled:
                self.q.put(("cancelled", None))
            except Exception as e:  # noqa: BLE001
                self.q.put(("error", f"{e}\n{traceback.format_exc(limit=3)}" if os.environ.get("DOCVOICE_DEBUG") else str(e)))

        self.thread = threading.Thread(target=work, daemon=True)
        self.thread.start()
        self.frame.after(100, self._poll)

    def _poll(self) -> None:
        try:
            while True:
                kind, *rest = self.q.get_nowait()
                if kind == "progress":
                    self.bar.set(max(0.0, min(1.0, rest[0])))
                    self.status.configure(text=rest[1])
                elif kind == "log":
                    self.write(rest[0])
                elif kind == "done":
                    self.last_result = list(rest[0])
                    for p in self.last_result:
                        self.write(f"✔ 저장됨: {p}")
                    self.bar.set(1.0)
                    self.status.configure(text="완료되었습니다 ✨")
                    self._finish(True)
                    return
                elif kind == "cancelled":
                    self.status.configure(text="취소되었습니다")
                    self.write("작업이 취소되었습니다.")
                    self._finish(False)
                    return
                elif kind == "error":
                    self.status.configure(text="오류가 발생했습니다")
                    self.write(f"✖ {rest[0]}")
                    self._finish(False)
                    return
        except queue.Empty:
            pass
        self.frame.after(100, self._poll)

    def _finish(self, ok: bool) -> None:
        self.start_btn.configure(state="normal")
        self.cancel_btn.configure(state="disabled")
        if ok and self.last_result:
            self.open_btn.configure(state="normal")


def labeled_row(parent, label: str) -> ctk.CTkFrame:
    row = ctk.CTkFrame(parent, fg_color="transparent")
    row.pack(fill="x", padx=18, pady=5)
    ctk.CTkLabel(row, text=label, width=96, anchor="w", font=font(13), text_color=MUTED).pack(side="left")
    return row


def menu(parent, values, variable=None, width=170, command=None) -> ctk.CTkOptionMenu:
    return ctk.CTkOptionMenu(parent, values=list(values), variable=variable, width=width, height=32,
                             corner_radius=10, font=font(13), dropdown_font=font(13),
                             fg_color=LAVENDER_SOFT, button_color=LAVENDER, button_hover_color=LAVENDER_HOVER,
                             text_color=INK, dropdown_fg_color=CARD, dropdown_text_color=INK,
                             dropdown_hover_color=LAVENDER_SOFT, command=command)


def switch(parent, text: str, variable) -> ctk.CTkSwitch:
    return ctk.CTkSwitch(parent, text=text, variable=variable, font=font(13), text_color=INK,
                         progress_color=MINT, button_color="white", button_hover_color="#F3F0FF",
                         fg_color="#D9D3F0")


def path_entry(parent, var, placeholder: str) -> ctk.CTkEntry:
    return ctk.CTkEntry(parent, textvariable=var, placeholder_text=placeholder, height=34, corner_radius=10,
                        font=font(12), fg_color=BG, border_color=BORDER, text_color=INK,
                        placeholder_text_color=MUTED)


def small_btn(parent, text: str, command, width: int = 84) -> ctk.CTkButton:
    return ctk.CTkButton(parent, text=text, width=width, height=34, corner_radius=10, font=font(13),
                         fg_color=LAVENDER_SOFT, hover_color="#DDD6F8", text_color=INK, command=command)


class App(ctk.CTk):
    def __init__(self) -> None:
        super().__init__(fg_color=BG)
        ctk.set_appearance_mode("light")
        self.title(f"{APP_NAME} · 문서 ↔ 음성 변환")
        self.geometry("900x860")
        self.minsize(820, 760)
        self._set_icon()
        self._build_header()

        self.tabs = ctk.CTkTabview(
            self, fg_color="transparent", corner_radius=16,
            segmented_button_fg_color=LAVENDER_SOFT, segmented_button_selected_color=LAVENDER,
            segmented_button_selected_hover_color=LAVENDER_HOVER, segmented_button_unselected_color=LAVENDER_SOFT,
            segmented_button_unselected_hover_color="#DDD6F8", text_color=INK,
        )
        self.tabs._segmented_button.configure(font=font(14, "bold"), height=38)
        self.tabs.pack(fill="both", expand=True, padx=20, pady=(0, 14))
        t1 = self.tabs.add("📄  문서 → 음성")
        t2 = self.tabs.add("🎙  음성 → 문서")
        self._build_tts(t1)
        self._build_stt(t2)

    # ---------------------------------------------------------------- 공통
    def _set_icon(self) -> None:
        try:
            if SYSTEM == "Windows":
                ico = str(ASSETS / "icon.ico")
                self.after(250, lambda: self.iconbitmap(ico))
            else:
                from tkinter import PhotoImage
                self._icon_img = PhotoImage(file=str(ASSETS / "icon.png"))
                self.iconphoto(True, self._icon_img)
        except Exception:  # noqa: BLE001
            pass

    def _build_header(self) -> None:
        head = ctk.CTkFrame(self, fg_color="transparent")
        head.pack(fill="x", padx=26, pady=(18, 10))
        try:
            from PIL import Image
            img = ctk.CTkImage(Image.open(ASSETS / "icon.png"), size=(54, 54))
            ctk.CTkLabel(head, image=img, text="").pack(side="left", padx=(0, 14))
        except Exception:  # noqa: BLE001
            pass
        box = ctk.CTkFrame(head, fg_color="transparent")
        box.pack(side="left")
        ctk.CTkLabel(box, text=APP_NAME, font=font(24, "bold"), text_color=INK, anchor="w").pack(anchor="w")
        ctk.CTkLabel(box, text="문서를 목소리로, 목소리를 문서로", font=font(12), text_color=MUTED,
                     anchor="w").pack(anchor="w")
        ctk.CTkLabel(head, text=f"v{__version__}", font=font(11), text_color=MUTED).pack(side="right")

    # ----------------------------------------------------------- 문서 → 음성
    def _build_tts(self, tab) -> None:
        self.files: list[Path] = []
        self.tts_out = ctk.StringVar()
        self.file_label = ctk.StringVar()
        self.accent = ctk.StringVar(value="미국식")
        self.en_voice = ctk.StringVar()
        self.ko_voice = ctk.StringVar(value=next(iter(KO_VOICES)))
        self.speed = ctk.DoubleVar(value=1.0)

        card = Card(tab, "변환할 문서")
        card.pack(fill="x", pady=(2, 10))
        row = labeled_row(card, "문서 파일")
        path_entry(row, self.file_label, "xlsx · docx · pdf · hwp · pptx … 파일을 선택하세요").pack(
            side="left", fill="x", expand=True, padx=(0, 8))
        small_btn(row, "찾아보기", self._pick_docs).pack(side="left")
        row = labeled_row(card, "저장 폴더")
        path_entry(row, self.tts_out, "비워 두면 원본 문서와 같은 폴더").pack(side="left", fill="x", expand=True, padx=(0, 8))
        small_btn(row, "선택", lambda: self._pick_dir(self.tts_out)).pack(side="left")
        ctk.CTkLabel(card, text="지원: " + " ".join(e.lstrip(".") for e in SUPPORTED_EXTS),
                     font=font(11), text_color=MUTED, anchor="w").pack(fill="x", padx=20, pady=(0, 12))

        card2 = Card(tab, "목소리 설정 (남성 음성)")
        card2.pack(fill="x", pady=(0, 10))
        row = labeled_row(card2, "영어 발음")
        ctk.CTkSegmentedButton(row, values=["미국식", "영국식"], variable=self.accent, font=font(13),
                               selected_color=LAVENDER, selected_hover_color=LAVENDER_HOVER,
                               unselected_color=LAVENDER_SOFT, unselected_hover_color="#DDD6F8",
                               text_color=INK, fg_color=LAVENDER_SOFT, height=32,
                               command=lambda _v: self._refresh_en_voices()).pack(side="left")
        self.en_menu = menu(row, ["-"], self.en_voice, width=190)
        self.en_menu.pack(side="left", padx=(14, 0))
        row = labeled_row(card2, "한국어")
        menu(row, KO_VOICES.keys(), self.ko_voice, width=190).pack(side="left")
        row = labeled_row(card2, "읽기 속도")
        ctk.CTkSlider(row, from_=0.7, to=1.4, number_of_steps=14, variable=self.speed, width=240,
                      progress_color=LAVENDER, button_color=LAVENDER, button_hover_color=LAVENDER_HOVER,
                      fg_color=LAVENDER_SOFT, command=lambda v: self.speed_lbl.configure(text=f"{v:.2f}배")
                      ).pack(side="left")
        self.speed_lbl = ctk.CTkLabel(row, text="1.00배", font=font(13), text_color=INK, width=60)
        self.speed_lbl.pack(side="left", padx=10)
        ctk.CTkLabel(card2, text="※ Microsoft 신경망 음성을 사용하므로 인터넷 연결이 필요합니다. "
                                 "한국어/영어가 섞인 문서는 문장별로 알맞은 목소리로 읽습니다.",
                     font=font(11), text_color=MUTED, anchor="w", wraplength=780, justify="left"
                     ).pack(fill="x", padx=20, pady=(2, 12))
        self._refresh_en_voices()

        self.tts_runner = Runner(tab, "🎧  mp3로 변환하기")
        self.tts_runner.frame.pack(fill="both", expand=True)
        self.tts_runner.start_btn.configure(command=self._start_tts)

    def _refresh_en_voices(self) -> None:
        key = "us" if self.accent.get() == "미국식" else "uk"
        names = list(EN_VOICES[key].keys())
        self.en_menu.configure(values=names)
        self.en_voice.set(names[0])

    def _pick_docs(self) -> None:
        exts = " ".join("*" + e for e in SUPPORTED_EXTS)
        paths = filedialog.askopenfilenames(title="변환할 문서 선택", filetypes=[("문서", exts), ("모든 파일", "*.*")])
        if paths:
            self.files = [Path(p) for p in paths]
            self.file_label.set(self.files[0].name if len(self.files) == 1
                                else f"{self.files[0].name} 외 {len(self.files) - 1}개")

    def _pick_dir(self, var: ctk.StringVar) -> None:
        d = filedialog.askdirectory(title="저장 폴더 선택")
        if d:
            var.set(d)

    def _start_tts(self) -> None:
        r = self.tts_runner
        if not self.files:
            r.write("문서 파일을 먼저 선택하세요.")
            return
        key = "us" if self.accent.get() == "미국식" else "uk"
        en = EN_VOICES[key].get(self.en_voice.get(), DEFAULT_EN[key])
        ko = KO_VOICES.get(self.ko_voice.get(), DEFAULT_KO)
        speed = float(self.speed.get())
        out_dir = self.tts_out.get().strip() or None
        files = list(self.files)

        def job(progress, cancel, log):
            outs: list[Path] = []
            for i, f in enumerate(files):
                log(f"[{i + 1}/{len(files)}] {f.name}")

                def prog(frac, msg, i=i):
                    progress((i + frac) / len(files), msg)

                outs.append(convert_document_to_mp3(f, out_dir, key, en, ko, speed, prog, cancel))
            return outs

        r.run(job)

    # ----------------------------------------------------------- 음성 → 문서
    def _build_stt(self, tab) -> None:
        self.audio = ctk.StringVar()
        self.stt_out = ctk.StringVar()
        self.fmt_vars = {f: ctk.BooleanVar(value=(f in ("xlsx", "docx"))) for f in FORMATS}
        self.lang = ctk.StringVar(value="자동 감지")
        self.model = ctk.StringVar(value="small")
        self.gap = ctk.DoubleVar(value=1.5)
        self.diar = ctk.BooleanVar(value=True)
        self.nspk = ctk.StringVar(value="자동")
        self.with_time = ctk.BooleanVar(value=True)
        self.with_spk = ctk.BooleanVar(value=False)

        card = Card(tab, "음성 파일")
        card.pack(fill="x", pady=(2, 10))
        row = labeled_row(card, "음성 파일")
        path_entry(row, self.audio, "mp3 · wav · m4a · flac · mp4 …").pack(side="left", fill="x", expand=True, padx=(0, 8))
        small_btn(row, "찾아보기", self._pick_audio).pack(side="left")
        row = labeled_row(card, "저장 폴더")
        path_entry(row, self.stt_out, "비워 두면 음성 파일과 같은 폴더").pack(side="left", fill="x", expand=True, padx=(0, 8))
        small_btn(row, "선택", lambda: self._pick_dir(self.stt_out)).pack(side="left")
        row = labeled_row(card, "문서 형태")
        for f in FORMATS:
            ctk.CTkCheckBox(row, text=f.upper(), variable=self.fmt_vars[f], font=font(13), text_color=INK,
                            fg_color=LAVENDER, hover_color=LAVENDER_HOVER, border_color="#CFC7F2",
                            checkmark_color="white", corner_radius=6, width=70).pack(side="left", padx=(0, 10))
        ctk.CTkLabel(card, text="XLSX는 한 문장이 한 행에 들어가고, DOCX/PDF/TXT는 화자가 바뀌거나 말 사이가 길면 줄을 바꿉니다.",
                     font=font(11), text_color=MUTED, anchor="w", wraplength=780, justify="left"
                     ).pack(fill="x", padx=20, pady=(2, 12))

        card2 = Card(tab, "인식 · 정리 옵션")
        card2.pack(fill="x", pady=(0, 10))
        row = labeled_row(card2, "언어 / 모델")
        menu(row, LANGUAGES.keys(), self.lang, width=130).pack(side="left")
        menu(row, MODEL_SIZES, self.model, width=130).pack(side="left", padx=(10, 0))
        ctk.CTkLabel(row, text="모델이 클수록 정확하지만 느립니다", font=font(11), text_color=MUTED).pack(side="left", padx=12)
        row = labeled_row(card2, "줄바꿈 간격")
        ctk.CTkSlider(row, from_=0.5, to=5.0, number_of_steps=18, variable=self.gap, width=240,
                      progress_color=LAVENDER, button_color=LAVENDER, button_hover_color=LAVENDER_HOVER,
                      fg_color=LAVENDER_SOFT, command=lambda v: self.gap_lbl.configure(text=f"{v:.1f}초")
                      ).pack(side="left")
        self.gap_lbl = ctk.CTkLabel(row, text="1.5초", font=font(13), text_color=INK, width=60)
        self.gap_lbl.pack(side="left", padx=10)
        ctk.CTkLabel(row, text="이 시간 이상 쉬면 줄 바꿈", font=font(11), text_color=MUTED).pack(side="left")
        row = labeled_row(card2, "화자 구분")
        switch(row, "화자가 바뀌면 줄 바꿈", self.diar).pack(side="left")
        ctk.CTkLabel(row, text="화자 수", font=font(13), text_color=MUTED).pack(side="left", padx=(20, 6))
        menu(row, ["자동", "2", "3", "4", "5", "6"], self.nspk, width=90).pack(side="left")
        row = labeled_row(card2, "표시")
        switch(row, "시간 표시", self.with_time).pack(side="left")
        switch(row, "화자 이름 표시", self.with_spk).pack(side="left", padx=(24, 0))
        ctk.CTkLabel(card2, text="※ 처음 사용할 때 음성 인식 모델(최대 수백 MB)과 화자 구분 모델(약 46MB)을 내려받습니다.",
                     font=font(11), text_color=MUTED, anchor="w").pack(fill="x", padx=20, pady=(2, 12))

        self.stt_runner = Runner(tab, "📝  문서로 변환하기")
        self.stt_runner.frame.pack(fill="both", expand=True)
        self.stt_runner.start_btn.configure(command=self._start_stt)

    def _pick_audio(self) -> None:
        exts = " ".join("*" + e for e in AUDIO_EXTS)
        p = filedialog.askopenfilename(title="음성 파일 선택", filetypes=[("오디오/비디오", exts), ("모든 파일", "*.*")])
        if p:
            self.audio.set(p)

    def _start_stt(self) -> None:
        r = self.stt_runner
        if not self.audio.get().strip():
            r.write("음성 파일을 먼저 선택하세요.")
            return
        formats = [f for f in FORMATS if self.fmt_vars[f].get()]
        if not formats:
            r.write("문서 형태를 하나 이상 선택하세요.")
            return
        kwargs = dict(
            audio=self.audio.get().strip(),
            out_dir=self.stt_out.get().strip() or None,
            formats=formats,
            language=LANGUAGES[self.lang.get()],
            model_size=self.model.get(),
            gap=float(self.gap.get()),
            diarize_speakers=bool(self.diar.get()),
            num_speakers=-1 if self.nspk.get() == "자동" else int(self.nspk.get()),
            include_time=bool(self.with_time.get()),
            show_speaker=bool(self.with_spk.get()),
        )

        def job(progress, cancel, log):
            return convert_audio_to_documents(progress=progress, cancel=cancel, log=log, **kwargs)

        r.run(job)


def main() -> None:
    ctk.set_default_color_theme("blue")
    App().mainloop()


if __name__ == "__main__":
    main()
