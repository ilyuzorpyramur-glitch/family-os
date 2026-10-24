#!/usr/bin/env python3
"""Иконки Family OS: зелёная для детского, оранжевая для родительского.

Рисуем сами, а не режем картинки: нужны PNG всех плотностей и отдельный
foreground для adaptive-иконки с прозрачным фоном.
"""
from __future__ import annotations

import math
import os
from PIL import Image, ImageDraw, ImageFilter

S = 1024          # базовый размер
SS = 4            # суперсэмплинг
W = S * SS


def hex_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def vertical_gradient(size: tuple[int, int], top: str, bottom: str) -> Image.Image:
    width, height = size
    top_rgb, bottom_rgb = hex_rgb(top), hex_rgb(bottom)
    strip = Image.new("RGB", (1, height))
    pixels = strip.load()
    for y in range(height):
        k = y / max(1, height - 1)
        pixels[0, y] = tuple(int(top_rgb[i] + (bottom_rgb[i] - top_rgb[i]) * k) for i in range(3))
    return strip.resize((width, height), Image.BILINEAR)


def round_corners(mask: Image.Image, radius: float) -> Image.Image:
    """Скругляет углы маски: размытие + порог дают радиус без «шариков»."""
    blurred = mask.filter(ImageFilter.GaussianBlur(radius / 2))
    return blurred.point(lambda v: 255 if v >= 130 else 0)


def house_mask(size: int) -> Image.Image:
    """Дом: широкая крыша с мягкой вершиной и скруглённый низ."""
    u = size
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)

    # Крыша
    roof = Image.new("L", (size, size), 0)
    ImageDraw.Draw(roof).polygon(
        [(0.5 * u, 0.146 * u), (0.112 * u, 0.497 * u), (0.888 * u, 0.497 * u)],
        fill=255,
    )
    roof = round_corners(roof, 0.075 * u)
    mask = Image.composite(roof, mask, roof)

    # Корпус
    body = Image.new("L", (size, size), 0)
    ImageDraw.Draw(body).rounded_rectangle(
        [0.256 * u, 0.406 * u, 0.744 * u, 0.836 * u],
        radius=0.085 * u,
        fill=255,
    )
    mask = Image.composite(body, mask, body)
    return mask


def punch(mask: Image.Image, shape: Image.Image) -> Image.Image:
    """Вырезаем силуэт: получается «дырка» цвета фона."""
    return Image.composite(Image.new("L", mask.size, 0), mask, shape)


def person(size: int, cx: float, head_y: float, head_r: float, body_top: float,
           body_bottom: float, body_w: float, body_r: float) -> Image.Image:
    s = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(s)
    u = size
    d.ellipse(
        [cx * u - head_r * u, head_y * u - head_r * u, cx * u + head_r * u, head_y * u + head_r * u],
        fill=255,
    )
    d.rounded_rectangle(
        [cx * u - body_w * u / 2, body_top * u, cx * u + body_w * u / 2, body_bottom * u],
        radius=body_r * u,
        fill=255,
    )
    return s


def sparkle(size: int, cx: float, cy: float, r: float, thin: float = 0.16) -> Image.Image:
    s = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(s)
    u = size
    points = []
    for i in range(4):
        angle = math.radians(90 * i)
        points.append((cx * u + math.cos(angle) * r * u, cy * u - math.sin(angle) * r * u))
    inner = []
    for i in range(4):
        angle = math.radians(90 * i + 45)
        inner.append(
            (
                cx * u + math.cos(angle) * r * thin * u,
                cy * u - math.sin(angle) * r * thin * u,
            )
        )
    path = []
    for i in range(4):
        path.append(points[i])
        path.append(inner[i])
    d.polygon(path, fill=255)
    return s


