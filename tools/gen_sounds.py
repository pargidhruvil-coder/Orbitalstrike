"""Synthesized original sound effects -> mono OGG (Minecraft needs mono for positional sounds)."""
import numpy as np, os, subprocess, sys, tempfile, wave
from scipy.signal import lfilter, butter

OUT = os.path.join(sys.argv[1], "assets/orbitalstrike/sounds")
os.makedirs(OUT, exist_ok=True)
SR = 44100
rng = np.random.default_rng(5)

def t_(sec): return np.arange(int(SR * sec)) / SR
def env(n, a, r, sec):
    t = t_(sec); e = np.ones_like(t)
    if a > 0: e *= np.clip(t / a, 0, 1)
    if r > 0: e *= np.clip((sec - t) / r, 0, 1)
    return e
def lp(x, f, order=2): b, a = butter(order, f / (SR / 2), 'low'); return lfilter(b, a, x)
def hp(x, f, order=2): b, a = butter(order, f / (SR / 2), 'high'); return lfilter(b, a, x)
def bp(x, lo, hi): b, a = butter(2, [lo / (SR / 2), hi / (SR / 2)], 'band'); return lfilter(b, a, x)
def sweep(f0, f1, sec, curve=1.0):
    t = t_(sec); k = (t / sec) ** curve
    f = f0 + (f1 - f0) * k
    return np.sin(2 * np.pi * np.cumsum(f) / SR)
def noise(sec): return rng.standard_normal(int(SR * sec))
def bell(freqs, sec, decay):
    t = t_(sec); s = 0
    for i, f in enumerate(freqs):
        s = s + np.sin(2 * np.pi * f * t) * np.exp(-t / (decay / (1 + i * 0.4))) / (1 + i * 0.5)
    return s

def write(name, x, gain=0.9):
    x = np.asarray(x, dtype=float)
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    fd, wav = tempfile.mkstemp(suffix=".wav"); os.close(fd)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4",
                    os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)

# charge: rising energy whine with shimmer
sec = 2.2
c = sweep(180, 1400, sec, 1.6) * 0.6 + sweep(360, 2800, sec, 1.6) * 0.25 + sweep(90, 700, sec, 1.6) * 0.4
c += hp(noise(sec), 3000) * 0.08 * t_(sec) / sec
c *= (0.2 + 0.8 * t_(sec) / sec) * env(0, 0.05, 0.1, sec) * (1 + 0.25 * np.sin(2 * np.pi * 14 * t_(sec)))
write("charge", c)

write("ready", bell([1046, 1568, 2093, 3136], 1.6, 0.6) + 0.5 * bell([1318, 1975], 1.6, 0.4) * (t_(1.6) > 0.12))

sec = 1.4
zap = sweep(4000, 150, 0.35, 0.4) * env(0, 0.003, 0.05, 0.35)
boom = lp(noise(sec), 400, 4) * np.exp(-t_(sec) / 0.35) * 3 + np.sin(2 * np.pi * 50 * t_(sec)) * np.exp(-t_(sec) / 0.4)
f = boom.copy(); f[:len(zap)] += zap * 0.8
write("fire", f)

l = np.zeros(int(SR * 0.5)); b1 = np.sin(2 * np.pi * 1400 * t_(0.09)) * env(0, 0.005, 0.02, 0.09)
l[:len(b1)] += b1; l[int(SR * 0.16):int(SR * 0.16) + len(b1)] += b1
write("lock", l, 0.7)

sec = 3.5
g = bell([98, 196 * 1.01, 293 * 0.99, 415, 587, 830], sec, 1.6)
g += 0.3 * bell([1975, 2637, 3520], sec, 0.9) * (1 + np.sin(2 * np.pi * 6 * t_(sec))) / 2
write("align", g * env(0, 0.01, 0.4, sec))

