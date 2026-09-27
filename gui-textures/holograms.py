#!/usr/bin/env python3
"""
Hologram logos: big blocky 3D pixel letters with an icy blue face, a deep extruded side and a
dark outline, with an optional smaller second line under the main word.

Each PNG is drawn at 1 image pixel = 1 font pixel, so in game it stays crisp at any scale.

Nexo glyph for a logo of height H:  ascent: H   height: H   (the logo sits on top of its line)
Hologram line:                      %nexo_<id>%   (or <glyph:id> where Nexo tags work)

Run:  python3 holograms.py      (needs Pillow: pip install pillow)
"""
import os
from PIL import Image

from generate import FONT, hexc, blend, contact_sheet

# ------------------------------------------------------------------ style

OUTLINE = hexc('#061529')
SIDE_TOP = hexc('#1D5CA6')
SIDE_BOTTOM = hexc('#0C2F5E')
FACE_STOPS = [          # top of the letters to the bottom
    (0.00, hexc('#F4FEFF')),
    (0.18, hexc('#C6F3FF')),
    (0.48, hexc('#7FDBF7')),
    (0.72, hexc('#46B4EC')),
    (1.00, hexc('#2A7FD4')),
]
EDGE_LIGHT = hexc('#FFFFFF')
EDGE_DARK = hexc('#1F66B8')


def gradient(t):
    for (a, ca), (b, cb) in zip(FACE_STOPS, FACE_STOPS[1:]):
        if t <= b:
            return blend(ca, cb, (t - a) / (b - a))
    return FACE_STOPS[-1][1]


# ------------------------------------------------------------------ letter shapes


def word_mask(text, cell, gap):
    """Pixels of a word in chunky letters: the 5x7 font, each stroke doubled in width,
    scaled up by `cell`, with every outside corner rounded off."""
    cells = set()
    x = 0
    for ch in text:
        rows = FONT[ch]
        width = len(rows[0]) + (1 if ch != ' ' else 0)
        for gy, line in enumerate(rows):
            for gx, p in enumerate(line):
                if p == '#':
                    for bx in (gx, gx + 1):
                        for dy in range(cell):
                            for dx in range(cell):
                                cells.add((x + bx * cell + dx, gy * cell + dy))
        x += width * cell + gap
    # round the outside corners
    rounded = set(cells)
    for (px, py) in cells:
        for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            if (px + sx, py) not in cells and (px, py + sy) not in cells:
                rounded.discard((px, py))
    return rounded, x - gap, 7 * cell


def draw_word(img, mask, ox, oy, height, depth):
    """Extruded side first, then the lit face on top."""
    px = img.load()
    face = {(x + ox, y + oy) for (x, y) in mask}
    for d in range(depth, 0, -1):
        t = d / depth
        tone = blend(SIDE_TOP, SIDE_BOTTOM, t)
        for (x, y) in face:
            if (x, y + d) not in face:
                px[x, y + d] = tone
    for (x, y) in face:
        t = (y - oy) / max(1, height - 1)
        tone = gradient(t)
        if (x, y - 1) not in face:
            tone = EDGE_LIGHT
        elif (x, y + 1) not in face:
            tone = EDGE_DARK
        elif (x - 1, y) not in face:
            tone = blend(tone, EDGE_LIGHT, 0.45)
        elif (x + 1, y) not in face:
            tone = blend(tone, EDGE_DARK, 0.35)
        px[x, y] = tone


def add_outline(img, thickness):
    px = img.load()
    w, h = img.size
    filled = {(x, y) for y in range(h) for x in range(w) if px[x, y][3]}
    ring = set()
    for (x, y) in filled:
        for dy in range(-thickness, thickness + 1):
            for dx in range(-thickness, thickness + 1):
                if abs(dx) + abs(dy) <= thickness + 1 and (x + dx, y + dy) not in filled:
                    ring.add((x + dx, y + dy))
    for (x, y) in ring:
        if 0 <= x < w and 0 <= y < h:
            px[x, y] = OUTLINE


# ------------------------------------------------------------------ logos

BIG = dict(cell=4, gap=3, depth=5)
SMALL = dict(cell=3, gap=2, depth=4)
OUTLINE_PX = 2

LOGOS = {
    # id: [(text, size), ...] top to bottom
    "holo_shop": [("SHOP", BIG)],
    "holo_coinflip": [("COINFLIP", BIG)],
    "holo_quests": [("QUESTS", BIG)],
    "holo_black_market": [("BLACK", BIG), ("MARKET", SMALL)],
    "holo_kill_streaks": [("KILL", BIG), ("STREAKS", SMALL)],
    "holo_media_rank": [("MEDIA", BIG), ("RANK", SMALL)],
    "holo_kits": [("KITS", BIG)],
    "holo_trash_bin": [("TRASH", BIG), ("BIN", SMALL)],
    "holo_booster": [("BOOSTER", BIG), ("REWARDS", SMALL)],
}


def render(lines):
    pad = OUTLINE_PX + 1
    words = []
    for text, size in lines:
        mask, w, h = word_mask(text, size['cell'], size['gap'])
        words.append((mask, w, h, size))
    width = max(w for (_, w, _, _) in words) + pad * 2
    line_gap = 3
    height = sum(h + s['depth'] for (_, _, h, s) in words) + line_gap * (len(words) - 1) + pad * 2
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    y = pad
    for (mask, w, h, size) in words:
        draw_word(img, mask, (width - w) // 2, y, h, size['depth'])
        y += h + size['depth'] + line_gap
    add_outline(img, OUTLINE_PX)
    return img.crop(img.getbbox())


def backdrop(width, height):
    """A plain Minecraft-ish sky over grass, for previews."""
    img = Image.new("RGBA", (width, height))
    px = img.load()
    sky_top, sky_bottom = hexc('#6FA8FF'), hexc('#BFDCFF')
    ground = height - height // 5
    for y in range(height):
        for x in range(width):
            if y < ground:
                px[x, y] = blend(sky_top, sky_bottom, y / ground)
            elif y < ground + 3:
                px[x, y] = hexc('#5DA130') if (x // 3 + y) % 4 else hexc('#4C8A26')
            else:
                px[x, y] = hexc('#8A5A36') if (x * 7 + y * 3) % 11 else hexc('#6E4527')
    return img


def preview(logo, scale=4):
    w, h = logo.width + 24, logo.height + 24
    bg = backdrop(w, h + 10)
    bg.alpha_composite(logo, (12, 10))
    return bg.resize((bg.width * scale, bg.height * scale), Image.NEAREST)


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(root, "holograms")
    out_preview = os.path.join(root, "previews")
    os.makedirs(out, exist_ok=True)
    os.makedirs(out_preview, exist_ok=True)
    shots = []
    sizes = {}
    for name, lines in LOGOS.items():
        img = render(lines)
        img.save(os.path.join(out, name + ".png"))
        shots.append(preview(img, scale=3))
        sizes[name] = img.size
    contact_sheet(shots, columns=2).save(os.path.join(out_preview, "all_holograms.png"))
    return sizes


if __name__ == "__main__":
    for name, (w, h) in main().items():
        print(f"{name}: {w}x{h}")
