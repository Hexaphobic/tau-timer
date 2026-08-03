#!/usr/bin/env python3
"""Re-tint the Play feature graphic from one palette colour to another.

The original was typeset ad hoc and its script was never committed, so this rotates hue on the
existing artwork rather than re-typesetting it — that way the face, weights and spacing are the
ones already approved, and only the colour moves.

Hue rotation rather than a palette swap because the green is a *gradient*: a radial glow falling
off into near-black, plus the tagline. A flat find-and-replace would band it. Rotating hue moves
every step of that ramp by the same angle and leaves saturation and value alone, so the falloff
survives intact.

White text is untouched for free: it has no saturation to rotate. The SAT_FLOOR guard only exists
to stop near-black background pixels, where hue is numerically unstable, from drifting.

    python3 tools/recolor_feature.py            # default Work green -> Rest blue
    python3 tools/recolor_feature.py --from 22E06A --to 8B5CF6
"""

import argparse
import colorsys
import sys

from PIL import Image

# app/src/main/java/com/chrispoole/intervaltimer/ui/Glass.kt, Palette.DEFAULT
WORK_GREEN = "22E06A"
REST_BLUE = "38BDF8"

# Below this saturation a pixel is either white text or near-black background; its hue is noise.
SAT_FLOOR = 0.12


def hue_of(hex_rgb: str) -> float:
    r, g, b = (int(hex_rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return colorsys.rgb_to_hsv(r, g, b)[0]


def recolor(src: str, dst: str, src_hex: str, dst_hex: str) -> None:
    shift = (hue_of(dst_hex) - hue_of(src_hex)) % 1.0
    im = Image.open(src).convert("RGB")
    px = im.load()
    w, h = im.size
    moved = 0
    for y in range(h):
        for x in range(w):
            r, g, b = (c / 255 for c in px[x, y])
            hue, sat, val = colorsys.rgb_to_hsv(r, g, b)
            if sat < SAT_FLOOR:
                continue
            nr, ng, nb = colorsys.hsv_to_rgb((hue + shift) % 1.0, sat, val)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255))
            moved += 1
    im.save(dst)
    print(f"{dst}  {w}x{h}  hue +{shift * 360:.1f}deg on {moved:,} px "
          f"({moved / (w * h) * 100:.1f}% of the frame)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="src", default="docs/play-assets/feature-1024x500.png")
    ap.add_argument("--out", dest="dst", default="docs/play-assets/feature-1024x500.png")
    ap.add_argument("--from", dest="src_hex", default=WORK_GREEN)
    ap.add_argument("--to", dest="dst_hex", default=REST_BLUE)
    a = ap.parse_args()
    if a.src == a.dst:
        print("note: rewriting in place; git has the previous version", file=sys.stderr)
    recolor(a.src, a.dst, a.src_hex.lstrip("#"), a.dst_hex.lstrip("#"))
