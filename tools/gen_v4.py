"""v4 assets: 3D cannon model + textures, alien skins (Zoro/Zara), shuttle icon, alien chirp sounds."""
import numpy as np, json, os, sys, subprocess, tempfile, wave
from PIL import Image, ImageDraw

OUT = sys.argv[1]
A = os.path.join(OUT, "assets/orbitalstrike")
ITEM_T = os.path.join(A, "textures/item"); MODEL = os.path.join(A, "models/item")
ENT = os.path.join(A, "textures/entity"); SND = os.path.join(A, "sounds")
for d in (ITEM_T, MODEL, ENT, SND): os.makedirs(d, exist_ok=True)
rng = np.random.default_rng(42)

# ------------------------------------------------------------------ gun textures (16x16 each)
def tex(name, base, var=0.12, frames=None, mcmeta=None, pattern=None):
    if frames is None:
        arr = np.array(base, float)[None, None, :] * (1 + (rng.random((16, 16, 1)) - 0.5) * var)
        if pattern is not None: arr = pattern(arr)
        img = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB")
    else:
        img = frames
    img.save(os.path.join(ITEM_T, name + ".png"))
    if mcmeta: open(os.path.join(ITEM_T, name + ".png.mcmeta"), "w").write(mcmeta)

def panel_lines(arr):
    arr[[0, 15], :, :] *= 0.6; arr[:, [0, 15], :] *= 0.6
    arr[7, 2:14, :] *= 0.75
    return arr
def brushed(arr):
    for y in range(16): arr[y] *= 0.92 + 0.16 * ((y * 7) % 5) / 5
    return arr

tex("cannon_dark", (34, 35, 44), 0.18, pattern=panel_lines)
tex("cannon_mid", (88, 92, 110), 0.15, pattern=brushed)
tex("cannon_gold", (226, 172, 58), 0.2, pattern=brushed)
tex("cannon_lens", (40, 220, 255), 0.3)

