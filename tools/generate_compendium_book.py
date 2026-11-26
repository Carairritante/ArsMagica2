#!/usr/bin/env python3
"""Draw the Arcane Compendium textures and write its Blockbench rig.

Model space is in pixels, y up, front cover towards -Z, hinge at x=0 with the covers
extending towards -x. The renderer turns the two half bones to open the book.
Run from anywhere: python tools/generate_compendium_book.py
"""
import base64
import io
import json
import math
import random
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/arsmagica2'
CHALK = ASSETS / 'textures/blocks/wizardchalk'

OUTLINE = (24, 12, 40, 255)
PURPLE_DARK = (45, 22, 82, 255)
PURPLE = (76, 38, 132, 255)
PURPLE_LIGHT = (112, 62, 178, 255)
GOLD_DARK = (146, 96, 30, 255)
GOLD = (218, 165, 58, 255)
GOLD_LIGHT = (255, 224, 130, 255)
CYAN = (122, 232, 255, 255)
VIOLET = (188, 132, 255, 255)
CREAM = (239, 227, 196, 255)
CREAM_DARK = (205, 188, 152, 255)
INK = (112, 84, 142, 255)

rng = random.Random(2026)


def leather(draw, box, base=PURPLE, dark=PURPLE_DARK, light=PURPLE_LIGHT):
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            roll = rng.random()
            draw.point((x, y), dark if roll < 0.16 else light if roll > 0.93 else base)


def rune(atlas, index, x, y, size, color):
    glyph = Image.open(CHALK / ('WizardChalk%d.png' % index)).convert('RGBA').resize((size, size), Image.NEAREST)
    solid = Image.new('RGBA', glyph.size, color)
    atlas.paste(solid, (x, y), glyph.getchannel('A').point(lambda a: 255 if a > 90 else 0))


def star(draw, cx, cy, long, short, color):
    points = []
    for i in range(16):
        angle = i * math.pi / 8 - math.pi / 2
        radius = long if i % 4 == 0 else (long * 0.62 if i % 2 == 0 else short)
        points.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
    draw.polygon(points, fill=color)


def cover(atlas, draw, x, front):
    w, h = 34, 46
    leather(draw, (x, 0, x + w, h))
    draw.rectangle((x, 0, x + w - 1, h - 1), outline=OUTLINE)
    draw.rectangle((x + 2, 2, x + w - 3, h - 3), outline=GOLD)
    draw.rectangle((x + 3, 3, x + w - 4, h - 4), outline=GOLD_DARK)
    cx, cy = x + w // 2, h // 2
    for dx, dy in ((4, 4), (w - 11, 4), (4, h - 11), (w - 11, h - 11)):
        rune(atlas, 1 + (dx + dy) % 16, x + dx, dy, 7, CYAN)
    if front:
        draw.ellipse((cx - 11, cy - 11, cx + 11, cy + 11), outline=GOLD)
        star(draw, cx, cy, 13, 4, GOLD_DARK)
        star(draw, cx, cy, 11, 3, GOLD)
        draw.polygon([(cx, cy - 6), (cx + 6, cy), (cx, cy + 6), (cx - 6, cy)], fill=PURPLE_DARK, outline=GOLD_LIGHT)
    else:
        draw.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), outline=GOLD)
        draw.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), outline=GOLD_DARK)
        rune(atlas, 5, cx - 4, cy - 4, 9, GOLD_LIGHT)


def page(atlas, draw, x, y, circle):
    w, h = 31, 42
    for py in range(y, y + h):
        for px in range(x, x + w):
            draw.point((px, py), CREAM_DARK if rng.random() < 0.05 else CREAM)
    draw.rectangle((x + 1, y + 1, x + w - 2, y + h - 2), outline=(176, 146, 196, 255))
    cx = x + w // 2
    if circle:
        cy = y + 17
        draw.ellipse((cx - 12, cy - 12, cx + 12, cy + 12), outline=INK)
        draw.ellipse((cx - 8, cy - 8, cx + 8, cy + 8), outline=VIOLET)
        points = [(cx + math.cos(i * math.tau / 3 - math.pi / 2) * 8, cy + math.sin(i * math.tau / 3 - math.pi / 2) * 8) for i in range(3)]
        draw.polygon(points, outline=INK)
        draw.polygon([(px, 2 * cy - py) for px, py in points], outline=INK)
        for i in range(6):
            a = i * math.tau / 6
            px, py = cx + int(round(math.cos(a) * 12)), cy + int(round(math.sin(a) * 12))
            draw.ellipse((px - 2, py - 2, px + 2, py + 2), fill=CREAM, outline=VIOLET)
        lines = (33, 36)
    else:
        rune(atlas, 9, x + 4, y + 4, 8, INK)
        for row in (5, 8, 11):
            draw.line((x + 14, y + row, x + w - 5 - rng.randrange(4), y + row), fill=INK)
        draw.ellipse((x + w - 15, y + 27, x + w - 4, y + 38), outline=VIOLET)
        rune(atlas, 12, x + w - 13, y + 29, 8, INK)
        lines = (16, 19, 22, 31, 34, 37)
    for row in lines:
        end = x + w - 5 - rng.randrange(6) if circle or row < 27 else x + w - 18
        cursor = x + 4
        while cursor < end:
            length = rng.randrange(2, 6)
            draw.line((cursor, y + row, min(end, cursor + length), y + row), fill=INK)
            cursor += length + 2


