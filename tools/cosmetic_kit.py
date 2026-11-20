"""Small toolkit to author BTB cosmetics in code: Bedrock geometry with box UV, procedural
pixel-art textures, emissive layers, animations and isometric preview icons.

All coordinates are Bedrock player space: pixels, Y up, feet at y=0, head from y=24 to y=32,
front of the player at -Z, the player's left side at +X.
"""
import json, math, os, random
import numpy as np
from PIL import Image, ImageDraw

# ------------------------------------------------------------------ colors

def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def clamp(v):
    return max(0, min(255, int(round(v))))

def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3])

def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))

# ------------------------------------------------------------------ painters
# A painter is f(face, x, y, w, h, rnd) -> (r, g, b, a) or None (transparent).
# face in: top, bottom, right(-x), front(-z), left(+x), back(+z). x/y are pixel coords inside the face
# as seen from outside, y=0 at the visual top.

def solid(color, noise=0.08, alpha=255, seed_shift=0):
    c = rgb(color) if isinstance(color, str) else color
    def p(face, x, y, w, h, rnd):
        f = 1 + (rnd.random() - 0.5) * 2 * noise
        if face == 'top':
            f *= 1.08
        elif face == 'bottom':
            f *= 0.82
        return shade(c, f) + (alpha,)
    return p

def bordered(fill, border, noise=0.06, alpha=255):
    fc, bc = rgb(fill), rgb(border)
    def p(face, x, y, w, h, rnd):
        edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
        c = bc if edge else fc
        f = 1 + (rnd.random() - 0.5) * 2 * noise
        if face == 'bottom':
            f *= 0.85
        return shade(c, f) + (alpha,)
    return p

def vertical(top, bottom, noise=0.05):
    a, b = rgb(top), rgb(bottom)
    def p(face, x, y, w, h, rnd):
        t = y / max(1, h - 1)
        if face in ('top',):
            t = 0
        if face == 'bottom':
            t = 1
        return shade(mix(a, b, t), 1 + (rnd.random() - 0.5) * 2 * noise) + (255,)
    return p

def palette_noise(colors, weights=None):
    cs = [rgb(c) for c in colors]
    def p(face, x, y, w, h, rnd):
        c = rnd.choices(cs, weights=weights)[0]
        if face == 'bottom':
            c = shade(c, 0.85)
        return c + (255,)
    return p

def stripes(a, b, width=2, horizontal=True, noise=0.05):
    ca, cb = rgb(a), rgb(b)
    def p(face, x, y, w, h, rnd):
        k = (y if horizontal else x) // width
        c = ca if k % 2 == 0 else cb
        return shade(c, 1 + (rnd.random() - 0.5) * 2 * noise) + (255,)
    return p

def faces(default, **per_face):
    """Different painters per face: faces(solid('#fff'), front=..., top=...)."""
    def p(face, x, y, w, h, rnd):
        return per_face.get(face, default)(face, x, y, w, h, rnd)
    return p

def glass(frame, tint='#d8f4ff', alpha=70):
    fc, tc = rgb(frame), rgb(tint)
    def p(face, x, y, w, h, rnd):
        edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
        if edge:
            return shade(fc, 1 + (rnd.random() - 0.5) * 0.1) + (255,)
        if (x + y) % 7 == 0 and x < w // 2:
            return (255, 255, 255, 150)  # reflection streak
        return tc + (alpha,)
    return p

def flame(core='#fff6b0', mid='#ffb02e', edge='#e8501a'):
    a, b, c = rgb(core), rgb(mid), rgb(edge)
    def p(face, x, y, w, h, rnd):
        cx = abs(x - (w - 1) / 2) / max(1, w / 2)
        t = min(1, cx * 0.8 + (y / max(1, h)) * -0.3 + 0.3 + rnd.random() * 0.2)
        col = mix(a, b, t * 1.4) if t < 0.6 else mix(b, c, (t - 0.6) * 2.5)
        return col + (255,)
    return p

def transparent(face, x, y, w, h, rnd):
    return None

# ------------------------------------------------------------------ model

