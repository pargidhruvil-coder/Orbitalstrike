"""Navagraha Cannon textures: planets, beam, ring, sigil, scope, portraits, animated gun sprite.
All procedural (original art)."""
import numpy as np
from PIL import Image, ImageDraw
import os, sys

OUT = sys.argv[1]
FX = os.path.join(OUT, "assets/orbitalstrike/textures/fx")
GUI = os.path.join(OUT, "assets/orbitalstrike/textures/gui")
ITEM = os.path.join(OUT, "assets/orbitalstrike/textures/item")
for d in (FX, GUI, ITEM): os.makedirs(d, exist_ok=True)
rng = np.random.default_rng(99)
PERM = rng.permutation(4096)

def _hash(ix, iy, iz):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)
    return PERM[h & 4095] / 4095.0

def vnoise(x, y, z):
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx*fx*(3-2*fx), fy*fy*(3-2*fy), fz*fz*(3-2*fz)
    L = lambda a, b, t: a + (b - a) * t
    c = {(a, b, e): _hash(ix+a, iy+b, iz+e) for a in (0, 1) for b in (0, 1) for e in (0, 1)}
    x00 = L(c[(0,0,0)], c[(1,0,0)], fx); x10 = L(c[(0,1,0)], c[(1,1,0)], fx)
    x01 = L(c[(0,0,1)], c[(1,0,1)], fx); x11 = L(c[(0,1,1)], c[(1,1,1)], fx)
    return L(L(x00, x10, fy), L(x01, x11, fy), fz)

def fbm(x, y, z, o=5):
    s, a, f, n = 0, 1, 1, 0
    for _ in range(o):
        s += a * vnoise(x*f, y*f, z*f); n += a; a *= 0.5; f *= 2
    return s / n

def save(arr, path):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8)).save(path, optimize=True)

def grid(N):
    c = (np.arange(N) + 0.5) / N * 2 - 1
    X, Y = np.meshgrid(c, c)
    return X, -Y

def sphere(N, R=0.9):
    X, Y = grid(N)
    r2 = (X**2 + Y**2) / (R*R)
    ins = r2 <= 1
    Z = np.sqrt(np.clip(1 - r2, 0, 1))
    return X, Y, ins, X / R, Y / R, Z, r2

def lit(nx, ny, nz, L=(-0.5, 0.4, 0.77)):
    L = np.array(L); L = L / np.linalg.norm(L)
    return np.clip(nx*L[0] + ny*L[1] + nz*L[2], 0, 1)

def rgba(col, a):
    return np.concatenate([np.clip(col, 0, 1), np.clip(a, 0, 1)[..., None]], -1)

def planet(name, base_fn, glow_col=None, N=256, R=0.8):
    X, Y, ins, nx, ny, nz, r2 = sphere(N, R)
    col = base_fn(nx, ny, nz)
    l = lit(nx, ny, nz)
    col = col * (0.12 + 0.88 * l)[..., None]
    col += (np.clip(r2, 0, 1) ** 6)[..., None] * (np.array(glow_col) if glow_col else np.array([0.3, 0.3, 0.35])) * 0.6
    Rr = np.sqrt(X**2 + Y**2)
    halo = np.exp(-((Rr - R) / 0.06) ** 2) * (~ins)
    a = np.where(ins, 1.0, halo * (0.9 if glow_col else 0.3))
    col = np.where(ins[..., None], col, np.array(glow_col) if glow_col else np.array([0.6, 0.6, 0.7]))
    save(rgba(col, a), os.path.join(FX, name + ".png"))

def bands(nx, ny, nz, cols, freq, turb):
    t = ny * freq + turb * (fbm(nx*3, ny*3, nz*3, 5) - 0.5)
    idx = (np.sin(t * np.pi) * 0.5 + 0.5) * (len(cols) - 1)
    i0 = np.floor(idx).astype(int); f = idx - i0; i1 = np.clip(i0 + 1, 0, len(cols) - 1)
    C = np.array(cols)
    return C[i0] * (1 - f[..., None]) + C[i1] * f[..., None]

