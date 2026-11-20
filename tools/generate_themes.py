#!/usr/bin/env python3
"""Generates three themed cosmetic packs (own designs, animated): Winter, Halloween, Pirates.

Bedrock player space in pixels: head x -4..4, y 24..32, z -4..4 (front = -z), body y 12..24,
z -2..2, arms x ±4..±8. Every piece is shaded like Minecraft's own textures (light from above).
"""
import json
import math
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from cosmetic_kit import *  # noqa
from generate_starter_pack import anim, make_icon  # noqa

BASE = 'src/main/resources/assets/btb_skinstudio/builtin'
AUTHOR = 'BTB Studios'


def meta(ns, name, de, en, typ, desc_de, desc_en, **extra):
    m = {'id': ns + ':' + name, 'version': '1.0.0', 'name': {'de': de, 'en': en}, 'author': AUTHOR, 'type': typ,
         'description': {'de': desc_de, 'en': desc_en}}
    m.update(extra)
    return m


def knit(base, dark):
    """Knitted wool: vertical ribs with a little noise."""
    b, d = rgb(base), rgb(dark)

    def p(face, x, y, w, h, rnd):
        c = d if x % 2 == 0 and face not in ('top', 'bottom') else b
        f = 0.94 + rnd.random() * 0.1
        if face == 'bottom':
            f *= 0.8
        elif face in ('left', 'right', 'back'):
            f *= 0.92
        return shade(c, f) + (255,)
    return p


def write_pack(root, pack_id, de, en, desc_de, desc_en, items):
    if os.path.isdir(root):
        shutil.rmtree(root)
    os.makedirs(root)
    for folder, mt, model, anims, mannequin, view in items:
        write_cosmetic(root, folder, mt, model, anims, mannequin, view=view)
    pack = {'format': 1, 'id': pack_id, 'version': '1.0.0', 'name': {'de': de, 'en': en},
            'description': {'de': desc_de, 'en': desc_en}, 'author': AUTHOR, 'icon': 'pack.png', 'categories': ['BTB', 'Themen']}
    with open(os.path.join(root, 'pack.json'), 'w', encoding='utf-8') as f:
        json.dump(pack, f, indent=2, ensure_ascii=False)
    make_icon(os.path.join(root, 'pack.png'), 64)
    print('%d cosmetics written to %s' % (len(items), root))


# ================================================================== winter

