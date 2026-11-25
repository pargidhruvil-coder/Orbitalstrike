"""Procedural space textures for the Orbital Strike: Galaxy mod.
Everything is generated from math (no external images)."""
import numpy as np
from PIL import Image
import os, sys

OUT = sys.argv[1]
ENV = os.path.join(OUT, "assets/orbitalstrike/textures/environment")
ITEM = os.path.join(OUT, "assets/orbitalstrike/textures/item")
os.makedirs(ENV, exist_ok=True); os.makedirs(ITEM, exist_ok=True)
rng = np.random.default_rng(1234)

# ---------------- 3D value noise ----------------
PERM = rng.permutation(4096)
def _hash(ix, iy, iz):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791)
    return PERM[h & 4095] / 4095.0

def vnoise(x, y, z):
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx, fy, fz = fx*fx*(3-2*fx), fy*fy*(3-2*fy), fz*fz*(3-2*fz)
    def L(a, b, t): return a + (b - a) * t
    c = {}
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                c[(dx, dy, dz)] = _hash(ix+dx, iy+dy, iz+dz)
    x00 = L(c[(0,0,0)], c[(1,0,0)], fx); x10 = L(c[(0,1,0)], c[(1,1,0)], fx)
    x01 = L(c[(0,0,1)], c[(1,0,1)], fx); x11 = L(c[(0,1,1)], c[(1,1,1)], fx)
    return L(L(x00, x10, fy), L(x01, x11, fy), fz)

def fbm(x, y, z, oct=5, lac=2.0, gain=0.5):
    s, a, f, n = 0, 1, 1, 0
    for _ in range(oct):
        s += a * vnoise(x*f, y*f, z*f); n += a; a *= gain; f *= lac
    return s / n

def stars3d(d, K, size, seed):
    """Round stars on a 3D lattice -> seamless across cube faces."""
    p = d * K
    cell = np.floor(p)
    ix, iy, iz = cell[..., 0].astype(np.int64), cell[..., 1].astype(np.int64), cell[..., 2].astype(np.int64)
    jx = _hash(ix + seed, iy, iz); jy = _hash(ix, iy + seed, iz); jz = _hash(ix, iy, iz + seed)
    star = cell + np.stack([jx, jy, jz], -1) * 0.8 + 0.1
    dist2 = ((p - star) ** 2).sum(-1)
    bright = _hash(ix, iy + 7*seed, iz + 3)
    present = bright > 0.55
    col_t = _hash(ix + 11*seed, iy, iz + 5)
    inten = np.where(present, np.exp(-dist2 / (size*size)) * (bright - 0.55) / 0.45, 0)
    return inten, col_t

def save(arr, path):
    Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8)).save(path, optimize=True)

def star_color(t):
    # blue-white ... white ... yellow ... orange
    cols = np.array([[0.65, 0.75, 1.0], [1, 1, 1], [1, 0.92, 0.75], [1, 0.7, 0.45]])
    idx = np.clip((t * 3.999).astype(int), 0, 3)
    return cols[idx]

# ---------------- Skybox (6 faces, matches renderEndSky rotations) ----------------
def rotx(v, deg):
    a = np.radians(deg); x, y, z = v[..., 0], v[..., 1], v[..., 2]
    return np.stack([x, y*np.cos(a) - z*np.sin(a), y*np.sin(a) + z*np.cos(a)], -1)
def rotz(v, deg):
    a = np.radians(deg); x, y, z = v[..., 0], v[..., 1], v[..., 2]
    return np.stack([x*np.cos(a) - y*np.sin(a), x*np.sin(a) + y*np.cos(a), z], -1)
FACE_ROT = [lambda v: v, lambda v: rotx(v, 90), lambda v: rotx(v, -90),
            lambda v: rotx(v, 180), lambda v: rotz(v, 90), lambda v: rotz(v, -90)]

