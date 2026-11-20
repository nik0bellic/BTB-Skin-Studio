#!/usr/bin/env python3
"""Generates the emote props (cup, cookie, guitar, camera, flag) as a hidden built-in pack.

Root bones are named after body parts (rightArm, body, head) so a prop follows that part while an
emote plays. Coordinates are Bedrock player space (pixels, y up, front = -z); the right hand is at
x -8..-4, y 12, z -2..2 when the arm hangs down.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from cosmetic_kit import *  # noqa

ROOT = 'src/main/resources/assets/btb_skinstudio/builtin/btb_props'
AUTHOR = 'BTB Studios'


def meta(name, de, en, typ):
    return {'id': 'btb_prop:' + name, 'version': '1.0.0', 'name': {'de': de, 'en': en}, 'author': AUTHOR, 'type': typ,
            'tags': ['emote_prop']}


def build():
    import shutil
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(ROOT)
    out = []

    hand = lambda m: m.bone('rightArm', [-5, 22, 0])

    # potion bottle (glass, pink potion, cork) in the right hand
    m = Model('geometry.btb_prop.potion', seed=101)
    b = hand(m)
    glass = lambda f_, x, y, w, h, r: (255, 255, 255, 150) if (x == 0 or x == w - 1) else (240, 120, 200, 235) if y >= 1 else (250, 170, 225, 200)
    m.box(b, (-7.5, 7, -3.5), (3, 3, 3), glass)
    m.box(b, (-6.5, 10, -2.5), (1, 1.5, 1), (lambda f_, x, y, w, h, r: (255, 255, 255, 140)))
    m.box(b, (-6.6, 11.5, -2.6), (1.2, 0.8, 1.2), solid('#9a6a3a'))
    out.append(('potion', meta('potion', 'Trank', 'Potion', 'animated'), m, FULL))

    # cookie
    m = Model('geometry.btb_prop.cookie', seed=102)
    b = hand(m)
    dough = lambda f_, x, y, w, h, r: (rgb('#5a3218') + (255,)) if (x * 3 + y * 5) % 7 == 0 else shade(rgb('#c88a48'), 0.9 + r.random() * 0.15) + (255,)
    m.box(b, (-7.5, 9.5, -4), (3, 3, 1), dough)
    out.append(('cookie', meta('cookie', 'Keks', 'Cookie', 'animated'), m, FULL))

    # diamond pickaxe (wooden handle along the arm, cyan head)
    m = Model('geometry.btb_prop.pickaxe', seed=103)
    b = hand(m)
    m.box(b, (-6.5, 4, -3.5), (1, 9, 1), palette_noise(['#7a5230', '#6a4628', '#8a5e38'], [3, 2, 1]))
    head = palette_noise(['#4ee8d8', '#2cc8b8', '#8af4ea', '#1a9a8c'], [4, 3, 2, 1])
    m.box(b, (-6.5, 3, -7.5), (1, 1, 9), head)
    m.box(b, (-6.5, 2, -7.5), (1, 1, 1), head)
    m.box(b, (-6.5, 2, 0.5), (1, 1, 1), head)
    out.append(('pickaxe', meta('pickaxe', 'Spitzhacke', 'Pickaxe', 'animated'), m, FULL))

    # diamond held out on the open hand
    m = Model('geometry.btb_prop.diamond', seed=104)
    b = hand(m)
    gem = palette_noise(['#8af4ea', '#4ee8d8', '#d8fffa', '#2cc8b8'], [3, 3, 1, 2])
    m.box(b, (-7, 8.5, -5), (2, 2, 2), gem, emissive=solid('#9afff4', alpha=70))
    m.box(b, (-6.5, 10.5, -4.5), (1, 1, 1), gem)
    m.box(b, (-6.5, 7.5, -4.5), (1, 1, 1), gem)
    out.append(('diamond', meta('diamond', 'Diamant', 'Diamond', 'animated'), m, FULL))

    # banner on a pole (pole runs along the arm, the banner hangs from a cross bar past the hand)
    m = Model('geometry.btb_prop.banner', seed=105)
    b = hand(m)
    m.box(b, (-6.5, -8, -0.5), (1, 21, 1), solid('#8a6a40', noise=0.06))
    m.box(b, (-9.5, -8.6, -0.5), (7, 0.8, 1), solid('#7a5a34'))
    fl = m.bone('cloth', [-6, -8, 0], parent='rightArm')
    cloth = lambda f_, x, y, w, h, r: ((rgb('#f5b83a') + (255,)) if (abs(x - w // 2) <= 1 and 2 <= y <= h - 4) or (y == 2 and 1 <= x <= w - 2)
                                        else shade(rgb('#c8302a'), 0.92 + r.random() * 0.1) + (255,))
    m.box(fl, (-9, -7.8, -0.25), (6, 9, 0.5), cloth)
    out.append(('banner', meta('banner', 'Banner', 'Banner', 'animated'), m, FULL))

    # spyglass held at the eye (follows the head)
    m = Model('geometry.btb_prop.spyglass', seed=106)
    b = m.bone('head', [0, 24, 0])
    copper = palette_noise(['#c8734f', '#d4825c', '#b8603f', '#e09a74'], [5, 3, 2, 1])
    m.box(b, (-3.5, 27, -10), (2, 2, 6), faces(copper, front=solid('#9ad0f0')))
    m.box(b, (-3.7, 26.8, -6), (2.4, 2.4, 1), solid('#a85a3c'))
    out.append(('spyglass', meta('spyglass', 'Fernrohr', 'Spyglass', 'animated'), m, FULL))

    for folder, mt, model, mannequin in out:
        write_cosmetic(ROOT, folder, mt, model, None, mannequin)
    pack = {'format': 1, 'id': 'btb_builtin_props', 'version': '1.0.0', 'name': {'de': 'BTB Emote-Requisiten', 'en': 'BTB emote props'},
            'description': {'de': 'Gegenstände für Emotes.', 'en': 'Items used by emotes.'}, 'author': AUTHOR}
    with open(os.path.join(ROOT, 'pack.json'), 'w', encoding='utf-8') as f:
        json.dump(pack, f, indent=2, ensure_ascii=False)
    print('%d props written to %s' % (len(out), ROOT))


if __name__ == '__main__':
    build()
