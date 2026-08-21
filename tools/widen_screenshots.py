#!/usr/bin/env python3
"""Widen tall phone screencaps to Play's 2:1 cap.

The Flip's main screen is 1080x2520 -- 2.33:1. Play caps store screenshots at 2:1 and rejected
the raw captures once already (commit eb45817). Widening to 1260x2520 rather than cropping keeps
every pixel of UI. The margins are a blurred, scaled copy of the shot itself: edge replication
streaked badly where the running screen's perimeter glow meets the frame.

    cd docs/play-assets/screenshots/phone
    python3 ../../../../tools/widen_screenshots.py raw/*.png -o .
    python3 tools/widen_screenshots.py --selfcheck    # from anywhere

Note what that second argument does: `-o .` WRITES OVER the committed screenshot of the same name.
That is deliberate — the five names are what Play expects and what git tracks — but it means the
originals live in git, not on disk, from the moment it runs.

The centre band is a bit-for-bit copy of the input, so nothing in the UI is ever resampled.
"""
import argparse
import os
import sys

from PIL import Image, ImageFilter

MAX_RATIO = 2.0
# ponytail: one blur radius for every shot. It is decoration outside the frame; if a future shot
# shows recognisable shapes bleeding into the margin, raise it rather than masking per-image.
BLUR_RADIUS = 48


def widen(src: Image.Image, max_ratio: float = MAX_RATIO, blur: int = BLUR_RADIUS) -> Image.Image:
    """Pad `src` horizontally until it is no taller than `max_ratio`:1, filling with a blurred copy."""
    w, h = src.size
    if h <= w * max_ratio:
        return src.copy()
    target_w = round(h / max_ratio)
    # Scale the whole shot to cover the wider canvas, centre-crop, blur: the margin then carries the
    # shot's own colour at the height it sits at, so the seam reads as depth of field, not a border.
    scale = target_w / w
    cover = src.resize((target_w, round(h * scale)), Image.LANCZOS)
    top = (cover.height - h) // 2
    out = cover.crop((0, top, target_w, top + h)).filter(ImageFilter.GaussianBlur(blur))
    out.paste(src, ((target_w - w) // 2, 0))
    return out


def selfcheck() -> None:
    import random

    random.seed(0)
    src = Image.new("RGB", (1080, 2520))
    src.putdata([(random.randrange(256), random.randrange(256), random.randrange(256))
                 for _ in range(1080 * 2520)])
    out = widen(src)
    assert out.size == (1260, 2520), out.size
    x0 = (1260 - 1080) // 2
    assert x0 == 90, x0
    assert out.crop((x0, 0, x0 + 1080, 2520)).tobytes() == src.tobytes(), "centre band was resampled"
    # Already-legal input passes straight through.
    wide = Image.new("RGB", (1260, 2520))
    assert widen(wide).size == (1260, 2520)
    print("selfcheck ok: 1080x2520 -> 1260x2520, centre band bit-identical, 90px margins")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("images", nargs="*", help="PNGs straight off `adb exec-out screencap -p`")
    ap.add_argument("-o", "--out-dir", help="write here instead of overwriting in place")
    ap.add_argument("--selfcheck", action="store_true")
    a = ap.parse_args()
    if a.selfcheck:
        selfcheck()
        return 0
    if not a.images:
        ap.error("give some images, or --selfcheck")
    for path in a.images:
        src = Image.open(path).convert("RGB")  # Play requires RGB, not RGBA
        out = widen(src)
        dst = os.path.join(a.out_dir, os.path.basename(path)) if a.out_dir else path
        out.save(dst)
        print(f"{os.path.basename(path)}: {src.size[0]}x{src.size[1]} -> {out.size[0]}x{out.size[1]}  {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
