import math
import random
from PIL import Image, ImageDraw, ImageFilter

def create_storm_cloud():
    out_path = r"c:\Users\PC\Downloads\AM2NEWERA\ArsMagica2\src\main\resources\assets\arsmagica2\textures\misc\vfx\storm_cloud.png"
    W, H = 512, 512
    # Grayscale with alpha: RGB is pure white (255, 255, 255) so glColor4f controls exact shade!
    # Alpha channel contains the fluffy organic cumulus cloud shape.
    alpha_img = Image.new("L", (W, H), 0)
    draw = ImageDraw.Draw(alpha_img)
    random.seed(42)

    cx, cy = W // 2, H // 2

    # Multiple overlapping circular/elliptical lobes
    lobes = [
        # (ox, oy, rx, ry, alpha)
        (0, 0, 160, 150, 255),
        (-60, -35, 130, 120, 245),
        (65, -30, 125, 115, 245),
        (-95, 25, 115, 105, 240),
        (90, 30, 120, 110, 240),
        (-40, 60, 110, 100, 235),
        (45, 55, 110, 100, 235),
        (0, -75, 105, 95, 230),
        (-125, -15, 95, 90, 220),
        (125, -15, 95, 90, 220),
        (0, 80, 95, 85, 220),
    ]

    # Dense clusters of medium & small organic puffs around the periphery
    for _ in range(40):
        ang = random.uniform(0, 2 * math.pi)
        dist = random.uniform(40, 175)
        px = cx + dist * math.cos(ang)
        py = cy + dist * math.sin(ang)
        pr = random.uniform(45, 95)
        a = int(random.uniform(160, 240))
        draw.ellipse([px - pr, py - pr, px + pr, py + pr], fill=a)

    for ox, oy, rx, ry, a in lobes:
        px = cx + ox
        py = cy + oy
        draw.ellipse([px - rx, py - ry, px + rx, py + ry], fill=a)

    # Core high-density center
    draw.ellipse([cx - 110, cy - 90, cx + 110, cy + 90], fill=255)
    draw.ellipse([cx - 80, cy - 60, cx + 80, cy + 60], fill=255)

    # Soft Gaussian blur to turn sharp circles into smooth volumetric cloud puffs
    smooth_alpha = alpha_img.filter(ImageFilter.GaussianBlur(radius=20))

    # Add a touch of fine organic edge detail
    draw_fine = ImageDraw.Draw(smooth_alpha)
    for _ in range(25):
        ang = random.uniform(0, 2 * math.pi)
        dist = random.uniform(60, 190)
        px = cx + dist * math.cos(ang)
        py = cy + dist * math.sin(ang)
        pr = random.uniform(30, 60)
        draw_fine.ellipse([px - pr, py - pr, px + pr, py + pr], fill=120)

    final_alpha = smooth_alpha.filter(ImageFilter.GaussianBlur(radius=10))

    # Combine pure white RGB with the final cumulus alpha map
    cloud_img = Image.new("RGBA", (W, H), (255, 255, 255, 255))
    cloud_img.putalpha(final_alpha)
    cloud_img.save(out_path, "PNG")
    print("Generated white cumulus storm_cloud.png at:", out_path)

if __name__ == "__main__":
    create_storm_cloud()
