#!/usr/bin/env python3
"""Generate the Arcane Guardian Spellbook 3D Blockbench model (.bbmodel) — V3 Artisanal Polish:
1. Directional pixel-art shading (polished beveled antique gold + smooth royal crimson leather
   with spine/edge depth + faceted ruby gems — zero random pixel noise)
2. Sleeker, sharper crimson-ruby 'Z' rune in crisp thin relief with glowing core accents,
   framed by a delicate antique-gold astrolabe finial motif
3. Refined spine (2 sleek antique-gold bands + 2 slim accent ribs + diamond-rotated ruby)
   and stepped gothic corner filigree plates with 45-deg diamond ruby gems
4. Delicate 3-piece golden clasp + crimson-gold swallowtail V-notch bookmark ribbon (own bone)
5. Stepped parchment page block (physically layered leaf edges) + 3 turning leaves
6. Built-in Blockbench 5.0 animations ('idle' & 'open') ready to preview right inside Blockbench!

Front faces -Z, hinge at x=0, covers extend towards -X.
Run: python tools/generate_arcane_spellbook.py
"""
import base64
import io
import json
import math
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/arsmagica2'
CHALK = ASSETS / 'textures/blocks/wizardchalk'

# Curated Royal Crimson + Polished Antique Gold + Faceted Ruby palette
OUTLINE = (24, 5, 10, 255)
CRIMSON_DEEP = (54, 8, 16, 255)
CRIMSON_DARK = (78, 12, 22, 255)
CRIMSON = (112, 18, 32, 255)
CRIMSON_MID = (130, 22, 38, 255)
CRIMSON_HI = (152, 30, 48, 255)

GOLD_SHADOW = (96, 62, 18, 255)
GOLD_DARK = (142, 96, 30, 255)
GOLD = (206, 152, 50, 255)
GOLD_HI = (242, 198, 92, 255)
GOLD_SPEC = (255, 234, 156, 255)

RUBY_DEEP = (118, 12, 32, 255)
RUBY_DARK = (168, 20, 48, 255)
RUBY = (224, 38, 76, 255)
RUBY_HI = (255, 110, 140, 255)
RUBY_CORE = (255, 220, 228, 255)

CREAM_SHADOW = (142, 118, 78, 255)
CREAM_DARK = (192, 168, 118, 255)
CREAM = (232, 212, 164, 255)
CREAM_HI = (246, 232, 192, 255)
INK = (118, 22, 42, 255)
INK_GOLD = (176, 128, 44, 255)


