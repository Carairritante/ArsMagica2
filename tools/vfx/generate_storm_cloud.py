import math
import random
from PIL import Image, ImageDraw, ImageFilter

def create_storm_cloud():
    out_dir = r"src/main/resources/assets/arsmagica2/textures/misc/vfx"
    W, H = 512, 512
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    random.seed(1337)

    cx, cy = W // 2, H // 2

    # Draw multiple overlapping soft cloud puffs
    # Colors: dark storm navy/charcoal (#0a0f1d, #111827, #1e293b, #1e3a8a)
    puffs = [
        # (offset_x, offset_y, radius, alpha)
        (0, 0, 160, 240),
        (-60, -40, 130, 220),
        (60, -35, 125, 220),
        (-90, 20, 115, 200),
        (85, 25, 120, 200),
        (-35, 55, 110, 190),
        (40, 50, 110, 190),
        (0, -70, 105, 180),
        (-120, -10, 95, 170),
        (120, -10, 95, 170),
        (0, 75, 90, 160),
    ]

    # Add small sub-puffs for organic fluffy silhouette
    for _ in range(25):
        ang = random.uniform(0, 2 * math.pi)
        dist = random.uniform(50, 170)
        px = cx + dist * math.cos(ang)
        py = cy + dist * math.sin(ang)
        pr = random.uniform(50, 90)
        draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(30, 41, 59, 140))

    # Base body puffs
    for ox, oy, pr, alpha in puffs:
        px = cx + ox
        py = cy + oy
        # Gradient fill from core to edge
        draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(15, 23, 42, alpha))

    # Center dense dark core
    draw.ellipse([cx - 100, cy - 80, cx + 100, cy + 80], fill=(10, 15, 30, 255))
    draw.ellipse([cx - 70, cy - 50, cx + 70, cy + 50], fill=(5, 8, 20, 255))

    # Heavy blur for perfectly soft volumetric cloud edges
    blurred = img.filter(ImageFilter.GaussianBlur(radius=18))
    # Extra pass of fine detail
    draw_fine = ImageDraw.Draw(blurred)
    for _ in range(15):
        ang = random.uniform(0, 2 * math.pi)
        dist = random.uniform(20, 110)
        px = cx + dist * math.cos(ang)
        py = cy + dist * math.sin(ang)
        pr = random.uniform(40, 70)
        draw_fine.ellipse([px - pr, py - pr, px + pr, py + pr], fill=(30, 58, 138, 50)) # Deep electric blue internal tint

    final_cloud = blurred.filter(ImageFilter.GaussianBlur(radius=8))
    final_cloud.save(f"{out_dir}/storm_cloud.png", "PNG")
    print("storm_cloud.png generated successfully!")

if __name__ == "__main__":
    create_storm_cloud()
