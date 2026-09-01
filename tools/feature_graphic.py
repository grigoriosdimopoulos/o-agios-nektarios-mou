"""
The 1024x500 banner at the top of the Play listing.

Drawn rather than photographed, from two things that are actually this
village's: the real road network out of the app's own asset — all eighty-two
ways, the same geometry the map draws — and the app's own palette and
typefaces. A stock mountain photograph with a name over it would have said
nothing that could not be said about anywhere.

Play crops this graphic differently in different places and may lay a play
button over the middle of it, so nothing that has to be read goes near an edge
or dead centre: the roads run to the bleed, the words sit left inside a wide
margin.

    python3 tools/feature_graphic.py
"""

import json
import math
import pathlib

from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
OUT = pathlib.Path("store/feature-graphic.png")
ROADS = pathlib.Path("app/src/main/assets/village_roads.json")
FONT_DIR = pathlib.Path("app/src/main/res/font")

# Straight out of ui/theme/Color.kt.
PINE = (0x1F, 0x6F, 0x5C)
PINE_DEEP = (0x0E, 0x2E, 0x27)
CREAM = (0xFB, 0xF7, 0xF2)
OLIVE = (0xF2, 0xB4, 0x41)
TERRACOTTA = (0xE2, 0x72, 0x4B)

# How many houses there are. Drawn, and written in the line above them, from
# one place so the picture cannot disagree with the caption.
HOUSES = 200


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def variable(path, weight):
    """A weight out of a variable font, or the default if this Pillow cannot."""
    font = ImageFont.truetype(str(path), size=10)
    try:
        font.set_variation_by_axes([weight])
    except Exception:
        pass
    return font


def load(path, size, weight):
    font = ImageFont.truetype(str(path), size=size)
    try:
        font.set_variation_by_axes([weight])
    except Exception:
        pass
    return font