class Box:
    def __init__(self, bone, origin, size, paint, inflate=0.0, rotation=None, pivot=None, mirror=False, emissive=None, tint=False):
        self.bone, self.origin, self.size = bone, list(origin), list(size)
        self.paint, self.inflate, self.rotation, self.pivot = paint, inflate, rotation, pivot
        self.mirror, self.emissive, self.tint = mirror, emissive, tint
        self.uv = None

    def uv_size(self):
        w, h, d = [int(math.ceil(s)) for s in self.size]
        return 2 * (w + d), d + h


class Bone:
    def __init__(self, name, pivot, parent=None, rotation=None):
        self.name, self.pivot, self.parent, self.rotation = name, list(pivot), parent, rotation
        self.boxes = []


class Model:
    def __init__(self, identifier, seed=1):
        self.identifier = identifier
        self.bones = []
        self.rnd = random.Random(seed)

    def bone(self, name, pivot, parent=None, rotation=None):
        b = Bone(name, pivot, parent, rotation)
        self.bones.append(b)
        return b

    def box(self, bone, origin, size, paint, **kw):
        b = Box(bone, origin, size, paint, **kw)
        bone.boxes.append(b)
        return b

    # ----------------------------------------------------- UV packing (shelf packer)
    def pack(self):
        boxes = [b for bone in self.bones for b in bone.boxes]
        order = sorted(boxes, key=lambda b: -b.uv_size()[1])
        for tex in (32, 64, 128, 256):
            x = y = shelf = 0
            ok = True
            for b in order:
                w, h = b.uv_size()
                if w > tex:
                    ok = False
                    break
                if x + w > tex:
                    x, y, shelf = 0, y + shelf, 0
                if y + h > tex:
                    ok = False
                    break
                b.uv = (x, y)
                x += w
                shelf = max(shelf, h)
            if ok:
                self.tex = tex
                return tex
        raise ValueError('model too large for a 256 texture: ' + self.identifier)

    # ----------------------------------------------------- textures
    def paint(self):
        tex = self.tex
        base = Image.new('RGBA', (tex, tex), (0, 0, 0, 0))
        emis = Image.new('RGBA', (tex, tex), (0, 0, 0, 0))
        mask = Image.new('RGBA', (tex, tex), (0, 0, 0, 0))
        has_emis = has_mask = False
        for bone in self.bones:
            for b in bone.boxes:
                u, v = b.uv
                w, h, d = [int(math.ceil(s)) for s in b.size]
                rects = {
                    'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
                    'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
                    'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
                }
                for face, (rx, ry, rw, rh) in rects.items():
                    for yy in range(rh):
                        for xx in range(rw):
                            c = b.paint(face, xx, yy, rw, rh, self.rnd)
                            if c is not None:
                                base.putpixel((rx + xx, ry + yy), tuple(c) if len(c) == 4 else tuple(c) + (255,))
                                if b.tint:
                                    mask.putpixel((rx + xx, ry + yy), (255, 255, 255, 255))
                                    has_mask = True
                            if b.emissive is not None:
                                e = b.emissive(face, xx, yy, rw, rh, self.rnd)
                                if e is not None:
                                    emis.putpixel((rx + xx, ry + yy), tuple(e) if len(e) == 4 else tuple(e) + (255,))
                                    has_emis = True
        return base, (emis if has_emis else None), (mask if has_mask else None)

    # ----------------------------------------------------- geometry json
    def geometry(self):
        bones = []
        for bone in self.bones:
            bo = {'name': bone.name, 'pivot': bone.pivot}
            if bone.parent:
                bo['parent'] = bone.parent
            if bone.rotation:
                bo['rotation'] = bone.rotation
            cubes = []
            for b in bone.boxes:
                c = {'origin': [round(x, 4) for x in b.origin], 'size': b.size, 'uv': list(b.uv)}
                if b.inflate:
                    c['inflate'] = b.inflate
                if b.mirror:
                    c['mirror'] = True
                if b.rotation:
                    c['rotation'] = b.rotation
                    c['pivot'] = b.pivot or [b.origin[i] + b.size[i] / 2 for i in range(3)]
                cubes.append(c)
            if cubes:
                bo['cubes'] = cubes
            bones.append(bo)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': self.identifier, 'texture_width': self.tex, 'texture_height': self.tex},
            'bones': bones}]}


