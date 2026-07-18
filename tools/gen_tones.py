#!/usr/bin/env python3
"""Generate the interval-timer cue tones as enveloped 44.1kHz/16-bit mono WAVs.

This is the tuning knob for how the beeps sound — edit freq/duration/harmonics
here and re-run to regenerate app/src/main/res/raw/*.wav. Stdlib only (no numpy).

Design (from research §6):
  warn5 : distinct "get ready" — E5 659Hz, rounder/lower, longer.
  tick  : plain 3/2/1 blip     — A5 880Hz, short, crisp.
  go    : distinct transition  — rising chirp 880->1319Hz.
Raised-cosine (Hann) attack/decay kill clicks; a little 2nd/3rd harmonic adds warmth.
"""
import math
import os
import struct
import wave

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def _env(n, total, attack, release):
    if attack > 0 and n < attack:
        return 0.5 * (1 - math.cos(math.pi * n / attack))
    if release > 0 and n > total - release:
        m = total - n
        return 0.5 * (1 - math.cos(math.pi * m / release))
    return 1.0


def tone(freq, dur_ms, attack_ms, release_ms, harmonics=((1, 1.0), (2, 0.2)), amp=0.8):
    total = int(SR * dur_ms / 1000)
    a, r = int(SR * attack_ms / 1000), int(SR * release_ms / 1000)
    norm = sum(h for _, h in harmonics)
    out = []
    for n in range(total):
        s = sum(h * math.sin(2 * math.pi * freq * mult * n / SR) for mult, h in harmonics) / norm
        out.append(amp * _env(n, total, a, r) * s)
    return out


def chirp(f0, f1, dur_ms, attack_ms, release_ms, amp=0.85):
    total = int(SR * dur_ms / 1000)
    a, r = int(SR * attack_ms / 1000), int(SR * release_ms / 1000)
    out, phase = [], 0.0
    for n in range(total):
        f = f0 + (f1 - f0) * (n / total)
        phase += 2 * math.pi * f / SR
        out.append(amp * _env(n, total, a, r) * math.sin(phase))
    return out


def write_wav(name, samples):
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples))
    print("wrote", path, len(samples), "samples")


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    write_wav("warn5.wav", tone(659.25, 200, 8, 80, harmonics=((1, 1.0), (2, 0.25)), amp=0.80))
    write_wav("tick.wav", tone(880.0, 120, 6, 55, harmonics=((1, 1.0), (3, 0.15)), amp=0.80))
    write_wav("go.wav", chirp(880.0, 1318.5, 320, 8, 90, amp=0.85))
