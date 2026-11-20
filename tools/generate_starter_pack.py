#!/usr/bin/env python3
"""Generates the built-in "BTB Starter" cosmetic pack (deterministic, all art original).

Run from the project root:  python3 tools/generate_starter_pack.py
Writes into src/main/resources/assets/btb_skinstudio/builtin/btb_starter/.
"""
import json, math, os, shutil, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from cosmetic_kit import *  # noqa

ROOT = 'src/main/resources/assets/btb_skinstudio/builtin/btb_starter'
AUTHOR = 'BTB Studios'


def meta(name, de, en, typ, attachment=None, desc_de='', desc_en='', **extra):
    m = {'id': 'btb:' + name, 'version': '1.0.0', 'name': {'de': de, 'en': en}, 'author': AUTHOR, 'type': typ}
    if desc_de:
        m['description'] = {'de': desc_de, 'en': desc_en or desc_de}
    if attachment:
        m['attachment'] = attachment
    m.update(extra)
    return m


def anim(**bones):
    return {'loop': True, 'bones': bones}


def ring(m, bone, y, h, paint, r=5, thick=1, tint=False, emissive=None, inflate=0.0):
    """Four boxes forming a square ring around the head (outer half-size r)."""
    for o, s in [((-r, y, -r), (2 * r, h, thick)), ((-r, y, r - thick), (2 * r, h, thick)),
                 ((-r, y, -r + thick), (thick, h, 2 * r - 2 * thick)), ((r - thick, y, -r + thick), (thick, h, 2 * r - 2 * thick))]:
        m.box(bone, o, s, paint, tint=tint, emissive=emissive, inflate=inflate)


def masked(painter, rows):
    """Cut-out painter: rows of '#'/'.' as seen on the front face; other faces use the edge pixels."""
    def p(face, x, y, w, h, rnd):
        if face in ('front', 'back'):
            yy = min(len(rows) - 1, y)
            row = rows[yy]
            xx = x if face == 'front' else w - 1 - x
            if xx >= len(row) or row[xx] != '#':
                return None
        return painter(face, x, y, w, h, rnd)
    return p


def torch_flame(m, parent, x, y, z, w=2, name='flame'):
    """A Minecraft-style flame on top of a torch stick, emissive and flickering."""
    fb = m.bone(name, [x, y, z], parent=parent)
    m.box(fb, (x - w / 2, y, z - w / 2), (w, 2, w), flame(), emissive=flame(), inflate=0.05)
    m.box(fb, (x - 0.5, y + 2, z - 0.5), (1, 1, 1), flame('#ffffff', '#fff2a0', '#ffc040'), emissive=flame('#ffffff', '#fff2a0', '#ffc040'), inflate=-0.05)
    return fb


FLICKER = {'scale': [1, '1 + math.sin(q.anim_time * 900) * 0.08 + math.sin(q.anim_time * 2100) * 0.05', 1],
           'rotation': [0, 'q.anim_time * 40', 'math.sin(q.anim_time * 500) * 4']}

def straps(m, b, color):
    """Backpack straps: down the chest and over the shoulders to the back."""
    s = solid(color, noise=0.06)
    for x in (-3, 2):
        m.box(b, (x, 14, -2.6), (1, 10, 1), s, inflate=-0.1)
        m.box(b, (x, 23.6, -2.6), (1, 1, 5), s, inflate=-0.1)


WOOD = palette_noise(['#8a5a2b', '#7a4e24', '#94622f', '#6e4520'], [4, 3, 2, 1])


