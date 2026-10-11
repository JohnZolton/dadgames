#!/usr/bin/env python3
"""Synthesize the game's small sound effects (no samples, no licences): place, flip, win.
Usage: python3 make_sounds.py OUT_DIR"""
import math, random, struct, sys, wave

RATE = 22050

def write(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32000)) for s in samples))

def place():
    # Soft "thock": low decaying tone plus a short noise click.
    rnd = random.Random(1); n = int(RATE * 0.07)
    return [0.55 * math.exp(-i / (RATE * 0.012)) * math.sin(2 * math.pi * 180 * i / RATE)
            + 0.35 * math.exp(-i / (RATE * 0.004)) * (rnd.random() * 2 - 1) for i in range(n)]

def flip():
    # Paper swish: band-limited noise with a quick swell.
    rnd = random.Random(2); n = int(RATE * 0.09); out = []; prev = 0.0
    for i in range(n):
        t = i / n
        env = math.sin(math.pi * t) ** 2
        prev = 0.6 * prev + 0.4 * (rnd.random() * 2 - 1)
        out.append(0.45 * env * prev)
    return out

def win():
    # Gentle rising arpeggio (C E G C) with bell-like decay.
    notes = [523.25, 659.25, 783.99, 1046.5]; n = int(RATE * 1.3); out = [0.0] * n
    for k, f in enumerate(notes):
        start = int(RATE * 0.13 * k)
        for i in range(n - start):
            e = math.exp(-i / (RATE * 0.35))
            out[start + i] += 0.22 * e * (math.sin(2 * math.pi * f * i / RATE) + 0.3 * math.sin(4 * math.pi * f * i / RATE))
    return out

d = sys.argv[1]
write(f"{d}/snd_place.wav", place()); write(f"{d}/snd_flip.wav", flip()); write(f"{d}/snd_win.wav", win())
