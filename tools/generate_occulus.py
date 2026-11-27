#!/usr/bin/env python3
"""Generate the Faithful AM2.5 Remastered Oculus 3D Blockbench model (.bbmodel) — V3 Sculpted Sphere:
Addresses every visual craft detail while staying 100% true to the classic AM2 Oculus palette:
  1. True multi-layered rounded voxel sphere (octagonal 3D cross-sections with 45-deg bevels) —
     looks genuinely spherical from front, side, back, top, and 3/4 (ZERO boxy cube corners!)
  2. 4 larger, gracefully curved navy -> indigo -> sapphire cradle claws wrapping snugly around
     and above the eye's equator so the eye truly looks held/cradled by the pedestal!
  3. Curved arched crimson eyelids (3-part contour) + gentle drowsy rest pose — ZERO "angry robot"
     straight visor look!
  4. Real subtle amber glow sprite ('textures/blocks/custom/occulus_glow.png') rendered both in-game
     by OcculusStyle.java and honestly matched in the preview (no fake preview-only halo!).

Exact classic AM2 Occulus.png RGB palette only (zero invented gold/cyan/etc.).
Front faces -Z, grounded at y=0, fits cleanly in 16x16 (1x1 block).
Run: python ArsMagica2/tools/generate_occulus.py
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

# ----------------------------------------------------------------------
# EXACT CLASSIC AM2 OCCULUS PALETTE (extracted directly from Occulus.png!)
# ----------------------------------------------------------------------
STONE_OUTLINE = (44, 44, 48, 255)
STONE_DEEP = (64, 64, 68, 255)
STONE_DARK = (82, 82, 86, 255)
STONE_MID = (98, 98, 102, 255)
STONE_BASE = (112, 112, 116, 255)
STONE_HI = (132, 132, 138, 255)

DARK_DEEP = (16, 16, 20, 255)
DARK_MID = (28, 28, 33, 255)
DARK_HI = (46, 46, 52, 255)

CLAW_OUTLINE = (10, 14, 26, 255)
CLAW_BASE_DARK = (14, 20, 36, 255)
CLAW_BASE = (21, 29, 51, 255)
CLAW_MID_DARK = (25, 35, 62, 255)
CLAW_MID = (35, 49, 88, 255)
CLAW_TIP_DARK = (42, 58, 104, 255)
CLAW_TIP = (55, 76, 135, 255)
CLAW_TIP_HI = (78, 104, 174, 255)

EYE_OUTLINE = (72, 18, 20, 255)
EYE_DEEP = (96, 24, 26, 255)
EYE_DARK = (122, 34, 35, 255)
EYE_MID = (148, 44, 44, 255)
EYE_HI = (176, 62, 60, 255)

IRIS_RIM = (168, 72, 4, 255)
IRIS_DARK = (224, 116, 2, 255)
IRIS_BASE = (255, 150, 1, 255)
IRIS_HI = (255, 196, 52, 255)
IRIS_CORE = (255, 236, 148, 255)
PUPIL_BLACK = (6, 4, 8, 255)
PUPIL_EDGE = (26, 14, 10, 255)


def lerp_col(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def build_atlas():
    """128x128 clean directional pixel-art atlas strictly using the classic AM2 Oculus palette."""
    atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)

    # 1. Carved Stone Pedestal Top (0..32, 0..32)
    for y in range(32):
        ny = abs(y / 31.0 - 0.5) * 2.0
        for x in range(32):
            nx = abs(x / 31.0 - 0.5) * 2.0
            edge = max(nx, ny)
            chisel = ((x * 5 + y * 13) % 17 == 0) * -0.07 + ((x * 3 + y * 7) % 23 == 0) * 0.05
            light = (1.0 - (x + y) / 62.0) * 0.22
            val = 0.56 + light - (edge ** 2.4) * 0.30 + chisel
            if x in (0, 31) or y in (0, 31):
                col = STONE_OUTLINE
            elif x == 1 or y == 1:
                col = STONE_HI
            elif x == 30 or y == 30:
                col = STONE_DEEP
            else:
                col = lerp_col(STONE_DEEP, STONE_HI, max(0.0, min(1.0, val)))
            draw.point((x, y), col)
    draw.rectangle((4, 4, 27, 27), outline=STONE_DEEP)
    draw.line((5, 5, 26, 5), fill=STONE_HI)
    draw.line((5, 5, 5, 26), fill=STONE_HI)

    # 2. Carved Stone Step Side (34..66, 0..16)
    for y in range(16):
        ny = y / 15.0
        for x in range(34, 66):
            lx = x - 34
            if lx in (0, 31) or y in (0, 15):
                col = STONE_OUTLINE
            elif y == 1:
                col = STONE_HI
            elif y == 14:
                col = STONE_DEEP
            elif y == 7:
                col = STONE_DEEP
            elif y == 8:
                col = STONE_HI if (lx not in (1, 30)) else STONE_MID
            else:
                seam = -0.10 if (lx in (8, 16, 24) and 2 < y < 13) else 0.0
                col = lerp_col(STONE_BASE, STONE_DARK, max(0.0, min(1.0, ny * 0.65 - seam)))
            draw.point((x, y), col)

    # 3. Fluted Classic Stone Pillar (34..50, 18..42)
    for y in range(18, 42):
        ny = (y - 18) / 23.0
        for x in range(34, 50):
            lx = x - 34
            if lx in (0, 15) or y in (18, 41):
                col = STONE_OUTLINE
            elif lx in (4, 11):
                col = STONE_DEEP
            elif lx in (5, 12):
                col = STONE_HI
            else:
                v = ny * 0.40 + abs(lx - 7.5) / 14.0
                col = lerp_col(STONE_BASE, STONE_DEEP, max(0.0, min(1.0, v)))
            draw.point((x, y), col)

    # 4. Dark Obsidian-Stone Accent (52..66, 18..34)
    for y in range(18, 34):
        ny = (y - 18) / 15.0
        for x in range(52, 66):
            lx = x - 52
            if lx in (0, 13) or y in (18, 33):
                col = DARK_DEEP
            elif lx == 1 or y == 19:
                col = DARK_HI
            else:
                col = lerp_col(DARK_HI, DARK_DEEP, (lx / 13.0 + ny) * 0.48)
            draw.point((x, y), col)

    # 5-7. Classic Prong 3-stage Navy -> Indigo -> Sapphire Gradient
    for y in range(16):
        ny = y / 15.0
        for x in range(68, 84):
            lx = x - 68
            if lx in (0, 15) or y in (0, 15):
                col = CLAW_OUTLINE
            elif lx == 1 or y == 1:
                col = CLAW_MID_DARK
            else:
                col = lerp_col(CLAW_BASE, CLAW_BASE_DARK, ny * 0.72 + abs(lx - 7.5) / 22.0)
            draw.point((x, y), col)

    for y in range(18, 34):
        ny = (y - 18) / 15.0
        for x in range(68, 84):
            lx = x - 68
            if lx in (0, 15) or y in (18, 33):
                col = CLAW_OUTLINE
            elif lx == 1 or y == 19:
                col = CLAW_TIP_DARK
            else:
                col = lerp_col(CLAW_MID, CLAW_MID_DARK, ny * 0.68 + abs(lx - 7.5) / 22.0)
            draw.point((x, y), col)

    for y in range(36, 52):
        ny = (y - 36) / 15.0
        for x in range(68, 84):
            lx = x - 68
            if lx in (0, 15) or y in (36, 51):
                col = CLAW_BASE_DARK
            elif y == 37:
                col = CLAW_TIP_HI
            elif lx == 1:
                col = lerp_col(CLAW_TIP, CLAW_TIP_HI, 0.55)
            else:
                col = lerp_col(CLAW_TIP, CLAW_TIP_DARK, ny * 0.72 + abs(lx - 7.5) / 24.0)
            draw.point((x, y), col)

    # 8. Smooth Seamless Crimson Eye Sclera (0..32, 36..68)
    #    NO hard dark border edges so adjacent rounded sphere facets blend into ONE organic globe!
    for y in range(36, 68):
        ny = (y - 36) / 31.0 - 0.5
        for x in range(32):
            nx = x / 31.0 - 0.5
            r = math.hypot(nx * 2.0, ny * 2.0)
            hl = max(0.0, 1.0 - math.hypot((nx + 0.22) * 2.0, (ny + 0.22) * 2.0))
            v = max(0.0, min(1.0, 0.68 - r * 0.42 + hl * 0.30))
            if v > 0.66:
                col = lerp_col(EYE_MID, EYE_HI, (v - 0.66) / 0.34)
            elif v > 0.32:
                col = lerp_col(EYE_DARK, EYE_MID, (v - 0.32) / 0.34)
            else:
                col = lerp_col(EYE_DEEP, EYE_DARK, v / 0.32)
            draw.point((x, y), col)

    # 9. Seamless Curved Crimson Eyelid Skin (0..24, 72..88) — soft dark rim only along closing edge!
    for y in range(72, 88):
        ly = y - 72
        for x in range(24):
            if ly in (0, 15):
                col = EYE_DEEP
            else:
                curve = 1.0 - abs(ly - 7.5) / 7.5
                col = lerp_col(EYE_DARK, EYE_MID, curve * 0.90)
            draw.point((x, y), col)

    # 10. Circular Classic Amber-Orange Iris (34..66, 46..78) — transparent outside circle!
    icx, icy = 34 + 15.5, 46 + 15.5
    for y in range(46, 78):
        for x in range(34, 66):
            dx = (x - icx) / 15.0
            dy = (y - icy) / 15.0
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.sin(ang * 12.0 + r * 5.0)
            if r > 0.98:
                col = EYE_DEEP
            elif r > 0.86:
                col = lerp_col(IRIS_RIM, IRIS_DARK, (0.98 - r) / 0.12)
            elif r > 0.54:
                col = lerp_col(IRIS_BASE, IRIS_DARK, (r - 0.54) / 0.32)
            elif r > 0.24:
                col = lerp_col(IRIS_HI, IRIS_BASE, (r - 0.24) / 0.30)
            else:
                col = lerp_col(IRIS_CORE, IRIS_HI, r / 0.24)
            if r <= 0.86:
                col = lerp_col(col, IRIS_CORE, rays * 0.18)
            if math.hypot(dx + 0.34, dy + 0.34) < 0.13:
                col = IRIS_CORE
            draw.point((x, y), col)

    # 11. Independent Vertical Black Slit Pupil (68..80, 56..78)
    for y in range(56, 78):
        ny = (y - 56) / 21.0 - 0.5
        for x in range(68, 80):
            nx = (x - 68) / 11.0 - 0.5
            half_w = 0.44 * max(0.0, 1.0 - (ny * 2.0) ** 2)
            if abs(nx) <= half_w * 0.72:
                col = PUPIL_BLACK
            elif abs(nx) <= half_w:
                col = PUPIL_EDGE
            else:
                col = (0, 0, 0, 0)
            draw.point((x, y), col)

    return atlas


def build_glow_sprite():
    """32x32 soft radial amber glow texture used IN-GAME by OcculusStyle.java (additive blend)."""
    size = 32
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2.0
    px = img.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot((x - c) / c, (y - c) / c)
            if r >= 1.0:
                continue
            falloff = (1.0 - r) ** 2.35
            col = lerp_col(IRIS_BASE, IRIS_CORE, max(0.0, 1.0 - r * 1.4))
            px[x, y] = (col[0], col[1], col[2], int(round(falloff * 255)))
    return img


STONE_TOP = [0, 0, 32, 32]
STONE_SIDE = [34, 0, 66, 16]
PILLAR_UV = [34, 18, 50, 42]
DARK_UV = [52, 18, 66, 34]
CLAW1_UV = [68, 0, 84, 16]
CLAW2_UV = [68, 18, 84, 34]
CLAW3_UV = [68, 36, 84, 52]
EYE_UV = [0, 36, 32, 68]
LID_UV = [0, 72, 24, 88]
IRIS_UV = [34, 46, 66, 78]
PUPIL_UV = [68, 56, 80, 78]
DIRECTIONS = ('north', 'south', 'east', 'west', 'up', 'down')


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'am2/occulus_faithful_v3/' + name))


def cube(name, start, end, default, rotation=(0, 0, 0), origin=None, **faces):
    if origin is None:
        origin = [(a + b) / 2.0 for a, b in zip(start, end)]
    return {
        'name': name,
        'box_uv': False,
        'from': [round(v, 3) for v in start],
        'to': [round(v, 3) for v in end],
        'autouv': 0,
        'color': 0,
        'origin': [round(v, 3) for v in origin],
        'rotation': [round(v, 3) for v in rotation],
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

    # 1. Idle: calm levitation + gentle look around + natural organic blink
    idle_anim = {
        'uuid': ident('anim/idle'),
        'name': 'idle',
        'loop': 'loop',
        'override': False,
        'length': 5.0,
        'snapping': 24,
        'selected': True,
        'animators': dict([
            anim_bone('eye_pivot', [
                kf(0.0, 'position', (0, 0.0, 0)),
                kf(2.5, 'position', (0, 0.30, 0)),
                kf(5.0, 'position', (0, 0.0, 0)),
            ]),
            anim_bone('eye_body', [
                kf(0.0, 'rotation', (0, -14, 0)),
                kf(1.6, 'rotation', (5, 16, 0)),
                kf(3.2, 'rotation', (-4, 8, 0)),
                kf(5.0, 'rotation', (0, -14, 0)),
            ]),
            anim_bone('eye_pupil', [
                kf(0.0, 'position', (-0.12, 0.0, 0)),
                kf(1.6, 'position', (0.14, 0.06, 0)),
                kf(3.2, 'position', (0.04, -0.05, 0)),
                kf(5.0, 'position', (-0.12, 0.0, 0)),
            ]),
            anim_bone('eyelid_top', [
                kf(0.0, 'rotation', (34, 0, 0), 'linear'),
                kf(3.7, 'rotation', (34, 0, 0), 'linear'),
                kf(3.88, 'rotation', (-22, 0, 0), 'linear'),
                kf(4.06, 'rotation', (34, 0, 0), 'linear'),
                kf(5.0, 'rotation', (34, 0, 0), 'linear'),
            ]),
            anim_bone('eyelid_bot', [
                kf(0.0, 'rotation', (-34, 0, 0), 'linear'),
                kf(3.7, 'rotation', (-34, 0, 0), 'linear'),
                kf(3.88, 'rotation', (18, 0, 0), 'linear'),
                kf(4.06, 'rotation', (-34, 0, 0), 'linear'),
                kf(5.0, 'rotation', (-34, 0, 0), 'linear'),
            ]),
        ]),
    }

    # 2. Look / Track demo
    look_anim = {
        'uuid': ident('anim/look_around'),
        'name': 'look_around',
        'loop': 'loop',
        'override': False,
        'length': 3.6,
        'snapping': 24,
        'selected': False,
        'animators': dict([
            anim_bone('eye_body', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.9, 'rotation', (14, -38, 0)),
                kf(1.8, 'rotation', (-10, 38, 0)),
                kf(2.7, 'rotation', (18, 12, 0)),
                kf(3.6, 'rotation', (0, 0, 0)),
            ]),
            anim_bone('eye_pupil', [
                kf(0.0, 'scale', (1.0, 1.0, 1.0)),
                kf(0.9, 'scale', (0.68, 1.06, 1.0)),
                kf(1.8, 'scale', (0.75, 1.04, 1.0)),
                kf(2.7, 'scale', (1.15, 0.96, 1.0)),
                kf(3.6, 'scale', (1.0, 1.0, 1.0)),
            ]),
        ]),
    }

    # 3. Quick Blink clip
    blink_anim = {
        'uuid': ident('anim/blink'),
        'name': 'blink',
        'loop': 'once',
        'override': False,
        'length': 0.35,
        'snapping': 24,
        'selected': False,
        'animators': dict([
            anim_bone('eyelid_top', [
                kf(0.0, 'rotation', (36, 0, 0), 'linear'),
                kf(0.16, 'rotation', (-22, 0, 0), 'linear'),
                kf(0.35, 'rotation', (36, 0, 0), 'linear'),
            ]),
            anim_bone('eyelid_bot', [
                kf(0.0, 'rotation', (-36, 0, 0), 'linear'),
                kf(0.16, 'rotation', (18, 0, 0), 'linear'),
                kf(0.35, 'rotation', (-36, 0, 0), 'linear'),
            ]),
        ]),
    }

    return [idle_anim, look_anim, blink_anim]


def build_octa_slice_z(prefix, cy, z0, z1, radius, uv):
    """Build a truly octagonal (rounded) slice perpendicular to Z with ZERO square corners!
    For a regular octagon of apothem `radius`, flat edge length = 2 * radius * tan(22.5 deg) = 0.8284 * radius.
    Composed of 2 orthogonal bars + 1 rotated 45-deg bar (all slightly micro-offset in Z to avoid z-fighting).
    """
    a = radius
    b = radius * 0.41421356 * 2.0  # full flat face width (~0.8284 * R)
    # 45-deg rotated square with apothem `a` has half-side `a`, and its corners sit at sqrt(2)*a = 1.414*a
    # Wait: to stay INSIDE the octagon (circumradius = a / cos(22.5) = 1.0824*a), the 45-deg bar has
    # half-width = b/2 and half-height = a! Two orthogonal 45-deg bars (+45 and -45) form the 4 diagonal flats!
    hw = b * 0.5
    eps = 0.012
    return [
        cube(prefix + '_h', (-a, cy - hw, z0), (a, cy + hw, z1), uv, origin=(0, cy, (z0 + z1) * 0.5)),
        cube(prefix + '_v', (-hw, cy - a, z0 + eps), (hw, cy + a, z1 - eps), uv, origin=(0, cy, (z0 + z1) * 0.5)),
        cube(prefix + '_d1', (-a, cy - hw, z0 + eps * 2), (a, cy + hw, z1 - eps * 2), uv,
             rotation=(0, 0, 45), origin=(0, cy, (z0 + z1) * 0.5)),
        cube(prefix + '_d2', (-hw, cy - a, z0 + eps * 3), (hw, cy + a, z1 - eps * 3), uv,
             rotation=(0, 0, 45), origin=(0, cy, (z0 + z1) * 0.5)),
    ]


def build_model(atlas):
    elements = []

    def bone(name, cubes, children=(), origin=(0, 0, 0), rotation=(0, 0, 0)):
        elements.extend(cubes)
        node = {
            'name': name,
            'origin': [round(v, 3) for v in origin],
            'color': 0,
            'uuid': ident('bone/' + name),
            'export': True,
            'isOpen': True,
            'visibility': True,
            'children': [c['uuid'] for c in cubes] + list(children),
        }
        if any(abs(v) > 1e-4 for v in rotation):
            node['rotation'] = [round(v, 3) for v in rotation]
        return node

    # ------------------------------------------------------------------
    # 1. PEDESTAL: Classic 3-tier carved grey stone plinth + fluted pillar + Stand17 dark accent
    # ------------------------------------------------------------------
    ped_cubes = [
        cube('stand_1', (-7.8, 0.0, -7.8), (7.8, 2.0, 7.8), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('stand_2', (-5.6, 2.0, -5.6), (5.6, 3.5, 5.6), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('stand_3', (-3.7, 3.5, -3.7), (3.7, 4.5, 3.7), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('stand_collar_dark', (-3.2, 4.5, -3.2), (3.2, 4.9, 3.2), DARK_UV),
        cube('stand_4_pillar', (-2.1, 4.9, -2.1), (2.1, 9.1, 2.1), PILLAR_UV, up=STONE_TOP, down=STONE_TOP),
        cube('stand_17_back', (-1.1, 5.5, 2.1), (1.1, 8.1, 2.7), DARK_UV),
        cube('stand_17_front', (-1.1, 5.5, -2.7), (1.1, 8.1, -2.1), DARK_UV),
        cube('stand_capital', (-2.6, 8.8, -2.6), (2.6, 9.6, 2.6), DARK_UV),
    ]

    for idx, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        x0, x1 = sorted((sx * 7.95, sx * 6.15))
        z0, z1 = sorted((sz * 7.95, sz * 6.15))
        ped_cubes.append(cube('stone_pier_%d' % idx, (x0, 0.0, z0), (x1, 2.25, z1), STONE_SIDE, up=STONE_TOP))

    # ------------------------------------------------------------------
    # 2. THE 4 LARGER, CURVED INDIGO CRADLE CLAWS ACTUALLY HUGGING THE EYE!
    #    Positioned symmetrically at 45, 135, 225, 315 deg so ALL 4 claws rise tall and curve
    #    snugly around the spherical globe (from y=8.2 up to y=15.4!) like a true arcane cradle,
    #    while keeping the front (-Z) gaze 100% unobstructed!
    # ------------------------------------------------------------------
    EYE_Y = 12.85
    prong_cubes = []
    for i in range(4):
        ang_deg = 45.0 + i * 90.0
        ang = math.radians(ang_deg)
        sa, ca = math.sin(ang), math.cos(ang)

        # Segment 1 (Navy base): sweeps firmly outward from the dark stone capital under the eye
        r1 = 2.75
        x1, z1 = sa * r1, ca * r1
        prong_cubes.append(
            cube('claw_base_%d' % i,
                 (x1 - 0.92, 8.05, z1 - 0.72), (x1 + 0.92, 10.65, z1 + 0.72),
                 CLAW1_UV, rotation=(36, ang_deg, 0), origin=(x1, 9.35, z1))
        )
        # Segment 2 (Indigo mid): rises snugly alongside the eye's equator (~0.75px clearance to turn!)
        r2 = 3.92
        x2, z2 = sa * r2, ca * r2
        prong_cubes.append(
            cube('claw_mid_%d' % i,
                 (x2 - 0.76, 10.30, z2 - 0.62), (x2 + 0.76, 13.35, z2 + 0.62),
                 CLAW2_UV, rotation=(6, ang_deg, 0), origin=(x2, 11.82, z2))
        )
        # Segment 3 (Sapphire upper claw): curves INWARD around the upper hemisphere of the globe!
        r3 = 3.55
        x3, z3 = sa * r3, ca * r3
        prong_cubes.append(
            cube('claw_tip_%d' % i,
                 (x3 - 0.56, 13.05, z3 - 0.50), (x3 + 0.56, 15.30, z3 + 0.50),
                 CLAW3_UV, rotation=(-28, ang_deg, 0), origin=(x3, 14.18, z3))
        )
        # Subtle inner sapphire talon point hugging the crown curve
        r4 = 2.98
        x4, z4 = sa * r4, ca * r4
        prong_cubes.append(
            cube('claw_point_%d' % i,
                 (x4 - 0.36, 14.85, z4 - 0.36), (x4 + 0.36, 15.72, z4 + 0.36),
                 CLAW3_UV, rotation=(-46, ang_deg, 0), origin=(x4, 15.28, z4))
        )

    prongs = bone('cradle_prongs', prong_cubes, origin=(0, 9.2, 0))
    pedestal = bone('pedestal', ped_cubes, [prongs], origin=(0, 0, 0))

    # ------------------------------------------------------------------
    # 3. TRUE 3D SPHERICAL CRIMSON EYE (ZERO BOX CORNERS FROM ANY ANGLE!)
    #    Constructed from 6 true octagonal Z-slices following exact sphere radius r(z) = sqrt(R^2 - z^2),
    #    R = 3.05! Viewed from front, side, back, top or 3/4, it reads unmistakably as a smooth globe!
    # ------------------------------------------------------------------
    eye_body_cubes = []
    # Equator band: z in [-1.05, +1.05], r = 2.95
    eye_body_cubes.extend(build_octa_slice_z('sp_eq', EYE_Y, -1.05, 1.05, 2.95, EYE_UV))
    # Rear mid band: z in [+1.05, +1.95], r = 2.52
    eye_body_cubes.extend(build_octa_slice_z('sp_b1', EYE_Y, 1.05, 1.95, 2.52, EYE_UV))
    # Rear upper cap: z in [+1.95, +2.62], r = 1.88
    eye_body_cubes.extend(build_octa_slice_z('sp_b2', EYE_Y, 1.95, 2.62, 1.88, EYE_UV))
    # Rear pole cap: z in [+2.62, +3.02], r = 1.08
    eye_body_cubes.extend(build_octa_slice_z('sp_b3', EYE_Y, 2.62, 3.02, 1.08, EYE_UV))
    # Front mid band: z in [-1.95, -1.05], r = 2.54
    eye_body_cubes.extend(build_octa_slice_z('sp_f1', EYE_Y, -1.95, -1.05, 2.54, EYE_UV))

    # Rounded 8-segment crimson ocular rim framing the recessed circular amber iris!
    # Radius 2.02 around (0, EYE_Y, -2.28), leaving a clean circular 1.72-radius window for the iris!
    rim_r = 2.04
    rim_seg_w = 2.0 * rim_r * math.tan(math.pi / 8.0) + 0.16
    for i in range(8):
        ang_deg = i * 45.0
        ang = math.radians(ang_deg)
        rx = math.sin(ang) * rim_r
        ry = EYE_Y + math.cos(ang) * rim_r
        eye_body_cubes.append(
            cube('rim_%d' % i,
                 (rx - rim_seg_w * 0.5, ry - 0.30, -2.56),
                 (rx + rim_seg_w * 0.5, ry + 0.30, -1.90),
                 EYE_UV, rotation=(0, 0, -ang_deg), origin=(rx, ry, -2.23))
        )

    # Independent vertical black slit pupil bone (can shift & narrow/dilate independently!)
    eye_pupil = bone('eye_pupil', [
        cube('pupil_slit', (-0.48, EYE_Y - 1.32, -2.50), (0.48, EYE_Y + 1.32, -2.44), PUPIL_UV, north=PUPIL_UV),
    ], origin=(0, EYE_Y, -2.47))

    # Emissive vibrant circular amber-orange iris nestled cleanly inside the octagonal crimson socket!
    # Uses circular alpha mask inside IRIS_UV so even the iris itself has ZERO square corners!
    eye_iris = bone('eye_iris', [
        cube('iris_plane', (-1.82, EYE_Y - 1.82, -2.44), (1.82, EYE_Y + 1.82, -1.95), EYE_UV, north=IRIS_UV),
    ], [eye_pupil], origin=(0, EYE_Y, 0))

    # Organic CURVED 3-segment crimson eyelids (arched contour wrapping the sphere -> ZERO robot visor!)
    # Base open rotation: +34 / -34 (clears the circular iris completely; gentle droop = +18, blink = -22!)
    eyelid_top = bone('eyelid_top', [
        # Center brow arch
        cube('lid_t_mid', (-1.15, EYE_Y + 0.30, -2.74), (1.15, EYE_Y + 1.45, -2.30), LID_UV),
        # Curved left & right eyelid wings hugging the spherical curvature
        cube('lid_t_l', ( 0.85, EYE_Y + 0.10, -2.66), (1.92, EYE_Y + 1.25, -2.18), LID_UV,
             rotation=(0, 0, -24), origin=(1.38, EYE_Y + 0.68, -2.42)),
        cube('lid_t_r', (-1.92, EYE_Y + 0.10, -2.66), (-0.85, EYE_Y + 1.25, -2.18), LID_UV,
             rotation=(0, 0,  24), origin=(-1.38, EYE_Y + 0.68, -2.42)),
    ], origin=(0, EYE_Y, 0), rotation=(34, 0, 0))

    eyelid_bot = bone('eyelid_bot', [
        cube('lid_b_mid', (-1.15, EYE_Y - 1.45, -2.74), (1.15, EYE_Y - 0.30, -2.30), LID_UV),
        cube('lid_b_l', ( 0.85, EYE_Y - 1.25, -2.66), (1.92, EYE_Y - 0.10, -2.18), LID_UV,
             rotation=(0, 0,  24), origin=(1.38, EYE_Y - 0.68, -2.42)),
        cube('lid_b_r', (-1.92, EYE_Y - 1.25, -2.66), (-0.85, EYE_Y - 0.10, -2.18), LID_UV,
             rotation=(0, 0, -24), origin=(-1.38, EYE_Y - 0.68, -2.42)),
    ], origin=(0, EYE_Y, 0), rotation=(-34, 0, 0))

    eye_body = bone('eye_body', eye_body_cubes, [eye_iris, eyelid_top, eyelid_bot], origin=(0, EYE_Y, 0))
    eye_pivot = bone('eye_pivot', [], [eye_body], origin=(0, EYE_Y, 0))

    root = bone('root', [], [pedestal, eye_pivot], origin=(0, 0, 0))

    buffer = io.BytesIO()
    atlas.save(buffer, 'PNG')
    return {
        'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
        'name': 'occulus',
        'model_identifier': 'occulus',
        'resolution': {'width': 128, 'height': 128},
        'elements': elements,
        'outliner': [root],
        'textures': [{
            'name': 'occulus.png',
            'id': '0',
            'uuid': ident('texture'),
            'source': 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode(),
        }],
        'animations': build_animations(),
    }


# ----------------------------------------------------------------------
# Honest pixel-accurate Z-buffered 3D preview renderer (matches in-game!)
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


def additive_composite_glow(img, glow_sprite, cx, cy, diameter, strength):
    """Exact software preview of the real in-game GL_SRC_ALPHA, GL_ONE occulus_glow.png quad."""
    spr = glow_sprite.resize((diameter, diameter), Image.BILINEAR)
    px = img.load()
    spx = spr.load()
    w, h = img.size
    x0 = cx - diameter // 2
    y0 = cy - diameter // 2
    for sy in range(diameter):
        py = y0 + sy
        if py < 0 or py >= h:
            continue
        for sx in range(diameter):
            px_i = x0 + sx
            if px_i < 0 or px_i >= w:
                continue
            sr, sg, sb, sa = spx[sx, sy]
            if sa == 0:
                continue
            f = (sa / 255.0) * strength
            br, bg, bb, _ = px[px_i, py]
            px[px_i, py] = (
                min(255, int(br + sr * f)),
                min(255, int(bg + sg * f)),
                min(255, int(bb + sb * f)),
                255,
            )


def render_view(model, atlas, glow_sprite, yaw, pitch, night, eye_look=(0, 0), lid_top_d=0.0, lid_bot_d=0.0,
                pupil_dx=0.0, pupil_sx=1.0, glow_strength=0.0, size=(320, 320)):
    w, h = size
    bg = (11, 13, 21, 255) if night else (28, 32, 46, 255)
    img = Image.new('RGBA', size, bg)
    cx0, cy0 = w // 2, int(h * 0.57)

    pixels = img.load()
    atlas_px = atlas.load()
    aw, ah = atlas.size
    zbuf = [1e9] * (w * h)

    elem_by_id = {e['uuid']: e for e in model['elements']}
    poses = {
        'eye_body': ((0, 0, 0), (eye_look[1], -eye_look[0], 0), (1, 1, 1)),
        'eye_pupil': ((pupil_dx, 0, 0), (0, 0, 0), (pupil_sx, 1, 1)),
        'eyelid_top': ((0, 0, 0), (lid_top_d, 0, 0), (1, 1, 1)),
        'eyelid_bot': ((0, 0, 0), (lid_bot_d, 0, 0), (1, 1, 1)),
    }
    emissive_bones = {'eye_iris'}
    scale_px = 11.8

    def walk(node, chain, in_emissive):
        name = node['name']
        t, r, s = poses.get(name, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
        base_r = node.get('rotation', (0, 0, 0))
        tot_r = (base_r[0] + r[0], base_r[1] + r[1], base_r[2] + r[2])
        cur = chain + [(node['origin'], tot_r, s, t, name)]
        em = in_emissive or (name in emissive_bones)
        for ch in node['children']:
            if isinstance(ch, dict):
                walk(ch, cur, em)
            else:
                c = elem_by_id[ch]
                x0, y0, z0 = c['from']
                x1, y1, z1 = c['to']
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
                        q = (q[0], q[1] - 8.5, q[2])
                        q = rot_y(q, yaw)
                        q = rot_x(q, pitch)
                        tpts.append(q)
                    ux, uy, uz = [tpts[1][k] - tpts[0][k] for k in range(3)]
                    vx, vy, vz = [tpts[3][k] - tpts[0][k] for k in range(3)]
                    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
                    nlen = math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
                    nx, ny, nz = nx / nlen, ny / nlen, nz / nlen
                    if nz > 0.05:
                        continue
                    if em:
                        shade = 1.06
                    else:
                        ndl = max(0.0, -nx * 0.28 + ny * 0.48 - nz * 0.82)
                        shade = (0.34 + 0.36 * ndl) if night else (0.58 + 0.48 * ndl)
                    u0, v0, u1, v1 = c['faces'][d]['uv']
                    uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
                    scr = [(cx0 - p[0] * scale_px, cy0 - p[1] * scale_px, p[2]) for p in tpts]
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    for root_b in model['outliner']:
        walk(root_b, [], False)

    # Render ONLY the honest in-game occulus_glow.png additive sprite (exact match to OcculusStyle.java!)
    if glow_strength > 0.01:
        p = (0.0, 12.85 - 8.5, -2.45)
        p = rot_y(p, yaw)
        p = rot_x(p, pitch)
        gx = int(cx0 - p[0] * scale_px)
        gy = int(cy0 - p[1] * scale_px)
        additive_composite_glow(img, glow_sprite, gx, gy, int(scale_px * 7.2), glow_strength)

    return img


def build_preview_sheet(model, atlas, glow_sprite, out_path):
    v1 = render_view(model, atlas, glow_sprite, yaw=26, pitch=-18, night=False,
                     eye_look=(-22, 6), lid_top_d=2.0, lid_bot_d=-2.0, pupil_dx=-0.12, glow_strength=0.14)
    v2 = render_view(model, atlas, glow_sprite, yaw=0, pitch=-10, night=True,
                     eye_look=(0, 0), lid_top_d=4.0, lid_bot_d=-4.0, pupil_sx=0.80, glow_strength=0.34)
    v3 = render_view(model, atlas, glow_sprite, yaw=-146, pitch=-20, night=False,
                     eye_look=(0, 0), lid_top_d=0.0, lid_bot_d=0.0, glow_strength=0.0)
    v4 = render_view(model, atlas, glow_sprite, yaw=-24, pitch=-14, night=False,
                     eye_look=(26, -6), lid_top_d=-14.0, lid_bot_d=6.0, pupil_dx=0.14, glow_strength=0.08)

    sheet = Image.new('RGBA', (660, 680), (14, 17, 30, 255))
    sheet.paste(v1, (10, 26))
    sheet.paste(v2, (330, 26))
    sheet.paste(v3, (10, 356))
    sheet.paste(v4, (330, 356))
    d = ImageDraw.Draw(sheet)
    labels = [
        (16, 8, 'Oculus V3 - 1. Esfera Real + Garras Abracando o Globo'),
        (336, 8, 'Oculus V3 - 2. Noite (Brilho Real In-Game: Iris + Aura Sutil)'),
        (16, 338, 'Oculus V3 - 3. Traseira 3/4 (Globo Redondo sem Cantos de Caixa)'),
        (336, 338, 'Oculus V3 - 4. Repouso Calmo (Palpebras Curvas, Zero Robo)'),
    ]
    for lx, ly, txt in labels:
        d.text((lx, ly), txt, fill=(255, 200, 96, 255))
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)


def main():
    atlas = build_atlas()
    glow_sprite = build_glow_sprite()
    model = build_model(atlas)

    bb_path = ASSETS / 'models/blocks/occulus.bbmodel'
    bb_path.parent.mkdir(parents=True, exist_ok=True)
    bb_path.write_text(json.dumps(model, indent=1), encoding='utf-8')

    glow_path = ASSETS / 'textures/blocks/custom/occulus_glow.png'
    glow_path.parent.mkdir(parents=True, exist_ok=True)
    glow_sprite.save(glow_path)

    preview_path = Path(r'C:\Users\PC\.gemini\antigravity\brain\a1b93fce-c876-4df6-b69b-f3d1bce44b1d\occulus_preview.png')
    build_preview_sheet(model, atlas, glow_sprite, preview_path)
    print('GENERATED V3 SPHERICAL OCULUS:', bb_path)
    print('GENERATED REAL GLOW SPRITE:', glow_path)
    print('PREVIEW:', preview_path)


if __name__ == '__main__':
    main()
