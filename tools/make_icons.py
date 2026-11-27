#!/usr/bin/env python3
"""生成 PowerMeter Pro 的启动图标与通知小图标（纯 Pillow，无外部素材）。"""
from PIL import Image, ImageDraw
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SS = 4  # 超采样倍数

BG1 = (9, 16, 30)
BG2 = (12, 105, 130)
WHITE = (255, 255, 255, 255)
CYAN = (125, 235, 230, 255)


def grad(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        # 对角渐变近似：按行插值，再叠一点横向变化
        r = int(BG1[0] + (BG2[0] - BG1[0]) * t)
        g = int(BG1[1] + (BG2[1] - BG1[1]) * t)
        b = int(BG1[2] + (BG2[2] - BG1[2]) * t)
        d.line([(0, y), (size, y)], fill=(r, g, b, 255))
    # 左上角提亮
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    for i in range(size):
        alpha = int(28 * (1 - i / size))
        od.line([(0, i), (size - i, 0)], fill=(255, 255, 255, alpha))
    return Image.alpha_composite(img, overlay)


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def icon(size, round_icon=False):
    s = size * SS
    img = grad(s)
    m = circle_mask(s) if round_icon else rounded_mask(s, int(s * 0.22))
    img.putalpha(m)

    d = ImageDraw.Draw(img)
    c = s / 2
    # 镜头外环 + 内环（“瓦镜”视觉）
    ring_r = s * 0.33
    ring_w = max(2, int(s * 0.022))
    d.ellipse([c - ring_r, c - ring_r, c + ring_r, c + ring_r],
              outline=(255, 255, 255, 110), width=ring_w)
    d.arc([c - ring_r, c - ring_r, c + ring_r, c + ring_r],
          start=-80, end=10, fill=(255, 255, 255, 245), width=ring_w)
    inner_r = s * 0.225
    d.ellipse([c - inner_r, c - inner_r, c + inner_r, c + inner_r],
              outline=(125, 235, 230, 190), width=max(2, int(s * 0.015)))
    # 四周小光点，像镜头刻度
    import math
    for ang in (-45, 45, 135, 225):
        rad = math.radians(ang)
        px = c + math.cos(rad) * s * 0.40
        py = c + math.sin(rad) * s * 0.40
        dot_r = s * 0.012
        d.ellipse([px - dot_r, py - dot_r, px + dot_r, py + dot_r],
                  fill=(125, 235, 230, 140))
    # 中心闪电
    bolt = [
        (c + s * 0.035, c - s * 0.29),
        (c - s * 0.20, c + s * 0.06),
        (c - s * 0.01, c + s * 0.06),
        (c - s * 0.055, c + s * 0.29),
        (c + s * 0.21, c - s * 0.08),
        (c + s * 0.02, c - s * 0.08),
    ]
    d.polygon(bolt, fill=WHITE)
    # 小圆点（底部）增加识别度
    dot_r = s * 0.018
    d.ellipse([c - dot_r, c + s * 0.36 - dot_r, c + dot_r, c + s * 0.36 + dot_r],
              fill=(255, 255, 255, 170))
    return img.resize((size, size), Image.LANCZOS)


def notify(size):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    # 与桌面图标一致的镜头环 + 闪电白色剪影（状态栏要求透明单色）
    ring_r = s * 0.37
    ring_w = max(2, int(s * 0.075))
    d.ellipse([c - ring_r, c - ring_r, c + ring_r, c + ring_r],
              outline=(255, 255, 255, 255), width=ring_w)
    inner_r = s * 0.235
    d.ellipse([c - inner_r, c - inner_r, c + inner_r, c + inner_r],
              outline=(255, 255, 255, 230), width=max(2, int(s * 0.05)))
    bolt = [
        (c + s * 0.08, c - s * 0.32),
        (c - s * 0.23, c + s * 0.05),
        (c - s * 0.02, c + s * 0.05),
        (c - s * 0.09, c + s * 0.32),
        (c + s * 0.24, c - s * 0.07),
        (c + s * 0.01, c - s * 0.07),
    ]
    d.polygon(bolt, fill=WHITE)
    return img.resize((size, size), Image.LANCZOS)


def save(img, rel):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print(rel, img.size)


# 传统 mipmap 尺寸（API 26+ 实际使用 anydpi-v26，这里作为兜底）
for dens, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    save(icon(size, False), "res/mipmap-%s/ic_launcher.png" % dens)
    save(icon(size, True), "res/mipmap-%s/ic_launcher_round.png" % dens)

# 通知小图标（纯白剪影，透明背景）
for dens, size in [("mdpi", 24), ("hdpi", 36), ("xhdpi", 48), ("xxhdpi", 72), ("xxxhdpi", 96)]:
    save(notify(size), "res/drawable-%s/ic_notify.png" % dens)

# 512 高清图标，便于 README/商店
save(icon(512, False), "docs/icon-512.png")
