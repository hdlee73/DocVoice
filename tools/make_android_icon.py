"""안드로이드 적응형 아이콘 레이어 생성. 실행: python tools/make_android_icon.py"""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

RES = Path(__file__).resolve().parent.parent / "android" / "app" / "src" / "main" / "res" / "drawable-nodpi"
SS = 2
N = 1024 * SS
LAV, MINT = (201, 184, 245), (168, 230, 207)


def content(mono=False):
    """투명 배경 위에 문서 + 음파."""
    k = SS
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    x0, y0, x1, y1 = 215 * k, 200 * k, 585 * k, 824 * k
    fold = 120 * k
    if not mono:
        sh = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        ImageDraw.Draw(sh).rounded_rectangle((x0 + 10 * k, y0 + 22 * k, x1 + 10 * k, y1 + 22 * k), radius=46 * k, fill=(120, 100, 180, 70))
        img = Image.alpha_composite(img, sh.filter(ImageFilter.GaussianBlur(18 * k)))
    d = ImageDraw.Draw(img)
    white = (255, 255, 255, 255)
    d.rounded_rectangle((x0, y0, x1, y1), radius=46 * k, fill=white)
    if not mono:
        d.polygon([(x1 - fold, y0), (x1, y0 + fold), (x1 - fold, y0 + fold)], fill=(233, 228, 250, 255))
        d.polygon([(x1 - fold, y0 + 4 * k), (x1 - 4 * k, y0 + fold), (x1 - fold, y0 + fold)], fill=(226, 219, 248, 255))
    colors = [(201, 184, 245), (255, 200, 221), (168, 230, 207), (201, 184, 245), (189, 224, 254)]
    widths = [250, 250, 200, 250, 150]
    for i, (c, w) in enumerate(zip(colors, widths)):
        y = (330 + i * 85) * k
        fill = (0, 0, 0, 0) if mono else c + (255,)
        if mono:
            # 투명하게 파내기
            hole = Image.new("L", (N, N), 255)
            ImageDraw.Draw(hole).rounded_rectangle((x0 + 55 * k, y, x0 + (55 + w) * k, y + 30 * k), radius=15 * k, fill=0)
            img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", (N, N), 0), hole))
            d = ImageDraw.Draw(img)
        else:
            d.rounded_rectangle((x0 + 55 * k, y, x0 + (55 + w) * k, y + 30 * k), radius=15 * k, fill=fill)
    cx, cy = 640 * k, 512 * k
    for r, a in [(120, 255), (210, 215), (300, 170)]:
        r *= k
        w = 44 * k
        al = 255 if mono else a
        d.arc((cx - r, cy - r, cx + r, cy + r), start=-48, end=48, fill=(255, 255, 255, al), width=w)
        for ang in (-48, 48):
            ex = cx + (r - w / 2) * math.cos(math.radians(ang))
            ey = cy + (r - w / 2) * math.sin(math.radians(ang))
            d.ellipse((ex - w / 2, ey - w / 2, ex + w / 2, ey + w / 2), fill=(255, 255, 255, al))
    d.ellipse((cx - 32 * k, cy - 32 * k, cx + 32 * k, cy + 32 * k), fill=white)
    return img


def layer(art, size=432, frac=0.60):
    box = art.getbbox()
    art = art.crop(box)
    s = size * frac / max(art.size)
    art = art.resize((max(1, int(art.width * s)), max(1, int(art.height * s))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return out


def gradient(size):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * size)
            px[x, y] = tuple(int(LAV[i] + (MINT[i] - LAV[i]) * t) for i in range(3))
    return img


def main():
    RES.mkdir(parents=True, exist_ok=True)
    gradient(432).save(RES / "ic_launcher_background.png")
    layer(content()).save(RES / "ic_launcher_foreground.png")
    layer(content(mono=True)).save(RES / "ic_launcher_monochrome.png")
    # 앱 내 로고 (둥근 사각형 아이콘 그대로)
    src = Image.open(Path(__file__).resolve().parent.parent / "docvoice" / "assets" / "icon.png").convert("RGBA")
    src.resize((256, 256), Image.LANCZOS).save(RES / "app_logo.png")
    print(sorted(p.name for p in RES.iterdir()))


if __name__ == "__main__":
    main()
