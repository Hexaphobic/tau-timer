#!/usr/bin/env python3
"""Rebuild the 1024x500 Play feature graphic: keep the type, replace the background.

WHY REBUILD RATHER THAN RECOLOUR. The original's glow showed concentric contour lines. That is
8-bit banding, not a mistake in the artwork: the ramp crosses ~25 levels over ~500px, so each
level holds a ~20px band and the eye reads the steps as rings. Re-tinting it (tools/recolor_feature.py)
moved the hue and carried the banding across intact, because the banding was already quantised
into the pixels. It can only be fixed by generating the ramp again in float and quantising once,
with dither.

TWO CHANGES FIX IT:

1. Gaussian falloff instead of a bounded radial. A radial gradient with a finite radius has an
   outer edge, and an edge is what makes it read as a spotlight sitting on the frame. A Gaussian
   never reaches zero, so the light covers the whole panel and has nowhere to terminate — the
   "omnipresent" part. Sigma is set from the diagonal, so it does not care about aspect ratio.

2. Dither before quantising. Uniform noise of +/-0.5 LSB added to the float value means a pixel
   whose true value is 12.3 lands on 12 about 70% of the time and 13 about 30%, so the boundary
   between bands dissolves into a gradient instead of a line. This is the whole fix for banding
   and it costs one line. The noise is far below the visible threshold on its own.

THE TYPE is lifted off the existing artwork rather than re-set, because the original was typeset
ad hoc and its font was never recorded — re-setting it would silently change the face. Lifting it
means the letterforms stay byte-identical to the ones already approved.

    python3 tools/gen_feature.py --preview     # write candidates to the scratchpad
    python3 tools/gen_feature.py               # apply
"""

import argparse
import math
import os
import random

from PIL import Image, ImageFilter

SRC = "docs/play-assets/feature-1024x500.png"
DST = "docs/play-assets/feature-1024x500.png"

# Shipping: no colour at all, to sit beside the flat monochrome launcher icon. Strictly neutral —
# equal channels, so nothing tints the wash. The blue kept here only as the alternative.
GLOW = (0x22, 0x22, 0x22)
BASE = (0x08, 0x08, 0x08)
# app/src/main/java/com/chrispoole/intervaltimer/ui/Glass.kt, Palette.DEFAULT rest
GLOW_BLUE = (0x0E, 0x28, 0x3A)
BASE_BLUE = (0x06, 0x07, 0x0A)

# Desaturate the lifted type as well as the background. The tagline was set in the palette colour;
# left alone it would be the one coloured thing on an otherwise neutral panel. Mapped to its own
# luminance rather than to white, so it stays dimmer than the title and the hierarchy survives.
MONO = True

CENTER = (0.42, 0.46)   # slightly left of and above centre, so the light is not a bullseye
SIGMA = 1.30            # as a fraction of the half-diagonal; >1 puts the falloff well off-panel,
                        # so no part of the ramp's shoulder lands inside the frame as a visible edge


def lift_type(src):
    """Separate ink from background: returns (alpha, colour) per pixel.

    The background is smooth and the type is thin, so eroding with a min filter wider than the
    thickest stroke leaves the background behind and nothing else. Blurring that estimate removes
    the residual chatter the erosion leaves at the glyph edges.
    """
    im = Image.open(src).convert("RGB")
    bg = im
    for _ in range(10):                       # 10 passes of radius 2 => ~20px erosion
        bg = bg.filter(ImageFilter.MinFilter(5))
    bg = bg.filter(ImageFilter.GaussianBlur(24))

    w, h = im.size
    ip, bp = im.load(), bg.load()
    alpha = [[0.0] * w for _ in range(h)]
    color = [[(0, 0, 0)] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            r, g, b = ip[x, y]
            br, bgn, bb = bp[x, y]
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            blum = 0.299 * br + 0.587 * bgn + 0.114 * bb
            a = (lum - blum) / max(255.0 - blum, 1.0)
            a = min(max(a, 0.0), 1.0)
            alpha[y][x] = a
            if a > 0.004:
                # Un-mix: observed = bg*(1-a) + ink*a, so recover the ink's own colour. Without
                # this the antialiased edges keep a rim of the OLD background colour.
                color[y][x] = tuple(
                    min(max(int((o - bl * (1 - a)) / a), 0), 255)
                    for o, bl in ((r, br), (g, bgn), (b, bb))
                )
    return im.size, alpha, color


def build(size, alpha, color, glow, sigma, seed=7):
    w, h = size
    rnd = random.Random(seed)
    cx, cy = CENTER[0] * w, CENTER[1] * h
    half_diag = math.hypot(w, h) / 2
    s = sigma * half_diag
    out = Image.new("RGB", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            d = math.hypot(x - cx, y - cy)
            k = math.exp(-(d * d) / (2 * s * s))
            a = alpha[y][x]
            ink = color[y][x]
            if MONO and a > 0:
                g = round(0.299 * ink[0] + 0.587 * ink[1] + 0.114 * ink[2])
                ink = (g, g, g)
            px = []
            for i in range(3):
                bgv = BASE[i] + (glow[i] - BASE[i]) * k
                v = bgv * (1 - a) + ink[i] * a
                # Dither: +/-0.5 LSB before rounding. This is what removes the rings.
                px.append(min(max(int(v + rnd.uniform(-0.5, 0.5) + 0.5), 0), 255))
            op[x, y] = tuple(px)
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    ap.add_argument("--out", default=".")
    a = ap.parse_args()

    size, alpha, color = lift_type(SRC)
    if a.preview:
        for name, glow, sigma in (
            ("even", GLOW, 0.62),
            ("wider", (0x24, 0x68, 0x96), 0.85),
            ("flat", (0x1C, 0x50, 0x74), 1.30),
        ):
            p = os.path.join(a.out, f"feature-{name}.png")
            build(size, alpha, color, glow, sigma).save(p)
            print(p)
    else:
        build(size, alpha, color, GLOW, SIGMA).save(DST)
        print(f"{DST} rebuilt: gaussian falloff sigma={SIGMA}, dithered")