# ------------------------------------------------------------------ isometric preview

def _rot(v, rx, ry, rz):
    """Bedrock bone rotation applied in Java convention (ZYX, angles as stored) — matches the renderer."""
    x, y, z = v
    # The renderer works in Java space (y down). Convert, rotate, convert back.
    y = -y
    ax, ay, az = math.radians(rx), math.radians(ry), math.radians(rz)
    # rotationZYX(z, y, x): R = Rz * Ry * Rx applied to v
    # Rx
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    # Ry
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    # Rz
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x, -y, z)


def _world(model, bone, p):
    """Applies bone rotations up the hierarchy (rest pose)."""
    by_name = {b.name: b for b in model.bones}
    b = bone
    while b is not None:
        if b.rotation:
            rel = [p[i] - b.pivot[i] for i in range(3)]
            r = _rot(rel, *b.rotation)
            p = [r[i] + b.pivot[i] for i in range(3)]
        b = by_name.get(b.parent) if b.parent else None
    return p


def preview(model, base_img, emis_img, out_path, mannequin=None, size=128, yaw=-35, pitch=28):
    """Renders an isometric icon. mannequin: list of (origin, size) gray boxes for context."""
    tex = model.tex
    quads = []  # (depth, poly[(x,y)], color)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def project(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy
        # camera above the model looking down: higher points are nearer, back points appear higher
        y2, z2 = y * cp + z * sp, z * cp - y * sp
        return (x, -y2, z2)

    def box_faces(origin, size, inflate=0):
        x0, y0, z0 = [origin[i] - inflate for i in range(3)]
        x1, y1, z1 = [origin[i] + size[i] + inflate for i in range(3)]
        # corners per face, ordered top-left, top-right, bottom-right, bottom-left as seen from outside
        return {
            'front': [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)],
            'back': [(x1, y1, z1), (x0, y1, z1), (x0, y0, z1), (x1, y0, z1)],
            'right': [(x0, y1, z1), (x0, y1, z0), (x0, y0, z0), (x0, y0, z1)],
            'left': [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)],
            'top': [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)],
            'bottom': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)],
        }

    light = {'top': 1.0, 'front': 0.86, 'left': 0.72, 'right': 0.72, 'back': 0.6, 'bottom': 0.5}

    def add_textured(corners, rect, img, eimg, face):
        rx, ry, rw, rh = rect
        if rw <= 0 or rh <= 0:
            return
        a, b, c, d = corners
        for j in range(rh):
            for i in range(rw):
                col = img.getpixel((rx + i, ry + j))
                e = eimg.getpixel((rx + i, ry + j)) if eimg is not None else (0, 0, 0, 0)
                if col[3] == 0 and e[3] == 0:
                    continue
                def lerp(s, t):
                    top = [a[k] + (b[k] - a[k]) * s for k in range(3)]
                    bot = [d[k] + (c[k] - d[k]) * s for k in range(3)]
                    return [top[k] + (bot[k] - top[k]) * t for k in range(3)]
                pts = [lerp(i / rw, j / rh), lerp((i + 1) / rw, j / rh), lerp((i + 1) / rw, (j + 1) / rh), lerp(i / rw, (j + 1) / rh)]
                f = light[face]
                if e[3] > 0:
                    rgbc = e[:3]
                    alpha = 255
                else:
                    rgbc = shade(col, f)
                    alpha = col[3]
                quads.append((pts, rgbc + (alpha,)))

    for bone in model.bones:
        for bx in bone.boxes:
            u, v = bx.uv
            w, h, d = [int(math.ceil(s)) for s in bx.size]
            rects = {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
                     'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
            fc = box_faces(bx.origin, bx.size, bx.inflate)
            for face, corners in fc.items():
                pts = corners
                if bx.rotation:
                    pv = bx.pivot or [bx.origin[k] + bx.size[k] / 2 for k in range(3)]
                    pts = [[_rot([p[k] - pv[k] for k in range(3)], *bx.rotation)[k] + pv[k] for k in range(3)] for p in pts]
                pts = [_world(model, bone, p) for p in pts]
                add_textured(pts, rects[face], base_img, emis_img, face)

    mq = []
    for (o, s) in (mannequin or []):
        for face, corners in box_faces(o, s).items():
            g = clamp(205 * light[face])
            mq.append((corners, (g, g, g, 255)))

    # depth: smaller = nearer. The mannequin is pushed back a little so parts lying flat on it always win.
    projected = []
    for pts, col in mq:
        projected.append(([project(p) for p in pts], col, 0.05))
    for pts, col in quads:
        projected.append(([project(p) for p in pts], col, 0.0))
    xs = [p[0] for pp, _, _ in projected for p in pp]
    ys = [p[1] for pp, _, _ in projected for p in pp]
    if not xs:
        return
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    scale = (size * 0.84) / max(maxx - minx, maxy - miny, 1)
    ox = size / 2 - (minx + maxx) / 2 * scale
    oy = size / 2 - (miny + maxy) / 2 * scale
    ss = 3
    n = size * ss
    zbuf = np.full((n, n), np.inf)
    cbuf = np.zeros((n, n, 4), dtype=np.float64)

    def raster(pp, col, bias, blend):
        v = [((p[0] * scale + ox) * ss, (p[1] * scale + oy) * ss, p[2] + bias) for p in pp]
        for tri in ((v[0], v[1], v[2]), (v[0], v[2], v[3])):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-9:
                continue
            bx0, bx1 = max(0, int(math.floor(min(x0, x1, x2)))), min(n - 1, int(math.ceil(max(x0, x1, x2))))
            by0, by1 = max(0, int(math.floor(min(y0, y1, y2)))), min(n - 1, int(math.ceil(max(y0, y1, y2))))
            if bx1 < bx0 or by1 < by0:
                continue
            gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
            w0 = ((x1 - gx) * (y2 - gy) - (x2 - gx) * (y1 - gy)) / area
            w1 = ((x2 - gx) * (y0 - gy) - (x0 - gx) * (y2 - gy)) / area
            w2 = 1 - w0 - w1
            eps = -1e-6
            inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
            if not inside.any():
                continue
            z = w0 * z0 + w1 * z1 + w2 * z2
            zb = zbuf[by0:by1 + 1, bx0:bx1 + 1]
            mask = inside & (z < zb)
            if not mask.any():
                continue
            cb = cbuf[by0:by1 + 1, bx0:bx1 + 1]
            if blend:
                a = col[3] / 255.0
                for k in range(3):
                    cb[..., k][mask] = cb[..., k][mask] * (1 - a) + col[k] * a
                cb[..., 3][mask] = np.minimum(255, cb[..., 3][mask] + col[3] * (1 - cb[..., 3][mask] / 255.0))
            else:
                cb[mask] = col
                zb[mask] = z[mask]

    for pp, col, bias in projected:
        if col[3] >= 255:
            raster(pp, col, bias, False)
    translucent = [q for q in projected if q[1][3] < 255]
    translucent.sort(key=lambda q: -sum(p[2] for p in q[0]))
    for pp, col, bias in translucent:
        raster(pp, col, bias, True)
    img = Image.fromarray(np.clip(cbuf, 0, 255).astype(np.uint8), 'RGBA')
    img = img.resize((size, size), Image.LANCZOS)
    img.save(out_path)


# ------------------------------------------------------------------ writing

VIEWS = {'front': -35, 'back': 145, 'right': 35}


def write_cosmetic(root, folder, meta, model, animations=None, mannequin=None, view='front'):
    d = os.path.join(root, 'cosmetics', folder)
    os.makedirs(d, exist_ok=True)
    model.pack()
    base, emis, mask = model.paint()
    base.save(os.path.join(d, 'texture.png'))
    meta = dict(meta)
    meta.setdefault('format', 1)
    meta['model'] = 'model.geo.json'
    meta['texture'] = 'texture.png'
    if emis is not None:
        emis.save(os.path.join(d, 'emissive.png'))
        meta['emissive_texture'] = 'emissive.png'
    if mask is not None:
        mask.save(os.path.join(d, 'tint_mask.png'))
        meta['tint_mask'] = 'tint_mask.png'
    if animations:
        with open(os.path.join(d, 'animation.json'), 'w') as f:
            json.dump({'format_version': '1.8.0', 'animations': animations}, f, indent=2)
        meta['animation'] = 'animation.json'
    with open(os.path.join(d, 'model.geo.json'), 'w') as f:
        json.dump(model.geometry(), f, indent=2)
    preview(model, base, emis, os.path.join(d, 'preview.png'), mannequin=mannequin, yaw=VIEWS[view])
    meta['preview'] = 'preview.png'
    order = ['format', 'id', 'version', 'name', 'description', 'author', 'type', 'attachment', 'slots', 'model', 'texture',
             'emissive_texture', 'tint_mask', 'animation', 'animations', 'preview', 'transform', 'tint', 'glow', 'particles',
             'hide_layers', 'tags']
    ordered = {k: meta[k] for k in order if k in meta}
    with open(os.path.join(d, 'cosmetic.json'), 'w', encoding='utf-8') as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)
    return d


