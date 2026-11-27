#!/usr/bin/env python3
"""Generate the AM2.5 Oculus 3D Blockbench model (.bbmodel) — V5 Bright Floating Arcane Relic:
100% pure Ars Magica 2 aesthetic (ZERO dark/eldritch Thaumcraft look!):
  - Bright, clean carved stone pedestal matching the classic AM2 Oculus lightness (#9E9EA8 -> #D4D4DE)
  - NO dark claws grabbing the eye! Instead, an open luminous sapphire-cobalt arcane cradle dish
    at the top of the pillar emits levitation energy upward
  - The Oculus Eye is a flat almond lens (vesica) that FLOATS FREELY above the pedestal: a sapphire
    frame with a carved stone edge, a glowing lens body (chalk rune on the back), a radiant
    amber-gold iris (#FF9E18 -> #FFE078) and an oval arcane pupil. (The old octagonal red ball
    read as raw flesh; the EYE_* shell colours are kept in the atlas but no longer used.)
  - Real floating AM2 WizardChalk runes orbiting gently around the levitating eye + delicate
    WizardChalk rune circle hovering cleanly over the bright stone base + subtle starlight motes!
  - Clean eye-movement bone rig preserved ('eye_pivot' -> 'eye_body' -> 'eye_iris' -> 'eye_pupil').

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
CHALK = ASSETS / 'textures/blocks/wizardchalk'

# ----------------------------------------------------------------------
# BRIGHT, CLEAN ARS MAGICA 2 OCCULUS PALETTE (Light Stone + Bright Cobalt + Luminous Eye)
# ----------------------------------------------------------------------
STONE_OUTLINE = (78, 80, 90, 255)
STONE_DEEP = (104, 108, 120, 255)
STONE_DARK = (128, 132, 144, 255)
STONE_MID = (154, 158, 170, 255)
STONE_BASE = (176, 180, 192, 255)
STONE_HI = (208, 212, 224, 255)

# Clean cool sapphire-cobalt trim (honoring the classic AM2 Oculus blue, bright & arcane!)
BLUE_DEEP = (30, 44, 88, 255)
BLUE_DARK = (46, 68, 130, 255)
BLUE_MID = (68, 100, 178, 255)
BLUE_HI = (104, 144, 224, 255)
BLUE_GLOW = (156, 198, 255, 255)
BLUE_CORE = (224, 240, 255, 255)

# Bright magical crimson-ruby eye shell (faithful to classic AM2 red, luminous & clean — not dark horror!)
EYE_DEEP = (128, 36, 42, 255)
EYE_DARK = (158, 48, 52, 255)
EYE_MID = (188, 62, 64, 255)
EYE_HI = (222, 92, 90, 255)
EYE_SPEC = (248, 152, 142, 255)

# Radiant crystalline amber-gold iris (classic AM2 #FF9601, luminous & magical!)
IRIS_RIM = (206, 98, 8, 255)
IRIS_DARK = (242, 138, 12, 255)
IRIS_BASE = (255, 172, 24, 255)
IRIS_HI = (255, 218, 92, 255)
IRIS_CORE = (255, 248, 196, 255)
PUPIL_DARK = (16, 12, 26, 255)
PUPIL_GLOW = (88, 128, 224, 255)  # faint astral sheen on inner pupil edge!


def lerp_col(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def paste_rune(canvas, index, x, y, size, color):
    path = CHALK / ('WizardChalk%d.png' % index)
    if not path.exists():
        return
    glyph = Image.open(path).convert('RGBA').resize((size, size), Image.NEAREST)
    solid = Image.new('RGBA', glyph.size, color)
    canvas.paste(solid, (x, y), glyph.getchannel('A').point(lambda a: 255 if a > 80 else 0))


def build_atlas():
    """128x128 bright, clean Ars Magica 2 pixel-art atlas."""
    atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(atlas)

    # 1. Bright Carved Stone Pedestal Top (0..32, 0..32) + delicate inlaid stone runes
    for y in range(32):
        ny = abs(y / 31.0 - 0.5) * 2.0
        for x in range(32):
            nx = abs(x / 31.0 - 0.5) * 2.0
            edge = max(nx, ny)
            chisel = ((x * 5 + y * 13) % 19 == 0) * -0.05 + ((x * 3 + y * 7) % 23 == 0) * 0.05
            light = (1.0 - (x + y) / 62.0) * 0.24
            val = 0.60 + light - (edge ** 2.4) * 0.26 + chisel
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
    for idx, (rx, ry) in enumerate(((6, 6), (20, 6), (6, 20), (20, 20))):
        paste_rune(atlas, 1 + idx * 3, rx, ry, 6, STONE_DARK)

    # 2. Bright Carved Stone Step Side (34..66, 0..16)
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
                seam = -0.08 if (lx in (8, 16, 24) and 2 < y < 13) else 0.0
                col = lerp_col(STONE_BASE, STONE_DARK, max(0.0, min(1.0, ny * 0.55 - seam)))
            draw.point((x, y), col)

    # 3. Bright Fluted Stone Pillar (34..50, 18..42)
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
                v = ny * 0.35 + abs(lx - 7.5) / 14.0
                col = lerp_col(STONE_HI, STONE_DARK, max(0.0, min(1.0, v)))
            draw.point((x, y), col)

    # 4. Luminous Sapphire-Cobalt Dish & Accents (68..84, 0..24) — bright classic AM2 blue!
    for y in range(24):
        ny = y / 23.0
        for x in range(68, 84):
            lx = x - 68
            if lx in (0, 15) or y in (0, 23):
                col = BLUE_DEEP
            elif y == 1:
                col = BLUE_GLOW
            elif lx == 1:
                col = BLUE_HI
            else:
                v = (1.0 - ny) * 0.65 + (1.0 - abs(lx - 7.5) / 8.0) * 0.35
                col = lerp_col(BLUE_DARK, BLUE_HI, max(0.0, min(1.0, v)))
            draw.point((x, y), col)

    # 5. Radiant Levitation Core Well (68..84, 26..42) — glowing astral pool inside the dish!
    for y in range(26, 42):
        ny = (y - 26) / 15.0 - 0.5
        for x in range(68, 84):
            nx = (x - 68) / 15.0 - 0.5
            r = math.hypot(nx * 2.0, ny * 2.0)
            if r < 0.35:
                col = lerp_col(BLUE_CORE, BLUE_GLOW, r / 0.35)
            elif r < 0.78:
                col = lerp_col(BLUE_GLOW, BLUE_HI, (r - 0.35) / 0.43)
            else:
                col = lerp_col(BLUE_HI, BLUE_DARK, min(1.0, (r - 0.78) / 0.25))
            draw.point((x, y), col)

    # 6. Emissive Sapphire Pillar Glyph (86..102, 0..16) — clean WizardChalk rune (immutable mask!)
    paste_rune(atlas, 7, 89, 3, 10, BLUE_CORE)
    p_mask = {(x, y) for y in range(0, 16) for x in range(86, 102) if atlas.getpixel((x, y))[3] > 200}
    for y in range(1, 15):
        for x in range(87, 101):
            if (x, y) not in p_mask:
                if any((x + dx, y + dy) in p_mask for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                    draw.point((x, y), BLUE_HI)

    # 7. Bright Magical Crimson-Ruby Eye Sphere (0..32, 36..68) — luminous, smooth, magical!
    for y in range(36, 68):
        ny = (y - 36) / 31.0 - 0.5
        for x in range(32):
            nx = x / 31.0 - 0.5
            r = math.hypot(nx * 2.0, ny * 2.0)
            hl = max(0.0, 1.0 - math.hypot((nx + 0.24) * 2.0, (ny + 0.24) * 2.0))
            v = max(0.0, min(1.0, 0.62 - r * 0.34 + hl * 0.42))
            if v > 0.78:
                col = lerp_col(EYE_HI, EYE_SPEC, (v - 0.78) / 0.22)
            elif v > 0.48:
                col = lerp_col(EYE_MID, EYE_HI, (v - 0.48) / 0.30)
            elif v > 0.22:
                col = lerp_col(EYE_DARK, EYE_MID, (v - 0.22) / 0.26)
            else:
                col = lerp_col(EYE_DEEP, EYE_DARK, v / 0.22)
            draw.point((x, y), col)

    # 8. Luminous Crystalline Amber-Gold Oculus Iris (34..66, 46..78) — bright, magical, clear!
    icx, icy = 34 + 15.5, 46 + 15.5
    for y in range(46, 78):
        for x in range(34, 66):
            dx = (x - icx) / 15.0
            dy = (y - icy) / 15.0
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            rays = 0.5 + 0.5 * math.sin(ang * 16.0)
            ring = max(0.0, 1.0 - abs(r - 0.62) / 0.12)
            if r > 0.98:
                col = BLUE_DEEP
            elif r > 0.88:
                col = lerp_col(IRIS_RIM, IRIS_DARK, (0.98 - r) / 0.10)
            elif r > 0.52:
                col = lerp_col(IRIS_BASE, IRIS_DARK, (r - 0.52) / 0.36)
            elif r > 0.22:
                col = lerp_col(IRIS_HI, IRIS_BASE, (r - 0.22) / 0.30)
            else:
                col = lerp_col(IRIS_CORE, IRIS_HI, r / 0.22)
            if r <= 0.88:
                col = lerp_col(col, IRIS_CORE, rays * 0.16 + ring * 0.26)
            # Crisp glossy crystal lens highlights (gives clear magical orb feel!)
            if math.hypot(dx + 0.38, dy + 0.36) < 0.16:
                col = (255, 255, 240, 255)
            elif math.hypot(dx - 0.34, dy - 0.40) < 0.09:
                col = IRIS_CORE
            draw.point((x, y), col)

    # 9. Clean Arcane Vertical-Oval Pupil (68..80, 56..78) — magical crystal pupil, NOT monster slit!
    for y in range(56, 78):
        ny = (y - 56) / 21.0 - 0.5
        for x in range(68, 80):
            nx = (x - 68) / 11.0 - 0.5
            # Smooth arcane almond/oval vertical pupil (wider & cleaner, with soft sapphire cosmic sheen!)
            d = math.hypot(nx / 0.34, ny / 0.45)
            if d < 0.72:
                col = PUPIL_DARK
            elif d < 0.94:
                col = lerp_col(PUPIL_DARK, PUPIL_GLOW, (d - 0.72) / 0.22 * 0.55)
            else:
                col = (0, 0, 0, 0)
            # Tiny starlight glint inside the pupil!
            if math.hypot(nx + 0.10, ny + 0.16) < 0.09:
                col = (240, 248, 255, 255)
            draw.point((x, y), col)

    # 10. 4 Glowing Floating WizardChalk Runes directly in the .bbmodel atlas (0..64, 96..112)
    #     Pure cut-out floating glyphs with crisp 1px rim (using immutable mask snapshot — NO flood fill!)
    orbit_specs = (
        (0,  1, BLUE_CORE,  BLUE_HI),
        (16, 5, IRIS_CORE,  IRIS_BASE),
        (32, 9, BLUE_CORE,  BLUE_HI),
        (48, 13, IRIS_CORE, IRIS_BASE),
    )
    for ox, r_id, core_col, rim_col in orbit_specs:
        paste_rune(atlas, r_id, ox + 2, 98, 12, core_col)
        core_mask = {(x, y) for y in range(96, 112) for x in range(ox, ox + 16)
                     if atlas.getpixel((x, y))[3] > 200}
        for y in range(97, 111):
            for x in range(ox + 1, ox + 15):
                if (x, y) not in core_mask:
                    if any((x + dx, y + dy) in core_mask for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))):
                        draw.point((x, y), (rim_col[0], rim_col[1], rim_col[2], 190))

    # 11. Delicate glowing levitation halo ring in the .bbmodel atlas (68..100, 84..116)
    cx_h, cy_h = 68 + 16, 84 + 16
    draw.ellipse((cx_h - 15, cy_h - 15, cx_h + 15, cy_h + 15), outline=BLUE_GLOW)
    draw.ellipse((cx_h - 13, cy_h - 13, cx_h + 13, cy_h + 13), outline=BLUE_HI)
    for i in range(8):
        a = i * math.tau / 8.0
        px = int(round(cx_h + math.cos(a) * 14.0))
        py = int(round(cy_h + math.sin(a) * 14.0))
        draw.point((px, py), IRIS_CORE if i % 2 == 0 else BLUE_CORE)

    # 12. Tiny glowing starlight diamond textures (104..120, 84..100 & 100..116)
    for (sx, sy, c_mid, c_out) in ((104, 84, BLUE_CORE, BLUE_GLOW), (104, 100, IRIS_CORE, IRIS_BASE)):
        for y in range(sy, sy + 16):
            for x in range(sx, sx + 16):
                d = abs((x - sx) - 7.5) + abs((y - sy) - 7.5)
                if d <= 3.5:
                    draw.point((x, y), c_mid)
                elif d <= 6.5:
                    draw.point((x, y), c_out)

    return atlas


def build_glow_sprite():
    """32x32 soft radial white-alpha glow sprite tinted in OpenGL."""
    size = 32
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    c = (size - 1) / 2.0
    px = img.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot((x - c) / c, (y - c) / c)
            if r >= 1.0:
                continue
            falloff = (1.0 - r) ** 2.15
            px[x, y] = (255, 255, 255, int(round(falloff * 255)))
    return img


def build_rune_circle():
    """128x128 crisp white-alpha AM2 WizardChalk rune circle for the bright stone base."""
    size = 128
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    c = size // 2
    draw.ellipse((c - 60, c - 60, c + 60, c + 60), outline=(255, 255, 255, 235))
    draw.ellipse((c - 58, c - 58, c + 58, c + 58), outline=(255, 255, 255, 115))
    draw.ellipse((c - 42, c - 42, c + 42, c + 42), outline=(255, 255, 255, 200))
    for i in range(8):
        a = i * math.tau / 8.0
        sx = int(round(c + math.cos(a) * 59.5))
        sy = int(round(c + math.sin(a) * 59.5))
        draw.polygon([(sx, sy - 2), (sx + 2, sy), (sx, sy + 2), (sx - 2, sy)], fill=(255, 255, 255, 245))
        ra = a + math.tau / 16.0
        rx = int(round(c + math.cos(ra) * 51.0 - 6))
        ry = int(round(c + math.sin(ra) * 51.0 - 6))
        paste_rune(img, 1 + (i * 2) % 15, rx, ry, 12, (255, 255, 255, 250))
        dx = int(round(c + math.cos(a) * 48.0))
        dy = int(round(c + math.sin(a) * 48.0))
        draw.rectangle((dx - 1, dy - 1, dx + 1, dy + 1), fill=(255, 255, 255, 190))
    return img


def build_orbit_runes_sheet():
    """64x16 horizontal strip of 4 distinct glowing AM2 WizardChalk runes (16x16 each)
    that float and orbit magically around the levitating Oculus eye in-game!"""
    sheet = Image.new('RGBA', (64, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(sheet)
    rune_indices = (1, 5, 9, 13)
    for idx, r_id in enumerate(rune_indices):
        ox = idx * 16
        # Delicate magical frame halo + authentic WizardChalk glyph
        paste_rune(sheet, r_id, ox + 2, 2, 12, (255, 255, 255, 255))
        # Subtle glow pixel softening around the glyph
        for y in range(1, 15):
            for x in range(ox + 1, ox + 15):
                if sheet.getpixel((x, y))[3] == 0:
                    nb = sum(1 for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1))
                             if sheet.getpixel((x + dx, y + dy))[3] > 200)
                    if nb >= 1:
                        draw.point((x, y), (255, 255, 255, 95))
    return sheet


STONE_TOP = [0, 0, 32, 32]
STONE_SIDE = [34, 0, 66, 16]
PILLAR_UV = [34, 18, 50, 42]
BLUE_UV = [68, 0, 84, 24]
POOL_UV = [68, 26, 84, 42]
PILLAR_RUNE_UV = [86, 0, 102, 16]
EYE_UV = [0, 36, 32, 68]
IRIS_UV = [34, 46, 66, 78]
PUPIL_UV = [68, 56, 80, 78]
ORBIT_RUNE_UVS = (
    [0, 96, 16, 112],
    [16, 96, 32, 112],
    [32, 96, 48, 112],
    [48, 96, 64, 112],
)
LEV_HALO_UV = [68, 84, 100, 116]
STAR_BLUE_UV = [104, 84, 120, 100]
STAR_AMBER_UV = [104, 100, 120, 116]
EMPTY_UV = [120, 120, 121, 121]
DIRECTIONS = ('north', 'south', 'east', 'west', 'up', 'down')


def ident(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'am2/occulus_arcane_v5/' + name))


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

    # 1. Idle: smooth magical levitation + curious gentle looking around + orbiting runes!
    idle_anim = {
        'uuid': ident('anim/idle'),
        'name': 'idle',
        'loop': 'loop',
        'override': False,
        'length': 4.8,
        'snapping': 24,
        'selected': True,
        'animators': dict([
            anim_bone('eye_pivot', [
                kf(0.0, 'position', (0, 0.0, 0)),
                kf(2.4, 'position', (0, 0.75, 0)),
                kf(4.8, 'position', (0, 0.0, 0)),
            ]),
            anim_bone('floating_runes', [
                kf(0.0, 'rotation', (0, 0, 0), 'linear'),
                kf(4.8, 'rotation', (0, 360, 0), 'linear'),
            ]),
            anim_bone('levitation_halo', [
                kf(0.0, 'rotation', (0, 0, 0), 'linear'),
                kf(4.8, 'rotation', (0, -360, 0), 'linear'),
            ]),
            anim_bone('eye_body', [
                kf(0.0, 'rotation', (0, -14, 0)),
                kf(1.6, 'rotation', (6, 18, 0)),
                kf(3.2, 'rotation', (-5, 8, 0)),
                kf(4.8, 'rotation', (0, -14, 0)),
            ]),
            anim_bone('eye_pupil', [
                kf(0.0, 'position', (-0.12, 0.0, 0)),
                kf(1.6, 'position', (0.14, 0.06, 0)),
                kf(3.2, 'position', (0.04, -0.05, 0)),
                kf(4.8, 'position', (-0.12, 0.0, 0)),
            ]),
        ]),
    }

    # 2. Look around demo: full tracking range of the floating magical eye + faster orbiting runes!
    look_anim = {
        'uuid': ident('anim/look_around'),
        'name': 'look_around',
        'loop': 'loop',
        'override': False,
        'length': 3.6,
        'snapping': 24,
        'selected': False,
        'animators': dict([
            anim_bone('eye_pivot', [
                kf(0.0, 'position', (0, 0.35, 0)),
                kf(1.8, 'position', (0, 0.95, 0)),
                kf(3.6, 'position', (0, 0.35, 0)),
            ]),
            anim_bone('floating_runes', [
                kf(0.0, 'rotation', (0, 0, 0), 'linear'),
                kf(3.6, 'rotation', (0, 360, 0), 'linear'),
            ]),
            anim_bone('levitation_halo', [
                kf(0.0, 'rotation', (0, 0, 0), 'linear'),
                kf(3.6, 'rotation', (0, -360, 0), 'linear'),
            ]),
            anim_bone('eye_body', [
                kf(0.0, 'rotation', (0, 0, 0)),
                kf(0.9, 'rotation', (14, -40, 0)),
                kf(1.8, 'rotation', (-10, 40, 0)),
                kf(2.7, 'rotation', (18, 12, 0)),
                kf(3.6, 'rotation', (0, 0, 0)),
            ]),
            anim_bone('eye_pupil', [
                kf(0.0, 'scale', (1.0, 1.0, 1.0)),
                kf(0.9, 'scale', (0.78, 1.04, 1.0)),
                kf(1.8, 'scale', (0.82, 1.03, 1.0)),
                kf(2.7, 'scale', (1.12, 0.98, 1.0)),
                kf(3.6, 'scale', (1.0, 1.0, 1.0)),
            ]),
        ]),
    }

    return [idle_anim, look_anim]


def build_octa_slice_z(prefix, cy, z0, z1, radius, uv):
    a = radius
    hw = radius * 0.41421356
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
    # 1. BRIGHT CLASSIC STONE PEDESTAL + OPEN SAPPHIRE LEVITATION BASIN (ZERO CLAWS!)
    # ------------------------------------------------------------------
    ped_cubes = [
        # Classic 3-tier light carved stone plinth (grounded at y = 0)
        cube('stand_1', (-7.6, 0.0, -7.6), (7.6, 1.9, 7.6), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('stand_2', (-5.5, 1.9, -5.5), (5.5, 3.3, 5.5), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('stand_3', (-3.7, 3.3, -3.7), (3.7, 4.3, 3.7), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        # Clean cobalt-sapphire trim ring separating base and fluted column
        cube('stand_collar_blue', (-3.2, 4.3, -3.2), (3.2, 4.8, 3.2), BLUE_UV),
        # Fluted bright stone central column
        cube('stand_4_pillar', (-2.1, 4.8, -2.1), (2.1, 8.2, 2.1), PILLAR_UV, up=STONE_TOP, down=STONE_TOP),
        # Subtle cobalt-blue rune housing plates on front & back of column (replaces dark block!)
        cube('pillar_plate_f', (-1.05, 5.4, -2.45), (1.05, 7.5, -2.10), BLUE_UV),
        cube('pillar_plate_b', (-1.05, 5.4,  2.10), (1.05, 7.5,  2.45), BLUE_UV),
        # Open shallow arcane levitation dish at top of column (y = 8.2 .. 9.5) — NO grabbing claws!
        cube('dish_base', (-3.2, 8.2, -3.2), (3.2, 8.95, 3.2), STONE_SIDE, up=STONE_TOP, down=STONE_TOP),
        cube('dish_rim_n', (-3.6, 8.75, -3.6), ( 3.6, 9.55, -2.7), BLUE_UV),
        cube('dish_rim_s', (-3.6, 8.75,  2.7), ( 3.6, 9.55,  3.6), BLUE_UV),
        cube('dish_rim_w', (-3.6, 8.75, -2.7), (-2.7, 9.55,  2.7), BLUE_UV),
        cube('dish_rim_e', ( 2.7, 8.75, -2.7), ( 3.6, 9.55,  2.7), BLUE_UV),
    ]

    # Clean carved light-stone corner piers on Stand1
    for idx, (sx, sz) in enumerate(((-1, -1), (1, -1), (-1, 1), (1, 1))):
        x0, x1 = sorted((sx * 7.80, sx * 6.05))
        z0, z1 = sorted((sz * 7.80, sz * 6.05))
        ped_cubes.append(cube('stone_pier_%d' % idx, (x0, 0.0, z0), (x1, 2.15, z1), STONE_SIDE, up=STONE_TOP))

    # Emissive elements on pedestal: glowing sapphire levitation pool inside the open dish + column runes!
    pedestal_glow = bone('pedestal_glow', [
        cube('levitation_pool', (-2.68, 8.92, -2.68), (2.68, 9.20, 2.68), BLUE_UV, up=POOL_UV),
        cube('pillar_glyph_f', (-0.80, 5.65, -2.48), (0.80, 7.25, -2.45), EMPTY_UV, north=PILLAR_RUNE_UV),
        cube('pillar_glyph_b', (-0.80, 5.65,  2.45), (0.80, 7.25,  2.48), EMPTY_UV, south=PILLAR_RUNE_UV),
    ], origin=(0, 8.5, 0))

    pedestal = bone('pedestal', ped_cubes, [pedestal_glow], origin=(0, 0, 0))

    # ------------------------------------------------------------------
    # 2. FREE-FLOATING MAGICAL OCULUS EYE (~3.0 px clear levitation gap above dish!)
    #    True rounded 3D sphere in bright magical crimson + luminous amber-gold crystal iris
    #    + clean arcane oval pupil (ZERO creepy eyelids, ZERO claws!)
    # ------------------------------------------------------------------
    EYE_Y = 14.35
    eye_body_cubes = []

    def seg(name, p0, p1, thick, z0, z1, uv):
        """Bar from p0 to p1 (x, y) in the XY plane, rotated around Z."""
        cx, cy = (p0[0] + p1[0]) / 2.0, (p0[1] + p1[1]) / 2.0
        length = math.hypot(p1[0] - p0[0], p1[1] - p0[1]) + 0.12
        ang = math.degrees(math.atan2(p1[1] - p0[1], p1[0] - p0[0]))
        return cube(name, (cx - length / 2, cy - thick / 2, z0), (cx + length / 2, cy + thick / 2, z1), uv,
                    rotation=(0, 0, ang), origin=(cx, cy, (z0 + z1) / 2.0))

    # Almond lens (vesica): the overlap of two circles of radius LENS_R whose centres sit LENS_D
    # above and below the eye centre. 7.2 px wide, 4.1 px tall, pointed at the sides.
    LENS_R, LENS_D = 4.3, 2.25
    arc0 = math.asin(LENS_D / LENS_R)

    def lens_point(t):
        if t < math.pi:  # upper arc, from the right tip to the left tip
            th = arc0 + (math.pi - 2 * arc0) * (t / math.pi)
            return LENS_R * math.cos(th), -LENS_D + LENS_R * math.sin(th)
        th = math.pi + arc0 + (math.pi - 2 * arc0) * ((t - math.pi) / math.pi)
        return LENS_R * math.cos(th), LENS_D + LENS_R * math.sin(th)

    n = 20
    pts = [lens_point(2 * math.pi * i / n) for i in range(n)]
    for i in range(n):
        (x0, y0), (x1, y1) = pts[i], pts[(i + 1) % n]
        # Sapphire frame, then a thinner carved-stone edge just outside it
        eye_body_cubes.append(seg('lens_rim_%d' % i, (x0, EYE_Y + y0), (x1, EYE_Y + y1), 0.7, -0.95, 0.95, BLUE_UV))
        k = 1.13
        eye_body_cubes.append(seg('lens_edge_%d' % i, (x0 * k, EYE_Y + y0 * k), (x1 * k, EYE_Y + y1 * k),
                                  0.45, -0.55, 0.55, STONE_SIDE))

    # Lens body: horizontal bars filling the almond (glowing pool in front, chalk rune on the back)
    y = -(LENS_R - LENS_D) + 0.2
    i = 0
    while y < (LENS_R - LENS_D) - 0.2:
        hw = math.sqrt(max(0.0, LENS_R * LENS_R - (abs(y) + LENS_D) ** 2))
        eye_body_cubes.append(cube('lens_fill_%d' % i, (-hw, EYE_Y + y - 0.26, -0.45), (hw, EYE_Y + y + 0.26, 0.45),
                                   BLUE_UV, north=POOL_UV, south=PILLAR_RUNE_UV))
        y += 0.5
        i += 1

    # Independent oval pupil bone (eye movement & focus narrowing), in front of the iris
    IRIS_Z = -0.62
    eye_pupil = bone('eye_pupil', [
        cube('pupil_lens', (-0.55, EYE_Y - 1.19, IRIS_Z - 0.06), (0.55, EYE_Y + 1.19, IRIS_Z), PUPIL_UV, north=PUPIL_UV),
    ], origin=(0, EYE_Y, IRIS_Z - 0.03))

    # Emissive amber iris disc set into the lens
    eye_iris = bone('eye_iris', [
        cube('iris_plane', (-1.7, EYE_Y - 1.7, IRIS_Z), (1.7, EYE_Y + 1.7, IRIS_Z + 0.4), EMPTY_UV, north=IRIS_UV),
    ], [eye_pupil], origin=(0, EYE_Y, 0))

    # 3. FLOATING WIZARDCHALK RUNES, STARLIGHT MOTES & LEVITATION HALO RIGHT INSIDE THE .BBMODEL!
    floating_rune_cubes = []
    for i in range(4):
        ang_deg = 45.0 + i * 90.0
        ang = math.radians(ang_deg)
        orb_r = 5.05
        rx = math.sin(ang) * orb_r
        rz = math.cos(ang) * orb_r
        ry = EYE_Y + (0.65 if i % 2 == 0 else -0.65)
        uv_r = ORBIT_RUNE_UVS[i]
        floating_rune_cubes.append(
            cube('orbit_rune_%d' % i,
                 (rx - 1.35, ry - 1.35, rz - 0.04),
                 (rx + 1.35, ry + 1.35, rz + 0.04),
                 EMPTY_UV, rotation=(0, ang_deg, 0), origin=(rx, ry, rz),
                 north=uv_r, south=uv_r)
        )
        # Delicate floating starlight crystal mote midway between runes!
        s_ang_deg = i * 90.0 + 18.0
        s_ang = math.radians(s_ang_deg)
        sr = 5.55
        sx, sz = math.sin(s_ang) * sr, math.cos(s_ang) * sr
        sy = EYE_Y + (-1.35 if i % 2 == 0 else 1.45)
        s_uv = STAR_AMBER_UV if i % 2 == 0 else STAR_BLUE_UV
        floating_rune_cubes.append(
            cube('orbit_star_%d' % i,
                 (sx - 0.28, sy - 0.28, sz - 0.28),
                 (sx + 0.28, sy + 0.28, sz + 0.28),
                 s_uv, rotation=(45, s_ang_deg, 45), origin=(sx, sy, sz))
        )
    floating_runes = bone('floating_runes', floating_rune_cubes, origin=(0, EYE_Y, 0))

    # Delicate levitation rune halo hovering cleanly between the open sapphire dish and floating eye!
    levitation_halo = bone('levitation_halo', [
        cube('lev_ring_mid', (-3.45, 10.65, -3.45), (3.45, 10.69, 3.45), EMPTY_UV,
             up=LEV_HALO_UV, down=LEV_HALO_UV),
        cube('lev_ring_base', (-7.25, 2.04, -7.25), (7.25, 2.08, 7.25), EMPTY_UV,
             up=LEV_HALO_UV, down=LEV_HALO_UV),
    ], origin=(0, 10.67, 0))

    eye_body = bone('eye_body', eye_body_cubes, [eye_iris], origin=(0, EYE_Y, 0))
    eye_pivot = bone('eye_pivot', [], [eye_body, floating_runes], origin=(0, EYE_Y, 0))
    root = bone('root', [], [pedestal, levitation_halo, eye_pivot], origin=(0, 0, 0))

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
# Honest pixel-accurate Z-buffered 3D preview renderer (exact in-game match!)
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


def rasterize_tri(pixels, zbuf, w, h, tex_px, tw, th, v0, v1, v2, uv0, uv1, uv2, shade,
                  additive=False, tint=(1.0, 1.0, 1.0), alpha_mul=1.0):
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
            tx = max(0, min(tw - 1, int(u)))
            ty = max(0, min(th - 1, int(v)))
            rc, gc, bc, ac = tex_px[tx, ty]
            if ac < 16:
                continue
            if additive:
                f = (ac / 255.0) * alpha_mul
                br, bg, bb, _ = pixels[px, py]
                pixels[px, py] = (
                    min(255, int(br + rc * tint[0] * f)),
                    min(255, int(bg + gc * tint[1] * f)),
                    min(255, int(bb + bc * tint[2] * f)),
                    255,
                )
            else:
                zbuf[idx] = z
                pixels[px, py] = (
                    min(255, int(rc * shade)),
                    min(255, int(gc * shade)),
                    min(255, int(bc * shade)),
                    255,
                )


def draw_additive_sprite(pixels, zbuf, w, h, spr_px, sx0, sy0, sw, sh, cx, cy, z_val, diam, rgb, strength):
    r_half = diam * 0.5
    minx = max(0, int(cx - r_half))
    maxx = min(w - 1, int(cx + r_half))
    miny = max(0, int(cy - r_half))
    maxy = min(h - 1, int(cy + r_half))
    for py in range(miny, maxy + 1):
        v = (py - (cy - r_half)) / max(1.0, diam)
        ty = sy0 + max(0, min(sh - 1, int(v * sh)))
        row_off = py * w
        for px in range(minx, maxx + 1):
            if z_val >= zbuf[row_off + px]:
                continue
            u = (px - (cx - r_half)) / max(1.0, diam)
            tx = sx0 + max(0, min(sw - 1, int(u * sw)))
            _, _, _, sa = spr_px[tx, ty]
            if sa == 0:
                continue
            f = (sa / 255.0) * strength
            br, bg, bb, _ = pixels[px, py]
            pixels[px, py] = (
                min(255, int(br + rgb[0] * f)),
                min(255, int(bg + rgb[1] * f)),
                min(255, int(bb + rgb[2] * f)),
                255,
            )


def render_view(model, atlas, glow_sprite, rune_circle, orbit_runes, yaw, pitch, night, awake_amt,
                eye_look=(0, 0), pupil_dx=0.0, pupil_sx=1.0, size=(320, 320)):
    w, h = size
    bg = (12, 15, 26, 255) if night else (36, 42, 62, 255)
    img = Image.new('RGBA', size, bg)
    cx0, cy0 = w // 2, int(h * 0.60)

    pixels = img.load()
    atlas_px = atlas.load()
    aw, ah = atlas.size
    glow_px = glow_sprite.load()
    gsize = glow_sprite.size[0]
    rune_px = rune_circle.load()
    rsize = rune_circle.size[0]
    orbit_px = orbit_runes.load()
    zbuf = [1e9] * (w * h)

    elem_by_id = {e['uuid']: e for e in model['elements']}
    poses = {
        'eye_pivot': ((0, 0.35 * awake_amt, 0), (0, 0, 0), (1, 1, 1)),
        'eye_body': ((0, 0, 0), (eye_look[1], -eye_look[0], 0), (1, 1, 1)),
        'eye_pupil': ((pupil_dx, 0, 0), (0, 0, 0), (pupil_sx, 1, 1)),
    }
    emissive_bones = {'eye_iris', 'pedestal_glow', 'floating_runes', 'levitation_halo'}
    scale_px = 11.2

    def proj(p):
        q = (p[0], p[1] - 9.0, p[2])
        q = rot_y(q, yaw)
        q = rot_x(q, pitch)
        return (cx0 - q[0] * scale_px, cy0 - q[1] * scale_px, q[2])

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
                        q = (q[0], q[1] - 9.0, q[2])
                        q = rot_y(q, yaw)
                        q = rot_x(q, pitch)
                        tpts.append(q)
                    ux, uy, uz = [tpts[1][k] - tpts[0][k] for k in range(3)]
                    vx, vy, vz = [tpts[3][k] - tpts[0][k] for k in range(3)]
                    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
                    nlen = math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6
                    nx, ny, nz = nx / nlen, ny / nlen, nz / nlen
                    if nz > 0.05 and name not in ('floating_runes', 'levitation_halo'):
                        continue
                    if em:
                        shade = 1.08
                    else:
                        ndl = max(0.0, -nx * 0.25 + ny * 0.52 - nz * 0.82)
                        shade = (0.42 + 0.38 * ndl) if night else (0.64 + 0.44 * ndl)
                    u0, v0, u1, v1 = c['faces'][d]['uv']
                    uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
                    scr = [(cx0 - p[0] * scale_px, cy0 - p[1] * scale_px, p[2]) for p in tpts]
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[1], scr[2], uvs[0], uvs[1], uvs[2], shade)
                    rasterize_tri(pixels, zbuf, w, h, atlas_px, aw, ah, scr[0], scr[2], scr[3], uvs[0], uvs[2], uvs[3], shade)

    for root_b in model['outliner']:
        walk(root_b, [], False)

    # Pure .bbmodel preview — shows EXACTLY what opens in Blockbench right now!
    return img


def build_preview_sheet(model, atlas, glow_sprite, rune_circle, orbit_runes, out_path):
    v1 = render_view(model, atlas, glow_sprite, rune_circle, orbit_runes, yaw=26, pitch=-18, night=False, awake_amt=1.0,
                     eye_look=(-20, 6), pupil_dx=-0.10)
    v2 = render_view(model, atlas, glow_sprite, rune_circle, orbit_runes, yaw=0, pitch=-12, night=True, awake_amt=1.0,
                     eye_look=(0, 0), pupil_sx=0.88)
    v3 = render_view(model, atlas, glow_sprite, rune_circle, orbit_runes, yaw=-146, pitch=-20, night=False, awake_amt=1.0,
                     eye_look=(0, 0))
    v4 = render_view(model, atlas, glow_sprite, rune_circle, orbit_runes, yaw=-24, pitch=-15, night=False, awake_amt=0.0,
                     eye_look=(24, -6), pupil_dx=0.10)

    sheet = Image.new('RGBA', (660, 680), (18, 22, 38, 255))
    sheet.paste(v1, (10, 26))
    sheet.paste(v2, (330, 26))
    sheet.paste(v3, (10, 356))
    sheet.paste(v4, (330, 356))
    d = ImageDraw.Draw(sheet)
    labels = [
        (16, 8, 'Oculus V5 - 1. Claro, Flutuante + Runas em Volta (Zero Garras!)'),
        (336, 8, 'Oculus V5 - 2. Noite (Lente Ambar + Runas Orbitais + Selo Base)'),
        (16, 338, 'Oculus V5 - 3. Traseira 3/4 (Pedra Clara + Bacia Safira Aberta)'),
        (336, 338, 'Oculus V5 - 4. Repouso Calmo (Flutuacao Suave + Rastreio)'),
    ]
    for lx, ly, txt in labels:
        d.text((lx, ly), txt, fill=(255, 218, 120, 255))
    out_path.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out_path)


def main():
    atlas = build_atlas()
    glow_sprite = build_glow_sprite()
    rune_circle = build_rune_circle()
    orbit_runes = build_orbit_runes_sheet()
    model = build_model(atlas)

    bb_path = ASSETS / 'models/blocks/occulus.bbmodel'
    bb_path.parent.mkdir(parents=True, exist_ok=True)
    bb_path.write_text(json.dumps(model, indent=1), encoding='utf-8')

    glow_path = ASSETS / 'textures/blocks/custom/occulus_glow.png'
    glow_path.parent.mkdir(parents=True, exist_ok=True)
    glow_sprite.save(glow_path)

    rune_path = ASSETS / 'textures/blocks/custom/occulus_runes.png'
    rune_path.parent.mkdir(parents=True, exist_ok=True)
    rune_circle.save(rune_path)

    orbit_path = ASSETS / 'textures/blocks/custom/occulus_orbit_runes.png'
    orbit_path.parent.mkdir(parents=True, exist_ok=True)
    orbit_runes.save(orbit_path)

    preview_path = Path(r'C:\Users\PC\.gemini\antigravity\brain\a1b93fce-c876-4df6-b69b-f3d1bce44b1d\occulus_preview.png')
    build_preview_sheet(model, atlas, glow_sprite, rune_circle, orbit_runes, preview_path)
    print('GENERATED V5 BRIGHT FLOATING OCULUS:', bb_path)
    print('GENERATED ORBIT RUNES:', orbit_path)
    print('PREVIEW:', preview_path)


if __name__ == '__main__':
    main()
