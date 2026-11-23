# Builds the Android sprite grids and persona text from a checkout of capri-desktop.
# usage: python3 prepare_assets.py /path/to/capri-desktop
import json, re, sys, os
from PIL import Image
src = sys.argv[1]
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
SCALE = 1.0
CLIPS = {  # name: (frames, fps, source-px shift to the right), from app/Capri.cs
    "idle": (60, 24, 12.2), "idle_blink": (8, 24, 12.2), "idle_mouth": (5, 12, 12.2), "mouth": (5, 12, 0),
    "glitch": (10, 15, 0), "grin": (16, 8, 0), "sleep": (16, 6, 0), "wake": (13, 15, 0), "arrive": (14, 15, 0),
    "leave": (12, 15, 0), "dangle": (24, 10, 0), "walk": (12, 12, 0), "dance": (16, 10, 0), "click": (8, 20, 0),
    "laydown_sleep": (60, 30, 0), "perch": (60, 30, 12.0), "perch_blink": (8, 30, 12.0),
}
THIN = {"laydown_sleep", "perch"}  # the lying sheets are huge: keep every second frame at half the rate
manifest = {}
for name, (frames, fps, sx) in CLIPS.items():
    im = Image.open(f"{src}/assets/sprites/{name}.png").convert("RGBA")
    fw, fh = im.width // frames, im.height
    keep = list(range(0, frames, 2)) if name in THIN else list(range(frames))
    if name in THIN: fps = fps // 2
    w, h = max(1, round(fw * SCALE)), round(fh * SCALE)
    cols = max(1, 4096 // w)
    rows = -(-len(keep) // cols)
    sheet = Image.new("RGBA", (cols * w, rows * h), (0, 0, 0, 0))
    for n, i in enumerate(keep):
        f = im.crop((i * fw, 0, (i + 1) * fw, fh)).resize((w, h), Image.LANCZOS)
        sheet.paste(f, ((n % cols) * w, (n // cols) * h))
    sheet.save(f"{out}/sprites/{name}.png", optimize=True)
    manifest[name] = dict(frames=len(keep), fps=fps, w=w, h=h, cols=cols, sx=sx)
json.dump(manifest, open(f"{out}/sprites.json", "w"), indent=1)

# persona text, copied from app/Brain.cs so she stays the same Capri
code = open(f"{src}/app/Brain.cs", encoding="utf-8").read()
def const(name):
    m = re.search(r"const string " + name + r"\s*=\s*(.*?);\s*\n", code, re.S)
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
    return "".join(json.loads('"' + p + '"') for p in parts)
open(f"{out}/personality.txt", "w", encoding="utf-8").write(const("DefaultPersonality"))
open(f"{out}/safety.txt", "w", encoding="utf-8").write(const("Safety"))

# launcher icons from her face
face = Image.open(f"{src}/assets/sprites/face.png").convert("RGBA")
for d, s in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    p = f"{out}/../res/mipmap-{d}"; os.makedirs(p, exist_ok=True)
    face.resize((s, s), Image.LANCZOS).save(f"{p}/ic_launcher.png", optimize=True)