def core_frames(hot):
    frames = []
    for f in range(8):
        y, x = np.mgrid[0:16, 0:16]
        d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
        wave_ = 0.5 + 0.5 * np.sin(d * 0.9 - f * np.pi / 4)
        b = (0.55 if not hot else 0.85) + 0.45 * wave_
        col = np.stack([150 + 100 * wave_, 60 + 80 * wave_ * (1.6 if hot else 1), 255 * np.ones_like(d)], -1) * b[..., None]
        if hot: col = col * 0.6 + np.array([255, 230, 255]) * 0.4
        frames.append(Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGB"))
    strip = Image.new("RGB", (16, 16 * len(frames)))
    for i, fr in enumerate(frames): strip.paste(fr, (0, i * 16))
    return strip
tex("cannon_core", None, frames=core_frames(False), mcmeta='{"animation":{"frametime":2,"interpolate":true}}')
tex("cannon_core_hot", None, frames=core_frames(True), mcmeta='{"animation":{"frametime":1,"interpolate":true}}')

# ------------------------------------------------------------------ gun model (built along +X, then whole gun rotated 45° like a sword sprite)
FULL = [0, 0, 16, 16]
def el(frm, to, t, name=""):
    faces = {f: {"uv": FULL, "texture": "#" + t} for f in ("north", "south", "east", "west", "up", "down")}
    return {"name": name, "from": frm, "to": to, "rotation": {"angle": 45, "axis": "z", "origin": [8, 8, 8]}, "faces": faces}

def gun_elements(hot=False):
    core = "core_hot" if hot else "core"
    E = [
        el([-5, 5.5, 7.25], [0, 9, 8.75], "dark", "stock"),
        el([-5.5, 5, 7], [-4.5, 9.5, 9], "mid", "buttplate"),
        el([0, 7, 7.5], [2, 8.5, 8.5], "mid", "stock_link"),
        el([1, 2, 7.3], [3, 7, 8.7], "dark", "grip"),
        el([3, 4.5, 7.6], [5, 5, 8.4], "gold", "trigger_guard"),
        el([3.5, 5, 7.8], [4, 6.5, 8.2], "gold", "trigger"),
        el([0.5, 6.5, 6.5], [9, 10.5, 9.5], "dark", "receiver"),
        el([2.5, 7, 5.9], [7, 10, 10.1], core, "core"),
        el([2, 6.8, 6.2], [2.5, 10.2, 9.8], "gold", "core_cap_l"),
        el([7, 6.8, 6.2], [7.5, 10.2, 9.8], "gold", "core_cap_r"),
        el([4, 10.5, 7.3], [10, 11.8, 8.7], "mid", "scope_body"),
        el([3.5, 10.3, 7.1], [4.5, 12, 8.9], "dark", "scope_back"),
        el([9.5, 10.3, 7.1], [10.5, 12, 8.9], "dark", "scope_front"),
        el([10.4, 10.6, 7.4], [10.6, 11.7, 8.6], "lens", "lens"),
        el([9, 7.4, 7], [20, 9.4, 9], "mid", "barrel"),
        el([9, 6.4, 7.5], [17, 7.4, 8.5], "dark", "rail"),
        el([10, 6.6, 6.6], [11, 10.2, 9.4], "gold", "coil1"),
        el([12.5, 6.8, 6.7], [13.5, 10, 9.3], "gold", "coil2"),
        el([15, 7, 6.8], [16, 9.8, 9.2], "gold", "coil3"),
        el([11, 7.6, 6.8], [15, 9.2, 9.2], core, "coil_glow"),
        el([19.5, 6.9, 6.5], [21.5, 9.9, 9.5], "dark", "muzzle"),
        el([21.5, 7.4, 7.0], [22.5, 9.4, 9.0], core, "muzzle_orb"),
        el([5, 9.3, 5.4], [8, 9.8, 10.6], "mid", "fin"),
        el([5.5, 8.2, 5.6], [7.5, 8.6, 10.4], "mid", "fin2"),
    ]
    return E

def gun_model(hot):
    t = {"dark": "orbitalstrike:item/cannon_dark", "mid": "orbitalstrike:item/cannon_mid", "gold": "orbitalstrike:item/cannon_gold",
         "lens": "orbitalstrike:item/cannon_lens", "core": "orbitalstrike:item/cannon_core", "core_hot": "orbitalstrike:item/cannon_core_hot",
         "particle": "orbitalstrike:item/cannon_mid"}
    m = {"textures": t, "elements": gun_elements(hot), "gui_light": "front",
         "display": {
             "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.7, 0.7, 0.7]},
             "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.7, 0.7, 0.7]},
             "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
             "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.55, 0.55, 0.55]},
             "gui": {"rotation": [20, -30, 0], "translation": [0, 0, 0], "scale": [0.62, 0.62, 0.62]},
             "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
             "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]}}}
    if not hot:
        m["overrides"] = [{"predicate": {"orbitalstrike:charge": 0.5}, "model": "orbitalstrike:item/orbital_cannon_charged"}]
    return m

json.dump(gun_model(False), open(os.path.join(MODEL, "orbital_cannon.json"), "w"), indent=1)
json.dump(gun_model(True), open(os.path.join(MODEL, "orbital_cannon_charged.json"), "w"), indent=1)
for f in ("orbital_cannon.png", "orbital_cannon.png.mcmeta"):
    p = os.path.join(ITEM_T, f)
    if os.path.exists(p): os.remove(p)

# ------------------------------------------------------------------ alien skins (64x64 player layout)
FACES = {  # part: (u, v, w, h, d) -> box unwrap origin and size
    "head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4)}

def face_rects(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}

def make_skin(name, eye):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = img.load()
    for part, (u, v, w, h, d) in FACES.items():
        for fname, (x, y, fw, fh) in face_rects(u, v, w, h, d).items():
            for i in range(fw):
                for j in range(fh):
                    n = rng.random()
                    c = int(14 + 18 * n)
                    px[x + i, y + j] = (c, c, c + 6, 255)
            # jagged white edges (like a sharp suit pattern)
            if fname in ("front", "back", "left", "right"):
                for j in range(fh):
                    if rng.random() < 0.3: px[x, y + j] = (235, 235, 240, 255)
                    if rng.random() < 0.3: px[x + fw - 1, y + j] = (235, 235, 240, 255)
                    if rng.random() < 0.08 and fw > 2: px[x + 1, y + j] = (200, 200, 210, 255)
                if part in ("rarm", "larm", "rleg", "lleg") and fname == "front":
                    for i in range(fw): px[x + i, y] = (230, 230, 235, 255)
                    for i in range(1, fw - 1): px[x + i, y + 1] = (180, 180, 190, 255)
    # head front: white crown spikes + glowing eyes
    hx, hy = 8, 8
    for i in range(8):
        if i % 2 == 0: px[hx + i, hy] = (240, 240, 245, 255)
    px[hx, hy + 1] = px[hx + 7, hy + 1] = (240, 240, 245, 255)
    px[hx + 1, hy + 2] = px[hx + 6, hy + 2] = (200, 200, 210, 255)
    for (ex, ey) in ((1, 3), (2, 3), (3, 3), (2, 4), (4, 3), (5, 3), (6, 3), (5, 4)):
        px[hx + ex, hy + ey] = eye
    px[hx + 1, hy + 4] = tuple(int(c * 0.6) for c in eye[:3]) + (255,)
    px[hx + 6, hy + 4] = tuple(int(c * 0.6) for c in eye[:3]) + (255,)
    for i in range(2, 6): px[hx + i, hy + 6] = (60, 60, 70, 255)     # mouth slit
    # chest emblem: white claw marks
    bx, by = 20, 20
    for k, off in enumerate((1, 3, 5)):
        for j in range(5): px[bx + off + (j // 3), by + 2 + j] = (230, 230, 235, 255)
    px[bx + 3, by + 9] = eye; px[bx + 4, by + 9] = eye                  # glowing belt core
    img.save(os.path.join(ENT, f"alien_{name}.png"))

# make_skin("zoro", (120, 255, 80, 255))
# make_skin("zara", (200, 90, 255, 255))

# ------------------------------------------------------------------ shuttle icon 16x16
def shuttle_icon():
    T = (0, 0, 0, 0); W = (240, 240, 245, 255); G = (150, 150, 165, 255); O = (230, 120, 30, 255); B = (30, 30, 40, 255)
    R = (255, 160, 40, 255); Y = (255, 230, 90, 255); K = (60, 140, 230, 255)
    img = Image.new("RGBA", (16, 16), T); d = ImageDraw.Draw(img)
    d.rectangle([6, 3, 9, 12], fill=O); d.ellipse([6, 1, 9, 5], fill=O)            # big orange tank
    d.rectangle([3, 4, 4, 12], fill=W); d.point((3, 3), fill=W); d.point((4, 3), fill=W)   # left booster
    d.rectangle([11, 4, 12, 12], fill=W); d.point((11, 3), fill=W); d.point((12, 3), fill=W)
    d.rectangle([7, 6, 8, 13], fill=W)                                              # orbiter body (front)
    d.point((7, 7), fill=K); d.point((8, 7), fill=K)
    d.line([(5, 11), (10, 11)], fill=G); d.line([(4, 12), (11, 12)], fill=B)
    d.point((3, 13), fill=R); d.point((4, 13), fill=R); d.point((11, 13), fill=R); d.point((12, 13), fill=R)
    d.point((7, 14), fill=Y); d.point((8, 14), fill=R); d.point((3, 14), fill=Y); d.point((12, 14), fill=Y); d.point((8, 15), fill=Y)
    img.save(os.path.join(ITEM_T, "rocket.png"))
shuttle_icon()

# ------------------------------------------------------------------ sounds
SR = 44100
def write(name, x, gain=0.85):
    x = np.asarray(x, float); x = x / (np.max(np.abs(x)) + 1e-9) * gain
    fd, wav = tempfile.mkstemp(suffix=".wav"); os.close(fd)
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(SND, name + ".ogg")], check=True)
    os.remove(wav)
def t_(s): return np.arange(int(SR * s)) / SR

# alien chirps: short syllables with formant-like pitch glide ("chu", "chi", "zrr", "plip")
specs = [(0.11, 900, 1500, 0), (0.09, 1300, 800, 1), (0.14, 600, 1100, 2), (0.08, 1600, 1900, 0), (0.12, 1000, 700, 2), (0.10, 1200, 1400, 1)]
for i, (dur, f0, f1, kind) in enumerate(specs):
    t = t_(dur); k = t / dur
    f = f0 + (f1 - f0) * k
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.2 * np.sin(3.01 * ph)
    if kind == 1: s *= 1 + 0.6 * np.sin(2 * np.pi * 45 * t)            # warble
    if kind == 2: s = np.sign(s) * np.abs(s) ** 0.6 * (1 + 0.5 * np.sin(2 * np.pi * 70 * t))  # buzzy "zrr"
    noise = rng.standard_normal(len(t)) * 0.25 * np.exp(-t / 0.015)     # "ch" consonant
    env = np.clip(t / 0.008, 0, 1) * np.clip((dur - t) / 0.03, 0, 1)
    write(f"chirp{i + 1}", (s + noise) * env, 0.7)

# hydraulic door hiss + clunk
t = t_(1.4)
hiss = rng.standard_normal(len(t)) * np.exp(-((t - 0.5) / 0.35) ** 2)
from scipy.signal import butter, lfilter
b, a = butter(2, [2000 / (SR / 2), 8000 / (SR / 2)], 'band'); hiss = lfilter(b, a, hiss)
clunk = np.sin(2 * np.pi * 70 * t) * np.exp(-np.clip(t - 1.05, 0, None) / 0.08) * (t > 1.05)
write("door", hiss * 2 + clunk * 1.5)
# big red button
t = t_(0.6)
write("button", np.sin(2 * np.pi * 120 * t) * np.exp(-t / 0.08) + 0.4 * np.sin(2 * np.pi * 2400 * t) * np.exp(-t / 0.02))
# ship cannon charge: rising whine with sub pulse, 6 s
t = t_(6.0)
f = 60 + 900 * (t / 6) ** 2
s = np.sin(2 * np.pi * np.cumsum(f) / SR) + 0.5 * np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR)
s += np.sin(2 * np.pi * 40 * t) * (0.5 + 0.5 * np.sin(2 * np.pi * (1 + 6 * t / 6) * t))
write("ship_charge", s * np.clip(t / 0.5, 0, 1) * np.clip((6 - t) / 0.2, 0, 1))
# ship ambient hum (loop-ish) 3 s
t = t_(3.0)
write("ship_hum", (np.sin(2 * np.pi * 55 * t) + 0.5 * np.sin(2 * np.pi * 110.5 * t) + 0.2 * np.sin(2 * np.pi * 220 * t)) * np.clip(t / 0.3, 0, 1) * np.clip((3 - t) / 0.3, 0, 1), 0.5)
# whoosh fly-by
t = t_(1.5)
n = rng.standard_normal(len(t)); out = np.zeros_like(n)
for i in range(20):
    s0, e0 = int(i * len(n) / 20), int((i + 1) * len(n) / 20); fc = 400 + 3000 * np.sin(np.pi * i / 20)
    b, a = butter(2, [fc * 0.6 / (SR / 2), min(fc * 1.4, 20000) / (SR / 2)], 'band'); out[s0:e0] = lfilter(b, a, n)[s0:e0]
write("whoosh", out * np.sin(np.pi * t / 1.5))
print("v4 assets done")