def sky_color(d):
    n = np.array([0.35, 0.85, 0.4]); n /= np.linalg.norm(n)
    lat = np.arcsin(np.clip((d * n).sum(-1), -1, 1))
    core_dir = np.array([0.9, -0.2, -0.38]); core_dir -= n * core_dir.dot(n); core_dir /= np.linalg.norm(core_dir)
    ang = np.arccos(np.clip((d * core_dir).sum(-1), -1, 1))
    x, y, z = d[..., 0], d[..., 1], d[..., 2]
    big = fbm(x*2.2 + 10, y*2.2, z*2.2, 6)
    mid = fbm(x*5 + 3, y*5 + 7, z*5, 6)
    band_w = 0.16 + 0.08 * big
    band = np.exp(-(lat / band_w) ** 2) * (0.35 + 0.9 * mid ** 1.5)
    bulge = np.exp(-(ang / 0.55) ** 2) * np.exp(-(lat / 0.3) ** 2)
    dust = np.clip(fbm(x*9, y*9 + 2, z*9, 5) - 0.42, 0, 1) * 2.5 * np.exp(-(lat / 0.07) ** 2)
    col = np.zeros(d.shape)
    col += band[..., None] * np.array([0.42, 0.45, 0.75]) * 0.55
    col += bulge[..., None] * np.array([1.0, 0.78, 0.55]) * 0.9
    # nebula clouds: purple / pink / teal (like the reference pictures)
    nb1 = np.clip(fbm(x*1.6 + 40, y*1.6, z*1.6, 6) - 0.45, 0, 1) ** 1.6 * 3.0
    nb2 = np.clip(fbm(x*2.1, y*2.1 + 55, z*2.1, 6) - 0.5, 0, 1) ** 1.6 * 3.5
    nb3 = np.clip(fbm(x*2.8 + 9, y*2.8, z*2.8 + 70, 6) - 0.52, 0, 1) ** 1.5 * 3.0
    col += nb1[..., None] * np.array([0.45, 0.18, 0.75])
    col += nb2[..., None] * np.array([0.85, 0.3, 0.55])
    col += nb3[..., None] * np.array([0.1, 0.45, 0.7])
    col *= (1 - np.clip(dust, 0, 0.85))[..., None]
    col += np.array([0.012, 0.012, 0.03])
    # stars
    for K, size, seed, gain, dens in [(220, 0.16, 1, 1.0, 1.0), (90, 0.12, 2, 1.6, 1.0), (40, 0.09, 3, 2.5, 1.0), (520, 0.2, 4, 0.7, None)]:
        inten, ct = stars3d(d, K, size, seed)
        if dens is None:  # extra faint stars mostly inside the band
            inten = inten * np.clip(band * 1.8 + bulge, 0, 1)
        col += inten[..., None] * star_color(ct) * gain
    return col

def make_skybox(N=1024):
    u = (np.arange(N) + 0.5) / N
    U, V = np.meshgrid(u, u)   # V = row (texture v)
    local = np.stack([2*U - 1, -np.ones_like(U), 2*V - 1], -1)
    for i in range(6):
        d = FACE_ROT[i](local)
        d = d / np.linalg.norm(d, axis=-1, keepdims=True)
        col = sky_color(d)
        col = 1 - np.exp(-col * 1.3)  # tone map
        save(col ** 0.9, os.path.join(ENV, f"galaxy_{i}.png"))
        print("face", i)

# ---------------- helpers for discs ----------------
def grid(N):
    c = (np.arange(N) + 0.5) / N * 2 - 1
    X, Y = np.meshgrid(c, c)
    return X, -Y

def rgba(col, a):
    return np.concatenate([np.clip(col, 0, 1), np.clip(a, 0, 1)[..., None]], -1)

def sphere(N, R=0.92):
    X, Y = grid(N)
    r2 = (X**2 + Y**2) / (R*R)
    inside = r2 <= 1
    Z = np.sqrt(np.clip(1 - r2, 0, 1))
    nx, ny, nz = X / R, Y / R, Z
    return X, Y, inside, nx, ny, nz, r2

def light(nx, ny, nz, L=(-0.6, 0.35, 0.72)):
    L = np.array(L); L /= np.linalg.norm(L)
    return np.clip(nx*L[0] + ny*L[1] + nz*L[2], 0, 1)

