import math
from PIL import Image, ImageDraw, ImageFilter

def create_mana_shield_texture(filepath):
    W = 1024
    H = 512
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Color palette
    HEX_LINE = (56, 189, 248, 190)      # Bright cyan #38bdf8
    HEX_NODE = (224, 247, 255, 240)     # White-cyan #e0f7ff
    HEX_FILL = (0, 229, 255, 30)        # Translucent glow #00e5ff
    RUNE_COLOR = (240, 253, 255, 230)   # Glowing rune white-cyan
    RUNE_GLOW = (14, 165, 233, 160)     # Deep mana cyan #0ea5e9
    ACCENT_LINE = (125, 211, 252, 210)  # Light sky blue #7dd3fc

    # Helper: draw a regular hexagon
    def get_hex_points(cx, cy, r):
        pts = []
        for i in range(6):
            angle = math.pi / 3.0 * i + math.pi / 6.0
            pts.append((cx + r * math.cos(angle), cy + r * math.sin(angle)))
        return pts

    # Helper: draw honeycomb grid in a bounding box
    def draw_honeycomb(box, r=14, line_w=2, fill=True):
        x0, y0, x1, y1 = box
        # Hex dimensions (pointy top or flat top)
        # With angle + pi/6: vertical distance is 1.5 * r, horizontal is sqrt(3) * r
        w_hex = math.sqrt(3) * r
        h_hex = 1.5 * r
        
        cols = int((x1 - x0) / w_hex) + 3
        rows = int((y1 - y0) / h_hex) + 3

        for row in range(-1, rows):
            cy = y0 + row * h_hex
            for col in range(-1, cols):
                cx = x0 + col * w_hex + ((row % 2) * (w_hex / 2.0))
                pts = get_hex_points(cx, cy, r)
                # Check if center or points are reasonably inside box
                if x0 - r <= cx <= x1 + r and y0 - r <= cy <= y1 + r:
                    # Clip to box
                    if fill:
                        draw.polygon(pts, fill=HEX_FILL)
                    for i in range(6):
                        p1 = pts[i]
                        p2 = pts[(i + 1) % 6]
                        # Only draw segment if within bounding box margin
                        if (x0 <= p1[0] <= x1 or x0 <= p2[0] <= x1) and (y0 <= p1[1] <= y1 or y0 <= p2[1] <= y1):
                            draw.line([p1, p2], fill=HEX_LINE, width=line_w)
                            # Joint node
                            if (col + row) % 2 == 0 and x0 <= p1[0] <= x1 and y0 <= p1[1] <= y1:
                                draw.ellipse([p1[0]-2, p1[1]-2, p1[0]+2, p1[1]+2], fill=HEX_NODE)

    # Standard Biped UV Box regions for 1024x512 (Scale = 16x)
    # Head
    box_head_top = (128, 0, 256, 128)
    box_head_bot = (256, 0, 384, 128)
    box_head_right = (0, 128, 128, 256)
    box_head_front = (128, 128, 256, 256)
    box_head_left = (256, 128, 384, 256)
    box_head_back = (384, 128, 512, 256)

    # Torso (Body)
    box_torso_top = (320, 256, 448, 320)
    box_torso_bot = (448, 256, 576, 320)
    box_torso_right = (256, 320, 320, 512)
    box_torso_front = (320, 320, 448, 512)
    box_torso_left = (448, 320, 512, 512)
    box_torso_back = (512, 320, 640, 512)

    # Right Arm
    box_arm_top = (704, 256, 768, 320)
    box_arm_bot = (768, 256, 832, 320)
    box_arm_outer = (640, 320, 704, 512)
    box_arm_front = (704, 320, 768, 512)
    box_arm_inner = (768, 320, 832, 512)
    box_arm_back = (832, 320, 896, 512)

    # Right Leg
    box_leg_top = (64, 256, 128, 320)
    box_leg_bot = (128, 256, 192, 320)
    box_leg_outer = (0, 320, 64, 512)
    box_leg_front = (64, 320, 128, 512)
    box_leg_inner = (128, 320, 192, 512)
    box_leg_back = (192, 320, 256, 512)

    # Apply honeycomb grids to each part
    biped_parts = [
        box_head_top, box_head_bot, box_head_right, box_head_front, box_head_left, box_head_back,
        box_torso_top, box_torso_bot, box_torso_right, box_torso_front, box_torso_left, box_torso_back,
        box_arm_top, box_arm_bot, box_arm_outer, box_arm_front, box_arm_inner, box_arm_back,
        box_leg_top, box_leg_bot, box_leg_outer, box_leg_front, box_leg_inner, box_leg_back
    ]

    for part in biped_parts:
        draw_honeycomb(part, r=12, line_w=2, fill=True)

    # Helper: Draw Arcane Circle Rune with inner glyphs
    def draw_arcane_seal(cx, cy, r_outer):
        # Outer concentric circles
        draw.ellipse([cx - r_outer, cy - r_outer, cx + r_outer, cy + r_outer], outline=RUNE_COLOR, width=3)
        draw.ellipse([cx - r_outer + 5, cy - r_outer + 5, cx + r_outer - 5, cy + r_outer - 5], outline=RUNE_GLOW, width=1)
        r_inner = r_outer * 0.72
        draw.ellipse([cx - r_inner, cy - r_inner, cx + r_inner, cy + r_inner], outline=RUNE_COLOR, width=2)

        # 6-pointed hexagram
        pts1 = []
        pts2 = []
        for i in range(3):
            a1 = i * (2 * math.pi / 3) - math.pi / 2
            a2 = i * (2 * math.pi / 3) + math.pi / 6
            pts1.append((cx + r_inner * math.cos(a1), cy + r_inner * math.sin(a1)))
            pts2.append((cx + r_inner * math.cos(a2), cy + r_inner * math.sin(a2)))
        
        draw.polygon(pts1, outline=RUNE_COLOR)
        draw.polygon(pts2, outline=RUNE_COLOR)

        # Center core diamond
        r_core = r_inner * 0.38
        core_pts = [
            (cx, cy - r_core),
            (cx + r_core, cy),
            (cx, cy + r_core),
            (cx - r_core, cy)
        ]
        draw.polygon(core_pts, fill=HEX_NODE, outline=RUNE_COLOR)

        # Surrounding mini runes / dots at outer ring
        for i in range(12):
            ang = i * (math.pi / 6)
            rx = cx + (r_outer - 2.5) * math.cos(ang)
            ry = cy + (r_outer - 2.5) * math.sin(ang)
            if i % 2 == 0:
                draw.ellipse([rx - 2, ry - 2, rx + 2, ry + 2], fill=RUNE_COLOR)
            else:
                draw.ellipse([rx - 1, ry - 1, rx + 1, ry + 1], fill=HEX_NODE)

    # Helper: Draw Rune Glyphs (Runic alphabet style marks)
    def draw_glyph_algiz(cx, cy, h):
        # Protective Elk / Shield rune (tree-fork like)
        draw.line([(cx, cy - h/2), (cx, cy + h/2)], fill=RUNE_COLOR, width=2)
        draw.line([(cx - h*0.35, cy - h/2), (cx, cy - h*0.1)], fill=RUNE_COLOR, width=2)
        draw.line([(cx + h*0.35, cy - h/2), (cx, cy - h*0.1)], fill=RUNE_COLOR, width=2)

    def draw_glyph_ansuz(cx, cy, h):
        # Arcane wisdom rune
        draw.line([(cx - h*0.2, cy - h/2), (cx - h*0.2, cy + h/2)], fill=RUNE_COLOR, width=2)
        draw.line([(cx - h*0.2, cy - h*0.3), (cx + h*0.25, cy - h*0.1)], fill=RUNE_COLOR, width=2)
        draw.line([(cx - h*0.2, cy), (cx + h*0.25, cy + h*0.2)], fill=RUNE_COLOR, width=2)

    def draw_glyph_kenaz(cx, cy, h):
        # Beacon / Flame rune (< shape)
        draw.line([(cx + h*0.2, cy - h/2), (cx - h*0.2, cy)], fill=RUNE_COLOR, width=2)
        draw.line([(cx - h*0.2, cy), (cx + h*0.2, cy + h/2)], fill=RUNE_COLOR, width=2)

    def draw_glyph_gebo(cx, cy, h):
        # Magical union / X shape
        draw.line([(cx - h*0.25, cy - h/2), (cx + h*0.25, cy + h/2)], fill=RUNE_COLOR, width=2)
        draw.line([(cx - h*0.25, cy + h/2), (cx + h*0.25, cy - h/2)], fill=RUNE_COLOR, width=2)

    # 1. Front Torso: Center Arcane Seal (Heart / Solar Plexus)
    # Box is 320 to 448 (cx = 384), 320 to 512 (cy = 400)
    cx_chest = 384
    cy_chest = 395
    draw_arcane_seal(cx_chest, cy_chest, r_outer=38)

    # Chest energy conduit lines radiating from seal
    draw.line([(cx_chest - 38, cy_chest), (cx_chest - 60, cy_chest)], fill=ACCENT_LINE, width=2)
    draw.line([(cx_chest + 38, cy_chest), (cx_chest + 60, cy_chest)], fill=ACCENT_LINE, width=2)
    draw.line([(cx_chest, cy_chest + 38), (cx_chest, cy_chest + 85)], fill=ACCENT_LINE, width=2)
    # Lower chest glyphs
    draw_glyph_algiz(cx_chest, cy_chest + 95, 20)
    draw_glyph_kenaz(cx_chest - 35, cy_chest + 55, 18)
    draw_glyph_gebo(cx_chest + 35, cy_chest + 55, 18)

    # 2. Back Torso: Arcane Spine & Power Conduit
    # Box is 512 to 640 (cx = 576), 320 to 512
    cx_back = 576
    draw.line([(cx_back, 335), (cx_back, 495)], fill=RUNE_COLOR, width=3)
    draw.line([(cx_back - 4, 340), (cx_back - 4, 490)], fill=RUNE_GLOW, width=1)
    draw.line([(cx_back + 4, 340), (cx_back + 4, 490)], fill=RUNE_GLOW, width=1)
    # Spine vertebra runes
    for y_spine, glyph in [(360, draw_glyph_algiz), (400, draw_glyph_gebo), (440, draw_glyph_ansuz), (475, draw_glyph_kenaz)]:
        glyph(cx_back, y_spine, 22)
        # Lateral energy ribs
        draw.line([(cx_back - 30, y_spine), (cx_back + 30, y_spine)], fill=ACCENT_LINE, width=2)
        draw.ellipse([cx_back - 32, y_spine - 2, cx_back - 28, y_spine + 2], fill=HEX_NODE)
        draw.ellipse([cx_back + 28, y_spine - 2, cx_back + 32, y_spine + 2], fill=HEX_NODE)

    # 3. Head Front: Forehead Rune Seal & Brow Diadem
    # Box is 128 to 256 (cx = 192), 128 to 256 (forehead is y=140 to 175)
    cx_head = 192
    draw.ellipse([cx_head - 12, 155 - 12, cx_head + 12, 155 + 12], outline=RUNE_COLOR, width=2)
    draw_glyph_algiz(cx_head, 155, 14)
    # Brow diadem wings
    draw.line([(cx_head - 12, 155), (cx_head - 48, 148)], fill=ACCENT_LINE, width=2)
    draw.line([(cx_head + 12, 155), (cx_head + 48, 148)], fill=ACCENT_LINE, width=2)
    draw.ellipse([cx_head - 50, 146, cx_head - 46, 150], fill=HEX_NODE)
    draw.ellipse([cx_head + 46, 146, cx_head + 50, 150], fill=HEX_NODE)

    # 4. Arms: Outer Arm Forearm Runic Bracelets & Shoulder Sigil
    # Box is 640 to 704 (cx = 672), 320 to 512
    cx_arm = 672
    # Shoulder crest
    draw.ellipse([cx_arm - 16, 350 - 16, cx_arm + 16, 350 + 16], outline=RUNE_COLOR, width=2)
    draw_glyph_gebo(cx_arm, 350, 16)
    # Forearm warding band (y=440 to 470)
    draw.line([(cx_arm - 24, 445), (cx_arm + 24, 445)], fill=RUNE_COLOR, width=3)
    draw.line([(cx_arm - 24, 465), (cx_arm + 24, 465)], fill=RUNE_COLOR, width=3)
    draw_glyph_algiz(cx_arm, 455, 14)

    # 5. Legs: Front Leg Shin Guard Runes & Knee Node
    # Box is 64 to 128 (cx = 96), 320 to 512
    cx_leg = 96
    # Knee node
    draw.ellipse([cx_leg - 14, 400 - 14, cx_leg + 14, 400 + 14], outline=RUNE_COLOR, width=2)
    draw.polygon([(cx_leg, 392), (cx_leg + 8, 400), (cx_leg, 408), (cx_leg - 8, 400)], fill=HEX_NODE)
    # Shin conduit
    draw.line([(cx_leg, 414), (cx_leg, 480)], fill=RUNE_COLOR, width=2)
    draw_glyph_kenaz(cx_leg, 445, 18)

    # Add soft outer bloom/glow layer
    glow = img.filter(ImageFilter.GaussianBlur(radius=2))
    result = Image.alpha_composite(glow, img)

    result.save(filepath, "PNG")
    print(f"Mana shield skin texture generated successfully at {filepath}")

if __name__ == "__main__":
    import os
    out_dir = r"c:\Users\PC\Downloads\AM2NEWERA\ArsMagica2\src\main\resources\assets\arsmagica2\textures\misc\vfx"
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "mana_shield_skin.png")
    create_mana_shield_texture(out_path)