def main():
    # ---------------------------------------------------------------- ground
    # A diagonal wash rather than a flat fill: flat reads as a placeholder at
    # this size, and a vertical gradient reads as a sky the roads then sit on
    # wrongly. The light corner is top-left, where the words go.
    base = Image.new("RGB", (W, H))
    px = base.load()
    for y in range(H):
        for x in range(0, W, 4):
            t = (x / W * 0.62 + y / H * 0.38)
            c = lerp(PINE, PINE_DEEP, min(1.0, t * 1.15))
            for dx in range(4):
                if x + dx < W:
                    px[x + dx, y] = c

    # ----------------------------------------------------------------- roads
    # Supersampled, because a one-pixel road drawn straight is a dashed mess.
    S = 3
    layer = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    pen = ImageDraw.Draw(layer)

    data = json.loads(ROADS.read_text())
    lines = []
    for feature in data["features"]:
        geom = feature["geometry"]
        parts = (
            [geom["coordinates"]]
            if geom["type"] == "LineString"
            else geom["coordinates"]
        )
        lines.extend(parts)

    lngs = [c[0] for p in lines for c in p]
    lats = [c[1] for p in lines for c in p]
    min_lng, max_lng = min(lngs), max(lngs)
    min_lat, max_lat = min(lats), max(lats)

    # Latitude is compressed by cos(lat) on this projection; ignoring it would
    # stretch the village east-west and it would stop being this village.
    lat_mid = math.radians((min_lat + max_lat) / 2)
    span_x = (max_lng - min_lng) * math.cos(lat_mid)
    span_y = max_lat - min_lat

    # Filled to the bleed and past it: the network is texture here, not a map
    # to be read, and a network floating in the middle with air around it would
    # read as a diagram.
    scale = max(W / span_x, H / span_y) * 1.62
    cx = (min_lng + max_lng) / 2
    cy = (min_lat + max_lat) / 2

    def project(lng, lat):
        x = W / 2 + (lng - cx) * math.cos(lat_mid) * scale
        # Pushed right and down so the tangle sits under the right-hand side
        # and the words on the left keep quiet ground.
        y = H / 2 - (lat - cy) * scale
        return (x * S + 150 * S, y * S + 30 * S)

    for part in lines:
        pts = [project(lng, lat) for lng, lat in part]
        if len(pts) < 2:
            continue
        pen.line(pts, fill=CREAM + (48,), width=int(2.1 * S), joint="curve")

    layer = layer.resize((W, H), Image.LANCZOS)
    base = Image.alpha_composite(base.convert("RGBA"), layer).convert("RGB")

    # ---------------------------------------------------------------- houses
    # Two hundred, because that is how many there are, and the words say so.
    #
    # It said forty-six for a while, which is the number of people who live
    # here all year rather than the number of houses — a different fact wearing
    # the same number. An earlier version also drew thirty-five of them under a
    # caption reading "46 σπίτια", because it sampled road points and then
    # dropped whatever fell off the canvas. So: candidates are filtered to what
    # is actually visible *before* anything is chosen, and spaced apart so two
    # do not land on top of each other and read as one.
    marks = Image.new("RGBA", (W * S, H * S), (0, 0, 0, 0))
    pen = ImageDraw.Draw(marks)

    margin = 26
    per_way = []
    for part in lines:
        pts = []
        for lng, lat in part:
            x, y = project(lng, lat)
            x, y = x / S, y / S
            # Left of this the words are, and a light behind a letter is a
            # smudge rather than a house.
            if margin < x < W - margin and margin < y < H - margin and x > 470:
                pts.append((x, y))
        if pts:
            per_way.append(pts)

    # Round-robin across ways, not along them.
    #
    # Walking the point list in order put all forty-six along whichever way was
    # longest, which drew a chain of lights down one road and read as a route
    # rather than as a village. Taking one from each way in turn, and only then
    # coming back for a second, spreads them over the network the way houses
    # are spread over it.
    chosen = []
    min_gap = 9.5
    for round_index in range(160):
        if len(chosen) >= HOUSES:
            break
        for pts in per_way:
            if len(chosen) >= HOUSES:
                break
            # A different point each pass, biased away from the ends where two
            # ways meet and would put two houses on one junction.
            if not pts:
                continue
            idx = int((round_index * 0.37 + 0.5) * len(pts)) % len(pts)
            x, y = pts[idx]
            if all((x - px) ** 2 + (y - py) ** 2 > min_gap ** 2 for px, py in chosen):
                chosen.append((x, y))
    if len(chosen) < HOUSES:
        for pts in per_way:
            for x, y in pts:
                if len(chosen) >= HOUSES:
                    break
                if all((x - px) ** 2 + (y - py) ** 2 > 5.5 ** 2 for px, py in chosen):
                    chosen.append((x, y))

    for x, y in chosen:
        x, y = x * S, y * S
        r = 2.6 * S
        pen.ellipse([x - r * 2.6, y - r * 2.6, x + r * 2.6, y + r * 2.6],
                    fill=OLIVE + (26,))
        pen.ellipse([x - r, y - r, x + r, y + r], fill=OLIVE + (255,))
    placed = len(chosen)

    marks = marks.resize((W, H), Image.LANCZOS)
    base = Image.alpha_composite(base.convert("RGBA"), marks).convert("RGB")

    # A scrim under the words only. Without it the roads run through the
    # lettering and both lose.
    scrim = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    pen = ImageDraw.Draw(scrim)
    for x in range(0, 620):
        a = int(150 * max(0.0, 1 - (x / 620) ** 1.7))
        pen.line([(x, 0), (x, H)], fill=PINE_DEEP + (a,))
    base = Image.alpha_composite(base.convert("RGBA"), scrim).convert("RGB")

    # ----------------------------------------------------------------- words
    draw = ImageDraw.Draw(base)
    serif = load(FONT_DIR / "alegreya_variable.ttf", 88, 700)
    sans = load(FONT_DIR / "inter_variable.ttf", 27, 400)
    small = load(FONT_DIR / "inter_variable.ttf", 21, 500)

    left = 74
    draw.text((left, 150), "Άγιος", font=serif, fill=CREAM)
    draw.text((left, 238), "Νεκτάριος", font=serif, fill=CREAM)

    rule_y = 348
    draw.line([(left, rule_y), (left + 78, rule_y)], fill=TERRACOTTA, width=3)

    draw.text((left, rule_y + 26), "Ο χάρτης, τα προβλήματα", font=sans,
              fill=CREAM)
    draw.text((left, rule_y + 60), "και τα νέα του οικισμού μας", font=sans,
              fill=CREAM)

    draw.text((left, 96), f"{HOUSES} ΣΠΙΤΙΑ · 640 ΜΕΤΡΑ · ΚΙΘΑΙΡΩΝΑΣ", font=small,
              fill=lerp(CREAM, PINE, 0.42))

    OUT.parent.mkdir(parents=True, exist_ok=True)
    base.save(OUT, "PNG")
    print(f"{OUT}  {base.size[0]}x{base.size[1]}  {OUT.stat().st_size} bytes")
    print(f"houses drawn: {placed}")


if __name__ == "__main__":
    main()
