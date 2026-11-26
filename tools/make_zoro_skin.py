"""Rebuild Zoro's 64x64 skin from the user's reference (black body, purple eyes, white VIP, white wrist cuffs)."""
import sys, numpy as np
from PIL import Image
rng = np.random.default_rng(21)
img = Image.new("RGBA", (64, 64), (0, 0, 0, 0)); px = img.load()
BOX = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rarm": (40, 16, 4, 12, 4), "larm": (32, 48, 4, 12, 4),
       "rleg": (0, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4)}
def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}
for part, b in BOX.items():
    for name, (x, y, w, h) in faces(*b).items():
        for i in range(w):
            for j in range(h):
                c = int(rng.choice([10, 16, 22, 28, 36, 44], p=[0.25, 0.25, 0.2, 0.15, 0.1, 0.05]))
                px[x + i, y + j] = (c, c, c + 2, 255)
        if part in ("rarm", "larm") and name in ("front", "back", "left", "right"):
            for i in range(w):                       # white wrist cuffs
                px[x + i, y + h - 2] = (235, 235, 238, 255); px[x + i, y + h - 1] = (215, 218, 222, 255)
W = (245, 245, 245, 255)
# eyes (head front = 8..15, 8..15)
px[9, 12] = (110, 40, 220, 255); px[10, 12] = (175, 110, 255, 255); px[11, 12] = W
px[12, 12] = W; px[13, 12] = (175, 110, 255, 255); px[14, 12] = (110, 40, 220, 255)
# VIP on chest (body front = 20..27, 20..31)
bx, by = 20, 23
for (dx, dy) in [(0, 0), (2, 0), (0, 1), (2, 1), (1, 2), (4, 0), (4, 1), (4, 2), (6, 0), (7, 0), (6, 1), (7, 1), (6, 2)]:
    px[bx + dx, by + dy] = W
img.save(sys.argv[1])
