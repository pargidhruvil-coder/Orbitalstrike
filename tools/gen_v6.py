"""v6 assets: equirectangular planet maps (for real 3D spheres), Saturn ring, sci-fi interior panels, shock dome."""
import numpy as np, os, sys
from PIL import Image, ImageDraw

OUT = sys.argv[1]
FX = os.path.join(OUT, "assets/orbitalstrike/textures/fx")
os.makedirs(FX, exist_ok=True)
rng = np.random.default_rng(11)
PERM = rng.permutation(4096)

def _h(ix, iy, iz):
    return PERM[((ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)) & 4095] / 4095.0
def vnoise(x, y, z):
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx*fx*(3-2*fx), fy*fy*(3-2*fy), fz*fz*(3-2*fz)
    L = lambda a, b, t: a + (b - a) * t
    c = {(a, b, e): _h(ix+a, iy+b, iz+e) for a in (0, 1) for b in (0, 1) for e in (0, 1)}
    return L(L(L(c[0,0,0], c[1,0,0], fx), L(c[0,1,0], c[1,1,0], fx), fy), L(L(c[0,0,1], c[1,0,1], fx), L(c[0,1,1], c[1,1,1], fx), fy), fz)
def fbm(x, y, z, o=6):
    s, a, f, n = 0, 1, 1, 0
    for _ in range(o):
        s += a * vnoise(x*f, y*f, z*f); n += a; a *= 0.5; f *= 2
    return s / n
def save(arr, name):
    a = np.clip(arr * 255, 0, 255).astype(np.uint8)
    Image.fromarray(a).save(os.path.join(FX, name + ".png"), optimize=True)

W, H = 512, 256
u = (np.arange(W) + 0.5) / W; v = (np.arange(H) + 0.5) / H
U, V = np.meshgrid(u, v)
TH, PH = V * np.pi, U * 2 * np.pi          # same mapping as the Java sphere
NX, NY, NZ = np.sin(TH) * np.cos(PH), np.cos(TH), np.sin(TH) * np.sin(PH)

def earth():
    land = fbm(NX*2.2 + 5, NY*2.2, NZ*2.2, 7); det = fbm(NX*9, NY*9, NZ*9 + 3, 5)
    isl = land > 0.52
    ocean = np.array([0.03, 0.13, 0.38]) + (0.07 * det)[..., None]
    grn = np.where((land > 0.6)[..., None], np.array([0.47, 0.4, 0.26]), np.array([0.13, 0.36, 0.13])) * (0.75 + 0.5 * det)[..., None]
    col = np.where(isl[..., None], grn, ocean)
    polar = np.abs(NY) + 0.1 * (det - 0.5) > 0.86
    col = np.where(polar[..., None], np.array([0.93, 0.96, 1.0]), col)
    cl = np.clip((fbm(NX*3.5 + 20, NY*6, NZ*3.5, 7) - 0.47) * 3, 0, 1)
    save(col * (1 - cl[..., None]) + cl[..., None] * 0.97, "map_earth")
def moon():
    g = 0.6 + 0.25 * (fbm(NX*3, NY*3, NZ*3, 7) - 0.5) - 0.28 * np.clip((fbm(NX*1.4 + 9, NY*1.4, NZ*1.4, 4) - 0.5) * 4, 0, 1)
    cr = np.zeros_like(g)
    r2 = np.random.default_rng(3)
    for _ in range(160):
        d = r2.normal(size=3); d /= np.linalg.norm(d); rad = r2.uniform(0.03, 0.2) ** 1.3 * 1.6
        dd = np.arccos(np.clip(NX*d[0] + NY*d[1] + NZ*d[2], -1, 1)) / rad
        cr += np.where(dd < 1, -0.16 * (1 - dd**2), 0) + np.exp(-((dd - 1) / 0.12) ** 2) * 0.13
    save(np.repeat((g + cr)[..., None], 3, -1), "map_moon")