def build():
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(ROOT)
    cos = []

    # ---------------------------------------------------------------- 1 Fackel auf dem Kopf
    m = Model('geometry.btb.torch_hat', seed=11)
    b = m.bone('torch', [0, 32, 0])
    copper = palette_noise(['#c8734f', '#d4825c', '#b8603f', '#e09a74'], [5, 3, 2, 1])
    m.box(b, (-6, 32, -6), (12, 1, 12), faces(copper, top=palette_noise(['#d07a54', '#dc8c64', '#c46a48'])))
    for x, z, h in [(-6, -6, 3), (-3, -6, 2), (2, -6, 4), (5, -6, 2), (-6, 1, 2), (5, 3, 3), (-1, 5, 2), (3, 5, 3), (-6, -3, 4), (5, -2, 2)]:
        m.box(b, (x, 32 - h, z), (1, h, 1), copper)
    wood_side = palette_noise(['#5b3a22', '#4e311c', '#664128', '#3f2716'], [4, 3, 2, 1])
    wood_top = lambda f_, x, y, w, h, r: ((rgb('#b07a52') + (255,)) if (x in (0, w - 1) or y in (0, h - 1)) else
                                          (rgb('#e0a090') + (255,)) if (x == w - 2 and y == h - 3) else shade(rgb('#7a5034'), 0.9 + r.random() * 0.15) + (255,))
    m.box(b, (-4.5, 33, -4.5), (9, 3, 9), faces(wood_side, top=wood_top))
    candle = palette_noise(['#f2e3b8', '#ead7a4', '#f8ecc8', '#e2cd96'], [4, 3, 2, 1])
    m.box(b, (-2.5, 36, -2.5), (5, 4, 5), faces(candle, top=palette_noise(['#f8ecc8', '#efdfb0'])))
    m.box(b, (-0.5, 40, -0.5), (1, 1, 1), solid('#111111'))
    torch_flame(m, 'torch', 0, 41, 0, w=1.6)
    cos.append(('torch_hat', meta('torch_hat', 'Fackel auf dem Kopf', 'Torch Hat', 'head_decoration',
                desc_de='Eine Kerzenfackel auf Holzsockel und Kupferteller – mit Flammenanimation und Lichtschein.',
                desc_en='A candle torch on a wooden base and copper plate – with flame animation and light glow.',
                animations=['animation.btb.torch_hat.flicker'],
                glow={'color': '#FFB347', 'size': 7, 'intensity': 0.6, 'offset': [0, 10, 0], 'flicker': True},
                particles={'type': 'minecraft:small_flame', 'rate': 1.2, 'offset': [0, 11.5, 0], 'spread': 0.6},
                tags=['fackel', 'torch', 'licht', 'feuer', 'kerze']),
                m, {'animation.btb.torch_hat.flicker': anim(flame=FLICKER)}, HEAD))

    # ---------------------------------------------------------------- 2 Stirnband mit Fackel
    # Durchgehendes Lederband über den Augen (y 29–31), Fackel steckt vorne mittig in einer Schlaufe am Band.
    m = Model('geometry.btb.headband_torch', seed=12)
    b = m.bone('band', [0, 28, 0])
    leather = lambda f_, x, y, w, h, r: ((rgb('#5a4330') + (255,)) if (y == 0 and f_ not in ('top', 'bottom')) else
                                         (rgb('#241a14') + (255,)) if (y == h - 1 and f_ not in ('top', 'bottom')) else
                                         shade(rgb('#3a2b20'), 0.88 + r.random() * 0.18) + (255,))
    m.box(b, (-4, 29, -4), (8, 2, 8), leather, inflate=0.55)
    loop = faces(bordered('#4a3828', '#241a14'), top=solid('#241a14'), bottom=solid('#241a14'))
    m.box(b, (-1.5, 29, -5.6), (3, 2, 1), loop, inflate=0.1)
    stick = lambda f_, x, y, w, h, r: ((rgb('#c9a06a') + (255,)) if (f_ not in ('top', 'bottom') and x == 0) else
                                       shade(rgb('#6e4724'), 0.85 + r.random() * 0.25) + (255,))
    m.box(b, (-1, 28, -6.1), (2, 6, 2), stick)
    fb = m.bone('flame', [0, 34, -5.1], parent='band')
    fl = lambda f_, x, y, w, h, r: ((rgb('#fffbe0') + (255,)) if (y == 0 and x == 0) else (rgb('#ffe14a') + (255,)) if y == 0 else (rgb('#ffc21a') + (255,)))
    m.box(fb, (-1, 34, -6.1), (2, 2, 2), fl, emissive=fl)
    cos.append(('headband_torch', meta('headband_torch', 'Stirnband mit Fackel', 'Headband Torch', 'head_decoration',
                desc_de='Breites Lederstirnband über den Augen, vorne mittig steckt eine Fackel in einer Schlaufe.',
                desc_en='A wide leather headband above the eyes with a torch tucked into a loop at the front.',
                animations=['animation.btb.headband_torch.flicker'],
                glow={'color': '#FFC94A', 'size': 6, 'intensity': 0.5, 'offset': [0, 3, -5], 'flicker': True},
                particles={'type': 'minecraft:small_flame', 'rate': 0.8, 'offset': [0, 4.5, -5], 'spread': 0.4},
                tags=['fackel', 'höhle', 'licht', 'stirnlampe', 'torch']),
                m, {'animation.btb.headband_torch.flicker': anim(flame={'scale': [1, '1 + math.sin(q.anim_time * 900) * 0.06', 1]})}, HEAD))

    # ---------------------------------------------------------------- 3 Seerosenblatt (flach, rund, mit Kerbe)
    m = Model('geometry.btb.lily_pad', seed=13)
    b = m.bone('lily_pad', [0, 32, 0])
    pad_rows = [
        '...#####...',
        '..#######..',
        '.#########.',
        '###########',
        '#####.#####',
        '####...####',
        '###.....###',
        '.#.......#.',
    ]
    # rows run back → front; the notch opens to the front like the Minecraft lily pad
    pad_rows = ['...#####...', '..#######..', '.#########.', '###########', '###########', '#####v#####', '####...####', '.##.....##.', '..#.....#..']
    def pad_paint(face, gx, gy, ch, rnd):
        cx, cy = gx - 5, gy - 4
        vein = ch == 'v' or (abs(cx) == abs(cy) and abs(cx) <= 3) or (cy < 0 and cx == 0)
        base = rgb('#5f9e2b') if vein else rgb('#7cbf35') if (cx * cx + cy * cy) > 12 else rgb('#8fd13f')
        f = 0.94 + rnd.random() * 0.1
        if face == 'bottom':
            base = rgb('#4f7d24')
        return shade(base, f) + (255,)
    shape(m, b, [r.replace('v', '#') for r in pad_rows], (-5.5, 32, -4.5), plane='xz', depth=1, paint=pad_paint)
    m.box(b, (-0.5, 32.6, 0.5), (1, 1, 1), faces(solid('#f7c6dc'), top=solid('#ffe38a')), inflate=-0.05)
    cos.append(('lily_pad', meta('lily_pad', 'Seerosenblatt', 'Lily Pad', 'flower',
                desc_de='Ein rundes, flaches Seerosenblatt mit Blüte, das oben auf dem Kopf liegt.',
                desc_en='A round, flat lily pad with a tiny blossom resting on top of your head.',
                tags=['seerose', 'pflanze', 'natur', 'wasser', 'lily']), m, None, HEAD))

    # ---------------------------------------------------------------- 4 Terrarium-Rucksack (Glas auf dem Rücken)
    m = Model('geometry.btb.terrarium', seed=14)
    b = m.bone('terrarium', [0, 18, 2])
    x0, x1, y0, y1, z0, z1 = -4, 4, 13, 23, 2.2, 8.2
    frame = solid('#ffffff', noise=0.03)
    # 12 Rahmenkanten als echte Geometrie
    for (ox, oy, oz, sx, sy, sz) in [
        (x0, y0, z0, 1, 10, 1), (x1 - 1, y0, z0, 1, 10, 1), (x0, y0, z1 - 1, 1, 10, 1), (x1 - 1, y0, z1 - 1, 1, 10, 1),
        (x0, y0, z0, 8, 1, 1), (x0, y1 - 1, z0, 8, 1, 1), (x0, y0, z1 - 1, 8, 1, 1), (x0, y1 - 1, z1 - 1, 8, 1, 1),
        (x0, y0, z0, 1, 1, 6), (x1 - 1, y0, z0, 1, 1, 6), (x0, y1 - 1, z0, 1, 1, 6), (x1 - 1, y1 - 1, z0, 1, 1, 6)]:
        m.box(b, (ox, oy, oz), (sx, sy, sz), frame)
    soil = faces(palette_noise(['#6b4a2b', '#5a3d23', '#7a5633', '#4a311b'], [4, 3, 2, 1]), top=palette_noise(['#5c8f34', '#6b4a2b', '#4f8f30'], [3, 2, 2]))
    m.box(b, (x0 + 1, y0 + 1, z0 + 1), (6, 2, 4), soil)
    m.box(b, (-2, 16, 4.2), (1, 3, 1), solid('#3f8a2c'))
    m.box(b, (-2.5, 19, 3.7), (2, 2, 2), faces(palette_noise(['#f58fc8', '#ffb8de', '#e66fb0']), top=solid('#ffd84a')))
    m.box(b, (1, 16, 5.2), (1, 2, 1), solid('#4f9a32'))
    m.box(b, (0.5, 18, 4.7), (2, 1, 2), faces(solid('#ffe066'), top=solid('#fff1a8')))
    m.box(b, (1.5, 16, 3.2), (1, 1, 1), solid('#6fbf45'))
    # Glasscheiben innen (durchscheinend), Deckel, Gurte
    pane = lambda f_, x, y, w, h, r: (255, 255, 255, 150) if (x + y) % 9 == 0 else (214, 240, 255, 60)
    m.box(b, (x0 + 1, y0 + 1, z0 + 1), (6, 8, 4), pane, inflate=0.45)
    lid = faces(bordered('#ffcf3f', '#c99417'), top=palette_noise(['#ffe680', '#fff0a8', '#ffd84a']))
    m.box(b, (x0 - 0.5, y1, z0 - 0.5), (9, 1, 7), lid)
    m.box(b, (-1, y1 + 1, 4.7), (2, 1, 1), solid('#ffe27a'))
    straps(m, b, '#5b3a1f')
    cos.append(('terrarium', meta('terrarium', 'Terrarium-Rucksack', 'Terrarium Backpack', 'backpack', 'back',
                desc_de='Ein Glas-Terrarium mit weißem Rahmen, Blumen und goldenem Deckel als Rucksack.',
                desc_en='A white-framed glass terrarium with flowers and a golden lid, worn as a backpack.',
                tags=['glas', 'blume', 'rucksack', 'terrarium']), m, None, BODY))

    # ---------------------------------------------------------------- 5/6 Kronen
    def crown(name, de, en, metal, dark, gem, gem2, palette, tall_middle=False, seed=15):
        m = Model('geometry.btb.' + name, seed=seed)
        b = m.bone('crown', [0, 32, 0])
        gold = bordered(metal, dark, noise=0.07)
        ring(m, b, 32, 2, gold, r=5, tint=True)
        for x in (-5, -1, 3):
            h = 3 if (tall_middle and x == -1) else 2
            m.box(b, (x + (1 if x == -1 else 0), 34, -5), (1, h, 1), solid(metal), tint=True)
            m.box(b, (x + (1 if x == -1 else 0), 34, 4), (1, h, 1), solid(metal), tint=True)
        for z in (-2, 1):
            m.box(b, (-5, 34, z), (1, 2, 1), solid(metal), tint=True)
            m.box(b, (4, 34, z), (1, 2, 1), solid(metal), tint=True)
        for x, z in [(-5, -5), (4, -5), (-5, 4), (4, 4)]:
            m.box(b, (x, 34, z), (1, 1, 1), solid(dark), tint=True)
        m.box(b, (-1, 32.5, -5.4), (2, 1, 1), faces(solid(gem), front=solid(gem2)), inflate=0.15)
        m.box(b, (-5.4, 32.5, -0.5), (1, 1, 1), solid(gem2), inflate=0.1)
        m.box(b, (4.4, 32.5, -0.5), (1, 1, 1), solid(gem2), inflate=0.1)
        return m
    m = crown('gold_crown', 'Goldene Krone', 'Golden Crown', '#ffcf3f', '#c99417', '#d6283a', '#ff5a6e', None, seed=15)
    cos.append(('gold_crown', meta('gold_crown', 'Goldene Krone', 'Golden Crown', 'crown',
                desc_de='Klassische Krone mit Rubinen – frei einfärbbar.', desc_en='A classic crown with rubies – freely recolorable.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFB0C0', '#B8F0FF', '#B4FFB0', '#D0B0FF', '#FFD080']},
                tags=['krone', 'könig', 'gold']), m, None, HEAD))
    m = crown('silver_crown', 'Silberne Krone', 'Silver Crown', '#d9dee6', '#8f98a6', '#2e6be0', '#7fb0ff', None, tall_middle=True, seed=16)
    cos.append(('silver_crown', meta('silver_crown', 'Silberne Krone', 'Silver Crown', 'crown',
                desc_de='Schlichte Silberkrone mit Saphiren.', desc_en='A simple silver crown with sapphires.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFE0A0', '#C0E8FF']},
                tags=['krone', 'silber']), m, None, HEAD))

    # ---------------------------------------------------------------- 7 Eis-Tiara
    m = Model('geometry.btb.ice_tiara', seed=17)
    b = m.bone('tiara', [0, 30, 0])
    ice = palette_noise(['#cfefff', '#e8f8ff', '#a8dcff', '#ffffff'], [4, 3, 2, 1])
    m.box(b, (-5, 30, -5), (10, 1, 1), ice)
    m.box(b, (-5, 30, -4), (1, 1, 6), ice)
    m.box(b, (4, 30, -4), (1, 1, 6), ice)
    m.box(b, (-3, 31, -5), (1, 1, 1), ice)
    m.box(b, (2, 31, -5), (1, 1, 1), ice)
    m.box(b, (-4, 31, -5), (1, 2, 1), solid('#ffffff'), inflate=-0.1)
    m.box(b, (3, 31, -5), (1, 2, 1), solid('#ffffff'), inflate=-0.1)
    m.box(b, (-1, 30.5, -5.6), (2, 2, 1), faces(solid('#ff5fa8'), front=bordered('#ff7cbc', '#d63f86')), emissive=solid('#ff7cbc', alpha=120))
    ribbon = lambda f_, x, y, w, h, r: (rgb('#ffffff') + (255,)) if (x + y) % 3 else (rgb('#bfe6ff') + (255,))
    m.box(b, (5, 30, 2), (6, 1, 1), ribbon, rotation=[0, -25, -12], pivot=[5, 30.5, 2.5], inflate=-0.15)
    m.box(b, (-11, 30, -3), (6, 1, 1), ribbon, rotation=[0, -20, 10], pivot=[-5, 30.5, -2.5], inflate=-0.15)
    m.box(b, (10, 28, 3), (4, 1, 1), solid('#d8f0ff', alpha=170), rotation=[0, -30, -25], pivot=[10, 28.5, 3.5], inflate=-0.2)
    s = m.bone('sparkle', [0, 38, -2], parent='tiara')
    glowmint = solid('#6dffc8', noise=0.04)
    m.box(s, (-0.5, 36, -2.5), (1, 5, 1), glowmint, emissive=glowmint)
    m.box(s, (-2.5, 38, -2.5), (5, 1, 1), glowmint, emissive=glowmint)
    cos.append(('ice_tiara', meta('ice_tiara', 'Eis-Tiara', 'Ice Tiara', 'crown',
                desc_de='Kühle Tiara mit pinkem Edelstein und schwebendem Funkelkreuz.', desc_en='A frosty tiara with a pink gem and a floating sparkle.',
                animations=['animation.btb.ice_tiara.sparkle'],
                glow={'color': '#7CFFD4', 'size': 4, 'intensity': 0.35, 'offset': [0, 6.5, -2], 'flicker': False},
                particles={'type': 'minecraft:end_rod', 'rate': 0.25, 'offset': [0, 7, -2], 'spread': 3},
                tags=['krone', 'eis', 'tiara']),
                m, {'animation.btb.ice_tiara.sparkle': anim(sparkle={'rotation': [0, 'q.anim_time * 90', 0], 'position': [0, 'math.sin(q.anim_time * 120) * 0.6', 0]})}, HEAD))

    # ---------------------------------------------------------------- 8 Heiligenschein
    m = Model('geometry.btb.halo', seed=18)
    b = m.bone('halo', [0, 35, 0])
    gold = solid('#ffe680', noise=0.05)
    for o, sz in [((-3, 35, -5), (6, 1, 1)), ((-3, 35, 4), (6, 1, 1)), ((-5, 35, -3), (1, 1, 6)), ((4, 35, -3), (1, 1, 6)),
                  ((-4, 35, -4), (1, 1, 1)), ((3, 35, -4), (1, 1, 1)), ((-4, 35, 3), (1, 1, 1)), ((3, 35, 3), (1, 1, 1))]:
        m.box(b, o, sz, gold, emissive=solid('#fff2b0', noise=0.03))
    cos.append(('halo', meta('halo', 'Heiligenschein', 'Halo', 'halo',
                desc_de='Ein leuchtender Ring schwebt über dem Kopf.', desc_en='A glowing ring floats above your head.',
                animations=['animation.btb.halo.float'],
                glow={'color': '#FFF0A0', 'size': 9, 'intensity': 0.35, 'offset': [0, 3.5, 0], 'flicker': False},
                tags=['halo', 'engel', 'licht']),
                m, {'animation.btb.halo.float': anim(halo={'position': [0, 'math.sin(q.anim_time * 90) * 0.7', 0], 'rotation': [0, 'q.anim_time * 20', 0]})}, HEAD))

    # ---------------------------------------------------------------- 9 Feuer-Halo
    m = Model('geometry.btb.fire_halo', seed=19)
    b = m.bone('ring', [0, 26, 5])
    R = 12
    cols = ['#ffe14a', '#ffb52e', '#ff8a1c', '#f0561a', '#c8321a']
    pts = set()
    for i in range(720):
        a = math.radians(i / 2)
        for rr in (R, R + 1):
            pts.add((round(math.cos(a) * rr), round(math.sin(a) * rr)))
    for (px, py) in sorted(pts):
        dist = math.hypot(px, py)
        c = cols[min(len(cols) - 1, int((dist - R + 0.5) * 2) + ((px * 5 + py * 3) % 2))]
        p = solid(c, noise=0.1)
        m.box(b, (px - 0.5, 26 + py - 0.5, 4.5), (1, 1, 1), p, emissive=p)
    for i in range(18):
        a = math.radians(i * 20 + 7)
        for k, (rr, c) in enumerate([(R + 2, '#ff9a1f'), (R + 3, '#ffd23f')]):
            if k == 1 and i % 2:
                continue
            px, py = round(math.cos(a) * rr), round(math.sin(a) * rr)
            p = solid(c, noise=0.15)
            m.box(b, (px - 0.5, 26 + py - 0.5, 4.5), (1, 1, 1), p, emissive=p)
    cos.append(('fire_halo', meta('fire_halo', 'Feuer-Halo', 'Fire Halo', 'halo',
                desc_de='Ein großer lodernder Flammenring hinter Kopf und Schultern.', desc_en='A large blazing ring of fire behind head and shoulders.',
                animations=['animation.btb.fire_halo.spin'],
                glow={'color': '#FF8A2A', 'size': 14, 'intensity': 0.4, 'offset': [0, -6, 5], 'flicker': True},
                particles={'type': 'minecraft:flame', 'rate': 3, 'offset': [0, -6, 5], 'spread': 22},
                slots=['halo'], tags=['feuer', 'halo', 'flammen']),
                m, {'animation.btb.fire_halo.spin': anim(ring={'rotation': [0, 0, 'q.anim_time * -30'], 'scale': ['1 + math.sin(q.anim_time * 400) * 0.02', '1 + math.sin(q.anim_time * 400) * 0.02', 1]})}, BODY))

    # ---------------------------------------------------------------- 10 Teufelshörner
    m = Model('geometry.btb.devil_horns', seed=20)
    for side, sx in (('left_horn', 1), ('right_horn', -1)):
        b = m.bone(side, [2.5 * sx, 32, 0], rotation=[-10, 0, 0])
        for i, (o, s, inf, c) in enumerate([((2, 32, -1), (2, 2, 2), 0, '#b3261e'), ((3, 34, -1), (2, 2, 2), -0.2, '#8f1d17'),
                                            ((4, 35.6, -0.5), (1, 2, 1), 0, '#5e130f'), ((4.6, 37.3, -0.5), (1, 1, 1), -0.15, '#2a0d0d')]):
            x = o[0] if sx > 0 else -o[0] - s[0]
            m.box(b, (x, o[1], o[2]), s, solid(c, noise=0.1), inflate=inf)
    cos.append(('devil_horns', meta('devil_horns', 'Teufelshörner', 'Devil Horns', 'horns',
                desc_de='Zwei gebogene rote Hörner.', desc_en='Two curved red horns.', tags=['hörner', 'teufel', 'halloween']), m, None, HEAD))

    # ---------------------------------------------------------------- 11 Blumenkrone
    m = Model('geometry.btb.flower_crown', seed=21)
    b = m.bone('wreath', [0, 32, 0])
    vine = palette_noise(['#4f9a32', '#3f8128', '#64b13e'])
    ring(m, b, 31.5, 1, vine, r=5)
    flowers = [('#ff8fc4', -3, -5), ('#ffe066', 1, -5), ('#ffffff', -5, -1), ('#c9a0ff', 4, 1), ('#ff8fc4', 2, 4), ('#ffffff', -3, 4), ('#ffe066', 4, -3), ('#c9a0ff', -5, 2)]
    for c, x, z in flowers:
        petal = faces(solid(c, noise=0.05), top=lambda f, x_, y_, w, h, r, c=c: (rgb('#ffd23f') + (255,)) if (x_ == 0 and y_ == 0) else solid(c)(f, x_, y_, w, h, r))
        m.box(b, (x, 32.2, z), (1, 1, 1), petal, inflate=0.25)
    cos.append(('flower_crown', meta('flower_crown', 'Kleine Blumenkrone', 'Little Flower Crown', 'flower',
                desc_de='Ein Kranz aus Ranken und bunten Blüten.', desc_en='A wreath of vines and colorful blossoms.',
                particles={'type': 'minecraft:cherry_leaves', 'rate': 0.15, 'offset': [0, 0, 0], 'spread': 8},
                tags=['blumen', 'frühling', 'kranz']), m, None, HEAD))

    # ---------------------------------------------------------------- 12 Kleeblatt
    m = Model('geometry.btb.clover', seed=22)
    b = m.bone('clover', [0, 32, 0])
    m.box(b, (-0.5, 32, -0.5), (1, 8, 1), solid('#3f8128'))
    clover_rows = [
        '.##...##.',
        '####.####',
        '####.####',
        '.###o###.',
        '...ooo...',
        '.###o###.',
        '####.####',
        '####.####',
        '.##...##.',
    ]
    leaf = ramp({'#': '#4fbf3a', 'o': '#2f7a22'})
    shape(m, b, clover_rows, (-4.5, 44, -0.5), plane='xy', depth=1, paint=leaf)
    cos.append(('clover', meta('clover', 'Kleeblatt', 'Four-Leaf Clover', 'flower',
                desc_de='Ein vierblättriges Glückskleeblatt, das im Wind wippt.', desc_en='A lucky four-leaf clover swaying in the wind.',
                animations=['animation.btb.clover.sway'], tags=['glück', 'klee', 'pflanze']),
                m, {'animation.btb.clover.sway': anim(clover={'rotation': ['math.sin(q.anim_time * 140) * 4', 'math.sin(q.anim_time * 50) * 20', 'math.sin(q.anim_time * 90) * 7']})}, HEAD))

    # ---------------------------------------------------------------- 13-15 Brillen
    def temples(m, b, color):
        s_ = solid(color, noise=0.04)
        m.box(b, (-4.6, 28, -4.6), (1, 1, 5), s_, inflate=-0.2)
        m.box(b, (3.6, 28, -4.6), (1, 1, 5), s_, inflate=-0.2)

    m = Model('geometry.btb.round_glasses', seed=23)
    b = m.bone('glasses', [0, 28, -4])
    ring_rows = ['.##.', '#..#', '#..#', '.##.']
    frame = ramp({'#': '#c9a23a'})
    for x in (-4, 0.5):
        shape(m, b, ring_rows, (x, 29.5, -5), plane='xy', depth=1, paint=frame)
        m.box(b, (x + 1, 26.5, -4.9), (2, 2, 1), lambda f_, x_, y_, w, h, r: (255, 255, 255, 140) if (x_ == 0 and y_ == 0) else (210, 236, 255, 70), inflate=-0.4)
    m.box(b, (-0.5, 28, -5), (1, 1, 1), solid('#c9a23a'))
    temples(m, b, '#c9a23a')
    cos.append(('round_glasses', meta('round_glasses', 'Runde Brille', 'Round Glasses', 'glasses',
                desc_de='Klassische runde Brille mit dünnem Holzrahmen.', desc_en='Classic round glasses with a thin wooden frame.', tags=['brille', 'glasses']), m, None, HEAD))

    m = Model('geometry.btb.sunglasses', seed=24)
    b = m.bone('glasses', [0, 28, -4])
    sun_rows = ['########', '###..###', '###..###', '.##..##.']
    def sun(face, gx, gy, ch, rnd):
        lens = gy >= 1 and gx not in (3, 4) and gx not in (0, 7)
        if lens and face == 'front':
            if (gx, gy) in ((1, 1), (5, 1)):
                return (230, 240, 255, 255)
            return (rgb('#1a1d26') if gy < 3 else rgb('#2a3142')) + (255,)
        return shade(rgb('#0e0e10'), 0.95 + rnd.random() * 0.1) + (255,)
    shape(m, b, sun_rows, (-4, 29.5, -5), plane='xy', depth=1, paint=sun)
    temples(m, b, '#0e0e10')
    cos.append(('sunglasses', meta('sunglasses', 'Sonnenbrille', 'Sunglasses', 'glasses',
                desc_de='Breite, dunkle Sonnenbrille mit Spiegelreflex.', desc_en='Wide dark sunglasses with a mirror glint.', tags=['brille', 'sommer', 'cool']), m, None, HEAD))

    m = Model('geometry.btb.star_glasses', seed=25)
    b = m.bone('glasses', [0, 28, -4])
    star_rows = ['..#..', '#####', '.###.', '##.##']
    pink = ramp({'#': '#ff5fb8'})
    for x in (-4.5, 0.5):
        shape(m, b, star_rows, (x, 30, -5), plane='xy', depth=1, paint=pink)
    m.box(b, (-0.5, 28, -5), (1, 1, 1), solid('#ff5fb8'))
    temples(m, b, '#ff5fb8')
    cos.append(('star_glasses', meta('star_glasses', 'Party-Sternbrille', 'Star Party Glasses', 'glasses',
                desc_de='Pinke Sternbrille für jede Party.', desc_en='Pink star glasses for every party.', tags=['brille', 'party']), m, None, HEAD))

    # ---------------------------------------------------------------- 16-18 Rucksäcke
    m = Model('geometry.btb.backpack_classic', seed=26)
    b = m.bone('backpack', [0, 18, 2])
    canvas = bordered('#3f6fb5', '#2c5291', noise=0.05)
    m.box(b, (-4, 13, 2), (8, 10, 4), canvas, tint=True)
    m.box(b, (-3, 14, 6), (6, 5, 1), bordered('#5a8ad0', '#2c5291'), tint=True)
    m.box(b, (-4, 20, 1.5), (8, 3, 5), bordered('#335e9e', '#22457a'), inflate=0.15, tint=True)
    m.box(b, (-1, 19, 6.4), (2, 1, 1), solid('#d8b04a'))
    straps(m, b, '#5b3a1f')
    cos.append(('backpack_classic', meta('backpack_classic', 'Klassischer Rucksack', 'Classic Backpack', 'backpack',
                desc_de='Robuster Rucksack mit Fronttasche – in jeder Farbe.', desc_en='A sturdy backpack with front pocket – in any color.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FF9A9A', '#9AFF9A', '#FFE08A', '#D0A0FF', '#A0A0A0']},
                tags=['rucksack', 'schule', 'reise']), m, None, BODY))

    m = Model('geometry.btb.adventure_backpack', seed=27)
    b = m.bone('backpack', [0, 18, 2])
    m.box(b, (-3, 15, 2), (6, 8, 3), bordered('#6b7a3a', '#4c5728'))
    m.box(b, (-2, 16, 5), (4, 3, 1), bordered('#7d8c46', '#4c5728'))
    m.box(b, (-4, 23, 2.5), (8, 2, 2), faces(stripes('#b8402e', '#8f2f22', 2, horizontal=False), right=solid('#e0c49a'), left=solid('#e0c49a')))
    m.box(b, (-2, 22.8, 2.3), (1, 2, 2), solid('#5b3a1f'), inflate=0.15)
    m.box(b, (1, 22.8, 2.3), (1, 2, 2), solid('#5b3a1f'), inflate=0.15)
    m.box(b, (3, 16, 3), (2, 3, 2), faces(solid('#9aa3ab'), top=solid('#5b3a1f')))
    straps(m, b, '#5b3a1f')
    cos.append(('adventure_backpack', meta('adventure_backpack', 'Kleiner Abenteuerrucksack', 'Small Adventure Pack', 'backpack',
                desc_de='Kompakter Rucksack mit Schlafrolle und Feldflasche.', desc_en='A compact pack with bedroll and canteen.',
                tags=['rucksack', 'abenteuer', 'camping']), m, None, BODY))

    m = Model('geometry.btb.creature_backpack', seed=28)
    b = m.bone('backpack', [0, 18, 2])
    leather = palette_noise(['#8a5a2b', '#7a4e24', '#94622f'], [3, 2, 1])
    eyes_back = lambda f, x, y, w, h, r: ((rgb('#ffd23f') + (255,)) if f == 'back' and y in (3, 4) and x in (1, 2, w - 3, w - 2) else
                                          (rgb('#2a1a0e') + (255,)) if f == 'back' and y == 2 and x in (1, 2, w - 3, w - 2) else leather(f, x, y, w, h, r))
    m.box(b, (-4.5, 11, 2), (9, 13, 5), eyes_back, tint=True, emissive=lambda f, x, y, w, h, r: (rgb('#ffd23f') + (255,)) if f == 'back' and y in (3, 4) and x in (1, 2, w - 3, w - 2) else None)
    m.box(b, (-3.5, 12, 7), (7, 4, 1), bordered('#6e4520', '#4a2f19'), tint=True)
    m.box(b, (-4.5, 22, 1.5), (9, 2, 6), bordered('#7a4e24', '#4a2f19'), inflate=0.1, tint=True)
    m.box(b, (-5.5, 13, 3), (1, 6, 3), leather, tint=True)
    m.box(b, (4.5, 13, 3), (1, 6, 3), leather, tint=True)
    m.box(b, (-4, 24, 3), (2, 2, 2), leather, tint=True)
    m.box(b, (2, 24, 3), (2, 2, 2), leather, tint=True)
    straps(m, b, '#4a2f19')
    cos.append(('creature_backpack', meta('creature_backpack', 'Mini-Rucksack mit Augen', 'Creature Backpack', 'backpack',
                desc_de='Ein Leder-Rucksack, der zurückschaut – in verschiedenen Farben.', desc_en='A leather backpack that looks back – in different colors.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#C0C0C0', '#FFB0B0', '#B0FFC0', '#B0C8FF', '#FFD890']},
                tags=['rucksack', 'monster', 'leder']), m, None, BODY))

    # ---------------------------------------------------------------- 19 Flügel
    m = Model('geometry.btb.wings', seed=29)
    wing_rows = [
        '......aaaa',
        '...aaaaaaa',
        '.aaaaaaaab',
        'aaaaaaabbb',
        'aaaaabbbbc',
        'aaabbbbcc.',
        'abbbbccc..',
        'bbbcccc...',
        'bccc......',
        'cc........',
    ]
    feather = ramp({'a': '#ffffff', 'b': '#eef1f6', 'c': '#d8dee8'})
    for name, sx in (('left_wing', 1), ('right_wing', -1)):
        bb = m.bone(name, [1 * sx, 21, 3], rotation=[0, -20 * sx, 0])
        x0 = 1 if sx > 0 else -11
        shape(m, bb, wing_rows, (x0, 23, 2.6), plane='xy', depth=1, paint=feather, tint=True, mirror_x=sx < 0)
    cos.append(('wings', meta('wings', 'Flügel', 'Wings', 'wings',
                desc_de='Weiße Federflügel, die sanft schlagen – einfärbbar.', desc_en='White feathered wings that flap softly – recolorable.',
                animations=['animation.btb.wings.flap'],
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#303030', '#FFB0E0', '#A0D8FF', '#FFE08A']},
                tags=['flügel', 'engel', 'fliegen', 'wings']),
                m, {'animation.btb.wings.flap': anim(left_wing={'rotation': [0, '-10 - math.sin(q.anim_time * 150) * 12', 0]},
                                                     right_wing={'rotation': [0, '10 + math.sin(q.anim_time * 150) * 12', 0]})}, BODY))

    # ---------------------------------------------------------------- 20 Umhang
    m = Model('geometry.btb.cape', seed=30)
    b = m.bone('cape', [0, 24, 2])
    cape_paint = lambda f, x, y, w, h, r: ((rgb('#e8b84a') + (255,)) if f in ('back', 'front') and (x == 0 or x == w - 1 or y == h - 1) else shade(rgb('#b3262e'), 0.92 + r.random() * 0.12) + (255,))
    m.box(b, (-5, 8, 2), (10, 16, 1), cape_paint, tint=True)
    m.box(b, (-4, 23, 1.5), (8, 1, 1), solid('#e8b84a'), inflate=0.1)
    cos.append(('cape', meta('cape', 'Umhang', 'Cape', 'cape',
                desc_de='Ein langer Umhang mit Goldborte, der beim Laufen weht.', desc_en='A long cape with golden trim that flows while walking.',
                animations=['animation.btb.cape.flow'], hide_layers=[],
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#6060FF', '#40C060', '#202020', '#B060FF']},
                tags=['umhang', 'cape', 'held']),
                m, {'animation.btb.cape.flow': anim(cape={'rotation': ['6 + q.modified_move_speed * 55 + math.sin(q.anim_time * 110) * 2', 0, 'math.sin(q.anim_time * 70) * 1.5']})}, BODY))

    # ---------------------------------------------------------------- 21 Mini-Schild
    m = Model('geometry.btb.mini_shield', seed=31)
    b = m.bone('shield', [0, 18, 3], rotation=[0, 0, 15])
    emblem = lambda f, x, y, w, h, r: ((rgb('#b9c2cc') + (255,)) if (x == 0 or y == 0 or x == w - 1 or y == h - 1) else
                                       (rgb('#e3b23c') + (255,)) if f == 'back' and (x == w // 2 or y == h // 2 - 1) else WOOD(f, x, y, w, h, r))
    m.box(b, (-3, 14, 2.5), (6, 8, 1), emblem)
    m.box(b, (-0.5, 17.5, 3.4), (1, 1, 1), solid('#9aa3ab'), inflate=0.1)
    cos.append(('mini_shield', meta('mini_shield', 'Mini-Schild', 'Mini Shield', 'shield',
                desc_de='Ein kleiner Holzschild mit Wappen auf dem Rücken.', desc_en='A little wooden shield with a crest on your back.',
                tags=['schild', 'ritter']), m, None, BODY))

    # ---------------------------------------------------------------- 22 Kopfhörer
    m = Model('geometry.btb.headphones', seed=32)
    b = m.bone('headphones', [0, 28, 0])
    band = solid('#e8e8ec', noise=0.04)
    m.box(b, (-5, 32, -1), (10, 1, 2), band, tint=True)
    m.box(b, (-5, 29, -1), (1, 3, 2), band, tint=True)
    m.box(b, (4, 29, -1), (1, 3, 2), band, tint=True)
    cup = faces(solid('#2a2a30'), right=bordered('#3c3c44', '#5ac8ff'), left=bordered('#3c3c44', '#5ac8ff'))
    m.box(b, (-6, 25, -2), (2, 4, 4), cup)
    m.box(b, (4, 25, -2), (2, 4, 4), cup)
    cos.append(('headphones', meta('headphones', 'Kopfhörer', 'Headphones', 'headphones',
                desc_de='Große Over-Ear-Kopfhörer mit farbigem Bügel.', desc_en='Big over-ear headphones with a colored band.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FF6A6A', '#6AD0FF', '#FFE16A', '#B06AFF', '#404040']},
                tags=['musik', 'kopfhörer']), m, None, HEAD))

    # ---------------------------------------------------------------- 23 Partyhut
    m = Model('geometry.btb.party_hat', seed=33)
    b = m.bone('hat', [1, 32, 0], rotation=[0, 0, -12])
    pstripes = stripes('#ff5fb8', '#5ac8ff', 1)
    for o, s in [((-3, 32, -3), (6, 2, 6)), ((-2, 34, -2), (4, 2, 4)), ((-1, 36, -1), (2, 2, 2)), ((-0.5, 38, -0.5), (1, 1, 1))]:
        m.box(b, o, s, pstripes, tint=True)
    m.box(b, (-1, 39, -1), (2, 2, 2), solid('#fff7c0'), inflate=-0.1)
    m.box(b, (-3, 32, -3), (6, 1, 6), solid('#ffd23f'), inflate=0.15)
    cos.append(('party_hat', meta('party_hat', 'Partyhut', 'Party Hat', 'hat',
                desc_de='Gestreifter Partyhut mit Bommel.', desc_en='A striped party hat with a pompom.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFD0D0', '#D0FFD0', '#D0D0FF']},
                tags=['party', 'hut', 'geburtstag']), m, None, HEAD))

    # ---------------------------------------------------------------- 24 Schulterbegleiter (Schleimi)
    m = Model('geometry.btb.shoulder_buddy', seed=34)
    b = m.bone('buddy', [6.5, 24, 0])
    face_px = lambda f, x, y, w, h, r: ((rgb('#1c3d1c') + (255,)) if f == 'front' and y == 1 and x in (0, 2) else
                                        (rgb('#1c3d1c') + (255,)) if f == 'front' and y == 3 and x == 1 else None)
    m.box(b, (5.5, 25, -1), (3, 3, 3), solid('#4fae3a', noise=0.05))
    m.box(b, (5.5, 25, -1.01), (3, 3, 1), face_px, inflate=0.01)
    m.box(b, (4.5, 24, -2), (5, 5, 5), solid('#7fd65f', alpha=150, noise=0.04))
    cos.append(('shoulder_buddy', meta('shoulder_buddy', 'Schleimi', 'Slimy Buddy', 'companion', 'left_shoulder',
                desc_de='Ein kleiner Schleim-Freund, der auf der Schulter hüpft.', desc_en='A little slime friend bouncing on your shoulder.',
                animations=['animation.btb.shoulder_buddy.bounce'], tags=['begleiter', 'schleim', 'haustier']),
                m, {'animation.btb.shoulder_buddy.bounce': anim(buddy={'position': [0, 'math.abs(math.sin(q.anim_time * 160)) * 1.2', 0],
                                                                        'scale': ['1 + math.sin(q.anim_time * 320) * 0.04', '1 - math.sin(q.anim_time * 320) * 0.06', '1 + math.sin(q.anim_time * 320) * 0.04']})}, HEAD_BODY))

    # ---------------------------------------------------------------- 25 Schultertasche
    # Gurt von der linken Schulter diagonal über Brust und Rücken zur rechten Hüfte (Pixeltreppe ohne Lücken), Tasche vorne an der Hüfte.
    m = Model('geometry.btb.shoulder_bag', seed=35)
    b = m.bone('body', [0, 24, 0])
    strap = solid('#5b3a1f', noise=0.06)
    for i in range(12):
        x = 3 - round(i * 7 / 11)
        y = 23 - i
        m.box(b, (x - 0.5, y, -2.6), (2, 1, 1), strap, inflate=-0.1, tint=True)
        m.box(b, (x - 0.5, y, 1.6), (2, 1, 1), strap, inflate=-0.1, tint=True)
    m.box(b, (2.5, 24, -2.6), (2, 1, 5.2), strap, inflate=-0.1, tint=True)
    bag = faces(bordered('#8a5a2b', '#5b3a1f'), top=solid('#6e4520'), front=lambda f_, x, y, w, h, r: (
        (rgb('#d8b04a') + (255,)) if (y == 1 and x == w // 2) else (rgb('#6e4520') + (255,)) if y <= 1 else shade(rgb('#8a5a2b'), 0.9 + r.random() * 0.15) + (255,)))
    m.box(b, (-4.5, 9, -3.6), (4, 4, 2), bag, tint=True)
    cos.append(('shoulder_bag', meta('shoulder_bag', 'Schultertasche', 'Shoulder Bag', 'shoulder_accessory', 'body',
                desc_de='Umhängetasche aus Leder: Gurt quer über die Brust, Tasche vorne an der Hüfte.',
                desc_en='A leather messenger bag: strap across the chest, bag at the front of the hip.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#909090', '#C08080', '#80A0C0']},
                tags=['tasche', 'leder', 'bag']), m, None, FULL))

    # ---------------------------------------------------------------- 26 Armband
    m = Model('geometry.btb.bracelet', seed=36)
    b = m.bone('rightArm', [-5, 22, 0])
    m.box(b, (-8, 13, -2), (4, 1, 4), bordered('#ffcf3f', '#c99417'), inflate=0.45, tint=True)
    m.box(b, (-6.5, 12.8, -2.9), (1, 1, 1), solid('#3fd6c8'), inflate=0.05)
    cos.append(('bracelet', meta('bracelet', 'Armband', 'Bracelet', 'bracelet', 'right_arm',
                desc_de='Goldenes Armband mit Türkis.', desc_en='A golden bracelet with a turquoise stone.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#D8D8E0', '#FFB0A0']},
                tags=['schmuck', 'arm']), m, None, FULL))

    # ---------------------------------------------------------------- 27 Gürtel
    m = Model('geometry.btb.belt', seed=37)
    b = m.bone('belt', [0, 12, 0])
    m.box(b, (-4, 12, -2), (8, 2, 4), bordered('#6e4520', '#4a2f19'), inflate=0.3, tint=True)
    m.box(b, (-1.5, 12, -2.8), (3, 2, 1), bordered('#ffcf3f', '#a87a12'), inflate=0.05)
    m.box(b, (3.4, 10.5, -1), (2, 3, 2), bordered('#8a5a2b', '#5b3a1f'))
    cos.append(('belt', meta('belt', 'Dekorativer Gürtel', 'Decorative Belt', 'belt', 'body',
                desc_de='Ledergürtel mit großer Schnalle und kleiner Tasche.', desc_en='A leather belt with a big buckle and a small pouch.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#505050', '#A07050']},
                tags=['gürtel', 'leder']), m, None, BODY))

    # ---------------------------------------------------------------- 28 Mini-Drache
    m = Model('geometry.btb.mini_dragon', seed=38)
    root = m.bone('dragon', [-6.5, 24, 0])
    scales = palette_noise(['#6a3fb5', '#5a339c', '#7d52c9', '#4e2a8a'], [4, 3, 2, 1])
    belly = solid('#d9b85c')
    m.box(root, (-8, 24.5, -1), (3, 3, 5), faces(scales, bottom=belly))
    hd = m.bone('head', [-6.5, 27, -1], parent='dragon')
    eyes = lambda f, x, y, w, h, r: (rgb('#ffe14a') + (255,)) if (f in ('right', 'left') and y == 0 and x == 1) else None
    m.box(hd, (-8, 26.5, -4), (3, 3, 3), lambda f, x, y, w, h, r: eyes(f, x, y, w, h, r) or scales(f, x, y, w, h, r), emissive=eyes)
    m.box(hd, (-7.5, 26.5, -5), (2, 2, 1), scales)
    m.box(hd, (-8, 29.5, -2), (1, 1, 1), solid('#e8e0c8'))
    m.box(hd, (-6, 29.5, -2), (1, 1, 1), solid('#e8e0c8'))
    m.box(root, (-7, 25, 4), (1, 1, 3), scales)
    m.box(root, (-7, 25.5, 7), (1, 1, 1), solid('#d9b85c'), inflate=0.15)
    wing = solid('#9b7ae0', noise=0.08)
    lw = m.bone('wing_l', [-5, 27.5, 1], parent='dragon')
    m.box(lw, (-5, 27, -0.5), (4, 1, 3), wing, inflate=-0.2)
    rw = m.bone('wing_r', [-8, 27.5, 1], parent='dragon')
    m.box(rw, (-12, 27, -0.5), (4, 1, 3), wing, inflate=-0.2)
    cos.append(('mini_dragon', meta('mini_dragon', 'Mini-Drache', 'Mini Dragon', 'companion', 'right_shoulder',
                desc_de='Ein kleiner violetter Drache mit leuchtenden Augen auf der Schulter.', desc_en='A tiny purple dragon with glowing eyes on your shoulder.',
                animations=['animation.btb.mini_dragon.idle'], slots=['shoulder_right'], tags=['drache', 'begleiter', 'fantasy']),
                m, {'animation.btb.mini_dragon.idle': anim(
                    dragon={'position': [0, 'math.sin(q.anim_time * 120) * 0.4', 0]},
                    head={'rotation': ['math.sin(q.anim_time * 60) * 6', 'math.sin(q.anim_time * 45) * 12', 0]},
                    wing_l={'rotation': [0, 0, 'math.sin(q.anim_time * 540) * 28']},
                    wing_r={'rotation': [0, 0, 'math.sin(q.anim_time * 540) * -28']})}, HEAD_BODY))

    # ---------------------------------------------------------------- Haare & Gesicht
    m = Model('geometry.btb.spiky_hair', seed=39)
    b = m.bone('hair', [0, 28, 0])
    hair = palette_noise(['#6b4423', '#5a381c', '#7c5230', '#4e2f17'], [4, 3, 2, 1])
    m.box(b, (-4, 30, -4), (8, 2, 8), hair, inflate=0.6, tint=True)
    m.box(b, (-4, 27, 2), (8, 3, 2), hair, inflate=0.6, tint=True)
    m.box(b, (-4, 28, -4), (2, 2, 1), hair, inflate=0.6, tint=True)
    for x, z, h in [(-3, -2, 2), (0, -3, 3), (2, 0, 2), (-2, 1, 3), (1, 2, 2), (-1, -1, 2)]:
        m.box(b, (x, 32, z), (2, h, 2), hair, rotation=[(z) * 4, 0, -x * 4], tint=True)
    cos.append(('spiky_hair', meta('spiky_hair', 'Stachelfrisur', 'Spiky Hair', 'hairstyle',
                desc_de='Wilde Stachelfrisur – in jeder Haarfarbe.', desc_en='Wild spiky hair – in any hair color.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFE0A0', '#202020', '#FF8060', '#A0C0FF', '#FFB0E0']},
                tags=['haare', 'frisur']), m, None, HEAD))

    m = Model('geometry.btb.full_beard', seed=40)
    b = m.bone('beard', [0, 24, -4])
    beard = palette_noise(['#6b4423', '#5a381c', '#7c5230'], [3, 2, 1])
    m.box(b, (-4, 23, -4.6), (8, 3, 1), beard, tint=True)
    m.box(b, (-3, 22, -4.6), (6, 1, 1), beard, tint=True)
    m.box(b, (-4.6, 24, -4), (1, 3, 3), beard, tint=True)
    m.box(b, (3.6, 24, -4), (1, 3, 3), beard, tint=True)
    m.box(b, (-2, 26, -4.7), (4, 1, 1), beard, tint=True)
    cos.append(('full_beard', meta('full_beard', 'Vollbart', 'Full Beard', 'beard',
                desc_de='Dichter Vollbart für echte Abenteurer.', desc_en='A thick full beard for true adventurers.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFE0A0', '#303030', '#E0E0E0', '#FF9060']},
                tags=['bart', 'gesicht']), m, None, HEAD))

    m = Model('geometry.btb.mustache', seed=41)
    b = m.bone('mustache', [0, 26, -4])
    st = solid('#3b2615', noise=0.08)
    m.box(b, (-3, 25.5, -4.7), (6, 1, 1), st, tint=True)
    m.box(b, (-4, 26.5, -4.7), (1, 1, 1), st, tint=True)
    m.box(b, (3, 26.5, -4.7), (1, 1, 1), st, tint=True)
    cos.append(('mustache', meta('mustache', 'Schnurrbart', 'Mustache', 'mustache',
                desc_de='Gezwirbelter Schnurrbart.', desc_en='A curled mustache.', tint={'enabled': True, 'default': '#FFFFFF'},
                tags=['bart', 'gesicht']), m, None, HEAD))

    # ---------------------------------------------------------------- Helm & Maske
    m = Model('geometry.btb.knight_helmet', seed=42)
    b = m.bone('helmet', [0, 24, 0])
    iron = lambda f, x, y, w, h, r: ((rgb('#1a1c20') + (255,)) if f == 'front' and y == 3 and 1 <= x <= w - 2 and x != w // 2 else
                                     shade(rgb('#b8c0c8'), 0.88 + r.random() * 0.14) + (255,))
    m.box(b, (-4, 24, -4), (8, 8, 8), iron, inflate=0.7)
    m.box(b, (-0.5, 32.5, -3), (1, 3, 6), solid('#c0262e', noise=0.12), inflate=0.1)
    cos.append(('knight_helmet', meta('knight_helmet', 'Ritterhelm', 'Knight Helmet', 'helmet',
                desc_de='Polierter Eisenhelm mit rotem Federbusch.', desc_en='A polished iron helmet with a red plume.',
                hide_layers=['hat'], tags=['helm', 'ritter', 'rüstung']), m, None, HEAD))

    m = Model('geometry.btb.fox_mask', seed=43)
    b = m.bone('mask', [0, 28, -4])
    fox = lambda f, x, y, w, h, r: ((rgb('#ffffff') + (255,)) if f == 'front' and y >= 3 and abs(x - (w - 1) / 2) < 2.5 else
                                    (rgb('#1a1a1a') + (255,)) if f == 'front' and y == 1 and x in (1, w - 2) else
                                    shade(rgb('#f08a2e'), 0.9 + r.random() * 0.12) + (255,))
    m.box(b, (-4, 25, -5), (8, 5, 1), fox)
    m.box(b, (-1, 25, -6), (2, 2, 1), solid('#ffffff'))
    m.box(b, (-0.5, 26.5, -6.4), (1, 1, 1), solid('#1a1a1a'), inflate=-0.1)
    m.box(b, (-4, 30, -5), (2, 3, 1), faces(solid('#f08a2e'), front=bordered('#1a1a1a', '#f08a2e')))
    m.box(b, (2, 30, -5), (2, 3, 1), faces(solid('#f08a2e'), front=bordered('#1a1a1a', '#f08a2e')))
    cos.append(('fox_mask', meta('fox_mask', 'Fuchsmaske', 'Fox Mask', 'mask',
                desc_de='Eine Fuchsmaske mit spitzen Ohren.', desc_en='A fox mask with pointy ears.', tags=['maske', 'fuchs']), m, None, HEAD))

    # ---------------------------------------------------------------- Oberkörper: Weste
    m = Model('geometry.btb.explorer_vest', seed=44)
    b = m.bone('body', [0, 24, 0])
    vest = lambda f, x, y, w, h, r: (None if f == 'front' and 3 <= x <= 4 else
                                     (rgb('#d8b04a') + (255,)) if f == 'front' and x in (1, 6) and y in (5, 6) else
                                     shade(rgb('#7a5233'), 0.88 + r.random() * 0.14) + (255,))
    m.box(b, (-4, 12, -2), (8, 12, 4), vest, inflate=0.55, tint=True)
    cos.append(('explorer_vest', meta('explorer_vest', 'Abenteurer-Weste', 'Explorer Vest', 'vest',
                desc_de='Offene Lederweste mit Messingknöpfen.', desc_en='An open leather vest with brass buttons.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#708090', '#406040', '#202020']},
                tags=['weste', 'kleidung']), m, None, BODY))

    # ---------------------------------------------------------------- Arme & Beine
    m = Model('geometry.btb.gloves', seed=45)
    glove = faces(bordered('#4a3a2c', '#2e231a'), bottom=solid('#2e231a'))
    for name, x, piv in (('rightArm', -8, [-5, 22, 0]), ('leftArm', 4, [5, 22, 0])):
        bb = m.bone(name, piv)
        m.box(bb, (x, 12, -2), (4, 4, 4), glove, inflate=0.3, tint=True)
        m.box(bb, (x, 16, -2), (4, 1, 4), solid('#c99417'), inflate=0.35)
    cos.append(('gloves', meta('gloves', 'Lederhandschuhe', 'Leather Gloves', 'gloves', 'right_arm',
                desc_de='Feste Handschuhe mit goldenem Saum (beide Hände).', desc_en='Sturdy gloves with a golden hem (both hands).',
                slots=['hands'], tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FF9090', '#202020']},
                tags=['handschuhe', 'hände']), m, None, FULL))

    m = Model('geometry.btb.boots', seed=46)
    boot = faces(bordered('#5b3a1f', '#3a2412'), bottom=solid('#2a1a0e'))
    for name, x, piv in (('rightLeg', -4, [-1.9, 12, 0]), ('leftLeg', 0, [1.9, 12, 0])):
        bb = m.bone(name, piv)
        m.box(bb, (x, 0, -2), (4, 5, 4), boot, inflate=0.35, tint=True)
        m.box(bb, (x, 0, -2), (4, 1, 4), solid('#2a1a0e'), inflate=0.45)
        m.box(bb, (x, 5, -2), (4, 1, 4), solid('#8a5a2b'), inflate=0.45, tint=True)
    cos.append(('boots', meta('boots', 'Wanderstiefel', 'Hiking Boots', 'boots', 'right_leg',
                desc_de='Robuste Lederstiefel mit Umschlag.', desc_en='Sturdy leather boots with a cuff.',
                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#505050', '#A08060']},
                tags=['schuhe', 'stiefel']), m, None, FULL))

    # ---------------------------------------------------------------- Besonderes
    m = Model('geometry.btb.floating_crystal', seed=47)
    b = m.bone('crystal', [9, 34, 0])
    cry = palette_noise(['#6fe8ff', '#4fd0f0', '#a8f4ff'], [3, 2, 1])
    m.box(b, (8, 33, -1), (2, 2, 2), cry, emissive=solid('#9cf2ff', alpha=160))
    m.box(b, (8.5, 35, -0.5), (1, 1, 1), cry, emissive=solid('#c8f8ff', alpha=200))
    m.box(b, (8.5, 32, -0.5), (1, 1, 1), cry, emissive=solid('#c8f8ff', alpha=200))
    cos.append(('floating_crystal', meta('floating_crystal', 'Schwebender Kristall', 'Floating Crystal', 'floating',
                desc_de='Ein leuchtender Kristall, der neben dem Kopf kreist.', desc_en='A glowing crystal hovering next to your head.',
                animations=['animation.btb.floating_crystal.hover'],
                glow={'color': '#7FE8FF', 'size': 5, 'intensity': 0.4, 'offset': [9, 2, 0], 'flicker': False},
                particles={'type': 'minecraft:end_rod', 'rate': 0.3, 'offset': [9, 2, 0], 'spread': 2},
                tags=['kristall', 'magie']),
                m, {'animation.btb.floating_crystal.hover': anim(crystal={'rotation': [0, 'q.anim_time * 120', 0], 'position': [0, 'math.sin(q.anim_time * 100) * 1', 0]})}, HEAD))

    m = Model('geometry.btb.fireflies', seed=48)
    root = m.bone('swarm', [0, 18, 0])
    glowbug = solid('#e8ff6a', noise=0.05)
    anims = {}
    for i, (r_, y, speed) in enumerate([(9, 20, 70), (11, 14, -55), (8, 27, 90)]):
        fb = m.bone('fly%d' % i, [0, y, 0], parent='swarm')
        m.box(fb, (r_ - 0.5, y - 0.5, -0.5), (1, 1, 1), glowbug, emissive=glowbug, inflate=-0.1)
        anims['fly%d' % i] = {'rotation': [0, 'q.anim_time * %d + %d' % (speed, i * 120), 0], 'position': [0, 'math.sin(q.anim_time * %d) * 1.5' % (abs(speed) * 2), 0]}
    cos.append(('fireflies', meta('fireflies', 'Glühwürmchen', 'Fireflies', 'moving_decoration', 'body',
                desc_de='Drei Glühwürmchen umkreisen dich – mit echten Leuchtpartikeln.', desc_en='Three fireflies circle around you – with glowing particles.',
                animations=['animation.btb.fireflies.orbit'],
                particles={'type': 'minecraft:firefly', 'rate': 0.6, 'offset': [0, 0, 0], 'spread': 18},
                slots=['floating'], tags=['natur', 'licht', 'partikel']),
                m, {'animation.btb.fireflies.orbit': anim(**anims)}, BODY))

    # ---------------------------------------------------------------- write
    back = {'terrarium', 'backpack_classic', 'adventure_backpack', 'creature_backpack', 'wings', 'cape', 'mini_shield', 'fire_halo'}
    for folder, mt, model, anims_, mannequin in cos:
        view = 'back' if folder in back else 'right' if folder in ('mini_dragon',) else 'front'
        write_cosmetic(ROOT, folder, mt, model, anims_, mannequin, view=view)
    pack = {'format': 1, 'id': 'btb_builtin_starter', 'version': '1.0.0',
            'name': {'de': 'BTB Starter', 'en': 'BTB Starter'},
            'description': {'de': 'Die eingebauten Cosmetics von BTB Skin Studio.', 'en': 'The built-in cosmetics of BTB Skin Studio.'},
            'author': AUTHOR, 'icon': 'pack.png', 'categories': ['BTB', 'Starter']}
    with open(os.path.join(ROOT, 'pack.json'), 'w', encoding='utf-8') as f:
        json.dump(pack, f, indent=2, ensure_ascii=False)
    make_icon(os.path.join(ROOT, 'pack.png'), 64)
    make_icon('src/main/resources/assets/btb_skinstudio/icon.png', 128)
    print('%d cosmetics written to %s' % (len(cos), ROOT))