sec = 4.0
t = t_(sec)
drone = sum(np.sin(2 * np.pi * f0 * t + 0.3 * np.sin(2 * np.pi * 0.7 * t)) / (i + 1) for i, f0 in enumerate([55, 110, 165, 220.5, 331]))
drone += bp(noise(sec), 800, 4000) * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t))
write("beam", drone * env(0, 0.4, 0.6, sec))

sec = 5.0
t = t_(sec)
imp = lp(noise(sec), 250, 4) * np.exp(-t / 0.9) * 4
imp += np.sin(2 * np.pi * np.cumsum(60 - 35 * (t / sec)) / SR) * np.exp(-t / 1.5) * 1.4
imp += bp(noise(sec), 400, 2500) * np.exp(-t / 0.18) * 1.5
imp += lp(noise(sec), 80, 2) * np.exp(-t / 2.0) * 3     # long rumble
write("impact", imp * env(0, 0.002, 0.5, sec), 0.98)

sec = 2.4
t = t_(sec)
trill = np.sin(2 * np.pi * 950 * t) * (np.sin(2 * np.pi * 20 * t) > 0) + 0.5 * np.sin(2 * np.pi * 1420 * t)
gate = ((t % 1.2) < 0.7).astype(float)
write("comm_ring", trill * gate * env(0, 0.01, 0.05, sec), 0.6)

sec = 2.4
t = t_(sec)
fs = np.where((t % 0.6) < 0.3, 720, 960)
siren = np.sign(np.sin(2 * np.pi * np.cumsum(fs) / SR)) * 0.6 + np.sin(2 * np.pi * np.cumsum(fs) / SR)
write("alarm", lp(siren, 3000) * env(0, 0.01, 0.05, sec), 0.6)

write("keypad", np.sin(2 * np.pi * 1180 * t_(0.09)) * env(0, 0.003, 0.03, 0.09), 0.6)

a1 = np.sin(2 * np.pi * 660 * t_(0.18)); a2 = np.sin(2 * np.pi * 990 * t_(0.35))
write("access", np.concatenate([a1 * env(0, 0.005, 0.02, 0.18), a2 * env(0, 0.005, 0.15, 0.35)]), 0.6)

sec = 2.8
t = t_(sec)
v = sum(np.sin(2 * np.pi * f0 * t * (1 + 0.004 * np.sin(2 * np.pi * 5 * t))) for f0 in (130.8, 131.6, 196, 261.3))
v += bp(noise(sec), 500, 900) * 0.4
write("voice", v * env(0, 0.6, 0.8, sec), 0.6)

write("blip", np.sin(2 * np.pi * 1600 * t_(0.035)) * env(0, 0.002, 0.015, 0.035), 0.35)

sec = 3.0
t = t_(sec)
ns = noise(sec); out = np.zeros_like(ns)
for i in range(30):   # sweeping band-pass whoosh
    s, e = int(i * len(ns) / 30), int((i + 1) * len(ns) / 30)
    fc = 300 + 2500 * (i / 30) ** 1.5
    out[s:e] = bp(ns, fc * 0.7, fc * 1.3)[s:e]
write("vajra", out * env(0, 1.5, 0.3, sec) + np.sin(2 * np.pi * np.cumsum(80 + 300 * t / sec) / SR) * 0.3 * env(0, 1.5, 0.3, sec))

write("fizzle", hp(noise(0.7), 2000) * np.exp(-t_(0.7) / 0.18), 0.5)

# Indra's call: gong + thunder-like rumble + low choir
sec = 4.0
t = t_(sec)
call = bell([65, 130.5, 196, 262, 392], sec, 2.0) + lp(noise(sec), 150, 3) * np.exp(-t / 1.2) * 2
call += 0.4 * sum(np.sin(2 * np.pi * f0 * t) for f0 in (98, 147, 196)) * env(0, 0.8, 1.2, sec)
write("divine", call * env(0, 0.01, 0.6, sec))
print("sounds done", sorted(os.listdir(OUT)))
