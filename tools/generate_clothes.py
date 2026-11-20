#!/usr/bin/env python3
"""Generates the built-in clothing set (own designs) for the character tab.

Every piece is a 64x64 overlay in the standard skin layout, for wide and slim arms:
  <id>/wide.png, <id>/slim.png              light grey, shaded – tinted with the chosen colour
  <id>/wide_fixed.png, <id>/slim_fixed.png  details in fixed colours (soles, buttons, zips, laces)
clothes.json lists the pieces with slot, names and default colours.
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from generate_skins import Skin, rgb, shade  # noqa

ROOT = 'src/main/resources/assets/btb_skinstudio/builtin/btb_clothes'
G = (226, 226, 226)  # base grey that is tinted


def g(s, f=1.0, noise=0.035):
    return s.noisy(shade(G, f), noise)


class Piece:
    def __init__(self, pid, slot, de, en, colors, draw, layer_order):
        self.pid, self.slot, self.de, self.en, self.colors, self.draw, self.order = pid, slot, de, en, colors, draw, layer_order


def two(slim, seed, draw):
    """Runs draw(tint_skin, fixed_skin) for one arm variant."""
    t, f = Skin(seed, slim=slim), Skin(seed + 1000, slim=slim)
    draw(t, f)
    return t, f


# ------------------------------------------------------------------ tops

def tshirt(t, f):
    def body(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t, 1.02)
        if y > 9:
            return None
        if fc == 'front' and y == 0 and 3 <= x <= 4:
            return None  # round neckline shows the skin
        return g(t, 0.94 if y == 9 else 1)
    t.body(body)

    def arm(fc, x, y, w, h):
        if fc == 'top':
            return g(t)
        if fc == 'bottom' or y > 3:
            return None
        return g(t, 0.92 if y == 3 else 1)
    t.right_arm(arm)
    t.left_arm(arm)


def longsleeve(t, f):
    tshirt(t, f)

    def arm(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 10:
            return None
        return g(t, 0.9 if y == 10 else 1)  # cuff
    t.right_arm(arm)
    t.left_arm(arm)


def hoodie(t, f):
    def body(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t, 1.02)
        if y > 10:
            return None
        if fc == 'front' and 6 <= y <= 8 and 2 <= x <= 5:
            return g(t, 0.86 if y == 6 else 0.92)  # front pocket
        return g(t, 0.9 if y == 10 else 1)
    t.body(body)

    def arm(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 10:
            return None
        return g(t, 0.88 if y == 10 else 1)
    t.right_arm(arm)
    t.left_arm(arm)

    def hood(fc, x, y, w, h):
        # hood lying on the back and around the neck (hat layer, back half only)
        if fc == 'back' and y >= 4:
            return g(t, 0.95)
        return None
    t.head(hood, overlay=True)

    def strings(fc, x, y, w, h):
        if fc == 'front' and x in (3, 4) and 1 <= y <= 3:
            return (240, 240, 236)
        return None
    f.body(strings)


def flannel(t, f):
    """Checked shirt: the check pattern is drawn in two greys so it takes any colour."""
    def check(x, y):
        a, b = x % 4 == 3, y % 4 == 3
        return 0.55 if (a and b) else 0.72 if (a or b) else 1.0

    def body(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 10:
            return None
        if fc == 'front' and x == 4 and 1 <= y <= 9:
            return g(t, 0.6)  # button placket
        return g(t, check(x, y), 0.03)
    t.body(body)

    def arm(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 10:
            return None
        return g(t, check(x, y), 0.03)
    t.right_arm(arm)
    t.left_arm(arm)

    def buttons(fc, x, y, w, h):
        return (240, 236, 224) if (fc == 'front' and x == 4 and y in (2, 5, 8)) else None
    f.body(buttons)


def striped(t, f):
    def body(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 10:
            return None
        return g(t, 1.0 if y % 2 == 0 else 0.62, 0.02)
    t.body(body)

    def arm(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t)
        if y > 8:
            return None
        return g(t, 1.0 if y % 2 == 0 else 0.62, 0.02)
    t.right_arm(arm)
    t.left_arm(arm)


def tanktop(t, f):
    def body(fc, x, y, w, h):
        if fc == 'bottom':
            return None
        if fc == 'top':
            return g(t) if x in (1, 2, 5, 6) else None
        if y > 9:
            return None
        if fc in ('front', 'back') and y < 2 and 2 <= x <= 5:
            return None
        if fc in ('left', 'right') and y < 3:
            return None
        return g(t)
    t.body(body)


# ------------------------------------------------------------------ bottoms

def legs_part(t, top_rows=0, length=12, seam=True, cuff=None):
    def leg(fc, x, y, w, h):
        if fc == 'bottom' or y >= length:
            return None
        if fc == 'top':
            return g(t)
        if seam and fc in ('left', 'right') and x == 1:
            return g(t, 0.86)
        if cuff is not None and y == length - 1:
            return g(t, cuff)
        return g(t)
    t.right_leg(leg)
    t.left_leg(leg)


def waist(t, belt=True):
    def body(fc, x, y, w, h):
        if fc in ('top', 'bottom') or y < 10:
            return None
        return g(t, 0.9 if y == 10 else 1)
    t.body(body)


def jeans(t, f):
    legs_part(t, cuff=0.85)
    waist(t)

    def belt(fc, x, y, w, h):
        if fc in ('top', 'bottom') or y != 10:
            return None
        return (196, 160, 70) if (fc == 'front' and x in (3, 4)) else (74, 50, 30)
    f.body(belt)


def shorts(t, f):
    legs_part(t, length=5, cuff=0.88)
    waist(t)


def joggers(t, f):
    legs_part(t, cuff=0.8)
    waist(t)

    def stripe(fc, x, y, w, h):
        return (245, 245, 245) if (fc in ('left', 'right') and x == 2 and y < 11) else None
    f.right_leg(stripe)
    f.left_leg(stripe)


def skirt(t, f):
    def body(fc, x, y, w, h):
        if fc in ('top', 'bottom') or y < 10:
            return None
        return g(t, 0.92 if x % 2 else 1)
    t.body(body)

    def leg(fc, x, y, w, h):
        if fc in ('top', 'bottom') or y > 4:
            return None
        return g(t, (0.92 if x % 2 else 1) * (0.88 if y == 4 else 1))
    t.right_leg(leg)
    t.left_leg(leg)


def overalls(t, f):
    legs_part(t, cuff=0.85)

    def body(fc, x, y, w, h):
        if fc in ('top', 'bottom'):
            return None
        if fc == 'front' and y >= 4:
            return g(t, 0.9 if (y == 4) else 1)
        if fc == 'back' and y >= 7:
            return g(t)
        if fc in ('left', 'right') and y >= 8:
            return g(t)
        if fc in ('front', 'back') and x in (1, 6) and y < 4:
            return g(t, 0.9)  # straps
        return None
    t.body(body)

    def buttons(fc, x, y, w, h):
        return (226, 196, 90) if (fc == 'front' and x in (1, 6) and y == 4) else None
    f.body(buttons)


# ------------------------------------------------------------------ shoes

def sneakers(t, f):
    def leg(fc, x, y, w, h):
        if fc == 'top' or y < 10:
            return None
        if fc == 'bottom':
            return None
        return g(t)
    t.right_leg(leg)
    t.left_leg(leg)

    def sole(fc, x, y, w, h):
        if fc == 'bottom':
            return (240, 240, 236)
        if fc != 'top' and y == 11:
            return (244, 244, 240)
        if fc == 'front' and y == 10 and x in (1, 2):
            return (250, 250, 250)  # laces
        return None
    f.right_leg(sole)
    f.left_leg(sole)


def boots(t, f):
    def leg(fc, x, y, w, h):
        if fc == 'top' or fc == 'bottom' or y < 7:
            return None
        return g(t, 0.9 if y == 7 else 1)
    t.right_leg(leg)
    t.left_leg(leg)

    def sole(fc, x, y, w, h):
        if fc == 'bottom':
            return (40, 32, 26)
        if fc != 'top' and y == 11:
            return (52, 40, 32)
        return None
    f.right_leg(sole)
    f.left_leg(sole)


# ------------------------------------------------------------------ jackets (outer layer, a bit of volume)

def jacket_common(t, f, open_front=True, sleeves=True, collar=True):
    def body(fc, x, y, w, h):
        if fc in ('top', 'bottom'):
            return g(t) if fc == 'top' and not (2 <= x <= 5) else None
        if y > 10:
            return None
        if open_front and fc == 'front' and 3 <= x <= 4:
            return None
        if collar and fc == 'front' and y == 0 and x in (2, 5):
            return g(t, 0.82)
        return g(t, 0.92 if y == 10 else 1)
    t.body(body, overlay=True)
    if sleeves:
        def arm(fc, x, y, w, h):
            if fc == 'bottom' or y > 10:
                return None
            if fc == 'top':
                return g(t)
            return g(t, 0.88 if y == 10 else 1)
        t.right_arm(arm, overlay=True)
        t.left_arm(arm, overlay=True)


def leather_jacket(t, f):
    jacket_common(t, f)

    def zip_(fc, x, y, w, h):
        return (200, 200, 206) if (fc == 'front' and x in (2, 5) and 3 <= y <= 9 and y % 3 == 0) else None
    f.body(zip_, overlay=True)


def vest(t, f):
    jacket_common(t, f, sleeves=False)

    def buttons(fc, x, y, w, h):
        return (226, 196, 90) if (fc == 'front' and x == 2 and y in (3, 6, 9)) else None
    f.body(buttons, overlay=True)


def coat(t, f):
    jacket_common(t, f, open_front=False)

    def legs(fc, x, y, w, h):
        if fc in ('top', 'bottom') or y > 3:
            return None
        return g(t, 0.9 if y == 3 else 1)
    t.right_leg(legs, overlay=True)
    t.left_leg(legs, overlay=True)

    def buttons(fc, x, y, w, h):
        return (40, 36, 34) if (fc == 'front' and x in (3, 4) and y in (2, 5, 8)) else None
    f.body(buttons, overlay=True)


PIECES = [
    Piece('tshirt', 'top', 'T-Shirt', 'T-shirt', ['#3A7AC8', '#E8E8E8', '#C8302A', '#2A2A2E', '#3E9A4A', '#F0C030'], tshirt, 20),
    Piece('longsleeve', 'top', 'Langarmshirt', 'Long sleeve', ['#2A2A2E', '#E8E8E8', '#7A3AA8', '#C86A2A'], longsleeve, 20),
    Piece('hoodie', 'top', 'Hoodie', 'Hoodie', ['#5A5A62', '#C8302A', '#2C4A8A', '#3E9A4A', '#F07AA8'], hoodie, 20),
    Piece('flannel', 'top', 'Karohemd', 'Flannel shirt', ['#B0261E', '#2C6A3A', '#2C4A8A', '#C8902A'], flannel, 20),
    Piece('striped', 'top', 'Ringelshirt', 'Striped shirt', ['#24418A', '#C8302A', '#2A2A2E', '#3E9A4A'], striped, 20),
    Piece('tanktop', 'top', 'Tanktop', 'Tank top', ['#E8E8E8', '#2A2A2E', '#F0C030', '#3AA8C8'], tanktop, 20),
    Piece('jeans', 'bottom', 'Jeans', 'Jeans', ['#3E5F8C', '#2A3550', '#6A8AB8', '#2A2A2E'], jeans, 10),
    Piece('shorts', 'bottom', 'Shorts', 'Shorts', ['#C8A870', '#3E5F8C', '#2A2A2E', '#3E7A4A'], shorts, 10),
    Piece('joggers', 'bottom', 'Jogginghose', 'Joggers', ['#3A3A42', '#2C4A8A', '#7A7A82', '#3E6A3A'], joggers, 10),
    Piece('skirt', 'bottom', 'Rock', 'Skirt', ['#C8302A', '#2A2A2E', '#7A3AA8', '#3A7AC8'], skirt, 10),
    Piece('overalls', 'bottom', 'Latzhose', 'Overalls', ['#3D6FB0', '#3E7A4A', '#8A5A2A', '#2A2A2E'], overalls, 12),
    Piece('sneakers', 'shoes', 'Sneaker', 'Sneakers', ['#E8E8E8', '#C8302A', '#2A2A2E', '#3A7AC8'], sneakers, 5),
    Piece('boots', 'shoes', 'Stiefel', 'Boots', ['#5A3A22', '#2A2A2E', '#8A6A42'], boots, 5),
    Piece('leather_jacket', 'jacket', 'Lederjacke', 'Leather jacket', ['#2A2422', '#6A3A22', '#8A1E1E'], leather_jacket, 30),
    Piece('vest', 'jacket', 'Weste', 'Vest', ['#5A3A22', '#2A2A2E', '#3E6A3A', '#8A8A92'], vest, 30),
    Piece('coat', 'jacket', 'Mantel', 'Coat', ['#8A6A42', '#2A2A2E', '#5A5A62', '#7A2A2A'], coat, 30),
]


def build():
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(ROOT)
    items = []
    for i, p in enumerate(PIECES):
        d = os.path.join(ROOT, p.pid)
        os.makedirs(d)
        for slim in (False, True):
            t, f = two(slim, 500 + i * 7, p.draw)
            v = 'slim' if slim else 'wide'
            t.save(os.path.join(d, v + '.png'))
            f.save(os.path.join(d, v + '_fixed.png'))
        items.append({'id': p.pid, 'slot': p.slot, 'name': {'de': p.de, 'en': p.en}, 'colors': p.colors, 'order': p.order})
    with open(os.path.join(ROOT, 'clothes.json'), 'w', encoding='utf-8') as fh:
        json.dump({'format': 1, 'items': items}, fh, indent=2, ensure_ascii=False)
    print('%d clothing pieces written to %s' % (len(items), ROOT))


if __name__ == '__main__':
    build()
