import math
import random
from PIL import Image, ImageDraw, ImageFilter

def create_lightning_textures():
    out_dir = r"src/main/resources/assets/arsmagica2/textures/misc/vfx"
    random.seed(42)

    # -------------------------------------------------------------
    # 1. lightning_sigil.png (1024x1024) - Ground Electric Runic Matrix
    # -------------------------------------------------------------
    W, H = 1024, 1024
    img_sigil = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img_sigil)
    cx, cy = W // 2, H // 2

    C_WHITE = (255, 255, 255, 250)
    C_CYAN = (0, 229, 255, 220)
    C_BLUE = (56, 189, 248, 180)
    C_DEEP = (99, 102, 241, 140)
    C_GOLD = (254, 240, 138, 200)

    # Concentric circles
    for r, col, w in [
        (480, C_DEEP, 3), (460, C_CYAN, 4), (435, C_WHITE, 2),
        (350, C_BLUE, 2), (320, C_CYAN, 3), (250, C_DEEP, 2),
        (180, C_WHITE, 3), (120, C_CYAN, 2), (60, C_WHITE, 4)
    ]:
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=col, width=w)

    # Outer 16 Hexagonal nodes
    for i in range(16):
        ang = i * (2 * math.pi / 16)
        nx = cx + 460 * math.cos(ang)
        ny = cy + 460 * math.sin(ang)
        # Small hex node
        hex_pts = [(nx + 14 * math.cos(a), ny + 14 * math.sin(a)) for a in [a * math.pi / 3 for a in range(6)]]
        draw.polygon(hex_pts, outline=C_CYAN, fill=(0, 229, 255, 40))
        draw.ellipse([nx - 4, ny - 4, nx + 4, ny + 4], fill=C_WHITE)

    # 12-pointed Star Polygon
    pts_star1 = []
    pts_star2 = []
    for i in range(12):
        ang = i * (2 * math.pi / 12)
        r_out = 435
        r_in = 280
        pts_star1.append((cx + r_out * math.cos(ang), cy + r_out * math.sin(ang)))
        pts_star2.append((cx + r_in * math.cos(ang + math.pi / 12), cy + r_in * math.sin(ang + math.pi / 12)))

    for i in range(12):
        draw.line([pts_star1[i], pts_star2[i]], fill=C_CYAN, width=2)
        draw.line([pts_star2[i], pts_star1[(i + 1) % 12]], fill=C_CYAN, width=2)
        draw.line([cx, cy, pts_star1[i][0], pts_star1[i][1]], fill=C_BLUE, width=1)

    # Jagged lightning discharge branches radiating outward
    for i in range(24):
        ang = i * (2 * math.pi / 24) + random.uniform(-0.05, 0.05)
        curr_x, curr_y = cx + 80 * math.cos(ang), cy + 80 * math.sin(ang)
        dist = 80
        while dist < 450:
            step = random.uniform(25, 45)
            dist += step
            tang = ang + random.uniform(-0.35, 0.35)
            next_x = cx + dist * math.cos(tang)
            next_y = cy + dist * math.sin(tang)
            draw.line([(curr_x, curr_y), (next_x, next_y)], fill=C_WHITE if dist < 300 else C_CYAN, width=2 if dist < 250 else 1)
            # Minor fork
            if random.random() < 0.35:
                fork_ang = tang + random.choice([-0.5, 0.5])
                draw.line([(next_x, next_y), (next_x + 30 * math.cos(fork_ang), next_y + 30 * math.sin(fork_ang))], fill=C_BLUE, width=1)
            curr_x, curr_y = next_x, next_y

    # Central Core Diamond
    r_c = 45
    draw.polygon([(cx, cy - r_c), (cx + r_c, cy), (cx, cy + r_c), (cx - r_c, cy)], outline=C_WHITE, fill=(255, 255, 255, 120))
    draw.ellipse([cx - 20, cy - 20, cx + 20, cy + 20], fill=C_WHITE)

    glow_sigil = img_sigil.filter(ImageFilter.GaussianBlur(radius=3))
    final_sigil = Image.alpha_composite(glow_sigil, img_sigil)
    final_sigil.save(f"{out_dir}/lightning_sigil.png", "PNG")
    print("lightning_sigil.png generated")

    # -------------------------------------------------------------
    # 2. lightning_shockwave.png (512x512) - Expanding Electric Plasma Ring
    # -------------------------------------------------------------
    W2, H2 = 512, 512
    img_shock = Image.new("RGBA", (W2, H2), (0, 0, 0, 0))
    draw2 = ImageDraw.Draw(img_shock)
    cx2, cy2 = W2 // 2, H2 // 2

    # Multiple jagged concentric shock bands
    for base_r in [190, 165, 140, 110]:
        num_pts = 64
        pts = []
        for i in range(num_pts):
            a = i * (2 * math.pi / num_pts)
            jitter = random.uniform(-6, 6)
            pts.append((cx2 + (base_r + jitter) * math.cos(a), cy2 + (base_r + jitter) * math.sin(a)))
        for i in range(num_pts):
            p1 = pts[i]
            p2 = pts[(i + 1) % num_pts]
            draw2.line([p1, p2], fill=C_WHITE if base_r >= 165 else C_CYAN, width=3 if base_r >= 165 else 2)

    # Arcing tendrils between inner and outer ring
    for i in range(32):
        a = i * (2 * math.pi / 32) + random.uniform(-0.1, 0.1)
        x1 = cx2 + 110 * math.cos(a)
        y1 = cy2 + 110 * math.sin(a)
        xm = cx2 + 150 * math.cos(a + random.uniform(-0.2, 0.2))
        ym = cy2 + 150 * math.sin(a + random.uniform(-0.2, 0.2))
        x2 = cx2 + 195 * math.cos(a + random.uniform(-0.1, 0.1))
        y2 = cy2 + 195 * math.sin(a + random.uniform(-0.1, 0.1))
        draw2.line([(x1, y1), (xm, ym)], fill=C_CYAN, width=2)
        draw2.line([(xm, ym), (x2, y2)], fill=C_WHITE, width=2)

    glow_shock = img_shock.filter(ImageFilter.GaussianBlur(radius=2))
    final_shock = Image.alpha_composite(glow_shock, img_shock)
    final_shock.save(f"{out_dir}/lightning_shockwave.png", "PNG")
    print("lightning_shockwave.png generated")

    # -------------------------------------------------------------
    # 3. lightning_bolt.png (256x1024) - Vertical High-Voltage Lightning
    # -------------------------------------------------------------
    W3, H3 = 256, 1024
    img_bolt = Image.new("RGBA", (W3, H3), (0, 0, 0, 0))
    draw3 = ImageDraw.Draw(img_bolt)
    cx3 = W3 // 2

    # Draw main central jagged bolt
    curr_x = cx3
    y = 0
    pts_main = [(curr_x, y)]
    while y < H3:
        y += random.randint(15, 30)
        curr_x += random.randint(-18, 18)
        curr_x = max(60, min(W3 - 60, curr_x))
        pts_main.append((curr_x, y))

    # Outer blue glow
    for i in range(len(pts_main) - 1):
        draw3.line([pts_main[i], pts_main[i+1]], fill=(56, 189, 248, 180), width=14)
    # Mid cyan core
    for i in range(len(pts_main) - 1):
        draw3.line([pts_main[i], pts_main[i+1]], fill=(0, 229, 255, 230), width=8)
    # Bright white spine
    for i in range(len(pts_main) - 1):
        draw3.line([pts_main[i], pts_main[i+1]], fill=(255, 255, 255, 255), width=3)

    # 4-6 branching tributary bolts
    for i in range(6):
        idx = random.randint(3, len(pts_main) - 8)
        bx, by = pts_main[idx]
        b_len = random.randint(4, 8)
        sign = -1 if random.random() < 0.5 else 1
        curr_bx, curr_by = bx, by
        for j in range(b_len):
            next_by = curr_by + random.randint(20, 40)
            next_bx = curr_bx + sign * random.randint(15, 30)
            draw3.line([(curr_bx, curr_by), (next_bx, next_by)], fill=(0, 229, 255, 160), width=3)
            draw3.line([(curr_bx, curr_by), (next_bx, next_by)], fill=(255, 255, 255, 210), width=1)
            curr_bx, curr_by = next_bx, next_by

    glow_bolt = img_bolt.filter(ImageFilter.GaussianBlur(radius=3))
    final_bolt = Image.alpha_composite(glow_bolt, img_bolt)
    final_bolt.save(f"{out_dir}/lightning_bolt.png", "PNG")
    print("lightning_bolt.png generated")

    # -------------------------------------------------------------
    # 4. lightning_dragon.png (512x512) - Kirin / Thunder Dragon Texture
    # -------------------------------------------------------------
    W4, H4 = 512, 512
    img_dragon = Image.new("RGBA", (W4, H4), (0, 0, 0, 0))
    draw4 = ImageDraw.Draw(img_dragon)

    # Top-half: Dragon Head Profile / Frontal features (0-256)
    # Dragon Snout & Head Crest
    head_cx = 256
    head_cy = 160

    # Serpentine jaw/skull base (plasma cyan)
    jaw_pts = [
        (head_cx - 90, head_cy - 70), (head_cx + 90, head_cy - 70),
        (head_cx + 120, head_cy), (head_cx + 70, head_cy + 80),
        (head_cx, head_cy + 110), (head_cx - 70, head_cy + 80),
        (head_cx - 120, head_cy)
    ]
    draw4.polygon(jaw_pts, fill=(14, 165, 233, 160), outline=C_CYAN)

    # Dragon Horns (sweeping lightning bolts)
    # Left Horn
    draw4.line([(head_cx - 70, head_cy - 60), (head_cx - 140, head_cy - 120)], fill=C_WHITE, width=6)
    draw4.line([(head_cx - 140, head_cy - 120), (head_cx - 200, head_cy - 150)], fill=C_CYAN, width=4)
    draw4.line([(head_cx - 140, head_cy - 120), (head_cx - 160, head_cy - 180)], fill=C_WHITE, width=3)
    # Right Horn
    draw4.line([(head_cx + 70, head_cy - 60), (head_cx + 140, head_cy - 120)], fill=C_WHITE, width=6)
    draw4.line([(head_cx + 140, head_cy - 120), (head_cx + 200, head_cy - 150)], fill=C_CYAN, width=4)
    draw4.line([(head_cx + 140, head_cy - 120), (head_cx + 160, head_cy - 180)], fill=C_WHITE, width=3)

    # Fierce Dragon Eyes (Pure glowing white-cyan with slant)
    draw4.polygon([(head_cx - 65, head_cy - 20), (head_cx - 25, head_cy - 10), (head_cx - 50, head_cy - 30)], fill=C_WHITE, outline=C_CYAN)
    draw4.polygon([(head_cx + 65, head_cy - 20), (head_cx + 25, head_cy - 10), (head_cx + 50, head_cy - 30)], fill=C_WHITE, outline=C_CYAN)
    # Eye pupils (gold-white spark)
    draw4.ellipse([head_cx - 48, head_cy - 22, head_cx - 40, head_cy - 16], fill=C_GOLD)
    draw4.ellipse([head_cx + 40, head_cy - 22, head_cx + 48, head_cy - 16], fill=C_GOLD)

    # Dragon Whiskers (plasma tendrils curling downwards)
    draw4.line([(head_cx - 40, head_cy + 50), (head_cx - 110, head_cy + 130), (head_cx - 160, head_cy + 220)], fill=C_CYAN, width=4)
    draw4.line([(head_cx + 40, head_cy + 50), (head_cx + 110, head_cy + 130), (head_cx + 160, head_cy + 220)], fill=C_CYAN, width=4)

    # Sharp Plasma Teeth / Fangs
    for tx in [-45, -25, -5, 15, 35]:
        draw4.polygon([(head_cx + tx, head_cy + 75), (head_cx + tx + 10, head_cy + 75), (head_cx + tx + 5, head_cy + 95)], fill=C_WHITE)

    # Bottom-half: Dragon Wings & Serpentine Lightning Body Scales (256-512)
    # Wing Left (arcane electric wing ribs)
    w_origin_x, w_origin_y = 130, 360
    for w_ang in [-0.8, -0.4, 0.0, 0.4, 0.8]:
        wx = w_origin_x + 110 * math.cos(w_ang + math.pi)
        wy = w_origin_y + 110 * math.sin(w_ang + math.pi)
        draw4.line([(w_origin_x, w_origin_y), (wx, wy)], fill=C_CYAN, width=4)
        draw4.line([(w_origin_x, w_origin_y), (wx, wy)], fill=C_WHITE, width=2)
    # Wing Right
    w_origin_x2, w_origin_y2 = 382, 360
    for w_ang in [-0.8, -0.4, 0.0, 0.4, 0.8]:
        wx = w_origin_x2 + 110 * math.cos(w_ang)
        wy = w_origin_y2 + 110 * math.sin(w_ang)
        draw4.line([(w_origin_x2, w_origin_y2), (wx, wy)], fill=C_CYAN, width=4)
        draw4.line([(w_origin_x2, w_origin_y2), (wx, wy)], fill=C_WHITE, width=2)

    # Center scales: dorsal plasma fin
    for sy in range(270, 500, 35):
        draw4.polygon([(head_cx, sy - 15), (head_cx + 25, sy), (head_cx, sy + 15), (head_cx - 25, sy)], fill=C_CYAN, outline=C_WHITE)
        draw4.ellipse([head_cx - 6, sy - 6, head_cx + 6, sy + 6], fill=C_WHITE)

    glow_dragon = img_dragon.filter(ImageFilter.GaussianBlur(radius=3))
    final_dragon = Image.alpha_composite(glow_dragon, img_dragon)
    final_dragon.save(f"{out_dir}/lightning_dragon.png", "PNG")
    print("lightning_dragon.png generated")

if __name__ == "__main__":
    create_lightning_textures()