def mars():
    t = fbm(NX*2.5, NY*2.5 + 4, NZ*2.5, 7); d = fbm(NX*8 + 3, NY*8, NZ*8, 6)
    col = np.array([0.6, 0.18, 0.06]) * (0.6 + 0.8 * t)[..., None] + np.array([0.35, 0.18, 0.06]) * np.clip(d - 0.45, 0, 1)[..., None] * 2
    lava = np.clip(np.exp(-((fbm(NX*4 + 30, NY*4, NZ*4, 5) - 0.5) / 0.02) ** 2) + 0.6 * np.exp(-((fbm(NX*7, NY*7 + 30, NZ*7, 4) - 0.5) / 0.022) ** 2), 0, 1)
    save(col + lava[..., None] * np.array([1.0, 0.55, 0.12]), "map_mars")
def bands(cols, freq, turb):
    t = NY * freq + turb * (fbm(NX*3, NY*3, NZ*3, 5) - 0.5)
    idx = (np.sin(t * np.pi) * 0.5 + 0.5) * (len(cols) - 1)
    i0 = np.floor(idx).astype(int); f = idx - i0; i1 = np.clip(i0 + 1, 0, len(cols) - 1)
    C = np.array(cols); return C[i0] * (1 - f[..., None]) + C[i1] * f[..., None]
def jupiter():
    col = bands([[0.85, 0.7, 0.55], [0.6, 0.38, 0.25], [0.95, 0.88, 0.78], [0.75, 0.5, 0.35]], 5, 1.6)
    spot = np.exp(-(((PH - 4.0) / 0.35) ** 2 + ((TH - 1.95) / 0.16) ** 2))
    save(col * (1 - spot[..., None] * 0.5) + spot[..., None] * np.array([0.75, 0.3, 0.15]), "map_jupiter")
def simple(name, base, var, freq):
    save(np.array(base) * (0.6 + 0.8 * fbm(NX*freq, NY*freq, NZ*freq, 6))[..., None], name)
def rahu():
    vein = np.exp(-((fbm(NX*4, NY*4, NZ*4, 5) - 0.5) / 0.025) ** 2)
    save(np.array([0.05, 0.0, 0.09]) + vein[..., None] * np.array([0.7, 0.25, 1.0]), "map_rahu")
def ketu():
    f = fbm(NX*5, NY*5, NZ*5, 6)
    save(np.array([0.7, 0.12, 0.04]) * (0.4 + f)[..., None] + np.clip(f - 0.62, 0, 1)[..., None] * 4 * np.array([1, 0.7, 0.2]), "map_ketu")
def sun():
    f = fbm(NX*6, NY*6, NZ*6, 6)
    save(np.stack([np.ones_like(f), 0.72 + 0.25 * f, 0.25 + 0.3 * f], -1), "map_sun")

earth(); moon(); mars(); jupiter(); rahu(); ketu(); sun()
simple("map_mercury", [0.55, 0.5, 0.45], 0, 5)
save(bands([[0.95, 0.85, 0.55], [0.9, 0.72, 0.42], [1.0, 0.93, 0.72]], 2, 3), "map_venus")
save(bands([[0.9, 0.8, 0.55], [0.75, 0.62, 0.4], [0.95, 0.9, 0.7]], 4, 1.0), "map_saturn")

# Saturn ring (annulus, transparent outside)
N = 512
c = (np.arange(N) + 0.5) / N * 2 - 1
X, Y = np.meshgrid(c, c); R = np.sqrt(X**2 + Y**2)
ring = ((R > 0.55) & (R < 0.98)).astype(float) * (0.5 + 0.5 * np.sin(R * 95) ** 2)
ring *= np.where((R > 0.74) & (R < 0.77), 0.1, 1.0)
save(np.concatenate([np.stack([0.95 * np.ones_like(R), 0.86 * np.ones_like(R), 0.66 * np.ones_like(R)], -1), ring[..., None] * 0.9], -1), "saturn_ring")

# Shock dome: soft horizontal bands
d = np.zeros((128, 128, 4)); yy = np.arange(128)[:, None] / 128
d[..., :3] = 1; d[..., 3] = 0.35 + 0.35 * np.sin(yy * 40) ** 2 * np.ones((1, 128))
save(d, "dome")

