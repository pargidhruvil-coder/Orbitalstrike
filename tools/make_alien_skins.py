"""Zoro & Zamzam: black alien with white spiky edges (from the user's reference picture). Zoro = green eyes, Zamzam = orange eyes.
Also writes eyes-only textures for the glow layer."""
import sys, os, numpy as np
from PIL import Image
OUT = sys.argv[1]
BOX = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
       "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4)}
def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}
W1, W2 = (238, 238, 242, 255), (196, 198, 206, 255)

def make(name, eye_hi, eye_lo, seed):
    rng = np.random.default_rng(seed)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); px = img.load()
    for part, b in BOX.items():
        for fname, (x, y, w, h) in faces(*b).items():
            for i in range(w):
                for j in range(h):
                    c = int(rng.choice([12, 18, 24, 30, 38], p=[0.3, 0.3, 0.2, 0.12, 0.08]))
                    px[x + i, y + j] = (c, c, c + 3, 255)
            if fname in ("front", "back") and part != "head":
                # thin white spiky outline: only the outer edge, zig-zag every 2 pixels
                for j in range(h):
                    if (j // 2) % 2 == 0:
                        px[x, y + j] = W1
                        px[x + w - 1, y + j] = W2
            if fname in ("left", "right") and part in ("rarm", "larm", "rleg", "lleg") and h > 4:
                for j in range(0, h, 3): px[x + (j // 3) % w, y + j] = W2
            if fname == "top" and part in ("rarm", "larm"):
                for i in range(w):
                    for j in range(h): px[x + i, y + j] = W1 if (i + j) % 2 == 0 else W2   # white shoulders
    # head: white spiky crown on all sides, slanted glowing eyes on the front
    hx, hy = 8, 8
    for (fx, fy) in ((0, 8), (8, 8), (16, 8), (24, 8)):
        for i in range(8):
            px[fx + i, fy] = W1
            if i % 2 == 0: px[fx + i, fy + 1] = W2
        px[fx, fy + 2] = W1; px[fx + 7, fy + 2] = W1; px[fx, fy + 3] = W2; px[fx + 7, fy + 3] = W2
    for i in range(8):
        for j in range(8):
            if (i in (0, 7) or j in (0, 7)) and (i + j) % 2 == 0: px[8 + i, 0 + j] = W1   # top of head spikes
    eyes = [(1, 4, eye_lo), (2, 4, eye_hi), (3, 4, eye_hi), (2, 5, eye_lo), (3, 5, eye_hi),
            (6, 4, eye_lo), (5, 4, eye_hi), (4, 4, eye_hi), (5, 5, eye_lo), (4, 5, eye_hi)]
    for (ex, ey, col) in eyes: px[hx + ex, hy + ey] = col
    for i in range(2, 6): px[hx + i, hy + 7] = (50, 50, 56, 255)
    # chest: white claw mark
    for j in range(5): px[21 + (j // 2), 22 + j] = W1; px[25 - (j // 2), 22 + j] = W1
    img.save(os.path.join(OUT, f"alien_{name}.png"))
    e = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for (ex, ey, col) in eyes: e.putpixel((hx + ex, hy + ey), col)
    e.save(os.path.join(OUT, f"alien_{name}_eyes.png"))

make("zoro", (170, 255, 90, 255), (90, 200, 40, 255), 3)
make("zamzam", (255, 190, 60, 255), (230, 110, 20, 255), 4)
print("alien skins done")