HEAD = [((-4, 24, -4), (8, 8, 8))]
HEAD_BODY = [((-4, 24, -4), (8, 8, 8)), ((-4, 12, -2), (8, 12, 4))]
BODY = [((-4, 12, -2), (8, 12, 4)), ((-4, 24, -4), (8, 8, 8))]
FULL = [((-4, 24, -4), (8, 8, 8)), ((-4, 12, -2), (8, 12, 4)), ((-8, 12, -2), (4, 12, 4)), ((4, 12, -2), (4, 12, 4)),
        ((-4, 0, -2), (4, 12, 4)), ((0, 0, -2), (4, 12, 4))]


# ------------------------------------------------------------------ pixel silhouettes

def shape(m, bone, rows, origin, plane='xy', depth=1, paint=None, inflate=0.0, tint=False, emissive=None, mirror_x=False):
    """Builds a solid pixel silhouette from text rows ('#' = filled, other chars = empty or color keys).

    plane 'xy': a vertical sheet facing the front; rows run top→bottom, columns left→right as seen
               from the front (+X on the viewer's right). origin = (left x, top y, front z).
    plane 'xz': a flat sheet lying horizontally; rows run back→front, columns -x→+x. origin = (min x, bottom y, back z).
    Each horizontal run of filled pixels becomes one box, so the silhouette is correct from every side.
    paint(face, gx, gy, rnd) receives global pixel coordinates (column, row) and the row character.
    """
    for r, row in enumerate(rows):
        c = 0
        while c < len(row):
            if row[c] in ' .':
                c += 1
                continue
            start, ch = c, row[c]
            while c < len(row) and row[c] == ch:
                c += 1
            n = c - start
            col0 = start if not mirror_x else len(row) - c
            if plane == 'xy':
                o = (origin[0] + col0, origin[1] - r - 1, origin[2])
                s = (n, 1, depth)
            else:
                o = (origin[0] + col0, origin[1], origin[2] + (len(rows) - 1 - r))
                s = (n, depth, 1)

            def painter(face, x, y, w, h, rnd, _r=r, _s=col0, _ch=ch, _n=n):
                gx = _s + (min(x, _n - 1) if face in ('front', 'back', 'top', 'bottom') else 0)
                return paint(face, gx, _r, _ch, rnd)
            em = None
            if emissive is not None:
                def em(face, x, y, w, h, rnd, _r=r, _s=col0, _ch=ch, _n=n):
                    gx = _s + (min(x, _n - 1) if face in ('front', 'back', 'top', 'bottom') else 0)
                    return emissive(face, gx, _r, _ch, rnd)
            m.box(bone, o, s, painter, inflate=inflate, tint=tint, emissive=em)


def ramp(colors):
    """Maps row characters to colors: ramp({'#': '#ffffff', 'g': '#d0d0d0'})."""
    cs = {k: rgb(v) for k, v in colors.items()}
    def p(face, gx, gy, ch, rnd):
        c = cs.get(ch, cs.get('#'))
        f = 0.96 + rnd.random() * 0.06
        if face == 'bottom':
            f *= 0.86
        elif face in ('left', 'right'):
            f *= 0.92
        return shade(c, f) + (255,)
    return p