def lerp_col(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def directional_leather(draw, box, spine_side='right'):
    """Smooth bookbinding leather with subtle spine crease, top-left highlight & edge vignette."""
    x0, y0, x1, y1 = box
    w = max(1, x1 - x0 - 1)
    h = max(1, y1 - y0 - 1)
    for y in range(y0, y1):
        ny = (y - y0) / h
        for x in range(x0, x1):
            nx = (x - x0) / w
            # Distance from edges
            edge = max(abs(nx - 0.5) * 2.0, abs(ny - 0.5) * 2.0)
            # Subtle directional lighting (top-left brighter, bottom-right & spine darker)
            light = (1.0 - nx * 0.45 - ny * 0.45)
            # Spine hinge crease shadow
            spine_dist = (1.0 - nx) if spine_side == 'right' else nx
            crease = max(0.0, 1.0 - spine_dist / 0.16)
            # Fine deterministic leather weave (subtle, never noisy!)
            grain = ((x * 7 + y * 13) % 11 == 0) * -0.06 + ((x * 5 + y * 3) % 17 == 0) * 0.04
            val = 0.50 + (light - 0.55) * 0.34 - (edge ** 2.4) * 0.36 - crease * 0.30 + grain
            if val < 0.22:
                col = lerp_col(CRIMSON_DEEP, CRIMSON_DARK, val / 0.22)
            elif val < 0.58:
                col = lerp_col(CRIMSON_DARK, CRIMSON, (val - 0.22) / 0.36)
            else:
                col = lerp_col(CRIMSON, CRIMSON_HI, (val - 0.58) / 0.42)
            draw.point((x, y), col)


def paste_rune(atlas, index, x, y, size, color):
    path = CHALK / ('WizardChalk%d.png' % index)
    if not path.exists():
        return
    glyph = Image.open(path).convert('RGBA').resize((size, size), Image.NEAREST)
    solid = Image.new('RGBA', glyph.size, color)
    atlas.paste(solid, (x, y), glyph.getchannel('A').point(lambda a: 255 if a > 90 else 0))


def draw_cover(atlas, draw, x, front):
    w, h = 34, 46
    # On FACE_NORTH (front), u0 is +X (spine side = left of texture), u1 is -X (outer edge = right)
    # On FACE_SOUTH (back), u0 is -X (outer edge = left), u1 is +X (spine side = right)
    directional_leather(draw, (x, 0, x + w, h), spine_side='left' if front else 'right')
    cx, cy = x + w // 2, h // 2
    if front:
        # Deep oxblood radial shadow well behind the sculpted Z rune so the ruby pops dramatically!
        for py in range(5, h - 5):
            for px in range(x + 5, x + w - 5):
                dx = (px - cx) / 11.5
                dy = (py - cy) / 15.5
                d = math.hypot(dx, dy)
                if d < 1.0:
                    well = (1.0 - d * d) * 0.48
                    cur = atlas.getpixel((px, py))
                    draw.point((px, py), lerp_col(cur, CRIMSON_DEEP, well))

    draw.rectangle((x, 0, x + w - 1, h - 1), outline=OUTLINE)
    # Crisp blind-tooled dark inner groove + balanced antique-gold double hairline
    draw.rectangle((x + 3, 3, x + w - 4, h - 4), outline=CRIMSON_DEEP)
    draw.rectangle((x + 4, 4, x + w - 5, h - 5), outline=GOLD_DARK)
    draw.line((x + 4, 4, x + w - 5, 4), fill=GOLD)
    draw.line((x + 4, 4, x + 4, h - 5), fill=GOLD)

    # Subtle embossed WizardChalk runes near inner corners (tone-on-tone luxury tooling)
    embossed = (72, 10, 20, 255)
    for idx, (dx, dy) in enumerate(((6, 6), (w - 13, 6), (6, h - 13), (w - 13, h - 13))):
        paste_rune(atlas, 1 + idx * 3, x + dx, dy, 7, embossed)

    if front:
        # Wide, generous engraved antique-gold astrolabe frame giving clean breathing room around the Z rune
        draw.ellipse((cx - 12, cy - 16, cx + 12, cy + 16), outline=CRIMSON_DEEP)
        draw.ellipse((cx - 11, cy - 15, cx + 11, cy + 15), outline=GOLD_DARK)
        draw.arc((cx - 11, cy - 15, cx + 11, cy + 15), start=205, end=335, fill=GOLD)
        # Cardinal micro-ticks
        for tx, ty in ((cx, cy - 15), (cx, cy + 15), (cx - 11, cy), (cx + 11, cy)):
            draw.point((tx, ty), GOLD_SPEC)
    else:
        # Back cover: balanced royal arcane medallion
        draw.ellipse((cx - 10, cy - 10, cx + 10, cy + 10), outline=GOLD_DARK)
        draw.arc((cx - 10, cy - 10, cx + 10, cy + 10), start=190, end=350, fill=GOLD_HI)
        draw.ellipse((cx - 7, cy - 7, cx + 7, cy + 7), outline=GOLD_DARK)
        draw.polygon([(cx, cy - 5), (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], fill=RUBY, outline=GOLD_HI)
        draw.point((cx - 1, cy - 1), RUBY_CORE)
        paste_rune(atlas, 7, cx - 3, cy - 17, 7, GOLD)
        paste_rune(atlas, 11, cx - 3, cy + 11, 7, GOLD)


def draw_page(atlas, draw, x, y, circle):
    w, h = 31, 42
    for py in range(y, y + h):
        ny = (py - y) / float(h - 1)
        for px in range(x, x + w):
            nx = (px - x) / float(w - 1)
            gutter = max(0.0, 1.0 - min(nx, 1.0 - nx) / 0.14)
            aged = max(0.0, abs(ny - 0.5) * 2.0 - 0.72)
            col = lerp_col(CREAM_HI, CREAM, min(1.0, 0.35 + aged * 0.9))
            if gutter > 0.0:
                col = lerp_col(col, CREAM_DARK, gutter * 0.75)
            draw.point((px, py), col)
    draw.rectangle((x + 1, y + 1, x + w - 2, y + h - 2), outline=INK_GOLD)
    cx = x + w // 2
    if circle:
        cy = y + 17
        draw.ellipse((cx - 11, cy - 11, cx + 11, cy + 11), outline=INK)
        draw.ellipse((cx - 7, cy - 7, cx + 7, cy + 7), outline=INK_GOLD)
        pts = [(cx + math.cos(i * math.tau / 3 - math.pi / 2) * 7.5,
                cy + math.sin(i * math.tau / 3 - math.pi / 2) * 7.5) for i in range(3)]
        draw.polygon(pts, outline=INK)
        draw.polygon([(px, 2 * cy - py) for px, py in pts], outline=INK)
        draw.point((cx, cy), RUBY)
        lines = (32, 35, 38)
    else:
        paste_rune(atlas, 5, x + 4, y + 4, 8, INK)
        for row in (5, 8, 11):
            draw.line((x + 14, y + row, x + w - 6 - (row % 3), y + row), fill=INK)
        draw.ellipse((x + w - 15, y + 26, x + w - 4, y + 37), outline=INK_GOLD)
        paste_rune(atlas, 13, x + w - 13, y + 28, 8, INK)
        lines = (16, 19, 22, 30, 33, 36)
    for row in lines:
        end = x + w - 6 if circle or row < 26 else x + w - 17
        cursor = x + 4
        step = 0
        while cursor < end:
            length = 3 + ((row + step) % 4)
            draw.line((cursor, y + row, min(end, cursor + length), y + row), fill=INK)
            cursor += length + 2
            step += 1


def build_atlas():
    atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)

    draw_cover(atlas, draw, 0, True)
    draw_cover(atlas, draw, 34, False)

    # Spine leather (68..82, 0..46) with curved cylindrical shading!
    for y in range(46):
        for x in range(68, 82):
            nx = (x - 68) / 13.0
            curve = 1.0 - abs(nx - 0.5) * 2.0  # 1 at spine ridge, 0 at edges
            col = lerp_col(CRIMSON_DEEP, CRIMSON_MID, curve ** 0.8)
            draw.point((x, y), col)
    draw.rectangle((68, 0, 81, 45), outline=OUTLINE)
    paste_rune(atlas, 3, 71, 19, 8, GOLD_HI)

    # Inner cover lining (84..116, 0..46)
    directional_leather(draw, (84, 0, 116, 46), spine_side='left')
    draw.rectangle((86, 2, 113, 43), outline=GOLD_DARK)

    # Left & Right inner pages
    draw_page(atlas, draw, 0, 48, True)
    draw_page(atlas, draw, 32, 48, False)

    # Crisp layered parchment page edges (64..80, 48..90) with subtle center gutter line
    for y in range(48, 90):
        for x in range(64, 80):
            if x in (64, 79) or y in (48, 89):
                draw.point((x, y), CREAM_SHADOW)
            elif y % 2 == 0:
                draw.point((x, y), CREAM_DARK)
            else:
                draw.point((x, y), CREAM if (y % 4 == 1) else CREAM_HI)

    # Polished beveled antique gold metal (84..100, 48..64) — ZERO random noise!
    for y in range(48, 64):
        ny = (y - 48) / 15.0
        for x in range(84, 100):
            nx = (x - 84) / 15.0
            if x == 99 or y == 63:
                col = GOLD_SHADOW
            elif x == 84 or y == 48:
                col = GOLD_SPEC
            elif x == 85 or y == 49:
                col = GOLD_HI
            elif x == 98 or y == 62:
                col = GOLD_DARK
            else:
                # Diagonal metallic sheen across polished antique gold
                sheen = max(0.0, 1.0 - abs((nx + ny) - 0.72) / 0.35)
                base_g = lerp_col(GOLD_HI, GOLD_DARK, (nx + ny) * 0.42)
                col = lerp_col(base_g, GOLD_SPEC, sheen * 0.48)
            draw.point((x, y), col)

    # Faceted glowing ruby gem (100..116, 48..64)
    for y in range(48, 64):
        ny = (y - 48) / 15.0
        for x in range(100, 116):
            nx = (x - 100) / 15.0
            if x in (100, 115) or y in (48, 63):
                col = RUBY_DEEP
            elif x == 101 or y == 49:
                col = RUBY_HI
            elif x == 114 or y == 62:
                col = RUBY_DARK
            else:
                # Bright upper-left facet + luminous crystalline core
                dist_core = math.hypot(nx - 0.36, ny - 0.36)
                if dist_core < 0.22:
                    col = lerp_col(RUBY_CORE, RUBY_HI, dist_core / 0.22)
                elif nx + ny < 0.95:
                    col = lerp_col(RUBY_HI, RUBY, (nx + ny) / 0.95)
                else:
                    col = lerp_col(RUBY, RUBY_DARK, (nx + ny - 0.95) / 1.05)
            draw.point((x, y), col)

    # Radiant rune core crystal (100..116, 66..82) — slightly brighter core for center of Z rune
    for y in range(66, 82):
        ny = (y - 66) / 15.0
        for x in range(100, 116):
            nx = (x - 100) / 15.0
            d = math.hypot(nx - 0.5, ny - 0.5) * 2.0
            if d < 0.45:
                col = lerp_col(RUBY_CORE, RUBY_HI, d / 0.45)
            else:
                col = lerp_col(RUBY_HI, RUBY, min(1.0, (d - 0.45) / 0.55))
            draw.point((x, y), col)

    # Velvet crimson-gold bookmark ribbon (118..126, 48..80)
    for y in range(48, 80):
        for x in range(118, 126):
            if x in (118, 125):
                draw.point((x, y), CRIMSON_DEEP)
            elif x in (121, 122):
                draw.point((x, y), GOLD_SPEC if y % 4 == 0 else GOLD)
            else:
                draw.point((x, y), RUBY_DARK if y < 74 else CRIMSON_DARK)

    # --- V4 DEDICATED CONTINUOUS RUBY FILAMENT & BEZEL TEXTURES (ZERO 'BEADED BLOCK' SEAMS!) ---
    # Horizontal continuous ruby filament (0..32, 96..108): dark edges top/bottom, glowing vein along center!
    for y in range(96, 108):
        ny = abs((y - 96) / 11.0 - 0.5) * 2.0  # 0 at center vein, 1 at top/bottom edge
        for x in range(0, 32):
            nx = (x / 31.0)
            wave = 0.08 * math.sin(nx * math.tau * 2.0)
            v = max(0.0, min(1.0, ny + wave))
            if v < 0.24:
                col = lerp_col(RUBY_CORE, RUBY_HI, v / 0.24)
            elif v < 0.62:
                col = lerp_col(RUBY_HI, RUBY, (v - 0.24) / 0.38)
            elif v < 0.88:
                col = lerp_col(RUBY, RUBY_DARK, (v - 0.62) / 0.26)
            else:
                col = RUBY_DEEP
            draw.point((x, y), col)

    # Vertical/diagonal continuous ruby blade (34..46, 92..124): dark left/right edges, radiant heart at center!
    for y in range(92, 124):
        ny = abs((y - 92) / 31.0 - 0.5) * 2.0  # 0 at heart of diagonal, 1 at tips
        for x in range(34, 46):
            nx = abs((x - 34) / 11.0 - 0.5) * 2.0  # 0 at center spine, 1 at side walls
            heart_boost = max(0.0, 1.0 - ny * 1.15) * 0.22
            v = max(0.0, min(1.0, nx - heart_boost))
            if v < 0.24:
                col = lerp_col(RUBY_CORE, RUBY_HI, v / 0.24)
            elif v < 0.62:
                col = lerp_col(RUBY_HI, RUBY, (v - 0.24) / 0.38)
            elif v < 0.88:
                col = lerp_col(RUBY, RUBY_DARK, (v - 0.62) / 0.26)
            else:
                col = RUBY_DEEP
            draw.point((x, y), col)

    # Dark antique-bronze / recessed cravacao bezel for the rune setting (50..66, 96..112)
    BEZEL_SHADOW = (48, 22, 14, 255)
    BEZEL_MID = (104, 66, 24, 255)
    BEZEL_RIM = (158, 110, 38, 255)
    for y in range(96, 112):
        ny = (y - 96) / 15.0
        for x in range(50, 66):
            nx = (x - 50) / 15.0
            if x in (50, 65) or y in (96, 111):
                col = BEZEL_SHADOW
            elif x == 51 or y == 97:
                col = BEZEL_RIM
            else:
                col = lerp_col(BEZEL_MID, BEZEL_SHADOW, (nx + ny) * 0.38)
            draw.point((x, y), col)

    # Oval faceted ruby glyph/droplet texture (70..86, 96..112)
    for y in range(96, 112):
        ny = (y - 96) / 15.0 - 0.5
        for x in range(70, 86):
            nx = (x - 70) / 15.0 - 0.5
            d = math.hypot(nx * 2.0, ny * 2.0)
            if d < 0.28:
                col = lerp_col(RUBY_CORE, RUBY_HI, d / 0.28)
            elif d < 0.68:
                col = lerp_col(RUBY_HI, RUBY, (d - 0.28) / 0.40)
            elif d < 0.94:
                col = lerp_col(RUBY, RUBY_DARK, (d - 0.68) / 0.26)
            else:
                col = RUBY_DEEP
            draw.point((x, y), col)

    return atlas


def build_circle():
    """64x64 crimson-gold arcane summoning circle shown when the spellbook opens."""
    size = 64
    image = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    c = size // 2
    for radius, color in ((31, GOLD), (28, RUBY_HI), (19, GOLD_SPEC), (8, RUBY)):
        draw.ellipse((c - radius, c - radius, c + radius - 1, c + radius - 1), outline=color)
    for start in (-math.pi / 2, math.pi / 2):
        pts = [(c + math.cos(start + i * math.tau / 3) * 19,
                c + math.sin(start + i * math.tau / 3) * 19) for i in range(3)]
        draw.polygon(pts, outline=RUBY_HI)
    for i in range(8):
        a = i * math.tau / 8
        paste_rune(image, 1 + i * 2, int(c + math.cos(a) * 23.5 - 3), int(c + math.sin(a) * 23.5 - 3), 6, GOLD_SPEC)
    return image


FRONT = [0, 0, 34, 46]
BACK = [34, 0, 68, 46]
SPINE = [68, 0, 82, 46]
LINING = [84, 0, 116, 46]
PAGE_LEFT = [0, 48, 31, 90]
PAGE_RIGHT = [32, 48, 63, 90]
EDGE = [64, 48, 80, 90]
GOLD_UV = [84, 48, 100, 64]
RUBY_UV = [100, 48, 116, 64]
RUBY_CORE_UV = [100, 66, 116, 82]
RIBBON = [118, 48, 126, 80]
RUNE_H_UV = [0, 96, 32, 108]
RUNE_V_UV = [34, 92, 46, 124]
RUNE_BEZEL_UV = [50, 96, 66, 112]
RUNE_NODE_UV = [70, 96, 86, 112]
DIRECTIONS = ('north', 'south', 'east', 'west', 'up', 'down')


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'am2/arcane_spellbook_v4/' + name))


