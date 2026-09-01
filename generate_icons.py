"""Builds the launcher icons from one source image.

The source is a flat two-tone mark. Every layer Android wants is derived from
it here rather than checked in, so the icon in a release is always the icon in
this repository — the release workflow runs this script before building, and a
PNG dropped into assets/ by hand would be regenerated over.

The source is a JPEG of a design with exactly two tones, so its edges carry
compression noise that a launcher would show as grey fringing. Deciding each
pixel is one tone or the other removes that noise rather than blurring it, and
costs nothing on a design that was never anti-aliased to begin with.
"""

import os
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).parent / "assets"
SOURCE = ASSETS / "icon-source.jpg"

# The mark and what sits behind it.
GLYPH_COLOR = (255, 255, 255)
BACKGROUND_COLOR = (0, 0, 0)

# Above this the source is the mark, below it the background.
THRESHOLD = 128

# Android's adaptive layers, and the size the tooling wants them at.
ADAPTIVE_SIZE = 432

# The legacy icon, and the one the README shows.
FULL_SIZE = 2000

# How round the README icon is. The launcher icons are not rounded here on
# purpose: Android masks them itself, to whatever shape the launcher uses, and
# a corner rounded twice is a corner cut twice.
README_RADIUS = 0.25


def glyph_mask(size):
    """The mark as an alpha channel, at [size], with its edges decided."""
    grey = Image.open(SOURCE).convert("L").resize((size, size), Image.LANCZOS)
    return grey.point(lambda v: 255 if v >= THRESHOLD else 0, mode="L")


def glyph(size):
    """The mark in its own colour, on transparency."""
    layer = Image.new("RGBA", (size, size), (*GLYPH_COLOR, 0))
    layer.putalpha(glyph_mask(size))
    return layer


def full_icon(size):
    """The whole icon: the mark on its background, square and unmasked."""
    icon = Image.new("RGBA", (size, size), (*BACKGROUND_COLOR, 255))
    icon.alpha_composite(glyph(size))
    return icon


def rounded(icon, radius_fraction):
    width, height = icon.size
    mask = Image.new("L", (width, height), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [(0, 0), (width, height)],
        radius=int(min(width, height) * radius_fraction),
        fill=255,
    )
    icon.putalpha(mask)
    return icon


def save(image, path):
    image.save(str(path), "PNG")
    print("Generated", path)


def main():
    if not SOURCE.exists():
        raise SystemExit(f"Missing {SOURCE}")

    save(full_icon(FULL_SIZE), ASSETS / "icon.png")
    save(rounded(full_icon(FULL_SIZE), README_RADIUS), ASSETS / "readme" / "icon.png")

    # Adaptive layers are separate so the launcher can move them against each
    # other; the foreground therefore carries the mark alone.
    save(glyph(ADAPTIVE_SIZE), ASSETS / "icon-foreground-432x432.png")
    save(glyph(ADAPTIVE_SIZE), ASSETS / "icon-monochrome-432x432.png")
    save(
        Image.new("RGBA", (ADAPTIVE_SIZE, ADAPTIVE_SIZE), (*BACKGROUND_COLOR, 255)),
        ASSETS / "icon-background.png",
    )


if __name__ == "__main__":
    main()
