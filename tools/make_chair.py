# Builds the chair emote (from capri-desktop assets/emotes/chair.zip) as small WebP frames for the Android overlay.
# usage: python3 make_chair.py /path/to/capri-desktop
import sys, os, zipfile, io
from PIL import Image
src = sys.argv[1]; out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "emotes", "chair")
os.makedirs(out, exist_ok=True)
for f in os.listdir(out): os.remove(os.path.join(out, f))
z = zipfile.ZipFile(f"{src}/assets/emotes/chair.zip")
pics = {n: Image.open(io.BytesIO(z.read(n))).convert("RGBA") for n in z.namelist() if n.endswith(".png")}
rows = {}
for line in z.read("frames.txt").decode().split("\n"):
    p = line.split()
    if p and p[0].isdigit(): rows[int(p[0])] = p
W, H, STEP, OW, OH = 1920, 1080, 2, 640, 360
boxes = []; frames = []
for i in range(0, len(rows), STEP):
    p = rows[i]; c = Image.new("RGBA", (W, H), (0, 0, 0, 0)); k = 1
    if p[k] != "-":
        im = pics[p[k]]; c.alpha_composite(im.resize((im.width * 2, im.height * 2), Image.LANCZOS), (int(p[k+1]) * 2, int(p[k+2]) * 2)); k += 3
    else: k += 1
    if len(p) >= k + 3:
        c.alpha_composite(pics[p[k]], (int(p[k+1]), int(p[k+2])))
    frames.append(c.resize((OW, OH), Image.LANCZOS))
n = 0
for i, f in enumerate(frames):
    f.save(f"{out}/{i:03d}.webp", quality=68, method=6); n += os.path.getsize(f"{out}/{i:03d}.webp")
open(f"{out}/info.txt", "w").write(f"{len(frames)} {30 // STEP} {OW} {OH}\n")
print(len(frames), "frames", n // 1024, "KB")