# ---------------- Earth ----------------
def make_earth(N=512):
    X, Y, ins, nx, ny, nz, r2 = sphere(N)
    land = fbm(nx*2.2 + 5, ny*2.2, nz*2.2, 7)
    detail = fbm(nx*9, ny*9, nz*9 + 3, 5)
    is_land = land > 0.52
    ocean = np.array([0.03, 0.12, 0.35]) + (0.06 * detail)[..., None]
    lnd = np.where((land > 0.6)[..., None], np.array([0.45, 0.38, 0.25]), np.array([0.12, 0.33, 0.12]))
    lnd = lnd * (0.75 + 0.5 * detail)[..., None]
    polar = np.abs(ny) + 0.12 * (detail - 0.5) + 0.05 * (land - 0.5) > 0.86
    col = np.where(is_land[..., None], lnd, ocean)
    col = np.where(polar[..., None], np.array([0.92, 0.95, 1.0]), col)
    clouds = np.clip((fbm(nx*3.5 + 20, ny*6, nz*3.5, 7) - 0.48) * 3.0, 0, 1)
    col = col * (1 - clouds[..., None]) + clouds[..., None] * 0.97
    lit = light(nx, ny, nz)
    shade = 0.03 + 0.97 * lit ** 0.8
    col = col * shade[..., None]
    # city lights on night side
    night = np.clip(0.25 - lit, 0, 0.25) * 4
    cities = (detail > 0.62) & is_land & ~polar
    col += (night * cities * (1 - clouds))[..., None] * np.array([1.0, 0.75, 0.35]) * 0.8
    # atmosphere rim
    rim = np.clip(r2, 0, 1) ** 6
    col += rim[..., None] * np.array([0.25, 0.5, 1.0]) * (0.25 + lit)[..., None]
    halo = np.exp(-((np.sqrt(X**2 + Y**2) - 0.92) / 0.035) ** 2) * (~ins)
    a = np.where(ins, 1.0, halo * 0.8)
    col = np.where(ins[..., None], col, np.array([0.35, 0.6, 1.0]) * 1.0)
    save(rgba(col, a), os.path.join(ENV, "earth.png"))

# ---------------- Moon ----------------
def make_moon(N=512):
    X, Y, ins, nx, ny, nz, r2 = sphere(N)
    base = fbm(nx*3 + 1, ny*3, nz*3, 7)
    maria = np.clip((fbm(nx*1.4 + 9, ny*1.4, nz*1.4, 4) - 0.5) * 4, 0, 1)
    g = 0.62 + 0.25 * (base - 0.5) - 0.28 * maria
    craters = np.zeros_like(X)
    crng = np.random.default_rng(7)
    for _ in range(140):
        v = crng.normal(size=3); v /= np.linalg.norm(v)
        rad = crng.uniform(0.02, 0.12) ** 1.3 * 1.6
        d = np.arccos(np.clip(nx*v[0] + ny*v[1] + nz*v[2], -1, 1)) / rad
        craters += np.where(d < 1, -0.18 * (1 - d**2), 0) + np.exp(-((d - 1) / 0.12) ** 2) * 0.15
    # one big rayed crater
    v = np.array([-0.2, -0.55, 0.81]); v /= np.linalg.norm(v)
    d = np.arccos(np.clip(nx*v[0] + ny*v[1] + nz*v[2], -1, 1))
    ang = np.arctan2(ny - v[1], nx - v[0])
    rays = np.clip(np.sin(ang * 23) ** 16, 0, 1) * np.exp(-d / 0.45) * 0.1
    g = g + craters * 0.8 + rays
    lit = light(nx, ny, nz, (-0.3, 0.2, 0.93))
    col = (g * (0.05 + 0.95 * lit))[..., None] * np.array([1, 1, 1.02])
    save(rgba(col, ins.astype(float)), os.path.join(ENV, "moon.png"))

# ---------------- Mars (lava, burning) ----------------
def make_mars(N=512):
    X, Y, ins, nx, ny, nz, r2 = sphere(N)
    t = fbm(nx*2.5, ny*2.5 + 4, nz*2.5, 7)
    d = fbm(nx*8 + 3, ny*8, nz*8, 6)
    col = np.array([0.55, 0.16, 0.05]) * (0.6 + 0.8 * t)[..., None]
    col += np.array([0.35, 0.18, 0.06]) * np.clip(d - 0.45, 0, 1)[..., None] * 2
    cracks = np.exp(-((fbm(nx*4 + 30, ny*4, nz*4, 5) - 0.5) / 0.018) ** 2)
    cracks2 = np.exp(-((fbm(nx*7, ny*7 + 30, nz*7, 4) - 0.5) / 0.02) ** 2) * 0.6
    lava = np.clip(cracks + cracks2, 0, 1)
    lit = light(nx, ny, nz, (0.6, 0.5, 0.62))
    col = col * (0.08 + 0.92 * lit)[..., None]
    col += lava[..., None] * np.array([1.0, 0.55, 0.12]) * (0.6 + 0.6 * lit)[..., None]
    rim = np.clip(r2, 0, 1) ** 5
    col += rim[..., None] * np.array([1.0, 0.45, 0.1]) * 0.7
    R = np.sqrt(X**2 + Y**2)
    glow = np.exp(-((R - 0.92) / 0.06) ** 2) * (~ins)
    a = np.where(ins, 1.0, glow * 0.9)
    col = np.where(ins[..., None], col, np.array([1.0, 0.45, 0.1]))
    save(rgba(col, a), os.path.join(ENV, "mars.png"))