def cube(name, start, end, default, rotation=(0, 0, 0), **faces):
    return {
        'name': name,
        'box_uv': False,
        'from': [round(v, 3) for v in start],
        'to': [round(v, 3) for v in end],
        'autouv': 0,
        'color': 0,
        'origin': [round((a + b) / 2.0, 3) for a, b in zip(start, end)],
        'rotation': list(rotation),
        'faces': {d: {'uv': faces.get(d, default), 'texture': 0} for d in DIRECTIONS},
        'type': 'cube',
        'uuid': ident(name),
    }


def kf(time, channel, xyz, interp='catmullrom'):
    return {
        'channel': channel,
        'data_points': [{'x': str(round(xyz[0], 3)), 'y': str(round(xyz[1], 3)), 'z': str(round(xyz[2], 3))}],
        'uuid': ident('kf/%s/%.3f/%s' % (channel, time, xyz)),
        'time': round(time, 3),
        'color': -1,
        'interpolation': interp,
    }


def build_animations():
    def anim_bone(bone_name, kfs):
        return ident('bone/' + bone_name), {
            'name': bone_name,
            'type': 'bone',
            'keyframes': kfs,
        }

    # 1. Idle animation: gentle boss-relic levitation + swaying ribbon
    idle_anim = {
        'uuid': ident('anim/idle'),
        'name': 'idle',
        'loop': 'loop',
        'override': False,
        'length': 2.4,
        'snapping': 24,
        'selected': False,
        'animators': dict([
            anim_bone('root', [
                kf(0.0, 'position', (0, 0.0, 0)),
                kf(1.2, 'position', (0, 0.55, 0)),
                kf(2.4, 'position', (0, 0.0, 0)),
            ]),
            anim_bone('ribbon', [
                kf(0.0, 'rotation', (0, 0, -4)),
                kf(1.2, 'rotation', (0, 0, 5)),
                kf(2.4, 'rotation', (0, 0, -4)),
            ]),
        ]),
    }

    # 2. Open animation: unlatch clasp -> spread covers -> flip 3 leaves -> hold open
    open_anim = {
        'uuid': ident('anim/open'),
        'name': 'open',
        'loop': 'hold',
        'override': False,
        'length': 1.2,
        'snapping': 24,
        'selected': True,
        'animators': dict([
            anim_bone('clasp', [
                kf(0.0, 'rotation', (0, 0, 0), 'linear'),
                kf(0.2, 'rotation', (0, -88, 0), 'linear'),
            ]),
            anim_bone('front_half', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.85, 'rotation', (0, -166, 0)),
                kf(1.2, 'rotation', (0, -166, 0)),
            ]),
            anim_bone('back_half', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.85, 'rotation', (0, -12, 0)),
                kf(1.2, 'rotation', (0, -12, 0)),
            ]),
            anim_bone('spine', [
                kf(0.0, 'position', (0, 0, 0)),
                kf(0.85, 'position', (-1.2, 0, 1.4)),
                kf(0.0, 'scale', (1, 1, 1)),
                kf(0.85, 'scale', (3.1, 1, 0.18)),
            ]),
            anim_bone('leaf1', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.15, 'rotation', (0, -14, 0)),
                kf(0.75, 'rotation', (0, -158, 0)),
            ]),
            anim_bone('leaf2', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.28, 'rotation', (0, -14, 0)),
                kf(0.92, 'rotation', (0, -152, 0)),
            ]),
            anim_bone('leaf3', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.42, 'rotation', (0, -14, 0)),
                kf(1.08, 'rotation', (0, -146, 0)),
            ]),
        ]),
    }

    return [idle_anim, open_anim]


