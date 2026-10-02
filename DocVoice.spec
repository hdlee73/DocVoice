# -*- mode: python ; coding: utf-8 -*-
# PyInstaller 빌드 설정:  pyinstaller DocVoice.spec
import sys
from PyInstaller.utils.hooks import collect_all

datas = [("docvoice/assets", "docvoice/assets")]
binaries, hiddenimports = [], []
for pkg in ("customtkinter", "faster_whisper", "ctranslate2", "sherpa_onnx", "edge_tts", "onnxruntime", "av", "tokenizers"):
    try:
        d, b, h = collect_all(pkg)
    except Exception:
        continue
    datas += d
    binaries += b
    hiddenimports += h

icon = "docvoice/assets/icon.icns" if sys.platform == "darwin" else "docvoice/assets/icon.ico"

a = Analysis(["launcher.py"], pathex=["."], binaries=binaries, datas=datas, hiddenimports=hiddenimports,
             excludes=["pytest"], noarchive=False)
pyz = PYZ(a.pure)
exe = EXE(pyz, a.scripts, [], exclude_binaries=True, name="DocVoice", console=False, icon=icon)
coll = COLLECT(exe, a.binaries, a.datas, name="DocVoice")
if sys.platform == "darwin":
    app = BUNDLE(coll, name="DocVoice.app", icon=icon, bundle_identifier="com.docvoice.app",
                 info_plist={"CFBundleShortVersionString": "1.0.0", "NSHighResolutionCapable": True})