# ---------------- Sun ----------------
def make_sun(N=256):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2)
    core = np.clip(1 - R / 0.25, 0, 1) ** 0.5
    glow = np.exp(-R / 0.22) * 0.9
    ang = np.arctan2(Y, X)
    rays = (np.abs(np.cos(ang * 2)) ** 60 + np.abs(np.cos(ang * 2 + 0.785)) ** 120 * 0.5) * np.exp(-R / 0.5) * 0.8
    a = np.clip(core + glow + rays, 0, 1) * np.clip(1 - R, 0, 1)
    col = np.stack([np.ones_like(R), 0.9 - 0.2*R, 0.7 - 0.4*R], -1)
    save(rgba(col, a), os.path.join(ENV, "sun.png"))

# ---------------- Spiral galaxy (face-on, rendered tilted in-game) ----------------
def make_spiral(N=768):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2) + 1e-6
    th = np.arctan2(Y, X)
    arms = 2
    k = 4.2
    phase = th * arms - np.log(R) * k * arms / 2
    arm = (0.5 + 0.5 * np.cos(phase)) ** 2
    n = fbm(X*6 + 3, Y*6, np.zeros_like(X), 6)
    n2 = fbm(X*20, Y*20 + 5, np.ones_like(X), 4)
    disk = np.exp(-R / 0.32)
    armi = arm * np.exp(-R / 0.5) * (0.5 + n) * np.clip(R / 0.12, 0, 1)
    core = np.exp(-(R / 0.09) ** 2) * 1.6 + np.exp(-R / 0.12) * 0.6
    col = core[..., None] * np.array([1.0, 0.85, 0.6])
    col += armi[..., None] * np.array([0.55, 0.65, 1.0]) * 2.6
    col += disk[..., None] * np.array([0.85, 0.45, 0.6]) * 0.35 * (0.5 + n)[..., None]
    dust = np.clip((0.5 + 0.5 * np.cos(phase + 0.9)) ** 6 * np.exp(-R / 0.45) * (n2 + 0.2), 0, 0.8)
    col *= (1 - dust * np.clip(R / 0.1, 0, 1))[..., None]
    knots = (n2 > 0.72) & (arm > 0.55)
    col += (knots * np.exp(-R / 0.5))[..., None] * np.array([1.0, 0.35, 0.5]) * 1.2
    inten, ct = stars3d(np.stack([X, Y, np.zeros_like(X)], -1), 160, 0.18, 9)
    col += (inten * armi * 3)[..., None] * star_color(ct)
    col = 1 - np.exp(-col * 1.5)
    a = np.clip(col.max(-1) * 1.3, 0, 1) * np.clip((1 - R) / 0.15, 0, 1)
    save(rgba(col, a), os.path.join(ENV, "spiral_galaxy.png"))

# ---------------- Black hole with accretion rings (face-on rings, tilted in game) ----------------
def make_black_hole(N=768):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2) + 1e-6
    th = np.arctan2(Y, X)
    n = fbm(X*5, Y*5, np.full_like(X, 2.0), 5)
    rings = np.zeros_like(R)
    rrng = np.random.default_rng(3)
    for _ in range(45):
        r0 = rrng.uniform(0.25, 0.95); w = rrng.uniform(0.002, 0.008)
        rings += np.exp(-((R - r0) / w) ** 2) * rrng.uniform(0.3, 1.0)
    disk = np.exp(-((R - 0.42) / 0.22) ** 2) * (0.5 + n)
    heat = np.clip(1 - (R - 0.22) / 0.6, 0, 1)
    col = np.stack([0.4 + 0.6*heat, 0.35 + 0.35*heat, 1.0 - 0.5*heat], -1)
    inten = disk * 0.9 + rings * 0.8
    doppler = 0.75 + 0.35 * np.cos(th)
    c = col * (inten * doppler)[..., None]
    photon = np.exp(-((R - 0.2) / 0.012) ** 2) * 2.0
    c += photon[..., None] * np.array([1.0, 0.85, 0.6])
    c = 1 - np.exp(-c * 1.4)
    a = np.clip(c.max(-1) * 1.4, 0, 1) * np.clip((1 - R) / 0.1, 0, 1)
    hole = R < 0.19
    c = np.where(hole[..., None], np.array([0.0, 0.0, 0.0]), c)
    a = np.where(hole, 1.0, a)
    save(rgba(c, a), os.path.join(ENV, "black_hole_disk.png"))
    # dark purple sphere drawn on top (always faces the camera)
    Xs, Ys, ins, nx, ny, nz, r2 = sphere(256, 0.9)
    cs = np.array([0.08, 0.04, 0.16]) * (0.3 + 0.7 * fbm(nx*6, ny*6, nz*6, 5))[..., None]
    cs += (np.clip(r2, 0, 1) ** 8)[..., None] * np.array([0.6, 0.4, 1.0])
    save(rgba(cs, ins.astype(float)), os.path.join(ENV, "black_hole_core.png"))