def winter():
    ns, items = 'btb_winter', []

    m = Model('geometry.btb_winter.beanie', seed=301)
    b = m.bone('beanie', [0, 32, 0])
    m.box(b, (-4.5, 29.5, -4.5), (9, 2.5, 9), knit('#c8302a', '#a8241e'), tint=True)
    m.box(b, (-4.3, 32, -4.3), (8.6, 1.5, 8.6), knit('#d84a3a', '#b8302a'), tint=True)
    m.box(b, (-3.5, 33.5, -3.5), (7, 1, 7), knit('#d84a3a', '#b8302a'), tint=True)
    pom = m.bone('pompom', [0, 34.5, 0], parent='beanie')
    fluff = palette_noise(['#f4f4f0', '#e4e4e0', '#ffffff', '#d8d8d4'], [4, 3, 2, 1])
    m.box(pom, (-1.5, 34.5, -1.5), (3, 3, 3), fluff, inflate=0.2)
    items.append(('beanie', meta(ns, 'beanie', 'Bommelmütze', 'Bobble Hat', 'beanie', 'Gestrickte Mütze mit wippendem Bommel – in jeder Farbe.',
                                 'Knitted hat with a bouncing bobble – in any colour.', animations=['animation.btb_winter.beanie'],
                                 tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#7AA8FF', '#7AE07A', '#FFE07A', '#C07AFF', '#909090']},
                                 tags=['winter', 'mütze', 'wolle']), m,
                  {'animation.btb_winter.beanie': anim(pompom={'rotation': ['math.sin(q.anim_time * 160) * 10 + math.sin(q.walk_distance * 30) * 14 * math.min(q.modified_move_speed * 3, 1)',
                                                                            0, 'math.cos(q.anim_time * 130) * 8']})}, HEAD, 'front'))

    m = Model('geometry.btb_winter.scarf', seed=302)
    b = m.bone('body', [0, 24, 0])
    stripe = lambda f_, x, y, w, h, r: shade(rgb('#f2f2ee') if (x + y) % 4 < 2 else rgb('#3a7ac8'), 0.92 + r.random() * 0.1) + (255,)
    m.box(b, (-4.5, 22, -2.6), (9, 2.5, 5.2), stripe, tint=True)
    end = m.bone('scarf_end', [2, 22, -2.8], parent='body')
    m.box(end, (1, 14.5, -3.2), (2.5, 7.5, 0.6), stripe, tint=True)
    m.box(end, (1, 13.8, -3.2), (2.5, 0.7, 0.6), lambda f_, x, y, w, h, r: (rgb('#f2f2ee') + (255,)) if x % 2 == 0 else (0, 0, 0, 0))
    items.append(('scarf', meta(ns, 'scarf', 'Schal', 'Scarf', 'armor_decoration', 'Geringelter Wollschal, das Ende schwingt beim Laufen.',
                                'Striped wool scarf, the end swings while walking.', animations=['animation.btb_winter.scarf'],
                                tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FF8A8A', '#8AFF8A', '#FFE08A']},
                                tags=['winter', 'schal']), m,
                  {'animation.btb_winter.scarf': anim(scarf_end={'rotation': ['-math.abs(math.sin(q.walk_distance * 28)) * 18 * math.min(q.modified_move_speed * 3, 1) - 3',
                                                                              0, 'math.sin(q.anim_time * 90) * 3']})}, BODY, 'front'))

    m = Model('geometry.btb_winter.earmuffs', seed=303)
    b = m.bone('earmuffs', [0, 28, 0])
    band = solid('#8a8a92', noise=0.05)
    m.box(b, (-4.8, 32, -0.7), (9.6, 0.8, 1.4), band)
    m.box(b, (-4.8, 29, -0.7), (0.8, 3, 1.4), band)
    m.box(b, (4, 29, -0.7), (0.8, 3, 1.4), band)
    for x in (-6.2, 4.2):
        m.box(b, (x, 26, -1.5), (2, 3.5, 3), fluff, tint=True, inflate=0.15)
    items.append(('earmuffs', meta(ns, 'earmuffs', 'Ohrenschützer', 'Earmuffs', 'headphones', 'Flauschige Ohrenschützer gegen die Kälte.',
                                   'Fluffy earmuffs against the cold.', tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FFB0D0', '#B0D0FF']},
                                   tags=['winter']), m, None, HEAD, 'front'))

    m = Model('geometry.btb_winter.antlers', seed=304)
    antler = palette_noise(['#8a5a2b', '#7a4e24', '#9a6a38', '#6e4520'], [4, 3, 2, 1])
    for side, sx in (('left_antler', 1), ('right_antler', -1)):
        b = m.bone(side, [2.5 * sx, 32, 0])
        for o, sz in [((2, 32, -0.5), (1, 4, 1)), ((2, 35, -0.5), (3, 1, 1)), ((4, 35, -0.5), (1, 3, 1)), ((1, 37, -0.5), (1, 2, 1)),
                      ((2, 36, -0.5), (1, 2, 1)), ((4, 37.5, -0.5), (2, 1, 1))]:
            x = o[0] if sx > 0 else -o[0] - sz[0]
            m.box(b, (x, o[1], o[2]), sz, antler)
    bell = m.bone('bell', [0, 31.5, -4.8])
    m.box(bell, (-0.8, 29.7, -5.6), (1.6, 1.8, 1.6), faces(solid('#f0c030'), bottom=solid('#5a4010')), emissive=solid('#ffe080', alpha=60))
    m.box(bell, (-1.5, 31.5, -4.9), (3, 0.6, 0.4), solid('#c8302a'))
    items.append(('antlers', meta(ns, 'antlers', 'Rentiergeweih', 'Reindeer Antlers', 'horns', 'Ein Geweih mit klingelndem Glöckchen.',
                                  'Antlers with a jingling bell.', animations=['animation.btb_winter.antlers'], tags=['winter', 'weihnachten']), m,
                  {'animation.btb_winter.antlers': anim(bell={'rotation': [0, 0, 'math.sin(q.anim_time * 400) * 10 * (math.mod(q.anim_time, 3) < 1 ? 1 : 0.2)']},
                                                        left_antler={'rotation': [0, 0, 'math.sin(q.anim_time * 60) * 1.5']},
                                                        right_antler={'rotation': [0, 0, '-math.sin(q.anim_time * 60) * 1.5']})}, HEAD, 'front'))

    m = Model('geometry.btb_winter.snowflake', seed=305)
    b = m.bone('flake', [0, 38, 0])
    ice = solid('#d8f4ff', noise=0.04)
    rows = ['#.#.#', '.###.', '#####', '.###.', '#.#.#']
    shape(m, b, rows, (-2.5, 40.5, -0.5), plane='xy', depth=1, paint=lambda f, gx, gy, ch, r: shade(rgb('#e8f8ff'), 0.92 + r.random() * 0.1) + (230,),
          emissive=lambda f, gx, gy, ch, r: (200, 240, 255, 90))
    items.append(('snowflake', meta(ns, 'snowflake', 'Schneeflocke', 'Snowflake', 'floating', 'Eine schwebende Schneeflocke – um dich herum schneit es.',
                                    'A floating snowflake – it snows around you.', animations=['animation.btb_winter.snowflake'],
                                    particles={'type': 'minecraft:snowflake', 'rate': 4, 'offset': [0, 6, 0], 'spread': 10},
                                    glow={'color': '#CDEFFF', 'size': 5, 'intensity': 0.3, 'offset': [0, 6, 0], 'flicker': False}, tags=['winter', 'schnee']), m,
                  {'animation.btb_winter.snowflake': anim(flake={'rotation': [0, 'q.anim_time * 60', 0], 'position': [0, 'math.sin(q.anim_time * 100) * 0.8', 0]})},
                  HEAD, 'front'))
    write_pack(os.path.join(BASE, 'btb_winter'), 'btb_builtin_winter', 'Winterzauber', 'Winter Magic',
               'Mütze, Schal, Ohrenschützer, Geweih und Schneeflocke.', 'Hat, scarf, earmuffs, antlers and snowflake.', items)