def make_icon(path, size):
    """BTB Skin Studio icon: rounded dark tile with a pixel crown and a hanger."""
    s = 4
    img = Image.new('RGBA', (size * s, size * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size * s - 1, size * s - 1], radius=size * s // 5, fill=(28, 30, 38, 255))
    d.rounded_rectangle([size * s * 0.06, size * s * 0.06, size * s * 0.94, size * s * 0.94], radius=size * s // 6, outline=(255, 255, 255, 40), width=s * 2)
    px = size * s // 16
    crown = ['...........', '.#...#...#.', '.##.###.##.', '.#########.', '.#########.', '.#.#.#.#.#.', '.#########.']
    ox, oy = size * s // 2 - len(crown[0]) * px // 2, size * s // 4
    for j, row in enumerate(crown):
        for i, ch in enumerate(row):
            if ch == '#':
                c = (255, 207, 63, 255) if j < 5 else (214, 40, 58, 255) if (i % 2) else (255, 207, 63, 255)
                d.rectangle([ox + i * px, oy + j * px, ox + (i + 1) * px - 1, oy + (j + 1) * px - 1], fill=c)
    # hanger
    cx, cy = size * s // 2, int(size * s * 0.7)
    d.line([(cx - 5 * px, cy + 2 * px), (cx, cy - px), (cx + 5 * px, cy + 2 * px), (cx - 5 * px, cy + 2 * px)], fill=(232, 236, 244, 255), width=max(2, px // 2))
    d.arc([cx - px, cy - 3 * px, cx + px, cy - px], 180, 360, fill=(232, 236, 244, 255), width=max(2, px // 2))
    img = img.resize((size, size), Image.LANCZOS)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == '__main__':
    build()