# ---------------- Globular cluster ----------------
def make_cluster(N=384):
    X, Y = grid(N); R = np.sqrt(X**2 + Y**2)
    col = np.zeros(X.shape + (3,))
    for K, size, seed, g in [(120, 0.2, 21, 1.0), (60, 0.16, 22, 1.5), (220, 0.2, 23, 0.8)]:
        inten, ct = stars3d(np.stack([X, Y, np.full_like(X, 0.37)], -1), K, size, seed)
        dens = np.exp(-(R / 0.5) ** 2) * 2.4
        col += (inten * np.clip(dens, 0, 1.5) * g)[..., None] * star_color(ct)
    col += np.exp(-(R / 0.12) ** 2)[..., None] * np.array([1.0, 0.92, 0.8]) * 0.8
    col = 1 - np.exp(-col * 1.6)
    a = np.clip(col.max(-1) * 1.5, 0, 1)
    save(rgba(col, a), os.path.join(ENV, "cluster.png"))

# ---------------- Rocket item icon 16x16 ----------------
def make_rocket_icon():
    T = (0, 0, 0, 0)
    W, G, R_, B, O, Y_ = (235, 235, 240, 255), (150, 150, 160, 255), (210, 35, 35, 255), (60, 150, 230, 255), (255, 140, 20, 255), (255, 220, 60, 255)
    img = [[T]*16 for _ in range(16)]
    def p(x, y, c): img[y][x] = c
    for y in (1,): p(7, y, R_); p(8, y, R_)
    for y in (2, 3): [p(x, y, R_) for x in range(6, 10)]
    for y in range(4, 12): [p(x, y, W) for x in range(6, 10)]
    for y in range(4, 12): p(9, y, G)
    p(7, 6, B); p(8, 6, B); p(7, 7, B); p(8, 7, B)
    for y in range(9, 13): p(5, y, R_); p(10, y, R_)
    p(4, 12, R_); p(11, 12, R_)
    [p(x, 12, G) for x in range(6, 10)]
    p(7, 13, O); p(8, 13, O); p(7, 14, Y_); p(8, 14, O); p(7, 15, O)
    Image.fromarray(np.array(img, dtype=np.uint8), "RGBA").save(os.path.join(ITEM, "rocket.png"))

make_rocket_icon()
def make_gun_icon():
    T = (0, 0, 0, 0)
    D, G, L, C, P, W = (35, 35, 45, 255), (80, 80, 95, 255), (140, 140, 160, 255), (40, 220, 255, 255), (170, 60, 255, 255), (230, 250, 255, 255)
    img = [[T]*16 for _ in range(16)]
    def p(x, y, c): img[y][x] = c
    # barrel diagonal (bottom-left grip, top-right muzzle)
    for i in range(10):
        x, y = 4 + i, 10 - i
        p(x, y, G); p(x, y + 1, D); p(x - 1, y + 1, L if i % 3 else C)
    for i in range(3, 9):
        p(4 + i, 11 - i, C if i % 2 else P)
    p(14, 0, W); p(15, 0, C); p(14, 1, C); p(13, 1, W)
    # scope
    for i in range(3):
        p(7 + i, 4 - i + 1, D); p(7 + i, 3 - i + 1, G)
    # grip + trigger
    for y in range(11, 15): p(3, y, D); p(4, y, G)
    p(2, 15, D); p(3, 15, D); p(5, 12, L); p(6, 12, D)
    # energy core
    p(6, 9, P); p(7, 9, C); p(6, 8, C)
    Image.fromarray(np.array(img, dtype=np.uint8), "RGBA").save(os.path.join(ITEM, "orbital_cannon.png"))
make_gun_icon()
make_sun(); make_earth(); make_moon(); make_mars()
make_spiral(); make_black_hole(); make_cluster()
make_skybox(int(os.environ.get("SKY", "1024")))
print("done")