def build_atlas():
    atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)
    cover(atlas, draw, 0, True)
    cover(atlas, draw, 34, False)
    leather(draw, (68, 0, 82, 46))  # spine
    for y in (4, 5, 14, 30, 31, 40):
        draw.line((68, y, 81, y), fill=GOLD if y % 2 == 0 else GOLD_DARK)
    rune(atlas, 3, 71, 19, 8, GOLD_LIGHT)
    leather(draw, (84, 0, 116, 46), PURPLE_DARK, OUTLINE, PURPLE)  # inner lining
    page(atlas, draw, 0, 48, True)
    page(atlas, draw, 32, 48, False)
    for y in range(48, 90):  # page edges
        for x in range(64, 80):
            draw.point((x, y), CREAM_DARK if x % 3 == 0 else CREAM)
    for y in range(48, 64):  # gold
        for x in range(84, 100):
            draw.point((x, y), GOLD_LIGHT if (x + y) % 9 == 0 else GOLD_DARK if rng.random() < 0.18 else GOLD)
    draw.rectangle((100, 48, 115, 63), fill=(120, 60, 220, 255))  # gem
    draw.polygon([(100, 48), (115, 48), (100, 63)], fill=(176, 128, 255, 255))
    draw.rectangle((103, 51, 106, 54), fill=(235, 250, 255, 255))
    draw.rectangle((108, 57, 112, 61), fill=CYAN)
    for y in range(48, 80):  # ribbon
        for x in range(118, 126):
            draw.point((x, y), GOLD_DARK if x in (118, 125) else GOLD)
    return atlas


def build_circle():
    size = 64
    image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    c = size // 2
    for radius, color in ((31, CYAN), (28, VIOLET), (18, CYAN), (6, VIOLET)):
        draw.ellipse((c - radius, c - radius, c + radius - 1, c + radius - 1), outline=color)
    for start in (-math.pi / 2, math.pi / 2):
        draw.polygon([(c + math.cos(start + i * math.tau / 3) * 18, c + math.sin(start + i * math.tau / 3) * 18) for i in range(3)], outline=VIOLET)
    for i in range(10):
        a = i * math.tau / 10
        rune(image, 1 + i, int(c + math.cos(a) * 23 - 3), int(c + math.sin(a) * 23 - 3), 6, CYAN)
    return image


def build_icon():
    """32x32 closed book seen slightly from the right, for the hotbar and inventory."""
    image = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.polygon([(4, 3), (24, 1), (28, 4), (28, 27), (8, 30), (4, 27)], fill=OUTLINE)
    draw.polygon([(25, 4), (27, 5), (27, 26), (25, 27)], fill=CREAM)  # page block
    for y in range(7, 26, 3):
        draw.line((25, y, 27, y), fill=CREAM_DARK)
    draw.polygon([(8, 28), (24, 26), (27, 26), (9, 29)], fill=CREAM_DARK)
    draw.polygon([(5, 4), (8, 5), (8, 28), (5, 26)], fill=PURPLE_DARK)  # spine
    for y in (8, 15, 22):
        draw.line((5, y, 8, y + 1), fill=GOLD)
    draw.polygon([(8, 5), (24, 3), (24, 26), (8, 28)], fill=PURPLE)
    draw.line((9, 6, 23, 4), fill=PURPLE_LIGHT)
    draw.line((9, 6, 9, 26), fill=PURPLE_LIGHT)
    for corner in ([(8, 5), (13, 5), (8, 10)], [(24, 3), (19, 4), (24, 8)], [(8, 28), (13, 27), (8, 23)], [(24, 26), (19, 27), (24, 21)]):
        draw.polygon(corner, fill=GOLD)
    for point in ((9, 6), (23, 4), (9, 26), (23, 24)):
        draw.point(point, fill=GOLD_LIGHT)
    cx, cy = 16, 15
    star(draw, cx, cy, 8, 2.4, GOLD)
    draw.polygon([(cx, cy - 4), (cx + 4, cy), (cx, cy + 4), (cx - 4, cy)], fill=(130, 70, 232, 255), outline=GOLD_LIGHT)
    draw.point((cx - 1, cy - 1), fill=(240, 252, 255, 255))
    draw.point((cx, cy - 1), fill=CYAN)
    draw.point((cx - 1, cy), fill=CYAN)
    draw.line((19, 28, 19, 31), fill=GOLD)
    draw.line((20, 28, 20, 30), fill=GOLD_DARK)
    for x, y in ((28, 1), (2, 19)):
        draw.point((x, y), fill=(240, 252, 255, 255))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            draw.point((x + dx, y + dy), fill=CYAN)
    return image


