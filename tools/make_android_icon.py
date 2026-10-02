"""안드로이드 적응형 아이콘 레이어 생성 (iOS 풍 블루 그라데이션 + 문서 + 음성 파형). 실행: python tools/make_android_icon.py"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
S = 1024
SS = 2
N = S * SS
TOP, BOTTOM = (88, 178, 255), (0, 98, 235)
BAR_TOP, BAR_BOTTOM = (64, 156, 255), (0, 90, 220)
BARS = [0.30, 0.55, 0.85, 0.50, 0.28]  # 파형 막대 상대 높이


def doc_art(mono=False):
    k = SS
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    x0, y0, x1, y1 = 262 * k, 150 * k, 762 * k, 874 * k
    r = 70 * k
    fold = 150 * k
    if not mono:
        sh = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle((x0, y0 + 26 * k, x1, y1 + 26 * k), radius=r, fill=(0, 40, 120, 90))
        img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(26 * k)))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((x0, y0, x1, y1), radius=r, fill=(255, 255, 255, 255))
    # 접힌 모서리
    if not mono:
        d.polygon([(x1 - fold, y0), (x1, y0 + fold), (x1 - fold, y0 + fold)], fill=(222, 232, 250, 255))
    # 파형 막대
    bar_w = 46 * k
    gap = 34 * k
    total = len(BARS) * bar_w + (len(BARS) - 1) * gap
    bx = (x0 + x1) / 2 - total / 2
    cy = (y0 + y1) / 2 + 70 * k
    maxh = 400 * k
    if mono:
        hole = Image.new("L", (N, N), 255)
        hd = ImageDraw.Draw(hole)
        for i, h in enumerate(BARS):
            hh = maxh * h
            hd.rounded_rectangle((bx + i * (bar_w + gap), cy - hh / 2, bx + i * (bar_w + gap) + bar_w, cy + hh / 2), radius=bar_w / 2, fill=0)
        img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (N, N), 0), hole))
    else:
        for i, h in enumerate(BARS):
            hh = maxh * h
            bar = Image.new("RGBA", (int(bar_w), int(hh)), (0, 0, 0, 0))
            bp = bar.load()
            for yy in range(int(hh)):
                t = yy / max(1, hh - 1)
                c = tuple(int(BAR_TOP[j] + (BAR_BOTTOM[j] - BAR_TOP[j]) * t) for j in range(3)) + (255,)
                for xx in range(int(bar_w)):
                    bp[xx, yy] = c
            m = Image.new("L", bar.size, 0)
            ImageDraw.Draw(m).rounded_rectangle((0, 0, bar.size[0] - 1, bar.size[1] - 1), radius=bar_w // 2, fill=255)
            img.paste(bar, (int(bx + i * (bar_w + gap)), int(cy - hh / 2)), m)
    return img


def gradient(size):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        t = y / (size - 1)
        c = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
        for x in range(size):
            px[x, y] = c
    return img


def layer(art, size=432, frac=0.60):
    art = art.crop(art.getbbox())
    s = size * frac / max(art.size)
    art = art.resize((max(1, int(art.width * s)), max(1, int(art.height * s))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return out


def main():
    RES.mkdir(parents=True, exist_ok=True)
    gradient(432).save(RES / "ic_launcher_background.png")
    layer(doc_art()).save(RES / "ic_launcher_foreground.png")
    layer(doc_art(mono=True)).save(RES / "ic_launcher_monochrome.png")
    # 정사각 로고 (squircle)
    big = gradient(1024).convert("RGBA")
    mask = Image.new("L", (1024, 1024), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 1023, 1023), radius=230, fill=255)
    logo = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    logo.paste(big, (0, 0), mask)
    art = doc_art().resize((1024, 1024), Image.LANCZOS)
    box = art.getbbox()
    a = art.crop(box)
    s = 1024 * 0.60 / max(a.size)
    a = a.resize((int(a.width * s), int(a.height * s)), Image.LANCZOS)
    logo.alpha_composite(a, ((1024 - a.width) // 2, (1024 - a.height) // 2))
    logo.save(RES / "app_logo.png")
    logo.resize((512, 512), Image.LANCZOS).save(Path(__file__).resolve().parent / "android_icon_512.png")
    print(sorted(p.name for p in RES.iterdir()))


if __name__ == "__main__":
    main()
