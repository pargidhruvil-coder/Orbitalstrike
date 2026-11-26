"""v8: holographic radar + sweep textures (original)."""
import numpy as np, os, sys, math
from PIL import Image, ImageDraw
FX = os.path.join(sys.argv[1], "assets/orbitalstrike/textures/fx")
N = 256
img = Image.new("RGBA", (N, N), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
c = N / 2; G = (80, 255, 150, 255); G2 = (40, 180, 110, 200)
d.rectangle([2, 2, N - 3, N - 3], outline=G2, width=3)
for r in (30, 60, 90, 118): d.ellipse([c - r, c - r, c + r, c + r], outline=G2, width=2)
d.line([(c, 8), (c, N - 8)], fill=G2, width=1); d.line([(8, c), (N - 8, c)], fill=G2, width=1)
for (rx, ry, rot) in ((100, 40, 20), (70, 30, -35), (110, 55, 60)):
    pts = []
    for i in range(80):
        a = i / 79 * 2 * math.pi
        x, y = rx * math.cos(a), ry * math.sin(a)
        t = math.radians(rot)
        pts.append((c + x * math.cos(t) - y * math.sin(t), c + x * math.sin(t) + y * math.cos(t)))
    d.line(pts, fill=(120, 220, 255, 220), width=2)
for (x, y, col) in ((c + 70, c - 30, (255, 200, 60, 255)), (c - 50, c + 45, (255, 80, 80, 255)), (c + 20, c + 80, (120, 220, 255, 255)), (c, c, (255, 255, 255, 255))):
    d.ellipse([x - 6, y - 6, x + 6, y + 6], fill=col)
for i in range(10):
    h = [12, 30, 18, 40, 25, 50, 22, 35, 15, 28][i]
    d.rectangle([12 + i * 8, N - 14 - h, 17 + i * 8, N - 14], fill=G)
img.save(os.path.join(FX, "holo_radar.png"))
sw = np.zeros((N, N, 4))
yy, xx = np.mgrid[0:N, 0:N]; dx, dy = xx - c, yy - c; R = np.sqrt(dx**2 + dy**2); A = np.arctan2(dy, dx)
wedge = np.clip(1 - ((A % (2 * np.pi)) / 0.9), 0, 1) * (R < 118)
sw[..., 0] = 0.4; sw[..., 1] = 1.0; sw[..., 2] = 0.6; sw[..., 3] = wedge * 0.8
Image.fromarray((sw * 255).astype(np.uint8)).save(os.path.join(FX, "holo_sweep.png"))
print("v8 done")
