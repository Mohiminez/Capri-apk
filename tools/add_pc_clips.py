# Adds the desktop's perch / lay-down / glitch strips to the Android sprite set.
# usage: python3 add_pc_clips.py /path/to/capri-desktop
import json, sys, os
from PIL import Image
src = sys.argv[1]
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets")
# name: (frames in source, source fps, shift right in source px, thin to every 2nd frame)
NEW = {
    "perch_arrive": (14, 15, 12.0, False), "perch_leave": (12, 15, 12.0, False),
    "perch_glitch": (10, 15, 12.0, False), "perch_blink_glitch": (8, 30, 12.0, False),
    "perch_music": (60, 30, 12.0, True), "idle_blink_glitch": (8, 24, 12.2, False),
    "laydown": (60, 30, 0, True), "laydown_blink": (8, 30, 0, False),
    "laydown_glitch": (10, 15, 0, False), "laydown_leave": (12, 15, 0, False),
}
mf = os.path.join(out, "sprites.json")
manifest = json.load(open(mf))
for name, (frames, fps, sx, thin) in NEW.items():
    im = Image.open(f"{src}/assets/sprites/{name}.png").convert("RGBA")
    fw, fh = im.width // frames, im.height
    keep = list(range(0, frames, 2)) if thin else list(range(frames))
    if thin: fps //= 2
    cols = max(1, 4096 // fw); rows = -(-len(keep) // cols)
    sheet = Image.new("RGBA", (cols * fw, rows * fh), (0, 0, 0, 0))
    for n, i in enumerate(keep):
        sheet.paste(im.crop((i * fw, 0, (i + 1) * fw, fh)), ((n % cols) * fw, (n // cols) * fh))
    sheet.save(f"{out}/sprites/{name}.webp", quality=82, method=6)
    manifest[name] = dict(frames=len(keep), fps=fps, w=fw, h=fh, cols=cols, sx=sx)
    print(name, len(keep), os.path.getsize(f"{out}/sprites/{name}.webp"))
json.dump(manifest, open(mf, "w"), indent=1)
