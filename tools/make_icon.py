"""앱 아이콘 생성 (파스텔 라벤더→민트 그라데이션 + 문서 + 음파). 실행: python tools/make_icon.py"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops

S = 1024
SS = 2  # 슈퍼샘플링
N = S * SS
OUT = Path(__file__).resolve().parent.parent / "docvoice" / "assets"


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient(c1, c2):
    img = Image.new("RGB", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = lerp(c1, c2, (x + y) / (2 * N))
    return img


def main():
    bg = gradient((201, 184, 245), (168, 230, 207))  # 라벤더 → 민트
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, N - 1, N - 1), radius=int(N * 0.225), fill=255)
    icon = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    icon.paste(bg, (0, 0), mask)

    d = ImageDraw.Draw(icon)
    k = SS
    # 문서 (흰색, 접힌 모서리)
    x0, y0, x1, y1 = 215 * k, 200 * k, 585 * k, 824 * k
    fold = 120 * k
    shadow = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((x0 + 10 * k, y0 + 22 * k, x1 + 10 * k, y1 + 22 * k),
                                             radius=46 * k, fill=(120, 100, 180, 70))
    from PIL import ImageFilter
    shadow = shadow.filter(ImageFilter.GaussianBlur(18 * k))
    icon = Image.alpha_composite(icon, shadow)
    d = ImageDraw.Draw(icon)
    d.rounded_rectangle((x0, y0, x1, y1), radius=46 * k, fill=(255, 255, 255, 255))
    # 접힌 모서리
    d.polygon([(x1 - fold, y0), (x1, y0 + fold), (x1 - fold, y0 + fold)], fill=(233, 228, 250, 255))
    d.polygon([(x1 - fold, y0), (x1 - fold + 2 * k, y0), (x1, y0 + fold - 2 * k), (x1, y0 + fold)],
              fill=(255, 255, 255, 255))
    d.polygon([(x1 - fold, y0 + 4 * k), (x1 - 4 * k, y0 + fold), (x1 - fold, y0 + fold)],
              fill=(226, 219, 248, 255))
    # 텍스트 줄 (파스텔)
    colors = [(201, 184, 245), (255, 200, 221), (168, 230, 207), (201, 184, 245), (189, 224, 254)]
    widths = [250, 250, 200, 250, 150]
    for i, (c, w) in enumerate(zip(colors, widths)):
        y = (330 + i * 85) * k
        d.rounded_rectangle((x0 + 55 * k, y, x0 + (55 + w) * k, y + 30 * k), radius=15 * k, fill=c + (255,))

    # 음파 (흰색, 반투명 단계)
    cx, cy = 640 * k, 512 * k
    for i, (r, a) in enumerate([(120, 255), (210, 215), (300, 170)]):
        r *= k
        w = 44 * k
        d.arc((cx - r, cy - r, cx + r, cy + r), start=-48, end=48, fill=(255, 255, 255, a), width=w)
        # 둥근 끝
        import math
        for ang in (-48, 48):
            ex = cx + (r - w / 2) * math.cos(math.radians(ang))
            ey = cy + (r - w / 2) * math.sin(math.radians(ang))
            d.ellipse((ex - w / 2, ey - w / 2, ex + w / 2, ey + w / 2), fill=(255, 255, 255, a))
    d.ellipse((cx - 32 * k, cy - 32 * k, cx + 32 * k, cy + 32 * k), fill=(255, 255, 255, 255))

    icon = icon.resize((S, S), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    icon.save(OUT / "icon.png")
    icon.save(OUT / "icon.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    try:
        icon.save(OUT / "icon.icns")
    except Exception as e:  # noqa: BLE001
        print("icns 저장 실패:", e)
    print("saved", [p.name for p in OUT.iterdir()])


if __name__ == "__main__":
    main()
