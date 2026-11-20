#!/usr/bin/env python3
"""Generates the BTB skin pack (own designs, 64x64, standard Java/Bedrock layout).

The pack is written in the Bedrock skin pack layout (skins.json, texts/*.lang, PNGs) to
src/main/resources/assets/btb_skinstudio/skin_packs/btb_skins/ and is picked up like any
skin pack inside a resource pack. All textures are drawn here, pixel by pixel.
"""
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources', 'assets', 'btb_skinstudio', 'skin_packs', 'btb_skins')


def rgb(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


class Skin:
    def __init__(self, seed, slim=False):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.rnd = random.Random(seed)
        self.slim = slim

    def set(self, x, y, c, a=255):
        self.px[x, y] = tuple(c) + (a,)

    def box(self, u, v, w, h, d, paint, overlay=False):
        """Paints all six faces of a box-UV part. paint(face, x, y, w, h) -> color or None.
        face: top, bottom, right, front, left, back; x/y are face-local, y=0 at the top of the part
        (top/bottom faces: y=0 at the back)."""
        faces = {
            'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
            'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
            'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in faces.items():
            for y in range(fh):
                for x in range(fw):
                    c = paint(face, x, y, fw, fh)
                    if c is None:
                        continue
                    c = self.shade_face(c, face, y, fh)
                    if len(c) == 4:
                        self.set(fx + x, fy + y, c[:3], c[3])
                    else:
                        self.set(fx + x, fy + y, c)

    @staticmethod
    def shade_face(c, face, y, h):
        """Hand-painted look: light from above, slightly darker towards the bottom and on the sides.
        Very bright pixels (eye whites, highlights) are kept as they are."""
        rgb_ = c[:3]
        if sum(rgb_) > 690:
            return c
        if face == 'top':
            f = 1.06
        elif face == 'bottom':
            f = 0.78
        else:
            f = 1.05 - 0.13 * (y / max(1, h - 1))
            if face in ('left', 'right'):
                f *= 0.93
            elif face == 'back':
                f *= 0.96
        out = shade(rgb_, f)
        return out + tuple(c[3:])

    # part helpers (base layer and overlay layer)
    def head(self, paint, overlay=False):
        self.box(32 if overlay else 0, 0, 8, 8, 8, paint)

    def body(self, paint, overlay=False):
        self.box(16, 32 if overlay else 16, 8, 12, 4, paint)

    def right_arm(self, paint, overlay=False):
        self.box(40, 32 if overlay else 16, 3 if self.slim else 4, 12, 4, paint)

    def left_arm(self, paint, overlay=False):
        self.box(48 if overlay else 32, 48, 3 if self.slim else 4, 12, 4, paint)

    def right_leg(self, paint, overlay=False):
        self.box(0, 32 if overlay else 16, 4, 12, 4, paint)

    def left_leg(self, paint, overlay=False):
        self.box(0 if overlay else 16, 48, 4, 12, 4, paint)

    def noisy(self, c, amount=0.06):
        return shade(c, 1 - amount + self.rnd.random() * amount * 2)

    def save(self, path):
        self.img.save(path)


# ------------------------------------------------------------------ shared face painting

def face(s, skin_c, hair_c, eye_c, hair_rows_front=2, fringe=None, mouth=True, brows=True, cheeks=None, beard=None):
    """Head with hair on top/back/sides, a face with eyes, brows and mouth on the front."""
    skin_c, hair_c, eye_c = rgb(skin_c), rgb(hair_c), rgb(eye_c)
    fringe = fringe or {}

    def p(f, x, y, w, h):
        if f == 'top':
            return s.noisy(hair_c, 0.1)
        if f == 'bottom':
            return shade(skin_c, 0.85)
        if f == 'back':
            return s.noisy(hair_c, 0.1) if y < 7 else shade(skin_c, 0.9)
        if f in ('left', 'right'):
            # hair covers the upper part and the back half of the side
            back = x >= 4 if f == 'right' else x < 4
            if y < 3 or (back and y < 6):
                return s.noisy(hair_c, 0.1)
            if y == 4 and ((f == 'right' and x == 2) or (f == 'left' and x == 5)):
                return shade(skin_c, 0.8)  # ear
            return s.noisy(skin_c, 0.03)
        # front
        if y < hair_rows_front + fringe.get(x, 0):
            return s.noisy(hair_c, 0.1)
        if brows and y == 3 and x in (1, 2, 5, 6):
            return shade(hair_c, 0.85)
        if y == 4 and x in (1, 6):
            return (245, 245, 245)
        if y == 4 and x in (2, 5):
            return eye_c
        if cheeks and y == 5 and x in (1, 6):
            return rgb(cheeks)
        if beard and y >= 5 and (y >= 6 or x in (0, 1, 6, 7)):
            if mouth and y == 6 and x in (3, 4):
                return shade(rgb(beard), 0.6)
            return s.noisy(rgb(beard), 0.08)
        if mouth and y == 6 and x in (3, 4):
            return shade(skin_c, 0.72)
        if y == 5 and x in (3, 4):
            return shade(skin_c, 0.9)  # nose
        return s.noisy(skin_c, 0.03)
    s.head(p)


def hair_overlay(s, hair_c, front_tufts=(), side_rows=4, back_rows=8, top=True):
    """Hat layer for hair volume: tufts over the forehead, sides and back."""
    hair_c = rgb(hair_c)

    def p(f, x, y, w, h):
        if f == 'top':
            return s.noisy(shade(hair_c, 1.08), 0.12) if top else None
        if f == 'front':
            return s.noisy(hair_c, 0.12) if (y == 0 or (y == 1 and x in front_tufts)) else None
        if f == 'back':
            return s.noisy(hair_c, 0.12) if y < back_rows else None
        if f in ('left', 'right'):
            back = x >= 4 if f == 'right' else x < 4
            return s.noisy(hair_c, 0.12) if (y < side_rows - 2 or (back and y < side_rows)) else None
        return None
    s.head(p, overlay=True)


def plaid(base, line, dark):
    base, line, dark = rgb(base), rgb(line), rgb(dark)

    def c(x, y):
        a, b = x % 4 == 3, y % 4 == 3
        if a and b:
            return dark
        if a or b:
            return line
        return base
    return c


# ------------------------------------------------------------------ skins

def lumberjack():
    """Steve in a lumberjack outfit: red-black flannel shirt with rolled-up sleeves, jeans, belt, boots.
    Only the outfit is drawn here; the head (and the skin colour of the forearms) is Steve's own and is taken
    from the installed game when the pack is loaded (see btb_bases.json), so no Mojang texture is shipped."""
    s = Skin(1)
    shirt = plaid('#b0261e', '#2a1414', '#160c0c')
    jeans, belt = rgb('#3e5f8c'), rgb('#5a3a1e')

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return shade(rgb('#b0261e'), 0.8) if f == 'top' else rgb('#2f4a6e')
        if y >= 10:
            if y == 10:
                return rgb('#c9a24a') if (f == 'front' and x in (3, 4)) else belt
            return s.noisy(jeans, 0.06)
        if f == 'front' and y < 2 and x in (3, 4):
            return (232, 232, 228)  # undershirt at the collar
        if f == 'front' and x == 4 and 2 <= y < 10:
            return shade(shirt(x, y), 0.75) if y % 3 else (90, 70, 60)  # button line
        return s.noisy(shirt(x, y), 0.05)
    s.body(body)

    def arm(f, x, y, w, h):
        if f == 'top':
            return rgb('#b0261e')
        if f == 'bottom':
            return None
        if y < 6:
            return s.noisy(shirt(x, y), 0.05)
        if y in (6, 7):
            return s.noisy(rgb('#e8e2d6') if y == 6 else rgb('#c9c2b4'), 0.04)  # rolled-up cuff
        return None  # Steve's own forearms and hands
    s.right_arm(arm)
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if f == 'bottom':
            return rgb('#2a1d14')
        if y >= 10:
            return s.noisy(rgb('#3a2a1e'), 0.08)
        if f == 'top':
            return jeans
        return s.noisy(shade(jeans, 0.9 if x in (0, w - 1) else 1), 0.06)
    s.right_leg(leg)
    s.left_leg(leg)

    def sleeves(f, x, y, w, h):
        # thicker rolled cuff on the outer layer
        return s.noisy(rgb('#e8e2d6'), 0.04) if (y == 6 and f not in ('top', 'bottom')) else None
    s.right_arm(sleeves, overlay=True)
    s.left_arm(sleeves, overlay=True)
    return s


def adventurer():
    """Green fluffy jacket over the shoulders, blue overalls, grey glove, short hair with a lighter tip."""
    s = Skin(2)
    face(s, '#d29a74', '#2a1d18', '#3b6a3a', fringe={1: 1, 6: 1})
    hair_overlay(s, '#3a271d', front_tufts=(2, 3), side_rows=4)
    green, green_d = rgb('#7cbf3a'), rgb('#4f8a24')
    denim, denim_d = rgb('#3d6fb0'), rgb('#2c5590')

    def body(f, x, y, w, h):
        if f == 'top':
            return s.noisy(green, 0.1)
        if f == 'bottom':
            return denim_d
        if y < 3:
            return s.noisy(green if (x + y) % 3 else green_d, 0.1)  # fluffy collar
        if f == 'front' and y < 6 and x in (2, 5):
            return shade(denim, 0.9)  # overall straps
        if f == 'front' and y == 6 and x in (2, 5):
            return rgb('#d8c060')  # buttons
        if y < 6:
            return s.noisy(rgb('#f0e6d0'), 0.04)  # shirt
        if f == 'front' and 7 <= y <= 8 and 3 <= x <= 4:
            return shade(denim, 0.8)  # bib pocket
        return s.noisy(denim, 0.05)
    s.body(body)

    def collar(f, x, y, w, h):
        if f == 'top':
            return s.noisy(green, 0.15)
        return s.noisy(green if (x + y) % 2 else green_d, 0.12) if y < 2 else None
    s.body(collar, overlay=True)

    def arm(f, x, y, w, h, glove=False):
        if f == 'top':
            return green
        if f == 'bottom':
            return rgb('#8a8a8a') if glove else rgb('#d29a74')
        if y < 5:
            return s.noisy(green if (x + y) % 3 else green_d, 0.1)
        if glove and y >= 8:
            return s.noisy(rgb('#9a9a9a') if y < 11 else rgb('#7a7a7a'), 0.05)
        return s.noisy(rgb('#d29a74'), 0.04)
    s.right_arm(lambda *a: arm(*a, glove=True))
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if f == 'bottom':
            return rgb('#4a2c18')
        if y >= 10:
            return s.noisy(rgb('#a8642a') if y == 10 else rgb('#7a4420'), 0.06)
        return s.noisy(denim if x not in (0, w - 1) else denim_d, 0.05)
    s.right_leg(leg)
    s.left_leg(leg)
    return s


def gardener():
    """Slim gardener with a straw sun hat, flower dress and green rubber boots."""
    s = Skin(3, slim=True)
    face(s, '#e2b38f', '#b0662c', '#4a7ac0', cheeks='#e89a8a', fringe={0: 1, 7: 1})
    straw, straw_d = rgb('#e8c870'), rgb('#c8a050')

    def hat(f, x, y, w, h):
        if f == 'top':
            return s.noisy(straw if (x + y) % 2 else straw_d, 0.06)
        if f in ('front', 'back', 'left', 'right'):
            if y == 0:
                return s.noisy(straw, 0.06)
            if y == 1:
                return rgb('#d0443c')  # ribbon
            if f != 'front' and y < 6:
                return s.noisy(rgb('#b0662c'), 0.1)  # long hair under the hat
        return None
    s.head(hat, overlay=True)
    dress = rgb('#f2d8e4')

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return dress
        flower = (x * 3 + y * 5) % 7 == 0
        if flower:
            return rgb('#e8506a') if (x + y) % 2 else rgb('#f0c030')
        if f == 'front' and y == 0 and 2 <= x <= 5:
            return rgb('#e2b38f')
        return s.noisy(dress, 0.04)
    s.body(body)

    def arm(f, x, y, w, h):
        if f == 'top':
            return dress
        if y < 3:
            return s.noisy(dress, 0.04)
        if y >= 9:
            return s.noisy(rgb('#8ab84a'), 0.06)  # gardening gloves
        return s.noisy(rgb('#e2b38f'), 0.03)
    s.right_arm(arm)
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if f == 'bottom':
            return rgb('#2e6a2a')
        if y < 3:
            return s.noisy(dress, 0.04)
        if y >= 6:
            return s.noisy(rgb('#3e8a36') if y > 6 else rgb('#58a64c'), 0.06)
        return s.noisy(rgb('#e2b38f'), 0.03)
    s.right_leg(leg)
    s.left_leg(leg)
    return s


def miner():
    """Miner with a yellow helmet and lamp, orange high-vis vest, grey work trousers."""
    s = Skin(4)
    face(s, '#b07a58', '#3a2a22', '#2a2a2a', beard='#4a3426')

    def helmet(f, x, y, w, h):
        y_c, y_d = rgb('#f2c230'), rgb('#c89a18')
        if f == 'top':
            return s.noisy(y_c, 0.06)
        if f in ('front', 'back', 'left', 'right') and y < 2:
            if f == 'front' and y < 2 and 3 <= x <= 4:
                return (255, 250, 210) if y == 0 else (230, 220, 160)  # lamp
            return s.noisy(y_c if y == 0 else y_d, 0.05)
        return None
    s.head(helmet, overlay=True)
    vest, stripe = rgb('#f06a1e'), rgb('#e8e8e0')

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return vest if f == 'top' else rgb('#5a5a60')
        if y >= 10:
            return s.noisy(rgb('#5a5a60'), 0.06)
        if y in (5, 6):
            return stripe
        if f == 'front' and x in (3, 4) and y < 3:
            return rgb('#3a3a40')  # shirt collar
        return s.noisy(vest, 0.05)
    s.body(body)

    def arm(f, x, y, w, h):
        if f == 'top':
            return rgb('#3a3a40')
        if y >= 9:
            return s.noisy(rgb('#6a4a2a'), 0.08)  # leather gloves
        return s.noisy(rgb('#3a3a40'), 0.06)
    s.right_arm(arm)
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if f == 'bottom':
            return rgb('#1a1a1a')
        if y >= 9:
            return s.noisy(rgb('#2a2a2a'), 0.08)
        if y == 6:
            return stripe
        return s.noisy(rgb('#5a5a60'), 0.06)
    s.right_leg(leg)
    s.left_leg(leg)
    return s


def sailor():
    """Striped sailor shirt, navy trousers, red neckerchief, blond hair."""
    s = Skin(5)
    face(s, '#d9a07c', '#d8b050', '#3a6aa0', fringe={1: 1, 2: 1, 6: 1})
    hair_overlay(s, '#e0bc5c', front_tufts=(1, 2, 6), side_rows=3, back_rows=4)

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return (240, 240, 240) if f == 'top' else rgb('#1f2e5a')
        if y >= 10:
            return s.noisy(rgb('#1f2e5a'), 0.05)
        if f == 'front' and y < 3 and 2 <= x <= 5:
            return rgb('#c8302a') if not (y == 2 and x in (2, 5)) else shade(rgb('#c8302a'), 0.8)
        return (236, 236, 236) if y % 2 == 0 else rgb('#24418a')
    s.body(body)

    def arm(f, x, y, w, h):
        if f == 'top':
            return (236, 236, 236)
        if y < 7:
            return (236, 236, 236) if y % 2 == 0 else rgb('#24418a')
        return s.noisy(rgb('#d9a07c'), 0.03)
    s.right_arm(arm)
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if f == 'bottom':
            return (30, 30, 30)
        if y >= 11:
            return (40, 40, 44)
        return s.noisy(rgb('#1f2e5a'), 0.05)
    s.right_leg(leg)
    s.left_leg(leg)
    return s


def astronaut():
    """Slim astronaut in a white suit with orange details and a tinted visor."""
    s = Skin(6, slim=True)
    face(s, '#8a5a3e', '#1a1210', '#2a1a12', cheeks='#a8705a')

    def helmet(f, x, y, w, h):
        white = (238, 240, 244)
        if f == 'top':
            return s.noisy(white, 0.03)
        if f == 'front':
            if 1 <= y <= 6 and 1 <= x <= 6:
                return (130, 190, 245, 70) if (x + y) % 5 else (230, 245, 255, 150)  # translucent visor
            return s.noisy(white, 0.03)
        if f in ('back', 'left', 'right'):
            return s.noisy(white, 0.03)
        return None
    s.head(helmet, overlay=True)
    white, orange = (232, 234, 238), rgb('#f07a2a')

    def body(f, x, y, w, h):
        if f in ('top', 'bottom'):
            return white
        if f == 'front' and 3 <= y <= 5 and 2 <= x <= 5:
            return rgb('#3a6ac8') if (x + y) % 2 else rgb('#d03030')  # mission panel
        if y == 10:
            return orange
        if f == 'back' and 1 <= y <= 8 and 1 <= x <= 6:
            return (200, 204, 210)  # life support pack outline
        return s.noisy(white, 0.03)
    s.body(body)

    def arm(f, x, y, w, h):
        if y == 4:
            return orange
        if y >= 9:
            return s.noisy((150, 156, 166), 0.04)
        return s.noisy(white, 0.03)
    s.right_arm(arm)
    s.left_arm(arm)

    def leg(f, x, y, w, h):
        if y == 5:
            return orange
        if y >= 9:
            return s.noisy((120, 126, 136), 0.05)
        return s.noisy(white, 0.03)
    s.right_leg(leg)
    s.left_leg(leg)
    return s


SKINS = [
    ('lumberjack', lumberjack, 'Holzfäller', 'Lumberjack'),
    ('adventurer', adventurer, 'Abenteurer', 'Adventurer'),
    ('gardener', gardener, 'Gärtnerin', 'Gardener'),
    ('miner', miner, 'Bergmann', 'Miner'),
    ('sailor', sailor, 'Matrose', 'Sailor'),
    ('astronaut', astronaut, 'Astronautin', 'Astronaut'),
]


def build():
    os.makedirs(os.path.join(ROOT, 'texts'), exist_ok=True)
    skins = []
    lang = {'de_de': ['skinpack.btb_skins=BTB Skins'], 'en_us': ['skinpack.btb_skins=BTB Skins']}
    for key, fn, de, en in SKINS:
        s = fn()
        s.save(os.path.join(ROOT, key + '.png'))
        skins.append({'localization_name': key, 'geometry': 'geometry.humanoid.custom' + ('Slim' if s.slim else ''),
                      'texture': key + '.png', 'type': 'free'})
        lang['de_de'].append(f'skin.btb_skins.{key}={de}')
        lang['en_us'].append(f'skin.btb_skins.{key}={en}')
    # skins that are an outfit on top of a default skin of the installed game
    with open(os.path.join(ROOT, 'btb_bases.json'), 'w') as f:
        json.dump({'lumberjack': 'vanilla:steve_wide'}, f, indent=2)
    with open(os.path.join(ROOT, 'skins.json'), 'w') as f:
        json.dump({'serialize_name': 'btb_skins', 'localization_name': 'btb_skins', 'skins': skins}, f, indent=2)
    for code, lines in lang.items():
        with open(os.path.join(ROOT, 'texts', code + '.lang'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(lines) + '\n')
    print(f'{len(skins)} skins written to {os.path.normpath(ROOT)}')


if __name__ == '__main__':
    build()
