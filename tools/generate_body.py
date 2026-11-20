#!/usr/bin/env python3
"""Body parts for the character editor, like Bedrock's Character Creator "Body" section (own designs).

Writes into the clothing pack (run generate_clothes.py first, this script adds to its clothes.json):
  skin   – the neutral body (wide and slim arms), tinted with the skin tone
  hair   – hair styles with eyebrows, tinted with the hair colour
  eyes   – eye shapes: iris tinted, whites and pupils fixed
  mouth  – mouths: lips tinted, teeth and inside fixed
  beard  – facial hair, tinted with the hair colour
Everything is a 64x64 overlay in the standard skin layout (grey 226 = the chosen colour).
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from generate_skins import Skin, shade  # noqa

ROOT = 'src/main/resources/assets/btb_skinstudio/builtin/btb_clothes'
G = (226, 226, 226)
WHITE = (244, 244, 244)
PUPIL = (24, 24, 30)
DARK = (58, 30, 30)
TEETH = (240, 236, 226)

SKIN_TONES = ['#F3D2B4', '#E9B992', '#D49C74', '#B9805A', '#8F5E3E', '#6B4330', '#4A2E22', '#C69C7A']
HAIR_COLORS = ['#2B1D14', '#4A2E1C', '#7A4A26', '#C8A060', '#E6CC86', '#A83A1E', '#8A8A8A', '#EDEDED', '#2E4FA8', '#D8689A']
EYE_COLORS = ['#5A3A22', '#3A7AC8', '#3E8A4A', '#6A7A88', '#8A6A3A', '#7A4AA8']
LIP_COLORS = ['#8A3A34', '#B85A5A', '#6A2E2A', '#C88080']


def g(f=1.0):
    return shade(G, f)


def face_px(s, x, y, c, a=255):
    """Front of the head: x 0–7 (viewer's left → right), y 0–7 (top → bottom)."""
    s.set(8 + x, 8 + y, c, a)


def side_px(s, side, x, y, c):
    """Head sides: side 'right' (viewer's left) at u 0–7, 'left' at u 16–23, 'back' at u 24–31."""
    u = {'right': 0, 'left': 16, 'back': 24}[side]
    s.set(u + x, 8 + y, c)


def top_px(s, x, y, c):
    s.set(8 + x, y, c)


# ------------------------------------------------------------------ base body

def body(slim):
    t, f = Skin(500, slim=slim), Skin(1500, slim=slim)
    paint = lambda face, x, y, w, h: t.noisy(g(), 0.02)
    for part in (t.head, t.body, t.right_arm, t.left_arm, t.right_leg, t.left_leg):
        part(paint)
    # soft shading on the face: nose and cheeks
    face_px(t, 3, 5, g(0.9))
    face_px(t, 4, 5, g(0.9))
    return t, f


# ------------------------------------------------------------------ hair

def hair_style(name):
    t, f = Skin(600, slim=False), Skin(1600, slim=False)

    def hp(f_=1.0):
        return t.noisy(g(f_), 0.05)

    # top of the head is always covered
    for x in range(8):
        for y in range(8):
            top_px(t, x, y, hp(1.04))
    fringe, sides, back, extra = {
        'short': ([8, 8], 3, 5, None),
        'sidepart': ([8, 6, 3], 4, 6, None),
        'long': ([8, 8], 8, 8, 'long'),
        'buzz': ([8], 1, 2, 'buzz'),
        'curly': ([8, 8, 2], 4, 6, 'curly'),
        'mohawk': ([0], 0, 0, 'mohawk'),
    }[name]
    if extra == 'mohawk':
        for y in range(8):
            for x in range(8):
                if x not in (3, 4):
                    top_px(t, x, y, g(0.55))  # shaved sides: darker stubble
        for y in range(4):
            side_px(t, 'back', 3, y, hp())
            side_px(t, 'back', 4, y, hp())
        face_px(t, 3, 0, hp())
        face_px(t, 4, 0, hp())
        # crest on the hat layer
        for y in range(8):
            t.set(40 + 3, y, hp(1.1))
            t.set(40 + 4, y, hp(1.1))
    else:
        for row, n in enumerate(fringe):
            for x in range(8):
                if n == 8 or (name == 'sidepart' and x < n) or (name != 'sidepart' and (x < n // 2 or x >= 8 - n // 2)):
                    face_px(t, x, row, hp(0.95 - row * 0.04))
        for side in ('right', 'left'):
            for y in range(sides):
                for x in range(8):
                    side_px(t, side, x, y, hp(0.92))
        for y in range(back):
            for x in range(8):
                side_px(t, 'back', x, y, hp(0.9))
        if extra == 'long':
            for y in range(2, 8):
                face_px(t, 0, y, hp(0.9))
                face_px(t, 7, y, hp(0.9))
        if extra == 'buzz':
            for x in range(8):
                for y in range(8):
                    top_px(t, x, y, g(0.7))
        if extra == 'curly':
            # volume on the hat layer: bumps on top and at the sides
            for x in range(8):
                for y in range(8):
                    if (x + y) % 2 == 0:
                        t.set(40 + x, y, hp(1.08))
    # eyebrows (hair colour), not on the mohawk / buzz
    if name not in ('mohawk',):
        for x in (1, 2, 5, 6):
            face_px(t, x, 3, hp(0.8))
    return t, f


HAIR = [('short', 'Kurz', 'Short'), ('sidepart', 'Seitenscheitel', 'Side part'), ('long', 'Lang', 'Long'),
        ('buzz', 'Raspelkurz', 'Buzz cut'), ('curly', 'Lockig', 'Curly'), ('mohawk', 'Irokese', 'Mohawk')]


# ------------------------------------------------------------------ eyes

EYES = {
    # rows for the eye on the viewer's left (columns 1–2, top row 4 unless given); the right eye is mirrored
    'classic': (4, ['WI']),
    'big': (3, ['WI', 'WP']),
    'round': (3, ['II', 'IP']),
    'narrow': (4, ['DI']),
}
EYE_NAMES = {'classic': ('Klassisch', 'Classic'), 'big': ('Groß', 'Big'), 'round': ('Rund', 'Round'), 'narrow': ('Schmal', 'Narrow')}


def eyes(name):
    t, f = Skin(700, slim=False), Skin(1700, slim=False)
    top, rows = EYES[name]
    for r, row in enumerate(rows):
        for i, ch in enumerate(row):
            for x in (1 + i, 6 - i):  # left eye, mirrored right eye
                y = top + r
                if ch == 'I':
                    face_px(t, x, y, g())
                elif ch == 'W':
                    face_px(f, x, y, WHITE)
                elif ch == 'P':
                    face_px(f, x, y, PUPIL)
                elif ch == 'D':
                    face_px(f, x, y, PUPIL)
    return t, f


# ------------------------------------------------------------------ mouths

MOUTHS = {
    # (x, y, 'L' lips tinted / 'T' teeth fixed / 'O' inside fixed)
    'neutral': [(3, 6, 'L'), (4, 6, 'L')],
    'smile': [(2, 5, 'L'), (5, 5, 'L'), (3, 6, 'L'), (4, 6, 'L')],
    'grin': [(2, 6, 'L'), (3, 6, 'T'), (4, 6, 'T'), (5, 6, 'L')],
    'open': [(3, 5, 'L'), (4, 5, 'L'), (3, 6, 'O'), (4, 6, 'O')],
    'smirk': [(3, 6, 'L'), (4, 6, 'L'), (5, 5, 'L')],
}
MOUTH_NAMES = {'neutral': ('Neutral', 'Neutral'), 'smile': ('Lächeln', 'Smile'), 'grin': ('Grinsen', 'Grin'),
               'open': ('Offen', 'Open'), 'smirk': ('Schmunzeln', 'Smirk')}


def mouth(name):
    t, f = Skin(800, slim=False), Skin(1800, slim=False)
    for x, y, k in MOUTHS[name]:
        if k == 'L':
            face_px(t, x, y, g())
        elif k == 'T':
            face_px(f, x, y, TEETH)
        else:
            face_px(f, x, y, DARK)
    return t, f


# ------------------------------------------------------------------ facial hair

def beard(name):
    t, f = Skin(900, slim=False), Skin(1900, slim=False)

    def bp(fac=1.0):
        return t.noisy(g(fac), 0.06)
    pts = []
    if name == 'stubble':
        for x in range(1, 7):
            for y in (6, 7):
                if (x + y) % 2 == 0:
                    pts.append((x, y))
    elif name == 'mustache':
        pts = [(x, 5) for x in range(2, 6)]
    elif name == 'goatee':
        pts = [(x, 7) for x in range(2, 6)] + [(2, 6), (5, 6)]
    elif name == 'full':
        pts = [(x, 7) for x in range(8)] + [(x, 6) for x in (0, 1, 2, 5, 6, 7)] + [(x, 5) for x in (0, 1, 2, 3, 4, 5, 6, 7)]
        for side in ('right', 'left'):
            for y in range(4, 8):
                for x in range(4, 8) if side == 'right' else range(0, 4):
                    side_px(t, side, x, y, bp(0.9))
    elif name == 'chinstrap':
        pts = [(x, 7) for x in range(8)] + [(0, y) for y in range(4, 7)] + [(7, y) for y in range(4, 7)]
    for x, y in pts:
        face_px(t, x, y, bp())
    return t, f


BEARDS = [('stubble', 'Dreitagebart', 'Stubble'), ('mustache', 'Schnurrbart', 'Mustache'), ('goatee', 'Kinnbart', 'Goatee'),
          ('full', 'Vollbart', 'Full beard'), ('chinstrap', 'Kinnbart-Rahmen', 'Chinstrap')]


# ------------------------------------------------------------------ writing

def write(item_id, slot, de, en, colors, order, make, slim_variant=None):
    d = os.path.join(ROOT, item_id)
    os.makedirs(d, exist_ok=True)
    tw, fw = make(False) if slim_variant is None else make(slim_variant)
    ts, fs = (tw, fw) if slim_variant is None else make(True)
    tw.save(os.path.join(d, 'wide.png'))
    fw.save(os.path.join(d, 'wide_fixed.png'))
    ts.save(os.path.join(d, 'slim.png'))
    fs.save(os.path.join(d, 'slim_fixed.png'))
    return {'id': item_id, 'slot': slot, 'name': {'de': de, 'en': en}, 'colors': colors, 'order': order}


def build():
    path = os.path.join(ROOT, 'clothes.json')
    data = json.load(open(path, encoding='utf-8'))
    data['items'] = [i for i in data['items'] if i['slot'] not in ('skin', 'hair', 'eyes', 'mouth', 'beard')]
    items = []
    # wide and slim body: separate items, the arm model follows the choice
    items.append(write('body_wide', 'skin', 'Körper (breit)', 'Body (wide)', SKIN_TONES, 0, lambda slim: body(False)))
    items.append(write('body_slim', 'skin', 'Körper (schmal)', 'Body (slim)', SKIN_TONES, 0, lambda slim: body(True)))
    for hid, de, en in HAIR:
        items.append(write('hair_' + hid, 'hair', de, en, HAIR_COLORS, 6, lambda slim, h=hid: hair_style(h)))
    for eid in EYES:
        items.append(write('eyes_' + eid, 'eyes', EYE_NAMES[eid][0], EYE_NAMES[eid][1], EYE_COLORS, 5, lambda slim, e=eid: eyes(e)))
    for mid in MOUTHS:
        items.append(write('mouth_' + mid, 'mouth', MOUTH_NAMES[mid][0], MOUTH_NAMES[mid][1], LIP_COLORS, 4, lambda slim, m=mid: mouth(m)))
    for bid, de, en in BEARDS:
        items.append(write('beard_' + bid, 'beard', de, en, HAIR_COLORS, 3, lambda slim, b=bid: beard(b)))
    data['items'] = items + data['items']
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
    print('%d body parts added to %s' % (len(items), path))


if __name__ == '__main__':
    build()
