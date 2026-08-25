"""
Το εικονίδιο 512×512 για τη σελίδα του Play.

Δεν ζωγραφίζεται από την αρχή: διαβάζει το ίδιο vector που χρησιμοποιεί η
εφαρμογή (`res/drawable/ic_launcher_foreground.xml`) και το ίδιο χρώμα φόντου,
τα μετατρέπει σε SVG και τα αποδίδει με τον Chromium. Έτσι το εικονίδιο του
store δεν μπορεί να ξεφύγει από το εικονίδιο της εφαρμογής.

    python3 tools/store_icon.py
"""

import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
VECTOR = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
BG_XML = ROOT / "app/src/main/res/values/ic_launcher_background.xml"
OUT = ROOT / "store/app-icon-512.png"
CHROME = "/opt/pw-browsers/chromium-1194/chrome-linux/chrome"

NS = "{http://schemas.android.com/apk/res/android}"

# An adaptive icon is a 108dp canvas of which a launcher shows the middle 72dp,
# masked to whatever shape that phone uses. The store icon is a plain square
# and gets no mask, so neither framing works straight off: the full canvas
# leaves the artwork small, and the 72dp crop inherits an asymmetry the mask
# was hiding — this drawing sits five units above the centre, which reads as a
# band of empty green along the bottom.
#
# So the frame is computed from the artwork instead. The icon is rendered once
# to find where the drawing actually is, then rendered again around it with an
# even margin. Reproducible, and it cannot drift when the vector changes.
VIEWPORT = 108.0
MARGIN = 0.17  # of the frame, on the tighter axis


def android_colour(value: str) -> str:
    """#AARRGGBB out of the vector, #RRGGBB into the SVG."""
    v = value.lstrip("#")
    return "#" + (v[2:] if len(v) == 8 else v)


def main():
    root = ET.parse(VECTOR).getroot()
    paths = [
        f'<path fill="{android_colour(p.get(NS + "fillColor"))}" '
        f'd="{p.get(NS + "pathData")}"/>'
        for p in root.iter("path")
    ]
    if not paths:
        sys.exit("Δεν βρέθηκαν paths στο vector.")

    bg_text = BG_XML.read_text()
    match = re.search(r'name="ic_launcher_background">(#[0-9A-Fa-f]{6,8})<', bg_text)
    if not match:
        sys.exit("Δεν βρέθηκε το χρώμα φόντου.")
    background = android_colour(match.group(1))

    def render(view, out, size):
        svg = (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" '
            f'viewBox="{view[0]} {view[1]} {view[2]} {view[2]}">'
            f'<rect x="{view[0]}" y="{view[1]}" width="{view[2]}" height="{view[2]}" '
            f'fill="{background}"/>' + "".join(paths) + "</svg>"
        )
        page = ("<!doctype html><meta charset=utf-8><style>"
                "html,body{margin:0;padding:0;background:" + background + "}"
                "svg{display:block}</style>" + svg)
        tmp = ROOT / "store" / ".icon.html"
        tmp.write_text(page, encoding="utf-8")
        subprocess.run(
            [CHROME, "--headless", "--disable-gpu", "--no-sandbox",
             "--hide-scrollbars", "--force-device-scale-factor=1",
             f"--window-size={size},{size}", f"--screenshot={out}", tmp.as_uri()],
            capture_output=True, text=True, check=False,
        )
        tmp.unlink()
        if not out.exists():
            sys.exit("Ο Chromium δεν έβγαλε εικόνα.")

    # Pass one: the whole canvas, to find the drawing.
    probe = ROOT / "store" / ".probe.png"
    render((0, 0, VIEWPORT), probe, 512)

    from PIL import Image
    import numpy as np
    a = np.array(Image.open(probe).convert("RGB"))
    bg = np.array([int(background[i:i + 2], 16) for i in (1, 3, 5)])
    ink = np.where(np.any(np.abs(a.astype(int) - bg) > 10, axis=-1))
    probe.unlink()
    if len(ink[0]) == 0:
        sys.exit("Δεν βρέθηκε σχέδιο μέσα στο εικονίδιο.")

    scale = VIEWPORT / 512
    top, bottom = ink[0].min() * scale, ink[0].max() * scale
    left, right = ink[1].min() * scale, ink[1].max() * scale
    cx, cy = (left + right) / 2, (top + bottom) / 2
    side = max(right - left, bottom - top) / (1 - 2 * MARGIN)

    # Pass two: the same drawing, centred in its own square.
    render((cx - side / 2, cy - side / 2, side), OUT, 512)
    print(f"{OUT}  {OUT.stat().st_size} bytes")
    print(f"σχέδιο: x {left:.1f}–{right:.1f}, y {top:.1f}–{bottom:.1f} από {VIEWPORT:.0f}")


if __name__ == "__main__":
    main()