# ---------------------------------------------------------------- interior sci-fi panels (64x64, tile)
def panel_wall():
    img = Image.new("RGB", (64, 64), (38, 42, 52)); dr = ImageDraw.Draw(img)
    for (x0, y0, x1, y1) in ((1, 1, 30, 30), (33, 1, 62, 30), (1, 33, 30, 62), (33, 33, 62, 62)):
        dr.rectangle([x0, y0, x1, y1], fill=(52, 57, 70), outline=(24, 27, 34))
        dr.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=(74, 80, 96))
    dr.rectangle([5, 6, 24, 15], fill=(8, 20, 26), outline=(0, 0, 0))
    for i, h in enumerate((3, 6, 4, 8, 5, 7)): dr.rectangle([7 + i * 3, 14 - h, 8 + i * 3, 14], fill=[(60, 220, 255), (255, 200, 60), (90, 255, 120)][i % 3])
    for i in range(5): dr.rectangle([37 + i * 5, 6, 39 + i * 5, 8], fill=[(255, 70, 70), (70, 255, 120), (80, 170, 255)][i % 3])
    for i in range(4): dr.line([(37, 14 + i * 3), (58, 14 + i * 3)], fill=(30, 34, 42))
    dr.rectangle([36, 38, 59, 57], fill=(30, 33, 42)); dr.text((39, 41), "Z7", fill=(230, 190, 60))
    dr.rectangle([5, 40, 26, 44], fill=(0, 200, 230)); dr.rectangle([5, 50, 18, 52], fill=(200, 100, 255))
    dr.line([(0, 31), (63, 31)], fill=(0, 190, 220)); dr.line([(0, 32), (63, 32)], fill=(0, 120, 150))
    img.save(os.path.join(FX, "panel_wall.png"))
def panel_floor():
    img = Image.new("RGB", (64, 64), (22, 25, 31)); dr = ImageDraw.Draw(img)
    for i in range(0, 64, 4):
        for j in range(0, 64, 4):
            if (i + j) % 8 == 0: dr.point((i + 1, j + 1), fill=(40, 44, 52))
    dr.line([(0, 0), (63, 0)], fill=(0, 170, 210)); dr.line([(0, 0), (0, 63)], fill=(0, 170, 210))
    dr.line([(0, 32), (63, 32)], fill=(0, 90, 110)); dr.line([(32, 0), (32, 63)], fill=(0, 90, 110))
    img.save(os.path.join(FX, "panel_floor.png"))
def panel_ceiling():
    img = Image.new("RGB", (64, 64), (30, 33, 40)); dr = ImageDraw.Draw(img)
    dr.rectangle([4, 26, 59, 37], fill=(230, 245, 255), outline=(120, 140, 160))
    dr.rectangle([0, 0, 63, 2], fill=(20, 22, 28)); dr.rectangle([0, 61, 63, 63], fill=(20, 22, 28))
    img.save(os.path.join(FX, "panel_ceiling.png"))
def screen():
    img = Image.new("RGB", (64, 32), (4, 16, 22)); dr = ImageDraw.Draw(img)
    pts = [(i * 4, 22 - int(8 * np.sin(i * 0.7)) - i % 3) for i in range(17)]
    dr.line(pts, fill=(60, 255, 160), width=1)
    for i in range(8): dr.rectangle([4 + i * 7, 28 - (i * 5) % 12, 8 + i * 7, 29], fill=(60, 200, 255))
    dr.rectangle([0, 0, 63, 31], outline=(60, 200, 255))
    dr.ellipse([46, 3, 60, 17], outline=(255, 210, 80)); dr.line([(53, 10), (58, 5)], fill=(255, 210, 80))
    img.save(os.path.join(FX, "panel_screen.png"))
def hazard():
    img = Image.new("RGB", (32, 8), (20, 20, 20)); dr = ImageDraw.Draw(img)
    for i in range(-8, 40, 8): dr.polygon([(i, 8), (i + 4, 0), (i + 8, 0), (i + 4, 8)], fill=(240, 190, 30))
    img.save(os.path.join(FX, "panel_hazard.png"))
panel_wall(); panel_floor(); panel_ceiling(); screen(); hazard()
print("v6 assets done")