planet("mercury", lambda x, y, z: (np.array([0.55, 0.5, 0.45]) * (0.6 + 0.6 * fbm(x*5, y*5, z*5, 6))[..., None]))
planet("venus", lambda x, y, z: bands(x, y, z, [[0.95, 0.85, 0.55], [0.9, 0.7, 0.4], [1.0, 0.92, 0.7]], 2.0, 3.0), [1.0, 0.85, 0.5])
planet("jupiter", lambda x, y, z: bands(x, y, z, [[0.85, 0.7, 0.55], [0.6, 0.38, 0.25], [0.95, 0.88, 0.78], [0.75, 0.5, 0.35]], 5.0, 1.6), [0.9, 0.7, 0.5])

# Saturn with rings
def make_saturn(N=384):
    X, Y = grid(N)
    R = 0.42
    r2 = (X**2 + Y**2) / R**2
    ins = r2 <= 1
    nx, ny = X / R, Y / R; nz = np.sqrt(np.clip(1 - r2, 0, 1))
    col = bands(nx, ny, nz, [[0.9, 0.8, 0.55], [0.75, 0.62, 0.4], [0.95, 0.9, 0.7]], 4.0, 1.0)
    col = col * (0.15 + 0.85 * lit(nx, ny, nz))[..., None]
    # tilted ring ellipse
    ry = Y / 0.32
    rr = np.sqrt(X**2 + ry**2)
    ring = ((rr > 0.55) & (rr < 0.95)).astype(float) * (0.55 + 0.45 * np.sin(rr * 90) ** 2)
    ring *= np.where((rr > 0.72) & (rr < 0.75), 0.15, 1.0)
    behind = ins & (Y > 0)                 # ring hidden behind planet top half
    rcol = np.array([0.95, 0.85, 0.65])
    a = np.where(ins, 1.0, 0.0)
    out = np.where(ins[..., None], col, 0)
    show_ring = (ring > 0) & ~(behind)
    out = np.where(show_ring[..., None], out * (1 - ring[..., None] * 0.9) + rcol * ring[..., None], out)
    a = np.maximum(a, np.where(show_ring, ring, 0))
    save(rgba(out, a), os.path.join(FX, "saturn.png"))
make_saturn()

# Rahu: dark shadow orb with violet corona
def make_rahu(N=256):
    X, Y, ins, nx, ny, nz, r2 = sphere(N, 0.6)
    Rr = np.sqrt(X**2 + Y**2)
    th = np.arctan2(Y, X)
    corona = np.exp(-(Rr - 0.6) / 0.18) * (0.6 + 0.4 * np.sin(th * 13 + fbm(X*4, Y*4, np.zeros_like(X)) * 6) ** 2) * (Rr > 0.6)
    col = np.where(ins[..., None], np.array([0.03, 0.0, 0.06]) + (np.clip(r2, 0, 1) ** 10)[..., None] * np.array([0.6, 0.2, 1.0]), np.array([0.55, 0.15, 1.0]))
    a = np.where(ins, 1.0, np.clip(corona, 0, 1))
    save(rgba(col, a), os.path.join(FX, "rahu.png"))
make_rahu()

# Ketu: red fiery orb with comet tail (tail toward bottom-right)
def make_ketu(N=256):
    X, Y = grid(N)
    cx, cy, R = -0.35, 0.35, 0.28
    dx, dy = X - cx, Y - cy
    r2 = (dx**2 + dy**2) / R**2
    ins = r2 <= 1
    nx, ny = dx / R, dy / R; nz = np.sqrt(np.clip(1 - r2, 0, 1))
    col = np.array([0.8, 0.15, 0.05]) * (0.5 + 0.8 * fbm(nx*4, ny*4, nz*4, 5))[..., None] * (0.3 + 0.7 * lit(nx, ny, nz))[..., None]
    # tail
    along = (dx - dy) / np.sqrt(2); across = (dx + dy) / np.sqrt(2)
    tail = np.where(along > 0, np.exp(-along / 0.7) * np.exp(-(across / (0.12 + along * 0.25)) ** 2), 0)
    tail *= 0.6 + 0.4 * fbm(X*8, Y*8, np.ones_like(X), 4)
    tcol = np.array([1.0, 0.45, 0.15])
    out = np.where(ins[..., None], col, tcol)
    a = np.where(ins, 1.0, np.clip(tail * 1.2, 0, 1))
    save(rgba(out, a), os.path.join(FX, "ketu.png"))
make_ketu()