# ================================================================== halloween

def halloween():
    ns, items = 'btb_halloween', []

    m = Model('geometry.btb_halloween.pumpkin', seed=311)
    b = m.bone('pumpkin', [0, 24, 0])
    face_rows = ['........', '.##..##.', '.##..##.', '........', '.######.', '..#..#..', '........', '........']

    def pumpkin_side(f_, x, y, w, h, r):
        rib = x % 3 == 0
        base = rgb('#d87a1a') if not rib else rgb('#b8600e')
        return shade(base, 0.9 + r.random() * 0.15) + (255,)

    def pumpkin_front(f_, x, y, w, h, r):
        yy, xx = min(7, y * 8 // h), min(7, x * 8 // w)
        if face_rows[yy][xx] == '#':
            return rgb('#3a1a04') + (255,)
        return pumpkin_side(f_, x, y, w, h, r)

    m.box(b, (-4.6, 23.6, -4.6), (9.2, 9.2, 9.2), faces(pumpkin_side, front=pumpkin_front, top=palette_noise(['#c8701a', '#b8600e'])))
    m.box(b, (-0.5, 32.8, -0.5), (1, 1.5, 1), solid('#4a6a1e'))
    glow = m.bone('glow', [0, 28, -4.7], parent='pumpkin')

    def glow_face(f_, x, y, w, h, r):
        yy, xx = min(7, y * 8 // h), min(7, x * 8 // w)
        return (255, 210, 80, 255) if face_rows[yy][xx] == '#' else (0, 0, 0, 0)
    m.box(glow, (-4.6, 23.6, -4.75), (9.2, 9.2, 0.1), faces(transparent, front=glow_face), emissive=faces(transparent, front=glow_face))
    items.append(('pumpkin_head', meta(ns, 'pumpkin_head', 'Kürbiskopf', 'Pumpkin Head', 'helmet', 'Ein geschnitzter Kürbis – das Gesicht flackert wie Kerzenlicht.',
                                       'A carved pumpkin – the face flickers like candlelight.', animations=['animation.btb_halloween.pumpkin'],
                                       hide_layers=['hat'], glow={'color': '#FFB040', 'size': 6, 'intensity': 0.35, 'offset': [0, 4, -5], 'flicker': True},
                                       tags=['halloween', 'kürbis']), m,
                  {'animation.btb_halloween.pumpkin': anim(glow={'scale': [1, 1, '1 + math.sin(q.anim_time * 900) * 0.3']})}, HEAD, 'front'))

    m = Model('geometry.btb_halloween.witch_hat', seed=312)
    b = m.bone('hat', [0, 32, 0])
    felt = palette_noise(['#3a2a4a', '#32243f', '#423052', '#2a1e36'], [4, 3, 2, 1])
    m.box(b, (-6.5, 32, -6.5), (13, 1, 13), felt, tint=True)
    m.box(b, (-4, 33, -4), (8, 3, 8), felt, tint=True)
    m.box(b, (-4.1, 33, -4.1), (8.2, 1, 8.2), solid('#7a3aa8'))
    m.box(b, (-1, 33, -4.3), (2, 1, 0.3), solid('#e0c050'))
    m.box(b, (-3, 36, -3), (6, 3, 6), felt, tint=True)
    tip = m.bone('tip', [0, 39, 0], parent='hat')
    m.box(tip, (-2, 39, -2), (4, 2.5, 4), felt, tint=True)
    tip2 = m.bone('tip2', [0, 41.5, 1], parent='tip')
    m.box(tip2, (-1, 41.5, 0), (2, 2.5, 2), felt, tint=True)
    items.append(('witch_hat', meta(ns, 'witch_hat', 'Hexenhut', 'Witch Hat', 'hat', 'Spitzer Hexenhut mit lila Band – die Spitze wippt.',
                                    'Pointy witch hat with a purple band – the tip bobs.', animations=['animation.btb_halloween.witch_hat'],
                                    tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#909090', '#7A9A6A', '#9A6A6A']},
                                    tags=['halloween', 'hexe']), m,
                  {'animation.btb_halloween.witch_hat': anim(tip={'rotation': ['-12 + math.sin(q.anim_time * 90) * 4', 0, 'math.sin(q.anim_time * 70) * 3']},
                                                             tip2={'rotation': ['-18 + math.sin(q.anim_time * 90 - 30) * 6', 0, 0]})}, HEAD, 'front'))

    m = Model('geometry.btb_halloween.bat_wings', seed=313)
    rows = ['#.........', '###.......', '#####.....', '#######...', '#########.', '##########', '#.##.##.##', '...........']

    def membrane(face, gx, gy, ch, rnd):
        bone = gx == 0 or gy == 0 and gx < 2
        c = rgb('#2a2230') if bone else rgb('#4a3a54')
        return shade(c, 0.9 + rnd.random() * 0.12) + (255,)
    for name, sx in (('left_wing', 1), ('right_wing', -1)):
        b = m.bone(name, [1 * sx, 22, 3], rotation=[0, -20 * sx, 0])
        r_ = rows if sx > 0 else [r[::-1] for r in rows]
        x0 = 1 if sx > 0 else -11
        shape(m, b, r_, (x0, 27, 2.5), plane='xy', depth=0.5, paint=membrane, tint=True)
    items.append(('bat_wings', meta(ns, 'bat_wings', 'Fledermausflügel', 'Bat Wings', 'wings', 'Ledrige Flügel, die schnell flattern.',
                                    'Leathery wings that flap quickly.', animations=['animation.btb_halloween.bat_wings'],
                                    tint={'enabled': True, 'default': '#FFFFFF', 'palette': ['#FFFFFF', '#FF8080', '#80A0FF']}, tags=['halloween', 'flügel']), m,
                  {'animation.btb_halloween.bat_wings': anim(left_wing={'rotation': [0, '-15 - math.sin(q.anim_time * 420) * 20', 0]},
                                                             right_wing={'rotation': [0, '15 + math.sin(q.anim_time * 420) * 20', 0]})}, BODY, 'back'))

    m = Model('geometry.btb_halloween.ghost', seed=314)
    b = m.bone('ghost', [9, 30, 0])
    sheet = lambda f_, x, y, w, h, r: (238, 240, 246, 215)
    m.box(b, (7, 28, -2), (4, 5, 4), faces(sheet, front=lambda f_, x, y, w, h, r: (20, 20, 30, 240) if (y == 1 and x in (1, 2)) or (y == 3 and x in (1, 2) and False) else (238, 240, 246, 215)))
    m.box(b, (7.5, 33, -1.5), (3, 1, 3), sheet)
    for i, x in enumerate((7, 8.5, 10)):
        m.box(b, (x, 27 - (i % 2), -2), (1, 1 + (i % 2), 4), sheet)
    items.append(('ghost', meta(ns, 'ghost', 'Geister-Begleiter', 'Ghost Buddy', 'floating', 'Ein kleiner Geist, der neben dir schwebt.',
                                'A little ghost floating next to you.', animations=['animation.btb_halloween.ghost'], tags=['halloween', 'geist']), m,
                  {'animation.btb_halloween.ghost': anim(ghost={'position': ['math.sin(q.anim_time * 50) * 0.8', 'math.sin(q.anim_time * 110) * 1.2', 0],
                                                                'rotation': [0, 'math.sin(q.anim_time * 40) * 15', 'math.sin(q.anim_time * 80) * 5']})}, HEAD, 'front'))
    write_pack(os.path.join(BASE, 'btb_halloween'), 'btb_builtin_halloween', 'Halloween', 'Halloween',
               'Kürbiskopf, Hexenhut, Fledermausflügel und ein Geist.', 'Pumpkin head, witch hat, bat wings and a ghost.', items)


# ================================================================== pirates

def pirates():
    ns, items = 'btb_pirates', []

    m = Model('geometry.btb_pirates.tricorne', seed=321)
    b = m.bone('hat', [0, 32, 0])
    felt = palette_noise(['#2a2422', '#241e1c', '#322a28'], [3, 2, 1])
    gold = solid('#e0b040', noise=0.06)
    m.box(b, (-4.4, 32, -4.4), (8.8, 2.5, 8.8), felt)
    for rot in (0, 120, 240):
        m.box(b, (-5.5, 32, -6.2), (11, 3.2, 1), faces(felt, top=gold), rotation=[0, rot, 0], pivot=[0, 32, 0])
    m.box(b, (-0.8, 33.2, -6.5), (1.6, 1.6, 0.4), solid('#f2f2ee'))
    items.append(('tricorne', meta(ns, 'tricorne', 'Dreispitz', 'Tricorne', 'hat', 'Der klassische Piratenhut mit Goldrand.',
                                   'The classic pirate hat with a golden trim.', animations=['animation.btb_pirates.tricorne'], tags=['pirat', 'hut']), m,
                  {'animation.btb_pirates.tricorne': anim(hat={'rotation': [0, 0, 'math.sin(q.walk_distance * 20) * 2 * math.min(q.modified_move_speed * 3, 1)']})},
                  HEAD, 'front'))

    m = Model('geometry.btb_pirates.eyepatch', seed=322)
    b = m.bone('patch', [0, 28, -4])
    m.box(b, (-3.2, 26.6, -4.65), (2.4, 2.6, 0.3), solid('#141414', noise=0.04))
    strap = solid('#1e1e1e')
    m.box(b, (-4.6, 28.8, -4.62), (9.2, 0.6, 0.2), strap)
    m.box(b, (-4.62, 28.8, -4.6), (0.2, 0.6, 9.2), strap)
    m.box(b, (4.42, 28.8, -4.6), (0.2, 0.6, 9.2), strap)
    m.box(b, (-4.6, 28.8, 4.42), (9.2, 0.6, 0.2), strap)
    items.append(('eyepatch', meta(ns, 'eyepatch', 'Augenklappe', 'Eyepatch', 'face_accessory', 'Arrr! Eine schwarze Augenklappe.',
                                   'Arrr! A black eyepatch.', tags=['pirat']), m, None, HEAD, 'front'))

    m = Model('geometry.btb_pirates.parrot', seed=323)
    b = m.bone('parrot', [-6, 24, 0])
    red, blue, yellow = solid('#d02a1e', noise=0.08), solid('#2a5ad0', noise=0.08), solid('#f0c030', noise=0.08)
    m.box(b, (-7, 24.5, -1), (2, 3, 2), red)
    m.box(b, (-7.5, 23.5, -0.5), (1, 2, 2), blue)
    m.box(b, (-5, 23.5, -0.5), (1, 2, 2), blue)
    m.box(b, (-6.5, 22, 0.8), (1, 2.5, 1), yellow)
    head = m.bone('parrot_head', [-6, 27.5, 0], parent='parrot')
    m.box(head, (-6.9, 27.5, -1.2), (1.8, 1.8, 1.8), faces(red, front=lambda f_, x, y, w, h, r: (16, 16, 16, 255) if (y == 0 and x == 0) else rgb('#d02a1e') + (255,)))
    m.box(head, (-6.4, 27.7, -1.9), (0.8, 0.9, 0.8), solid('#3a3a3a'))
    items.append(('parrot', meta(ns, 'parrot', 'Papagei', 'Parrot', 'companion', 'Ein bunter Papagei auf der Schulter – er nickt und schaut sich um.',
                                 'A colourful parrot on your shoulder – it nods and looks around.', attachment='right_shoulder',
                                 animations=['animation.btb_pirates.parrot'], tags=['pirat', 'begleiter']), m,
                  {'animation.btb_pirates.parrot': anim(parrot_head={'rotation': ['math.sin(q.anim_time * 300) * 8 * (math.mod(q.anim_time, 2.5) < 0.6 ? 1 : 0)',
                                                                                   'math.sin(q.anim_time * 45) * 35', 0]},
                                                        parrot={'position': [0, 'math.abs(math.sin(q.anim_time * 200)) * 0.3', 0]})}, HEAD_BODY, 'front'))

    m = Model('geometry.btb_pirates.cutlass', seed=324)
    b = m.bone('body', [0, 24, 0])
    steel = vertical('#e8ecf0', '#9aa2ac')
    m.box(b, (4.2, 4, -2.8), (0.6, 8, 1.4), steel, rotation=[0, 0, -12], pivot=[4.5, 12, -2])
    m.box(b, (3.6, 12, -3.2), (1.8, 0.8, 2.2), solid('#e0b040'), rotation=[0, 0, -12], pivot=[4.5, 12, -2])
    m.box(b, (4.2, 12.8, -2.5), (0.6, 2.5, 0.8), solid('#5a3a22'), rotation=[0, 0, -12], pivot=[4.5, 12, -2])
    m.box(b, (-4.5, 11.5, -2.6), (9, 1.2, 5.2), solid('#4a3020', noise=0.06))
    items.append(('cutlass', meta(ns, 'cutlass', 'Säbel', 'Cutlass', 'belt', 'Ein Piratensäbel am Gürtel.', 'A pirate cutlass on the belt.',
                                  tags=['pirat']), m, None, FULL, 'front'))
    write_pack(os.path.join(BASE, 'btb_pirates'), 'btb_builtin_pirates', 'Piraten', 'Pirates',
               'Dreispitz, Augenklappe, Papagei und Säbel.', 'Tricorne, eyepatch, parrot and cutlass.', items)


if __name__ == '__main__':
    winter()
    halloween()
    pirates()