# UV rectangles inside the 128x128 atlas
FRONT, BACK, SPINE, LINING = [0, 0, 34, 46], [34, 0, 68, 46], [68, 0, 82, 46], [84, 0, 116, 46]
PAGE_LEFT, PAGE_RIGHT, EDGE = [0, 48, 31, 90], [32, 48, 63, 90], [64, 48, 80, 90]
GOLD_UV, GEM_UV, RIBBON = [84, 48, 100, 64], [100, 48, 116, 64], [118, 48, 126, 80]
DIRECTIONS = ('north', 'south', 'east', 'west', 'up', 'down')


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'am2/compendium/' + name))


def cube(name, start, end, default, rotation=(0, 0, 0), **faces):
    return {'name': name, 'box_uv': False, 'from': list(start), 'to': list(end), 'autouv': 0, 'color': 0,
            'origin': [(a + b) / 2 for a, b in zip(start, end)], 'rotation': list(rotation),
            'faces': {d: {'uv': faces.get(d, default), 'texture': 0} for d in DIRECTIONS},
            'type': 'cube', 'uuid': ident(name)}


def build_model(atlas):
    elements = []

    def bone(name, cubes, children=()):
        elements.extend(cubes)
        return {'name': name, 'origin': [0, 0, 0], 'color': 0, 'uuid': ident('bone/' + name), 'export': True,
                'isOpen': True, 'visibility': True, 'children': [c['uuid'] for c in cubes] + list(children)}

    def corners(prefix, z0, z1):
        return [cube(prefix + '_corner_top', (-8.65, 4.3, z0), (-7.0, 6.05, z1), GOLD_UV),
                cube(prefix + '_corner_bottom', (-8.65, -6.05, z0), (-7.0, -4.3, z1), GOLD_UV)]

    gem = bone('gem', [
        cube('gem_mount', (-5.55, -1.35, -1.85), (-2.85, 1.35, -1.55), GOLD_UV, (0, 0, 45)),
        cube('gem_stone', (-5.05, -0.85, -2.25), (-3.35, 0.85, -1.8), GEM_UV, (0, 0, 45))])
    front = bone('front_half', [
        cube('front_cover', (-8.4, -5.8, -1.6), (0, 5.8, -1.0), SPINE, north=FRONT, south=LINING),
        cube('front_pages', (-7.8, -5.3, -1.0), (-0.1, 5.3, -0.02), EDGE, south=PAGE_LEFT, north=PAGE_LEFT),
        cube('front_clasp', (-9.0, -1.0, -1.75), (-8.0, 1.0, -0.6), GOLD_UV)] + corners('front', -1.75, -0.9), [gem])
    back = bone('back_half', [
        cube('back_cover', (-8.4, -5.8, 1.0), (0, 5.8, 1.6), SPINE, south=BACK, north=LINING),
        cube('back_pages', (-7.8, -5.3, 0.02), (-0.1, 5.3, 1.0), EDGE, north=PAGE_RIGHT, south=PAGE_RIGHT),
        cube('ribbon', (-2.0, -7.7, 0.05), (-1.1, -5.2, 0.2), RIBBON)] + corners('back', 0.9, 1.75))
    spine = bone('spine', [cube('spine_leather', (-0.05, -5.8, -1.6), (0.7, 5.8, 1.6), SPINE)])
    leaves = [bone('leaf%d' % i, [cube('leaf%d_paper' % i, (-7.6, -5.15, -0.03), (-0.1, 5.15, 0.03), EDGE,
                                       north=PAGE_RIGHT, south=PAGE_LEFT)]) for i in (1, 2, 3)]

    buffer = io.BytesIO()
    atlas.save(buffer, 'PNG')
    return {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
            'name': 'arcane_compendium', 'model_identifier': 'arcane_compendium',
            'resolution': {'width': 128, 'height': 128}, 'elements': elements,
            'outliner': [back, front, spine] + leaves,
            'textures': [{'name': 'arcane_compendium.png', 'id': '0', 'uuid': ident('texture'),
                          'source': 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()}]}


def main():
    atlas = build_atlas()
    model = build_model(atlas)
    (ASSETS / 'models/items/arcane_compendium.bbmodel').write_text(json.dumps(model, indent=1), encoding='utf-8')
    (ASSETS / 'textures/models').mkdir(parents=True, exist_ok=True)
    build_circle().save(ASSETS / 'textures/models/compendium_circle.png')
    build_icon().save(ASSETS / 'textures/items/arcanecompendium.png')
    print('written', len(model['elements']), 'cubes')


if __name__ == '__main__':
    main()