# Beam: vertical texture, bright core across U
def make_beam():
    W, H = 64, 256
    u = (np.arange(W) + 0.5) / W
    v = (np.arange(H) + 0.5) / H
    U, V = np.meshgrid(u, v)
    core = np.exp(-((U - 0.5) / 0.12) ** 2)
    streak = 0.75 + 0.25 * np.sin(V * 40 + U * 6) * np.sin(V * 13)
    a = np.clip(core * streak, 0, 1)
    col = np.stack([np.ones_like(a), 0.9 + 0.1 * a, 0.7 + 0.3 * a], -1)
    save(rgba(col, a), os.path.join(FX, "beam.png"))
make_beam()

def make_ring(N=256):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2)
    a = np.exp(-((R - 0.85) / 0.06) ** 2) + 0.35 * np.exp(-((R - 0.7) / 0.1) ** 2)
    col = np.stack([np.ones_like(R), 0.85 * np.ones_like(R), 0.6 * np.ones_like(R)], -1)
    save(rgba(col, np.clip(a, 0, 1) * (R < 1)), os.path.join(FX, "ring.png"))
make_ring()

def make_glow(N=128):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2)
    a = np.clip(np.exp(-R / 0.25) * (1 - R), 0, 1)
    save(rgba(np.ones(X.shape + (3,)), a), os.path.join(FX, "glow.png"))
make_glow()

# Sigil: original geometric mandala (9-pointed star + rings + ticks)
def make_sigil(N=512):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = N / 2
    gold = (255, 210, 120, 255)
    for r, w in ((0.48, 6), (0.44, 3), (0.30, 4), (0.12, 3)):
        R = r * N
        d.ellipse([c - R, c - R, c + R, c + R], outline=gold, width=w)
    pts = []
    for i in range(9):
        a = -np.pi / 2 + i * 2 * np.pi / 9
        pts.append((c + np.cos(a) * 0.44 * N, c + np.sin(a) * 0.44 * N))
    for i in range(9):
        d.line([pts[i], pts[(i + 4) % 9]], fill=gold, width=4)
    for i in range(72):
        a = i * 2 * np.pi / 72
        r1, r2 = 0.445 * N, (0.48 if i % 8 == 0 else 0.465) * N
        d.line([(c + np.cos(a) * r1, c + np.sin(a) * r1), (c + np.cos(a) * r2, c + np.sin(a) * r2)], fill=gold, width=3)
    for i in range(9):
        a = -np.pi / 2 + i * 2 * np.pi / 9
        x, y = c + np.cos(a) * 0.37 * N, c + np.sin(a) * 0.37 * N
        d.ellipse([x - 14, y - 14, x + 14, y + 14], outline=gold, width=3)
        d.ellipse([x - 5, y - 5, x + 5, y + 5], fill=gold)
    img.save(os.path.join(FX, "sigil.png"), optimize=True)
make_sigil()