def build(accent_top: str, accent_bottom: str, glow: str, role: str, with_border: bool,
          background: bool) -> Image.Image:
    """Собираем иконку. background=False — прозрачный foreground для adaptive."""
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))

    if background:
        # Тёмная подложка со скруглением и лёгким свечением в центре.
        bg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        bd = ImageDraw.Draw(bg)
        radius = int(0.225 * W)
        bd.rounded_rectangle([0, 0, W - 1, W - 1], radius=radius, fill=(10, 12, 16, 255))
        center_glow = Image.new("L", (W, W), 0)
        ImageDraw.Draw(center_glow).ellipse(
            [int(0.12 * W), int(0.02 * W), int(0.88 * W), int(0.9 * W)], fill=70
        )
        center_glow = center_glow.filter(ImageFilter.GaussianBlur(W * 0.13))
        tint = Image.new("RGBA", (W, W), hex_rgb(glow) + (255,))
        bg = Image.composite(tint, bg, center_glow.point(lambda v: int(v * 0.10)))
        canvas = Image.alpha_composite(canvas, bg)

        if with_border:
            border = Image.new("L", (W, W), 0)
            ImageDraw.Draw(border).rounded_rectangle(
                [2, 2, W - 3, W - 3], radius=radius, outline=255, width=int(0.0018 * W)
            )
            halo = border.filter(ImageFilter.GaussianBlur(W * 0.005)).point(lambda v: int(v * 0.35))
            canvas = Image.alpha_composite(canvas, Image.merge("RGBA", (*(Image.new("L", (W, W), c) for c in hex_rgb(glow)), halo)))
            canvas = Image.alpha_composite(canvas, Image.merge("RGBA", (*(Image.new("L", (W, W), c) for c in hex_rgb(glow)), border.point(lambda v: int(v * 0.9)))))

    mask = house_mask(W)

    # Силуэты внутри дома — вырезы до цвета фона.
    holes = Image.new("L", (W, W), 0)
    if role == "child":
        head = person(W, 0.5, 0.512, 0.100, 0.622, 0.828, 0.315, 0.090)
        holes = Image.composite(head, holes, head)
        star = sparkle(W, 0.5, 0.312, 0.058, thin=0.30)
        holes = Image.composite(star, holes, star)
    else:
        big = person(W, 0.442, 0.400, 0.076, 0.470, 0.828, 0.230, 0.090)
        small = person(W, 0.638, 0.462, 0.055, 0.528, 0.828, 0.150, 0.070)
        holes = Image.composite(big, holes, big)
        holes = Image.composite(small, holes, small)

    house_with_holes = punch(mask, holes)

    # Градиентная заливка дома.
    fill = vertical_gradient((W, W), accent_top, accent_bottom).convert("RGBA")
    house_layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    house_layer.paste(fill, (0, 0), house_with_holes)

    # Свечение вокруг дома (считаем по целой маске, чтобы не текло в вырезы).
    halo_mask = mask.filter(ImageFilter.GaussianBlur(W * 0.028)).point(lambda v: int(v * 0.85))
    halo_mask = Image.composite(Image.new("L", (W, W), 0), halo_mask, mask.point(lambda v: 255 - v))
    halo_layer = Image.new("RGBA", (W, W), hex_rgb(glow) + (255,))
    halo_layer.putalpha(halo_mask)

    canvas = Image.alpha_composite(canvas, halo_layer)
    canvas = Image.alpha_composite(canvas, house_layer)

    # Вырезы делаем плотно тёмными, чтобы силуэты читались как в макете.
    dark = Image.new("RGBA", (W, W), (10, 12, 16, 255))
    canvas.paste(dark, (0, 0), holes)
    return canvas


def downsample(image: Image.Image, size: int) -> Image.Image:
    return image.resize((size, size), Image.LANCZOS)


def round_icon(image: Image.Image) -> Image.Image:
    mask = Image.new("L", image.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, image.size[0] - 1, image.size[1] - 1], fill=255)
    out = Image.new("RGBA", image.size, (0, 0, 0, 0))
    out.paste(image, (0, 0), mask)
    return out


def adaptive_foreground(color_icon: Image.Image, target: int = 432) -> Image.Image:
    """House в центральной безопасной зоне (66 из 108), фон прозрачный."""
    canvas = Image.new("RGBA", (target, target), (0, 0, 0, 0))
    inner = int(target * 0.62)
    house = color_icon.crop((int(S * 0.06), int(S * 0.10), int(S * 0.94), int(S * 0.92)))
    house = house.resize((inner, inner), Image.LANCZOS)
    offset = (target - inner) // 2
    canvas.alpha_composite(house, (offset, offset))
    return canvas


def write_flavor(role: str, accent_top: str, accent_bottom: str, glow: str, out_root: str) -> None:
    os.makedirs(out_root, exist_ok=True)
    full = build(accent_top, accent_bottom, glow, role, with_border=True, background=True)
    full.save(os.path.join(out_root, f"icon-{role}-1024.png"))

    for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        folder = os.path.join(out_root, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        icon = downsample(full, px)
        icon.save(os.path.join(folder, "ic_launcher.png"))
        round_icon(icon).save(os.path.join(folder, "ic_launcher_round.png"))

    # Foreground для adaptive: дом без подложки, чтобы маска лончера работала.
    transparent = build(accent_top, accent_bottom, glow, role, with_border=False, background=False)
    drawable = os.path.join(out_root, "drawable")
    os.makedirs(drawable, exist_ok=True)
    for density, px in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)):
        folder = os.path.join(out_root, f"drawable-{density}")
        os.makedirs(folder, exist_ok=True)
        adaptive_foreground(transparent, px).save(os.path.join(folder, "ic_launcher_foreground.png"))

    # Любая плотность: adaptive-описание.
    anydpi = os.path.join(out_root, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for name in ("ic_launcher", "ic_launcher_round"):
        with open(os.path.join(anydpi, f"{name}.xml"), "w", encoding="utf-8") as handle:
            handle.write(
                '<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                '    <background android:drawable="@color/ic_launcher_background" />\n'
                '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
                '</adaptive-icon>\n'
            )


def main() -> None:
    base = "apps/android/app/src"
    child_root = os.path.join(base, "child", "res")
    parent_root = os.path.join(base, "parent", "res")

    write_flavor("child", "#B6F154", "#2FBB37", "#63E04A", child_root)
    write_flavor("parent", "#FFA733", "#FF4E08", "#FF7A1A", parent_root)

    # Цвет фона adaptive-иконки одинаковый для обоих.
    for root in (child_root, parent_root):
        values = os.path.join(root, "values")
        os.makedirs(values, exist_ok=True)
        with open(os.path.join(values, "ic_launcher_background.xml"), "w", encoding="utf-8") as handle:
            handle.write(
                '<?xml version="1.0" encoding="utf-8"?>\n'
                '<resources>\n'
                '    <color name="ic_launcher_background">#0A0C10</color>\n'
                '</resources>\n'
            )
    print("иконки готовы")


if __name__ == "__main__":
    main()