def build_model(atlas):
    elements = []

    def bone(name, cubes, children=(), origin=(0, 0, 0)):
        elements.extend(cubes)
        return {
            'name': name,
            'origin': list(origin),
            'color': 0,
            'uuid': ident('bone/' + name),
            'export': True,
            'isOpen': True,
            'visibility': True,
            'children': [c['uuid'] for c in cubes] + list(children),
        }

    # Refined grimoire dimensions: 9.4 wide x 13.0 tall x 3.7 thick
    # Strict non-overlapping Z planes (ZERO z-fighting):
    #   cover surface:  ±1.85
    #   gold trim/halo: ±1.85 .. ±1.96
    #   gothic corners: ±1.20 .. ±2.01
    #   ruby gems/rune: ±2.01 .. ±2.12  (core highlight ±2.12 .. ±2.16)
    W = 9.4
    H = 6.5
    Z = 1.85

    def gothic_corners(prefix, is_front):
        """Stepped gothic gold corner filigree + 45-deg diamond-cut ruby gems."""
        z_in, z_out = (-Z + 0.62, -Z - 0.16) if is_front else (Z - 0.62, Z + 0.16)
        z0, z1 = min(z_in, z_out), max(z_in, z_out)
        gz0, gz1 = (-Z - 0.27, -Z - 0.16) if is_front else (Z + 0.16, Z + 0.27)
        return [
            # Outer-top stepped gothic L-cap
            cube(prefix + '_ct_a', (-W - 0.18, H - 0.68, z0), (-W + 1.65, H + 0.18, z1), GOLD_UV),
            cube(prefix + '_ct_b', (-W - 0.18, H - 1.70, z0), (-W + 0.68, H - 0.68, z1), GOLD_UV),
            cube(prefix + '_ct_step', (-W + 0.68, H - 1.12, z0), (-W + 1.12, H - 0.68, z1), GOLD_UV),
            # Outer-bottom stepped gothic L-cap
            cube(prefix + '_cb_a', (-W - 0.18, -H - 0.18, z0), (-W + 1.65, -H + 0.68, z1), GOLD_UV),
            cube(prefix + '_cb_b', (-W - 0.18, -H + 0.68, z0), (-W + 0.68, -H + 1.70, z1), GOLD_UV),
            cube(prefix + '_cb_step', (-W + 0.68, -H + 0.68, z0), (-W + 1.12, -H + 1.12, z1), GOLD_UV),
            # 45-degree diamond-cut ruby gems inset cleanly in the stepped corners!
            cube(prefix + '_gem_top', (-W + 0.18, H - 0.86, gz0), (-W + 0.84, H - 0.20, gz1), RUBY_UV, (0, 0, 45)),
            cube(prefix + '_gem_bot', (-W + 0.18, -H + 0.20, gz0), (-W + 0.84, -H + 0.86, gz1), RUBY_UV, (0, 0, 45)),
        ]

    def cover_trim(prefix, is_front):
        """Sleek beveled antique-gold C-border connecting the stepped corners without overlap."""
        z0, z1 = (-Z - 0.11, -Z) if is_front else (Z, Z + 0.11)
        return [
            cube(prefix + '_trim_top', (-W + 1.65, H - 0.62, z0), (-0.02, H - 0.10, z1), GOLD_UV),
            cube(prefix + '_trim_bot', (-W + 1.65, -H + 0.10, z0), (-0.02, -H + 0.62, z1), GOLD_UV),
            cube(prefix + '_trim_out', (-W + 0.10, -H + 1.70, z0), (-W + 0.62, H - 1.70, z1), GOLD_UV),
        ]

    # --- V4 CALLIGRAPHIC ARCANE GUARDIAN 'Z' RUNE ---
    # Looking straight at the front cover (FACE_NORTH from -Z):
    #   +X is LEFT (spine side), -X is RIGHT (outer clasp side), +Y is UP.
    # Helper places bars in natural front-facing reading coordinates (sx > 0 = RIGHT, sy > 0 = UP),
    # split cleanly into two sub-bones:
    #   - rune_frame: dark antique-bronze cravacao bezel (z = -Z-0.11 .. -Z)
    #   - rune_core:  continuous faceted ruby crystal + glowing heart (z = -Z-0.22 .. -Z-0.11, heart to -Z-0.27)
    cx_f = -4.45
    bz0, bz1 = -Z - 0.11, -Z
    rz0, rz1 = -Z - 0.22, -Z - 0.11
    hz0, hz1 = -Z - 0.27, -Z - 0.22
    gz0, gz1 = -Z - 0.10, -Z

    rune_frame_cubes = [
        # Wide cardinal antique-gold diamond finials framing the engraved astrolabe ring (clean breathing room!)
        cube('emb_fin_t', (cx_f - 0.34,  4.46, gz0), (cx_f + 0.34,  5.14, gz1), GOLD_UV, (0, 0, 45)),
        cube('emb_fin_b', (cx_f - 0.34, -5.14, gz0), (cx_f + 0.34, -4.46, gz1), GOLD_UV, (0, 0, 45)),
        cube('emb_fin_l', (cx_f + 2.95, -0.32, gz0), (cx_f + 3.59,  0.32, gz1), GOLD_UV, (0, 0, 45)),
        cube('emb_fin_r', (cx_f - 3.59, -0.32, gz0), (cx_f - 2.95,  0.32, gz1), GOLD_UV, (0, 0, 45)),
    ]
    rune_core_cubes = []

    def add_rune_stroke(name, sx, sy, w_bar, h_bar, screen_deg, uv_tex, pad=0.14, z_tier=0):
        """Place a beveled cravacao + continuous ruby crystal bar in natural front-view coords.
        Micro Z-tier (0.006 px) prevents any coplanar OpenGL z-fighting at stroke junctions."""
        wx = cx_f - sx
        rot = (0, 0, -screen_deg)
        dz = z_tier * 0.006
        rune_frame_cubes.append(
            cube('rf_' + name,
                 (wx - w_bar * 0.5 - pad, sy - h_bar * 0.5 - pad, bz0 - dz),
                 (wx + w_bar * 0.5 + pad, sy + h_bar * 0.5 + pad, bz1),
                 RUNE_BEZEL_UV, rot)
        )
        rune_core_cubes.append(
            cube('rc_' + name,
                 (wx - w_bar * 0.5, sy - h_bar * 0.5, rz0 - dz),
                 (wx + w_bar * 0.5, sy + h_bar * 0.5, rz1 - dz),
                 uv_tex, rot)
        )

    # 1. Sweeping upper calligraphic arch (left upturned serif -> bold crown -> right hook into diagonal)
    add_rune_stroke('top_l',   -1.56,  2.54, 1.18, 0.50,  28, RUNE_H_UV, pad=0.13, z_tier=0)
    add_rune_stroke('top_mid', -0.18,  2.88, 2.05, 0.58,   4, RUNE_H_UV, pad=0.14, z_tier=1)
    add_rune_stroke('top_r',    1.16,  2.60, 1.15, 0.52, -32, RUNE_H_UV, pad=0.13, z_tier=0)

    # 2. Single continuous rotated diagonal blade (ZERO staircase!) + organic thickened mystic heart
    add_rune_stroke('diag_main',  -0.06,  0.10, 0.58, 5.45, -26, RUNE_V_UV, pad=0.14, z_tier=2)
    add_rune_stroke('diag_heart', -0.18, -0.12, 0.86, 1.85, -26, RUNE_V_UV, pad=0.13, z_tier=3)
    # Radiant inner crystal vein at the exact heart of the Z rune
    hx = cx_f - (-0.18)
    rune_core_cubes.append(
        cube('rc_heart_glow', (hx - 0.22, -0.12 - 0.58, hz0 - 0.018), (hx + 0.22, -0.12 + 0.58, hz1 - 0.018),
             RUBY_CORE_UV, (0, 0, 26))
    )

    # 3. Calligraphic lower flourish (iconic upturned left claw + undulating bottom sweep)
    add_rune_stroke('bot_claw', -1.74, -2.06, 0.48, 1.05,  24, RUNE_V_UV, pad=0.12, z_tier=0)
    add_rune_stroke('bot_l',    -0.84, -2.66, 1.72, 0.58,  -8, RUNE_H_UV, pad=0.14, z_tier=1)
    add_rune_stroke('bot_r',     0.52, -2.64, 1.48, 0.52,  10, RUNE_H_UV, pad=0.13, z_tier=0)

    # 4. Iconic Arcane Guardian satellite glyphs:
    #    - vertical ruby droplet to the right of the diagonal
    #    - pair of trailing ruby pips at bottom-right
    #    - subtle balancing ruby spark at upper-left
    add_rune_stroke('drop_r',     1.64, -0.42, 0.54, 1.24,  -6, RUNE_NODE_UV, pad=0.13)
    add_rune_stroke('pip_br1',    1.62, -2.56, 0.44, 0.44,  45, RUNE_NODE_UV, pad=0.11)
    add_rune_stroke('pip_br2',    2.28, -2.86, 0.34, 0.34,  45, RUNE_NODE_UV, pad=0.10)
    add_rune_stroke('pip_tl',    -1.92,  0.96, 0.38, 0.38,  45, RUNE_NODE_UV, pad=0.10)

    rune_frame = bone('rune_frame', rune_frame_cubes)
    rune_core = bone('rune_core', rune_core_cubes)
    emblem = bone('emblem_rune', [], [rune_frame, rune_core])

    # Delicate 3-piece golden clasp (hinged at outer front cover edge, bridges cleanly across
    # the fore-edge to latch onto the back cover; buckle & ruby diamond lie flush in YZ plane!)
    clasp = bone('clasp', [
        cube('clasp_strap', (-W - 0.30, -0.42, -Z - 0.08), (-W + 0.04, 0.42, Z + 0.08), GOLD_UV),
        cube('clasp_tab_front', (-W + 0.04, -0.46, -Z - 0.14), (-W + 0.48, 0.46, -Z), GOLD_UV),
        cube('clasp_buckle', (-W - 0.44, -0.68, -0.68), (-W - 0.26, 0.68, 0.68), GOLD_UV, (45, 0, 0)),
        cube('clasp_gem', (-W - 0.52, -0.34, -0.34), (-W - 0.40, 0.34, 0.34), RUBY_UV, (45, 0, 0)),
    ], origin=(-W, 0, -Z))

    # Front cover + physically stepped aged parchment pages
    front = bone('front_half', [
        cube('front_cover', (-W, -H, -Z), (0.0, H, -Z + 0.58), SPINE, north=FRONT, south=LINING),
        cube('front_pages', (-W + 0.62, -H + 0.58, -Z + 0.58), (-0.12, H - 0.58, -0.04), EDGE, south=PAGE_LEFT, north=PAGE_LEFT),
        # Physically stepped loose leaf protrusion near outer-bottom edge (hand-crafted tome feel!)
        cube('front_page_step', (-W + 0.46, -H + 1.20, -1.05), (-W + 0.62, H - 1.50, -0.58), EDGE),
    ] + gothic_corners('front', True) + cover_trim('front', True), [emblem, clasp])

    # Swallowtail V-notch velvet bookmark ribbon (own bone pivoted at top so it sways in 'idle'!)
    ribbon = bone('ribbon', [
        cube('ribbon_main', (-2.45, -H - 1.65, 0.02), (-1.25, -H + 0.45, 0.22), RIBBON),
        cube('ribbon_tip_l', (-2.45, -H - 2.35, 0.02), (-1.92, -H - 1.65, 0.22), RIBBON),
        cube('ribbon_tip_r', (-1.78, -H - 2.35, 0.02), (-1.25, -H - 1.65, 0.22), RIBBON),
    ], origin=(-1.85, -H + 0.35, 0.12))

    back = bone('back_half', [
        cube('back_cover', (-W, -H, Z - 0.58), (0.0, H, Z), SPINE, south=BACK, north=LINING),
        cube('back_pages', (-W + 0.62, -H + 0.58, 0.04), (-0.12, H - 0.58, Z - 0.58), EDGE, north=PAGE_RIGHT, south=PAGE_RIGHT),
        cube('back_page_step', (-W + 0.48, -H + 1.65, 0.48), (-W + 0.62, H - 1.15, 0.96), EDGE),
    ] + gothic_corners('back', False) + cover_trim('back', False), [ribbon])

    # Sleek reinforced spine: flush at x=0.0 (ZERO overlap/z-fighting with covers!)
    # Crimson leather clearly visible + 2 refined antique-gold bands + 2 slim center ribs + 45-deg diamond ruby!
    spine_cubes = [
        cube('spine_leather', (0.0, -H, -Z), (0.76, H, Z), SPINE),
        cube('spine_band_top', (-0.08,  3.25, -Z - 0.12), (0.90,  4.45, Z + 0.12), GOLD_UV),
        cube('spine_band_bot', (-0.08, -4.45, -Z - 0.12), (0.90, -3.25, Z + 0.12), GOLD_UV),
        cube('spine_rib_u', (-0.05,  1.05, -Z - 0.07), (0.85,  1.42, Z + 0.07), GOLD_UV),
        cube('spine_rib_d', (-0.05, -1.42, -Z - 0.07), (0.85, -1.05, Z + 0.07), GOLD_UV),
        cube('spine_gem', (0.74, -0.44, -0.44), (0.96, 0.44, 0.44), RUBY_UV, (45, 0, 0)),
    ]
    spine = bone('spine', spine_cubes)

    leaves = [
        bone('leaf%d' % i, [
            cube('leaf%d_paper' % i, (-W + 0.78, -H + 0.70, -0.025), (-0.14, H - 0.70, 0.025), EDGE,
                 north=PAGE_RIGHT, south=PAGE_LEFT)
        ]) for i in (1, 2, 3)
    ]

    # Root bone wraps everything so 'idle' levitation animates the whole tome cleanly in Blockbench!
    root = bone('root', [], [back, front, spine] + leaves, origin=(0, 0, 0))

    buffer = io.BytesIO()
    atlas.save(buffer, 'PNG')
    return {
        'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
        'name': 'arcane_spellbook',
        'model_identifier': 'arcane_spellbook',
        'resolution': {'width': 128, 'height': 128},
        'elements': elements,
        'outliner': [root],
        'textures': [{
            'name': 'arcane_spellbook.png',
            'id': '0',
            'uuid': ident('texture'),
            'source': 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode(),
        }],
        'animations': build_animations(),
    }