# Scope overlay (256x256): black outside circle, crosshair, range ticks
def make_scope(N=512):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    c = N / 2; R = N * 0.46
    d.ellipse([c - R, c - R, c + R, c + R], fill=(0, 0, 0, 0))
    X, Y = grid(N); Rr = np.sqrt(X**2 + Y**2)
    arr = np.array(img).astype(float) / 255
    vign = np.clip((Rr - 0.6) / 0.32, 0, 1) ** 2 * (Rr < 0.92)
    arr[..., 3] = np.maximum(arr[..., 3], vign * 0.8)
    img = Image.fromarray((arr * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    cyan = (80, 230, 255, 230)
    d.ellipse([c - R, c - R, c + R, c + R], outline=cyan, width=4)
    d.line([(c - R, c), (c - 18, c)], fill=cyan, width=2); d.line([(c + 18, c), (c + R, c)], fill=cyan, width=2)
    d.line([(c, c - R), (c, c - 18)], fill=cyan, width=2); d.line([(c, c + 18), (c, c + R)], fill=cyan, width=2)
    for i in range(1, 8):
        o = i * 24
        for s in (-1, 1):
            d.line([(c + s * o, c - 6), (c + s * o, c + 6)], fill=cyan, width=2)
            d.line([(c - 6, c + s * o), (c + 6, c + s * o)], fill=cyan, width=2)
    d.ellipse([c - 4, c - 4, c + 4, c + 4], outline=(255, 70, 70, 255), width=2)
    img.save(os.path.join(GUI, "scope.png"), optimize=True)
make_scope()

# Portraits 32x32 pixel art, upscaled in-game
def portrait(name, draw_fn):
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_fn(d)
    img.save(os.path.join(GUI, f"portrait_{name}.png"))

def p_vikram(d):
    d.rectangle([0, 0, 31, 31], fill=(18, 28, 60, 255))
    d.ellipse([3, 2, 28, 29], fill=(230, 230, 235, 255))           # helmet
    d.ellipse([6, 6, 25, 26], fill=(40, 70, 120, 255))             # visor ring
    d.ellipse([8, 8, 23, 25], fill=(150, 100, 70, 255))            # face
    d.rectangle([11, 13, 13, 14], fill=(30, 20, 20, 255)); d.rectangle([18, 13, 20, 14], fill=(30, 20, 20, 255))
    d.rectangle([10, 11, 14, 11], fill=(40, 25, 20, 255)); d.rectangle([17, 11, 21, 11], fill=(40, 25, 20, 255))
    d.rectangle([12, 19, 19, 20], fill=(40, 25, 20, 255))         # moustache
    d.rectangle([13, 22, 18, 22], fill=(110, 50, 40, 255))
    d.rectangle([4, 27, 27, 31], fill=(240, 120, 30, 255))          # orange suit
    d.rectangle([14, 28, 17, 30], fill=(255, 255, 255, 255))

def p_chintu(d):
    d.rectangle([0, 0, 31, 31], fill=(10, 40, 45, 255))
    d.rectangle([15, 1, 16, 5], fill=(180, 180, 190, 255)); d.ellipse([13, 0, 18, 4], fill=(255, 60, 60, 255))
    d.rounded_rectangle([5, 5, 26, 25], 5, fill=(110, 220, 230, 255))
    d.rounded_rectangle([8, 9, 23, 20], 3, fill=(20, 30, 40, 255))
    d.ellipse([10, 11, 14, 16], fill=(255, 255, 255, 255)); d.ellipse([17, 11, 21, 16], fill=(255, 255, 255, 255))
    d.rectangle([11, 13, 12, 14], fill=(0, 0, 0, 255)); d.rectangle([18, 13, 19, 14], fill=(0, 0, 0, 255))
    d.arc([12, 15, 19, 20], 200, 340, fill=(255, 200, 80, 255))
    d.rectangle([3, 12, 4, 17], fill=(80, 170, 180, 255)); d.rectangle([27, 12, 28, 17], fill=(80, 170, 180, 255))
    d.rectangle([9, 26, 22, 31], fill=(80, 170, 180, 255))
    d.ellipse([22, 6, 24, 9], fill=(180, 240, 255, 255))           # sweat drop

def p_system(d):
    d.rectangle([0, 0, 31, 31], fill=(8, 12, 20, 255))
    d.rectangle([3, 5, 28, 24], fill=(30, 50, 70, 255)); d.rectangle([5, 7, 26, 22], fill=(5, 20, 30, 255))
    for i, y in enumerate((10, 13, 16, 19)):
        d.rectangle([7, y, 7 + 4 + (i * 5) % 15, y], fill=(60, 255, 140, 255))
    d.rectangle([13, 25, 18, 27], fill=(30, 50, 70, 255)); d.rectangle([9, 28, 22, 29], fill=(30, 50, 70, 255))

def p_voice(d):
    d.rectangle([0, 0, 31, 31], fill=(5, 0, 12, 255))
    for r, c in ((14, (40, 10, 70, 255)), (10, (70, 20, 120, 255)), (6, (120, 50, 200, 255))):
        d.ellipse([16 - r, 16 - r, 15 + r, 15 + r], fill=c)
    d.rectangle([10, 14, 13, 15], fill=(255, 220, 120, 255)); d.rectangle([18, 14, 21, 15], fill=(255, 220, 120, 255))

def p_indra(d):
    d.rectangle([0, 0, 31, 31], fill=(20, 25, 55, 255))
    d.polygon([(6, 10), (9, 2), (12, 8), (16, 0), (20, 8), (23, 2), (26, 10)], fill=(255, 200, 40, 255))  # crown
    d.rectangle([7, 9, 25, 11], fill=(230, 170, 30, 255)); d.rectangle([15, 9, 16, 10], fill=(220, 30, 60, 255))
    d.ellipse([8, 10, 24, 27], fill=(235, 190, 150, 255))
    d.rectangle([11, 16, 14, 17], fill=(255, 255, 255, 255)); d.rectangle([18, 16, 21, 17], fill=(255, 255, 255, 255))
    d.rectangle([12, 16, 13, 17], fill=(40, 80, 200, 255)); d.rectangle([19, 16, 20, 17], fill=(40, 80, 200, 255))
    d.rectangle([11, 14, 14, 14], fill=(60, 40, 30, 255)); d.rectangle([18, 14, 21, 14], fill=(60, 40, 30, 255))
    d.polygon([(10, 22), (22, 22), (19, 31), (13, 31)], fill=(70, 50, 40, 255))   # beard
    d.rectangle([13, 21, 18, 21], fill=(150, 60, 50, 255))
    d.line([(28, 2), (25, 9), (29, 9), (25, 17)], fill=(150, 220, 255, 255), width=1)  # lightning
    d.line([(4, 3), (2, 8)], fill=(150, 220, 255, 255))

for n, f in (("vikram", p_vikram), ("chintu", p_chintu), ("system", p_system), ("voice", p_voice), ("indra", p_indra)):
    portrait(n, f)

# Keypad panel background
def make_keypad():
    img = Image.new("RGBA", (128, 160), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, 127, 159], 10, fill=(25, 30, 40, 240), outline=(80, 230, 255, 255), width=3)
    d.rectangle([12, 12, 115, 38], fill=(5, 15, 10, 255), outline=(60, 255, 140, 255))
    for r in range(4):
        for c in range(3):
            x, y = 14 + c * 34, 48 + r * 27
            d.rounded_rectangle([x, y, x + 30, y + 23], 4, fill=(55, 60, 75, 255), outline=(120, 130, 150, 255))
    img.save(os.path.join(GUI, "keypad.png"))
make_keypad()

# Animated 32x32 gun sprite (4 frames, glowing core cycles)
def make_gun():
    frames = []
    core_cols = [(160, 60, 255), (90, 140, 255), (60, 230, 255), (120, 100, 255)]
    for f in range(4):
        img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        dark, mid, light, gold = (30, 30, 42, 255), (70, 72, 92, 255), (140, 145, 170, 255), (240, 190, 60, 255)
        # barrel (diagonal from grip at bottom-left to muzzle top-right), drawn as thick line
        d.line([(9, 22), (28, 3)], fill=dark, width=6)
        d.line([(9, 21), (27, 3)], fill=mid, width=3)
        d.line([(10, 19), (26, 3)], fill=light, width=1)
        # gold rings
        for t in (0.35, 0.55, 0.75):
            x, y = 9 + t * 19, 22 - t * 19
            d.line([(x - 3, y - 3), (x + 3, y + 3)], fill=gold, width=2)
        # muzzle with planet orbs
        cc = core_cols[f]
        d.ellipse([25, 0, 31, 6], fill=cc + (255,)); d.ellipse([27, 2, 29, 4], fill=(255, 255, 255, 255))
        # body block
        d.polygon([(5, 20), (14, 11), (19, 16), (10, 25)], fill=dark)
        d.polygon([(7, 20), (14, 13), (17, 16), (10, 23)], fill=mid)
        # energy core
        d.ellipse([10, 15, 15, 20], fill=core_cols[(f + 1) % 4] + (255,))
        d.point((12, 17), fill=(255, 255, 255, 255))
        # scope
        d.line([(13, 9), (19, 3)], fill=dark, width=3); d.point((19, 3), fill=(255, 60, 60, 255))
        # grip & trigger
        d.polygon([(4, 23), (8, 23), (6, 31), (2, 31)], fill=dark)
        d.line([(4, 24), (3, 30)], fill=mid)
        d.arc([7, 23, 12, 28], 0, 180, fill=gold)
        # small orbiting planets glow
        ox = int(16 + 6 * np.cos(f * np.pi / 2)); oy = int(9 + 3 * np.sin(f * np.pi / 2))
        d.point((ox, oy), fill=(255, 220, 120, 255))
        frames.append(img)
    strip = Image.new("RGBA", (32, 128))
    for i, fr in enumerate(frames): strip.paste(fr, (0, i * 32))
    strip.save(os.path.join(ITEM, "orbital_cannon.png"))
    with open(os.path.join(ITEM, "orbital_cannon.png.mcmeta"), "w") as fh:
        fh.write('{"animation":{"frametime":3,"interpolate":true}}')
make_gun()
print("navagraha textures done")
