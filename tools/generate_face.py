#!/usr/bin/env python3
"""Generates the face parts for the character tab (eyes and mouths, animated) as a built-in pack.

All parts sit just in front of the face (head front is z -4, hat layer -4.5) and follow the head.
Eyelids and lips are painted light grey with a tint mask so the character tab can colour them like
the skin (blinking lids then look like the player's own skin).
"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from cosmetic_kit import *  # noqa

ROOT = 'src/main/resources/assets/btb_skinstudio/builtin/btb_face'
AUTHOR = 'BTB Studios'
Z = -4.62  # front of the parts


def meta(name, de, en, typ, desc_de, desc_en, tint=None, animations=None):
    m = {'id': 'btb_face:' + name, 'version': '1.0.0', 'name': {'de': de, 'en': en}, 'author': AUTHOR, 'type': typ,
         'description': {'de': desc_de, 'en': desc_en}, 'tags': ['character', 'face']}
    if tint:
        m['tint'] = tint
    if animations:
        m['animations'] = animations
    return m


SKIN_TINT = {'enabled': True, 'default': '#C69C7A', 'palette': ['#F2D2B4', '#E0B08A', '#C69C7A', '#9A6A48', '#6A4430', '#4A2E20']}
BLINK = 'math.mod(q.anim_time, 4.2) < 0.16 ? 1 : 0.02'


def eyes_googly(cos):
    m = Model('geometry.btb_face.googly', seed=201)
    for side, x0 in (('left_eye', 0.6), ('right_eye', -3.6)):
        b = m.bone(side, [x0 + 1.5, 28, Z], parent=None)
        m.box(b, (x0, 26.4, Z - 0.9), (3, 3, 0.9), faces(solid('#fafafa', noise=0.02), front=bordered('#ffffff', '#d8d8d8')))
        p = m.bone(side + '_pupil', [x0 + 1.5, 27.9, Z - 0.95], parent=side)
        m.box(p, (x0 + 0.9, 27.3, Z - 1.0), (1.2, 1.2, 0.1), solid('#151515'))
    anim_ = {'loop': True, 'bones': {
        'left_eye_pupil': {'position': ['math.sin(q.anim_time * 470) * 0.55', 'math.cos(q.anim_time * 380) * 0.5', 0]},
        'right_eye_pupil': {'position': ['math.sin(q.anim_time * 430 + 60) * 0.55', 'math.cos(q.anim_time * 410 + 40) * 0.5', 0]}}}
    cos.append(('googly', meta('googly', 'Glubschaugen', 'Googly Eyes', 'face_accessory',
                               'Große Wackelaugen, die Pupillen hüpfen bei jeder Bewegung.', 'Big googly eyes with jiggling pupils.',
                               animations=['animation.btb_face.googly']), m, {'animation.btb_face.googly': anim_}))


def eyes_cartoon(cos, key, de, en, iris, lid_rest, desc_de, desc_en):
    m = Model('geometry.btb_face.' + key, seed=202)
    for side, x0 in (('left', 0.5), ('right', -3.5)):
        b = m.bone(side + '_eye', [x0 + 1.5, 28, Z])
        m.box(b, (x0, 26, Z - 0.2), (3, 4, 0.2), solid('#fafafa'))
        m.box(b, (x0 + 0.5 if side == 'left' else x0 + 0.5, 26.5, Z - 0.3), (2, 2.5, 0.12), solid(iris))
        m.box(b, (x0 + 1, 27, Z - 0.4), (1, 1.5, 0.1), solid('#101010'))
        m.box(b, (x0 + 1.6, 28.3, Z - 0.45), (0.5, 0.5, 0.05), solid('#ffffff'))
        lid = m.bone(side + '_lid', [x0 + 1.5, 30, Z - 0.5], parent=side + '_eye')
        m.box(lid, (x0 - 0.1, 26, Z - 0.6), (3.2, 4, 0.1), solid('#e8e8e8'), tint=True)
    bones = {}
    for side in ('left', 'right'):
        if lid_rest > 0:
            bones[side + '_lid'] = {'scale': [1, 'math.mod(q.anim_time, 5.5) < 0.22 ? 1 : %s' % lid_rest, 1]}
        else:
            bones[side + '_lid'] = {'scale': [1, BLINK, 1]}
    anim_name = 'animation.btb_face.%s' % key
    cos.append((key, meta(key, de, en, 'face_accessory', desc_de, desc_en, tint=SKIN_TINT, animations=[anim_name]), m,
                {anim_name: {'loop': True, 'bones': bones}}))


def mouth_smile(cos):
    m = Model('geometry.btb_face.smile', seed=210)
    b = m.bone('mouth', [0, 25.5, Z])
    dark = solid('#5a2a24')
    m.box(b, (-1.5, 25, Z - 0.1), (3, 0.6, 0.1), dark)
    m.box(b, (-2.1, 25.5, Z - 0.1), (0.6, 0.6, 0.1), dark)
    m.box(b, (1.5, 25.5, Z - 0.1), (0.6, 0.6, 0.1), dark)
    cos.append(('smile', meta('smile', 'Lächeln', 'Smile', 'mustache', 'Ein freundliches Lächeln.', 'A friendly smile.'), m, None))


def mouth_talk(cos):
    m = Model('geometry.btb_face.talk', seed=211)
    b = m.bone('mouth', [0, 25.6, Z])
    m.box(b, (-1.5, 24.6, Z - 0.1), (3, 2, 0.1), solid('#3a1414'))
    m.box(b, (-1, 24.7, Z - 0.15), (2, 0.6, 0.05), solid('#d05a5a'))
    m.box(b, (-1.5, 26.2, Z - 0.15), (3, 0.4, 0.05), solid('#f4f0e8'))
    anim_ = {'loop': True, 'bones': {'mouth': {'scale': [1, '0.35 + math.abs(math.sin(q.anim_time * 520)) * 0.65 * (math.mod(q.anim_time, 3) < 2 ? 1 : 0.2)', 1]}}}
    cos.append(('talk', meta('talk', 'Sprech-Mund', 'Talking Mouth', 'mustache', 'Ein Mund, der sich wie beim Sprechen bewegt.',
                             'A mouth that moves as if talking.', animations=['animation.btb_face.talk']), m, {'animation.btb_face.talk': anim_}))


def mouth_tongue(cos):
    m = Model('geometry.btb_face.tongue', seed=212)
    b = m.bone('mouth', [0, 25.5, Z])
    m.box(b, (-1.5, 25, Z - 0.1), (3, 0.7, 0.1), solid('#3a1414'))
    m.box(b, (-0.8, 23.8, Z - 0.5), (1.6, 1.4, 0.4), faces(solid('#e0707a'), front=bordered('#f08890', '#c05860')))
    drop = m.bone('drool', [0.6, 23.8, Z - 0.6], parent='mouth')
    m.box(drop, (0.3, 23, Z - 0.7), (0.5, 0.8, 0.3), lambda f_, x, y, w, h, r: (190, 225, 255, 190))
    anim_ = {'loop': True, 'bones': {'drool': {'position': [0, '-math.mod(q.anim_time, 2.4) * 1.8', 0],
                                               'scale': [1, '1 + math.mod(q.anim_time, 2.4) * 0.6', 1]}}}
    cos.append(('tongue', meta('tongue', 'Zunge raus', 'Tongue Out', 'mustache', 'Zunge raus – mit tropfender Spucke.',
                               'Tongue out – with a dripping drop.', animations=['animation.btb_face.tongue']), m, {'animation.btb_face.tongue': anim_}))


def build():
    if os.path.isdir(ROOT):
        shutil.rmtree(ROOT)
    os.makedirs(ROOT)
    cos = []
    eyes_googly(cos)
    eyes_cartoon(cos, 'big_eyes', 'Kulleraugen', 'Big Eyes', '#3a7ac8', 0, 'Große Augen, die blinzeln.', 'Big eyes that blink.')
    eyes_cartoon(cos, 'sleepy_eyes', 'Verschlafene Augen', 'Sleepy Eyes', '#5a8a3a', 0.45, 'Halb geschlossene Augen mit langsamem Blinzeln.',
                 'Half-closed eyes with a slow blink.')
    eyes_cartoon(cos, 'blink_eyes', 'Blinzel-Augen', 'Blinking Eyes', '#6a4a2a', 0, 'Augen in Skin-Größe mit Lidern, die blinzeln.',
                 'Skin-sized eyes with lids that blink.')
    mouth_smile(cos)
    mouth_talk(cos)
    mouth_tongue(cos)
    for key, mt, model, anims in cos:
        write_cosmetic(ROOT, key, mt, model, anims, HEAD)
    pack = {'format': 1, 'id': 'btb_builtin_face', 'version': '1.0.0', 'name': {'de': 'BTB Gesicht', 'en': 'BTB Face'},
            'description': {'de': 'Augen und Münder für den Charakter-Editor.', 'en': 'Eyes and mouths for the character editor.'}, 'author': AUTHOR}
    with open(os.path.join(ROOT, 'pack.json'), 'w', encoding='utf-8') as f:
        json.dump(pack, f, indent=2, ensure_ascii=False)
    print('%d face parts written to %s' % (len(cos), ROOT))


if __name__ == '__main__':
    build()