# ----------------------------------------------------------------------
# Pixel-accurate Z-buffered 3D preview renderer (exact .bbmodel cubes & UVs)
# ----------------------------------------------------------------------
def rot_x(p, deg):
    r = math.radians(deg); c, s = math.cos(r), math.sin(r)
    return (p[0], p[1] * c - p[2] * s, p[1] * s + p[2] * c)


def rot_y(p, deg):
    r = math.radians(deg); c, s = math.cos(r), math.sin(r)
    return (p[0] * c + p[2] * s, p[1], -p[0] * s + p[2] * c)


def rot_z(p, deg):
    r = math.radians(deg); c, s = math.cos(r), math.sin(r)
    return (p[0] * c - p[1] * s, p[0] * s + p[1] * c, p[2])


def apply_xf(p, origin, rot, scale=(1, 1, 1), trans=(0, 0, 0)):
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    x, y, z = x * scale[0], y * scale[1], z * scale[2]
    x, y, z = rot_x((x, y, z), rot[0])
    x, y, z = rot_y((x, y, z), rot[1])
    x, y, z = rot_z((x, y, z), rot[2])
    return (x + origin[0] + trans[0], y + origin[1] + trans[1], z + origin[2] + trans[2])


def rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, v0, v1, v2, uv0, uv1, uv2, shade):
    minx = max(0, int(math.floor(min(v0[0], v1[0], v2[0]))))
    maxx = min(w - 1, int(math.ceil(max(v0[0], v1[0], v2[0]))))
    miny = max(0, int(math.floor(min(v0[1], v1[1], v2[1]))))
    maxy = min(h - 1, int(math.ceil(max(v0[1], v1[1], v2[1]))))
    if minx > maxx or miny > maxy:
        return
    x0, y0, z0 = v0; x1, y1, z1 = v1; x2, y2, z2 = v2
    denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
    if abs(denom) < 1e-6:
        return
    inv_d = 1.0 / denom
    u0, t0 = uv0; u1, t1 = uv1; u2, t2 = uv2
    for py in range(miny, maxy + 1):
        fy = py + 0.5
        row_off = py * w
        for px in range(minx, maxx + 1):
            fx = px + 0.5
            w0 = ((y1 - y2) * (fx - x2) + (x2 - x1) * (fy - y2)) * inv_d
            if w0 < -1e-4:
                continue
            w1 = ((y2 - y0) * (fx - x2) + (x0 - x2) * (fy - y2)) * inv_d
            if w1 < -1e-4:
                continue
            w2 = 1.0 - w0 - w1
            if w2 < -1e-4:
                continue
            z = w0 * z0 + w1 * z1 + w2 * z2
            idx = row_off + px
            if z >= zbuf[idx]:
                continue
            u = w0 * u0 + w1 * u1 + w2 * u2
            v = w0 * t0 + w1 * t1 + w2 * t2
            tx = max(0, min(aw - 1, int(u)))
            ty = max(0, min(ah - 1, int(v)))
            rc, gc, bc, ac = atlas_px[tx, ty]
            if ac < 20:
                continue
            zbuf[idx] = z
            pixels[px, py] = (
                min(255, int(rc * shade)),
                min(255, int(gc * shade)),
                min(255, int(bc * shade)),
                255,
            )


