"""v5 cannon: black/grey sci-fi rifle with glowing purple panels, top scope, crystal magazine, fork muzzle.
Writes model JSONs + textures, and an isometric preview PNG (for checking)."""
import numpy as np, json, os, sys, math
from PIL import Image, ImageDraw

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
A = os.path.join(OUT, "assets/orbitalstrike")
IT = os.path.join(A, "textures/item"); MD = os.path.join(A, "models/item")
rng = np.random.default_rng(7)

def save_rgb(arr, name, mcmeta=None):
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB").save(os.path.join(IT, name + ".png"))
    p = os.path.join(IT, name + ".png.mcmeta")
    if mcmeta: open(p, "w").write(mcmeta)
    elif os.path.exists(p): os.remove(p)

def noise(base, var):
    return np.array(base, float)[None, None, :] * (1 + (rng.random((16, 16, 1)) - 0.5) * var)

blk = noise((26, 24, 30), 0.25)
blk[[0, 15], :] *= 0.7; blk[:, [0, 15]] *= 0.7; blk[5, 1:15] *= 1.35; blk[10, 1:15] *= 0.75
save_rgb(blk, "cannon_black")
gry = noise((150, 150, 158), 0.10)
gry[0, :] *= 1.15; gry[15, :] *= 0.75; gry[:, 0] *= 1.1; gry[:, 15] *= 0.8
save_rgb(gry, "cannon_grey")
yy, xx = np.mgrid[0:16, 0:16]
grip = np.where(((xx // 2 + yy // 2) % 2 == 0)[..., None], np.array([34, 33, 38]), np.array([70, 70, 76])).astype(float)
save_rgb(grip, "cannon_grip")

def glow_frames(hot):
    frames = []
    for f in range(8):
        k = 0.5 + 0.5 * math.sin(f * math.pi / 4)
        edge = np.minimum(np.minimum(xx, 15 - xx), np.minimum(yy, 15 - yy))
        inner = np.clip(edge / 3.0, 0, 1)
        base = np.array([150, 40, 225]) * (1 - inner[..., None]) + np.array([225, 160, 255]) * inner[..., None]
        base = base * (0.8 + 0.25 * k)
        if hot: base = base * 0.55 + np.array([255, 235, 255]) * 0.45
        frames.append(base)
    return np.concatenate(frames, 0)
save_rgb(glow_frames(False), "cannon_glow", '{"animation":{"frametime":3,"interpolate":true}}')
save_rgb(glow_frames(True), "cannon_glow_hot", '{"animation":{"frametime":1,"interpolate":true}}')
cr = np.zeros((16, 16, 3))
for i in range(16):
    for j in range(16):
        n = ((i * 7 + j * 3) % 5 + rng.random() * 2) / 6
        cr[i, j] = np.array([120, 40, 200]) * (0.6 + 0.6 * n) + (np.array([240, 200, 255]) * 0.5 if (i + 2 * j) % 9 == 0 else 0)
save_rgb(cr, "cannon_crystal")
save_rgb(noise((245, 235, 255), 0.04), "cannon_white")
# remove v4 textures
for n in ("cannon_dark", "cannon_mid", "cannon_gold", "cannon_lens", "cannon_core", "cannon_core_hot"):
    for ext in (".png", ".png.mcmeta"):
        p = os.path.join(IT, n + ext)
        if os.path.exists(p): os.remove(p)

SH = 1.0   # shift along x so the gun's middle sits near the rotation origin
P = []
def e(x0, y0, z0, x1, y1, z1, t, name=""):
    P.append((name, [x0 + SH, y0, z0], [x1 + SH, y1, z1], t))

# stock + rear rail box
e(-6, 6, 7, -1, 9, 9, "black", "stock")
e(-7, 5, 6.5, -6, 10, 9.5, "grey", "butt")
e(-6.2, 10, 7, 1, 12, 9, "black", "rail_box")
e(-5.6, 10.4, 6.9, 0.4, 11.6, 9.1, "glow", "rail_glow")
e(-6.8, 9.5, 6.5, -5.8, 12.6, 9.5, "grey", "rail_cap_r")
e(0.6, 9.5, 6.5, 1.6, 12.6, 9.5, "grey", "rail_cap_f")
# receiver with 3 glowing windows
e(-1, 6.5, 6.6, 9.5, 10.5, 9.4, "black", "receiver")
e(-1, 10.5, 7, 9.5, 11, 9, "grey", "top_plate")
for i, x in enumerate((1, 3.5, 6)):
    e(x, 7.4, 6.4, x + 2, 10, 9.6, "glow", f"window{i}")
# top scope
e(2, 11.5, 7.2, 8, 13.5, 8.8, "black", "scope")
e(2.4, 12, 7.0, 7.6, 13, 9.0, "glow", "scope_glow")
e(1.4, 11, 6.8, 2.4, 14, 9.2, "grey", "scope_cap_r")
e(7.6, 11, 6.8, 8.6, 14, 9.2, "grey", "scope_cap_f")
# magazine (crystal) + base, grip, trigger guard
e(4.5, 2.4, 7.0, 7.5, 6.5, 9.0, "crystal", "mag")
e(4.1, 1.4, 6.7, 7.9, 2.4, 9.3, "grey", "mag_base")
e(-0.6, 2.2, 7.3, 1.6, 6.5, 8.7, "grip", "grip")
e(1.6, 4.9, 7.6, 4.2, 5.4, 8.4, "grey", "guard")
# barrel, rails, coils
e(9.5, 7.6, 7.2, 17.5, 9.4, 8.8, "black", "barrel")
e(9.5, 6.8, 7.5, 15.5, 7.6, 8.5, "grey", "under_rail")
e(11, 5.7, 7.6, 15, 6.8, 8.4, "grey", "blade")
for i, x in enumerate((10.4, 12.4, 14.4)):
    e(x, 7.1, 6.8, x + 1, 9.9, 9.2, "glow", f"coil{i}")
e(9.2, 10.4, 7.4, 10.4, 11.6, 8.6, "white", "light")
e(8.6, 10.5, 7.7, 9.2, 12.2, 8.3, "grey", "sight")
# fork muzzle
e(17.5, 6.0, 6.5, 18.5, 11.0, 9.5, "grey", "muzzle_plate")
e(18.5, 10.0, 7.0, 20.5, 11.0, 9.0, "grey", "prong_top")
e(18.5, 6.0, 7.0, 20.5, 7.0, 9.0, "grey", "prong_bot")
e(18.0, 7.6, 7.3, 19.6, 9.4, 8.7, "glow", "muzzle_glow")

FULL = [0, 0, 16, 16]
def model(hot):
    tex = {"black": "orbitalstrike:item/cannon_black", "grey": "orbitalstrike:item/cannon_grey", "grip": "orbitalstrike:item/cannon_grip",
           "glow": "orbitalstrike:item/cannon_glow_hot" if hot else "orbitalstrike:item/cannon_glow",
           "crystal": "orbitalstrike:item/cannon_crystal", "white": "orbitalstrike:item/cannon_white", "particle": "orbitalstrike:item/cannon_black"}
    els = []
    for name, f, t, tx in P:
        els.append({"name": name, "from": f, "to": t,
                    "faces": {fc: {"uv": FULL, "texture": "#" + tx} for fc in ("north", "south", "east", "west", "up", "down")}})
    m = {"textures": tex, "elements": els, "gui_light": "front",
         "display": {
             "thirdperson_righthand": {"rotation": [0, -90, -90], "translation": [0, 3.0, 0.5], "scale": [0.62, 0.62, 0.62]},
             "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [1.5, 2.6, 0.5], "scale": [0.5, 0.5, 0.5]},
             "gui": {"rotation": [15, -25, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
             "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
             "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]}}}
    if not hot:
        m["overrides"] = [{"predicate": {"orbitalstrike:charge": 0.5}, "model": "orbitalstrike:item/orbital_cannon_charged"}]
    return m
json.dump(model(False), open(os.path.join(MD, "orbital_cannon.json"), "w"), indent=1)
json.dump(model(True), open(os.path.join(MD, "orbital_cannon_charged.json"), "w"), indent=1)

# ---------------------------------------------------------------- preview (painter's algorithm, un-rotated side/iso view)
if PREVIEW:
    COL = {"black": (30, 28, 36), "grey": (150, 150, 158), "glow": (205, 120, 255), "crystal": (140, 60, 210), "grip": (55, 55, 60), "white": (250, 240, 255)}
    S = 22
    yaw, pitch = math.radians(-35), math.radians(25)
    def proj(p):
        x, y, z = p[0] - 7, p[1] - 8, p[2] - 8
        x, z = x * math.cos(yaw) - z * math.sin(yaw), x * math.sin(yaw) + z * math.cos(yaw)
        y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
        return (700 + x * S, 400 - y * S, z)
    faces = []
    for name, f, t, tx in P:
        (x0, y0, z0), (x1, y1, z1) = f, t
        cs = {"n": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], "s": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
              "w": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], "e": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
              "u": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], "d": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]}
        shade = {"n": 0.8, "s": 0.8, "w": 0.65, "e": 0.65, "u": 1.0, "d": 0.5}
        for k, pts in cs.items():
            pp = [proj(p) for p in pts]
            depth = sum(p[2] for p in pp) / 4
            c = COL[tx]; sh = 1.0 if tx in ("glow", "white") else shade[k]
            faces.append((depth, [(p[0], p[1]) for p in pp], tuple(int(v * sh) for v in c)))
    img = Image.new("RGB", (1400, 800), (30, 14, 40)); d = ImageDraw.Draw(img)
    for depth, pts, c in sorted(faces, key=lambda f: -f[0]):
        d.polygon(pts, fill=c, outline=(10, 10, 12))
    img.save(PREVIEW)
print("gun v5 done")
