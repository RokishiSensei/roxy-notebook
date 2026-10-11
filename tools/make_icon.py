"""
Builds the Lexi WordBook launcher icon from tools/src/roxy-source.png.

Source: tools/src/roxy-source.png (user-supplied, AI-generated artwork).

**No colour grading is applied** — the artwork is used exactly as supplied. The only processing
is framing (crop + scale) plus a soft vignette that feathers the square edges into the picture's
own edge tone, so no hard cut shows through the launcher mask.

Run:  python tools/make_icon.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(ROOT, "tools", "src", "roxy-source.png")

SS = 2                      # supersampling factor
CANVAS = 512                # final artwork size (px)
W = CANVAS * SS

# Adaptive icons show roughly the middle 66% of the 108dp canvas.
SAFE = 0.66

# How far the feather reaches inwards (0.30 = soft, 0.55 = strong).
FEATHER = 0.30


def average_edge_colour(img: Image.Image) -> tuple[int, int, int]:
    """Mean colour of the outer 6% frame — used for the vignette and the icon background."""
    w, h = img.size
    band = max(1, int(min(w, h) * 0.06))
    pixels = []
    for y in range(0, h, 3):
        for x in range(0, w, 3):
            if x < band or x >= w - band or y < band or y >= h - band:
                pixels.append(img.getpixel((x, y)))
    if not pixels:
        return (32, 44, 96)
    n = len(pixels)
    return (
        sum(p[0] for p in pixels) // n,
        sum(p[1] for p in pixels) // n,
        sum(p[2] for p in pixels) // n,
    )


def load_art() -> Image.Image:
    """Square framing: keeps the composition, anchored slightly above centre."""
    img = Image.open(SRC).convert("RGB")
    w, h = img.size
    if w != h:
        side = min(w, h)
        left = (w - side) // 2
        # Anchor 42% down the spare height so the eyes sit a touch above the icon centre.
        top = max(0, min(h - side, int((h - side) * 0.42)))
        img = img.crop((left, top, left + side, top + side))
    return img.resize((W, W), Image.LANCZOS)


def vignette(size: int, strength: float) -> Image.Image:
    """Radial mask: opaque in the middle, fading towards the rim."""
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    cx = cy = size / 2
    steps = 140
    outer = size * 0.72
    for i in range(steps, 0, -1):
        r = outer * i / steps
        t = i / steps
        value = int(255 * max(0.0, min(1.0, (1.0 - t) / strength)) ** 0.9)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=value)
    return mask.filter(ImageFilter.GaussianBlur(size * 0.045))


def rounded_mask(size: int, radius_ratio: float) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=size * radius_ratio, fill=255
    )
    return mask


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def save(img: Image.Image, rel_path: str) -> None:
    path = os.path.join(RES, rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)
    print(f"  {rel_path}  {img.size[0]}x{img.size[1]}")


def main() -> None:
    art = load_art()
    edge = average_edge_colour(art)
    print(f"artwork loaded, edge colour = {edge}")

    # Feather the square edges into the picture's own edge tone. This only fades the outer rim;
    # the frame itself is left exactly as supplied.
    feathered = Image.composite(
        art, Image.new("RGB", (W, W), edge), vignette(W, FEATHER)
    ).convert("RGBA")

    # Slightly darkened edge tone for the adaptive background, so the mask blends in.
    bg_colour = tuple(int(c * 0.78) for c in edge)

    # Adaptive foreground: artwork scaled into the safe zone on a transparent canvas.
    fg = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    inner = int(W * SAFE)
    fg.alpha_composite(
        feathered.resize((inner, inner), Image.LANCZOS), ((W - inner) // 2,) * 2
    )

    # Adaptive background: flat colour sampled from the artwork's own edge.
    bg = Image.new("RGB", (W, W), bg_colour)

    for folder, size in (
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192),
    ):
        save(fg.resize((size, size), Image.LANCZOS), f"{folder}/ic_launcher_foreground.png")
        save(bg.resize((size, size), Image.LANCZOS), f"{folder}/ic_launcher_background.png")

        square = feathered.resize((size, size), Image.LANCZOS)
        square.putalpha(rounded_mask(size, 0.22))
        save(square, f"{folder}/ic_launcher.png")

        round_icon = feathered.resize((size, size), Image.LANCZOS)
        round_icon.putalpha(circle_mask(size))
        save(round_icon, f"{folder}/ic_launcher_round.png")

    feathered.resize((256, 256), Image.LANCZOS).save(
        os.path.join(ROOT, "tools", "icon-preview.png"), "PNG", optimize=True
    )
    print("done")


if __name__ == "__main__":
    main()