def render_view(model, atlas, yaw, pitch, open_amt, night, size=(320, 320)):
    w, h = size
    bg = (10, 12, 22, 255) if night else (24, 28, 48, 255)
    img = Image.new('RGBA', size, bg)
    cx0, cy0 = w // 2, h // 2
    for r in range(135, 10, -5):
        a = int((1 - r / 135.0) * (54 if night else 28))
        col = (196, 30, 68, a) if night else (110, 82, 148, a)
        layer = Image.new('RGBA', size, (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse((cx0 - r, cy0 - r, cx0 + r, cy0 + r), fill=col)
        img = Image.alpha_composite(img, layer)

    pixels = img.load()
    atlas_px = atlas.load()
    aw, ah = atlas.size
    zbuf = [1e9] * (w * h)

    elem_by_id = {e['uuid']: e for e in model['elements']}
    poses = {
        'back_half': ((0, 0, 0), (0, -12 * open_amt, 0), (1, 1, 1)),
        'front_half': ((0, 0, 0), (0, -166 * open_amt, 0), (1, 1, 1)),
        'clasp': ((0, 0, 0), (0, -88 * min(1.0, open_amt * 3.0), 0), (1, 1, 1)),
        'spine': ((-1.2 * open_amt, 0, 1.4 * open_amt), (0, 0, 0), (1 + 2.1 * open_amt, 1, 1 - 0.82 * open_amt)),
        'leaf1': ((0, 0, 0), (0, -45 * open_amt, 0), (1 if open_amt > 0.1 else 0, 1, 1)),
        'leaf2': ((0, 0, 0), (0, -90 * open_amt, 0), (1 if open_amt > 0.1 else 0, 1, 1)),
        'leaf3': ((0, 0, 0), (0, -135 * open_amt, 0), (1 if open_amt > 0.1 else 0, 1, 1)),
    }

    scale_px = 15.2 if open_amt < 0.5 else 11.4

    def walk(node, chain):
        name = node['name']
        t, r, s = poses.get(name, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
        if s[0] == 0:
            return
        cur = chain + [(node['origin'], r, s, t, name)]
        for ch in node['children']:
            if isinstance(ch, dict):
                walk(ch, cur)
            else:
                c = elem_by_id[ch]
                x0, y0, z0 = c['from']
                x1, y1, z1 = c['to']
                emissive = (c['name'].startswith('rs_') or c['name'].startswith('rc_') or 'gem' in c['name'])
                # Exact BlockbenchRigModel.java face vertices (BL, BR, TR, TL viewed from outside)
                # Right-handed camera at -Z looking toward +Z: +X is LEFT, -X is RIGHT!
                faces_def = {
                    'north': [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)],
                    'south': [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                    'east':  [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)],
                    'west':  [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                    'up':    [(x1, y1, z0), (x0, y1, z0), (x0, y1, z1), (x1, y1, z1)],
                    'down':  [(x1, y0, z1), (x0, y0, z1), (x0, y0, z0), (x1, y0, z0)],
                }
                for d, pts in faces_def.items():
                    tpts = []
                    for pt in pts:
                        q = apply_xf(pt, c['origin'], c['rotation'])
                        for bo, br, bs, bt, _ in reversed(cur):
                            q = apply_xf(q, bo, br, bs, bt)
                        q = (q[0] + (4.5 if open_amt < 0.5 else 0.0), q[1], q[2])
                        q = rot_y(q, yaw)
                        q = rot_x(q, pitch)
                        tpts.append(q)
                    # Outward normal: (p1 - p0) x (p3 - p0)
                    # Check north: p1-p0=(-dx,0,0), p3-p0=(0,+dy,0) -> (0,0,-dx*dy) = (0,0,-1) OK!
                    ux, uy, uz = [tpts[1][k] - tpts[0][k] for k in range(3)]
                    vx, vy, vz = [tpts[3][k] - tpts[0][k] for k in range(3)]
                    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
                    nlen = math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
                    nx, ny, nz = nx / nlen, ny / nlen, nz / nlen
                    if nz > 0.05:
                        continue
                    if emissive:
                        shade = 1.24 if night else 1.06
                    else:
                        ndl = max(0.0, -nx * 0.28 + ny * 0.42 - nz * 0.84)
                        shade = (0.44 + 0.42 * ndl) if night else (0.58 + 0.48 * ndl)

                    u0, v0, u1, v1 = c['faces'][d]['uv']
                    # UV mapping matching BlockbenchRigModel.java: p3=TL(u0,v0), p2=TR(u1,v0), p1=BR(u1,v1), p0=BL(u0,v1)
                    uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
                    # True right-handed camera at -Z looking at +Z: screen_x = cx0 - world_x * scale!
                    scr = [(cx0 - p[0] * scale_px, cy0 - p[1] * scale_px, p[2]) for p in tpts]
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    for root_b in model['outliner']:
        walk(root_b, [])

    if night and open_amt < 0.2:
        layer = Image.new('RGBA', size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        for r in range(48, 4, -4):
            a = int((1 - r / 48.0) * 66)
            ld.ellipse((cx0 - r, cy0 - r, cx0 + r, cy0 + r), fill=(255, 58, 96, a))
        img = Image.alpha_composite(img, layer)
    elif open_amt > 0.7:
        circ = build_circle().resize((126, 126), Image.NEAREST)
        layer = Image.new('RGBA', size, (0, 0, 0, 0))
        layer.paste(circ, (cx0 - 63, cy0 - 70), circ)
        img = Image.alpha_composite(img, layer)

    return img


def build_preview_sheet(model, atlas, out_path):
    v1 = render_view(model, atlas, yaw=24, pitch=12, open_amt=0.0, night=False)
    v2 = render_view(model, atlas, yaw=10, pitch=8, open_amt=0.0, night=True)
    v3 = render_view(model, atlas, yaw=-154, pitch=14, open_amt=0.0, night=False)
    v4 = render_view(model, atlas, yaw=-6, pitch=16, open_amt=1.0, night=True)

    sheet = Image.new('RGBA', (660, 680), (14, 17, 34, 255))
    sheet.paste(v1, (10, 26))
    sheet.paste(v2, (330, 26))
    sheet.paste(v3, (10, 356))
    sheet.paste(v4, (330, 356))
    d = ImageDraw.Draw(sheet)
    labels = [
        (16, 8, 'V4 - 1. Frente + Lombada (Runa Z Caligrafica + Cravacao)'),
        (336, 8, 'V4 - 2. Visao Noturna (Filamento Rubi Continuo + Nucleo)'),
        (16, 338, 'V4 - 3. Verso + Fecho + Fita em V'),
        (336, 338, 'V4 - 4. Aberto (Anims idle & open no Blockbench)'),
    ]
    for lx, ly, txt in labels:
        d.text((lx, ly), txt, fill=(255, 226, 134, 255))
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)


def main():
    atlas = build_atlas()
    model = build_model(atlas)
    bb_path = ASSETS / 'models/items/arcane_spellbook.bbmodel'
    bb_path.parent.mkdir(parents=True, exist_ok=True)
    bb_path.write_text(json.dumps(model, indent=1), encoding='utf-8')

    circle_path = ASSETS / 'textures/models/arcane_spellbook_circle.png'
    circle_path.parent.mkdir(parents=True, exist_ok=True)
    build_circle().save(circle_path)

    preview_path = Path(r'C:\Users\PC\.gemini\antigravity\brain\a1b93fce-c876-4df6-b69b-f3d1bce44b1d\arcane_spellbook_preview.png')
    build_preview_sheet(model, atlas, preview_path)
    print('GENERATED V4:', bb_path)
    print('GENERATED:', circle_path)
    print('PREVIEW:', preview_path)


if __name__ == '__main__':
    main()
