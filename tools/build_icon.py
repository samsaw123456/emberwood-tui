#!/usr/bin/env python3
"""Render launcher sizes from the original SunnyLand fox; requires Pillow.

The terminal window and amber prompt distinguish this port from the other
Emberwood editions. Draw the frame at higher resolution for clean curved edges,
then composite the licensed fox with nearest-neighbor scaling to retain its art.
Game assets are read only; generated artwork stays in packaging/.
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
FOX = (
    ROOT / "src/main/resources/assets/pixel/characters/sunnyland/fox-idle-frame-01.png"
)
SIZES = (16, 24, 32, 48, 64, 128, 256, 512)


def render(size: int, fox: Image.Image) -> Image.Image:
    scale = size * 4 / 128
    canvas = Image.new("RGBA", (size * 4, size * 4))
    draw = ImageDraw.Draw(canvas)

    def box(bounds, radius, color):
        draw.rounded_rectangle(
            tuple(coordinate * scale for coordinate in bounds),
            radius=radius * scale,
            fill=color,
        )

    box((6, 10, 122, 122), 17, "#162e27")
    box((6, 6, 122, 116), 17, "#416951")
    box((11, 29, 117, 110), 9, "#102724")
    for x in (19, 28, 37):
        box((x - 2, 16, x + 2, 20), 2, "#b3c2a1")
    draw.line(
        [(15 * scale, 85 * scale), (25 * scale, 93 * scale), (15 * scale, 101 * scale)],
        fill="#ffd28a",
        width=max(1, round(4 * scale)),
        joint="curve",
    )
    draw.line(
        [(30 * scale, 102 * scale), (42 * scale, 102 * scale)],
        fill="#ffd28a",
        width=max(1, round(4 * scale)),
    )
    canvas = canvas.resize((size, size), Image.Resampling.LANCZOS)
    sprite = fox.resize(
        (round(size * 63 / 128), round(size * 77 / 128)),
        Image.Resampling.NEAREST,
    )
    canvas.alpha_composite(sprite, (round(size * 48 / 128), round(size * 30 / 128)))
    return canvas


def main() -> None:
    fox = Image.open(FOX).convert("RGBA")
    fox = fox.crop(fox.getbbox())
    destination = ROOT / "packaging/icons"
    destination.mkdir(parents=True, exist_ok=True)
    for size in SIZES:
        image = render(size, fox)
        image.save(destination / f"{size}.png")
        if size == 512:
            image.save(ROOT / "packaging/emberwood-terminal.png")


if __name__ == "__main__":
    main()
