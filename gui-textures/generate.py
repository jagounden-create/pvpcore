#!/usr/bin/env python3
"""
Themed Nexo GUI textures for chest menus, drawn as pixel art at Minecraft's GUI scale.

Every texture is 176 px wide (a chest GUI) and starts TOP px above the GUI so its sign can
stick out over the top edge. Slot cubbies are drawn exactly where the menu puts its items:
slot (row r, column c) has its frame at x = 7 + 18c, y = TOP + 17 + 18r.

Nexo glyph for a texture of height H:  ascent: 13 + TOP   height: H
Menu title:                            <white><shift:-8><glyph:ID>
"""
import json
import os
import random
from PIL import Image

W = 176
TOP = 10          # rows of art above the GUI's top edge (the sign)
HEADER = 17       # vanilla title bar height
PITCH = 18
LEFT = 7
BOTTOM_PAD = 3

# ------------------------------------------------------------------ canvas helpers


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


class Canvas:
    def __init__(self, width, height, seed):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = width, height
        self.rng = random.Random(seed)

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            if len(c) == 4 and c[3] < 255:
                base = self.px[x, y]
                a = c[3] / 255.0
                c = tuple(int(base[i] * (1 - a) + c[i] * a) for i in range(3)) + (max(base[3], c[3]),)
            self.px[x, y] = c

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1 + 1):
            self.put(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1 + 1):
            self.put(x, y, c)

    def outline(self, x0, y0, x1, y1, c, round_corners=False):
        self.hline(x0 + (1 if round_corners else 0), x1 - (1 if round_corners else 0), y0, c)
        self.hline(x0 + (1 if round_corners else 0), x1 - (1 if round_corners else 0), y1, c)
        self.vline(x0, y0 + (1 if round_corners else 0), y1 - (1 if round_corners else 0), c)
        self.vline(x1, y0 + (1 if round_corners else 0), y1 - (1 if round_corners else 0), c)

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = (0, 0, 0, 0)


class Ramp:
    """Five tones of one material, darkest (outline) to brightest (highlight)."""

    def __init__(self, outline, dark, mid, light, high):
        self.outline, self.dark, self.mid, self.light, self.high = (hexc(v) for v in (outline, dark, mid, light, high))


# ------------------------------------------------------------------ materials


def blend(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


def planks(cv, x0, y0, x1, y1, ramp, board=9, vertical=True, grain=0.05):
    """Wooden boards with gaps, per-board tone, grain streaks and a lit top-left edge."""
    span = (x1 - x0 + 1) if vertical else (y1 - y0 + 1)
    boards = max(1, round(span / board))
    edges = [x0 + round(i * (x1 - x0 + 1) / boards) for i in range(boards + 1)] if vertical else \
        [y0 + round(i * (y1 - y0 + 1) / boards) for i in range(boards + 1)]
    for b in range(boards):
        tone = ramp.mid if cv.rng.random() < 0.6 else ramp.light
        a, z = edges[b], edges[b + 1] - 1
        if vertical:
            cv.rect(a, y0, z, y1, tone)
            cv.vline(z, y0, y1, ramp.dark)
            cv.vline(a, y0, y1, ramp.light if tone == ramp.mid else ramp.high)
            for x in range(a + 1, z):
                y = y0
                while y <= y1:
                    if cv.rng.random() < grain:
                        length = cv.rng.randint(3, 8)
                        streak = blend(tone, ramp.dark, 0.55) if cv.rng.random() < 0.7 else blend(tone, ramp.high, 0.35)
                        cv.vline(x, y, min(y1, y + length - 1), streak)
                        y += length + 3
                    y += 1
            # a knot now and then
            if cv.rng.random() < 0.35 and z - a >= 5 and y1 - y0 >= 8:
                kx, ky = cv.rng.randint(a + 2, z - 2), cv.rng.randint(y0 + 2, y1 - 2)
                cv.put(kx, ky, ramp.dark)
                cv.put(kx + 1, ky, ramp.outline)
        else:
            cv.rect(x0, a, x1, z, tone)
            cv.hline(x0, x1, z, ramp.dark)
            cv.hline(x0, x1, a, ramp.light if tone == ramp.mid else ramp.high)
            for y in range(a + 1, z):
                x = x0
                while x <= x1:
                    if cv.rng.random() < grain:
                        length = cv.rng.randint(3, 9)
                        streak = blend(tone, ramp.dark, 0.55) if cv.rng.random() < 0.7 else blend(tone, ramp.high, 0.35)
                        cv.hline(x, min(x1, x + length - 1), y, streak)
                        x += length + 3
                    x += 1


def bevel_box(cv, x0, y0, x1, y1, ramp, fill=None):
    """Raised block: dark outline, light top-left, shaded bottom-right."""
    if fill is not None:
        cv.rect(x0, y0, x1, y1, fill)
    cv.outline(x0, y0, x1, y1, ramp.outline)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, ramp.high)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, ramp.light)
    cv.hline(x0 + 1, x1 - 1, y1 - 1, ramp.dark)
    cv.vline(x1 - 1, y0 + 2, y1 - 1, ramp.dark)


def cubby(cv, x, y, frame, inner):
    """An 18x18 slot: raised frame around a 16x16 recess where the item sits."""
    cv.rect(x, y, x + 17, y + 17, frame.mid)
    cv.outline(x, y, x + 17, y + 17, frame.outline)
    cv.hline(x + 1, x + 16, y + 1, frame.light)
    cv.vline(x + 1, y + 1, y + 16, frame.light)
    # recess
    cv.rect(x + 2, y + 2, x + 15, y + 15, inner.mid)
    cv.hline(x + 2, x + 15, y + 2, inner.outline)
    cv.vline(x + 2, y + 2, y + 15, inner.outline)
    cv.hline(x + 3, x + 15, y + 3, inner.dark)
    cv.vline(x + 3, y + 3, y + 15, inner.dark)
    cv.hline(x + 3, x + 15, y + 15, inner.light)
    cv.vline(x + 15, y + 3, y + 15, inner.light)


def slot_xy(slot):
    row, col = divmod(slot, 9)
    return LEFT + col * PITCH, TOP + HEADER + row * PITCH


def row_span(slots):
    xs = [slot_xy(s)[0] for s in slots]
    ys = [slot_xy(s)[1] for s in slots]
    return min(xs), min(ys), max(xs) + 17, max(ys) + 17


# ------------------------------------------------------------------ lettering

FONT = {
 'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
 'B': ["####.", "#...#", "####.", "#...#", "#...#", "#...#", "####."],
 'C': [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
 'D': ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
 'E': ["#####", "#....", "####.", "#....", "#....", "#....", "#####"],
 'F': ["#####", "#....", "####.", "#....", "#....", "#....", "#...."],
 'G': [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
 'H': ["#...#", "#...#", "#####", "#...#", "#...#", "#...#", "#...#"],
 'I': ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
 'K': ["#...#", "#..#.", "###..", "#..#.", "#...#", "#...#", "#...#"],
 'L': ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
 'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"],
 'N': ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
 'O': [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
 'P': ["####.", "#...#", "####.", "#....", "#....", "#....", "#...."],
 'Q': [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
 'R': ["####.", "#...#", "####.", "#...#", "#...#", "#...#", "#...#"],
 'S': [".####", "#....", ".###.", "....#", "....#", "#...#", ".###."],
 'T': ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
 'U': ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
 'V': ["#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."],
 'W': ["#...#", "#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#"],
 'Y': ["#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..", "..#.."],
 ' ': ["...", "...", "...", "...", "...", "...", "..."],
}


def bold_glyph(ch):
    """Each lit pixel also lights its right neighbour: chunky sign lettering like carved wood."""
    rows = FONT[ch]
    width = len(rows[0]) + (1 if ch != ' ' else 0)
    out = []
    for line in rows:
        cells = [False] * width
        for i, p in enumerate(line):
            if p == '#':
                cells[i] = True
                if i + 1 < width:
                    cells[i + 1] = True
        out.append(cells)
    return out


def text_width(text, bold=True):
    total = 0
    for ch in text:
        total += (len(FONT[ch][0]) + (1 if bold and ch != ' ' else 0)) + 1
    return total - 1


def lettering(cv, x, y, text, face, outline, highlight=None, bold=True):
    """Sign letters: a dark outline all round, a face colour, and a lit top edge."""
    cells = set()
    cx = x
    for ch in text:
        glyph = bold_glyph(ch) if bold else [[p == '#' for p in line] for line in FONT[ch]]
        for gy, line in enumerate(glyph):
            for gx, on in enumerate(line):
                if on:
                    cells.add((cx + gx, y + gy))
        cx += len(glyph[0]) + 1
    for (px, py) in cells:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if (px + dx, py + dy) not in cells:
                    cv.put(px + dx, py + dy, outline)
        if (px, py + 2) not in cells and (px, py + 1) not in cells:
            cv.put(px, py + 2, outline)
    for (px, py) in cells:
        lit = highlight is not None and (px, py - 1) not in cells
        cv.put(px, py, highlight if lit else face)


def sign(cv, cx, y0, text, board, face, shadow, highlight=None, pad=8, height=17):
    width = text_width(text) + pad * 2
    x0 = cx - width // 2
    x1 = x0 + width - 1
    y1 = y0 + height - 1
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, board.mid)
    planks(cv, x0 + 2, y0 + 2, x1 - 2, y1 - 2, board, board=(y1 - y0 - 3) // 2, vertical=False, grain=0.03)
    cv.outline(x0, y0, x1, y1, board.outline, round_corners=True)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, board.high)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, board.light)
    cv.hline(x0 + 1, x1 - 1, y1 - 1, board.dark)
    cv.vline(x1 - 1, y0 + 2, y1 - 1, board.dark)
    # inner frame
    cv.outline(x0 + 2, y0 + 2, x1 - 2, y1 - 2, board.dark, round_corners=True)
    lettering(cv, cx - text_width(text) // 2, y0 + (height - 7) // 2 - 1, text, face, shadow, highlight)
    return x0, x1, y1


# ------------------------------------------------------------------ shared structure


def body_and_posts(cv, wall, post, y_top, y_bottom):
    """A plank wall across the GUI with a sturdy post at each side."""
    planks(cv, 2, y_top, W - 3, y_bottom, wall, board=9, vertical=True)
    for (a, z) in ((0, 5), (W - 6, W - 1)):
        cv.rect(a, y_top, z, y_bottom, post.mid)
        cv.vline(a + 1, y_top, y_bottom, post.light)
        cv.vline(z - 1, y_top, y_bottom, post.dark)
        cv.vline(a, y_top, y_bottom, post.outline)
        cv.vline(z, y_top, y_bottom, post.outline)
    cv.hline(0, W - 1, y_bottom, wall.outline)


def display(cv, slots, frame, inner, box, shelf=True, overhang=3, pad=3):
    """A recessed display box holding a run of cubbies, with a ledge under it."""
    x0, y0, x1, y1 = row_span(slots)
    bx0, by0, bx1, by1 = x0 - pad, y0 - pad, x1 + pad, y1 + pad
    cv.rect(bx0, by0, bx1, by1, box.dark)
    cv.outline(bx0, by0, bx1, by1, box.outline)
    cv.hline(bx0 + 1, bx1 - 1, by0 + 1, box.outline)
    cv.hline(bx0 + 1, bx1 - 1, by1 - 1, box.light)
    for s in slots:
        cubby(cv, *slot_xy(s), frame, inner)
    if shelf:
        lx0, lx1 = bx0 - overhang, bx1 + overhang
        cv.rect(lx0, by1 + 1, lx1, by1 + 3, box.light)
        cv.hline(lx0, lx1, by1 + 1, box.high)
        cv.hline(lx0, lx1, by1 + 3, box.dark)
        cv.outline(lx0, by1, lx1, by1 + 4, box.outline)


# ------------------------------------------------------------------ palettes

OAK = Ramp('#2B1810', '#5B3421', '#744429', '#8E5635', '#A96A42')
DARK_OAK = Ramp('#22130C', '#3E2416', '#4E2E1C', '#653D27', '#7A4C31')
TAN = Ramp('#3A2518', '#8B6547', '#A7805E', '#C09A76', '#D6B592')
RECESS = Ramp('#1C110B', '#2A1911', '#3A2418', '#4A3020', '#5A3A27')
BOX = Ramp('#22140D', '#3C2317', '#4E2E1C', '#5B3824', '#7A4C31')
IRON = Ramp('#1E1F24', '#4A4D57', '#6B6F7A', '#8E929D', '#B4B8C2')
STONE_RECESS = Ramp('#141518', '#1F2126', '#2A2D33', '#363940', '#44474F')


def awning(cv, y0, y1, stripe_a, stripe_b, outline, width=8, hem='scallop'):
    """Striped cloth valance: lit top edge, shading down the cloth, a scalloped (or ragged) hem,
    and the shadow it casts on the wall below."""
    rng = cv.rng
    for x in range(0, W):
        local = x % width
        ramp = stripe_a if (x // width) % 2 == 0 else stripe_b
        for y in range(y0 + 1, y1 + 1):
            t = (y - y0) / max(1, y1 - y0)
            tone = ramp[2] if t < 0.2 else ramp[1] if t < 0.75 else blend(ramp[1], ramp[0], 0.6)
            cv.put(x, y, tone)
        if hem == 'scallop':
            depth = 3 if 2 <= local <= width - 3 else 2 if local in (1, width - 2) else 1
        else:
            depth = rng.choice((1, 2, 2, 3, 4))
        for y in range(y1 + 1, y1 + depth):
            cv.put(x, y, blend(ramp[1], ramp[0], 0.7))
        cv.put(x, y1 + depth, outline)
        if local == 0:
            cv.vline(x, y0 + 1, y1, blend(ramp[1], outline, 0.3))
        for y in range(y1 + depth + 1, y1 + depth + 3):
            if 0 <= y < cv.h and cv.px[x, y][3]:
                cv.put(x, y, blend(cv.px[x, y], (0, 0, 0, 255), 0.45 if y == y1 + depth + 1 else 0.22))
    cv.hline(0, W - 1, y0, outline)


def roof_beam(cv, y, ramp, overhang=0):
    x0, x1 = -overhang, W - 1 + overhang
    cv.rect(x0, y, x1, y + 3, ramp.mid)
    cv.hline(x0, x1, y, ramp.outline)
    cv.hline(x0, x1, y + 1, ramp.high)
    cv.hline(x0, x1, y + 2, ramp.light)
    cv.hline(x0, x1, y + 3, ramp.dark)
    cv.hline(x0, x1, y + 4, ramp.outline)
    for x in range(4, W - 4, 22):
        cv.put(x, y + 2, ramp.dark)


# ------------------------------------------------------------------ more materials


def bricks(cv, x0, y0, x1, y1, ramp, bw=10, bh=5, jitter=0.35):
    """Staggered bricks with mortar, per-brick tone and a lit top edge."""
    row = 0
    y = y0
    while y <= y1:
        offset = 0 if row % 2 == 0 else bw // 2
        x = x0 - offset
        while x <= x1:
            tone = ramp.mid if cv.rng.random() > jitter else (ramp.light if cv.rng.random() < 0.5 else ramp.dark)
            bx0, bx1 = max(x0, x), min(x1, x + bw - 2)
            by1 = min(y1, y + bh - 2)
            cv.rect(bx0, y, bx1, by1, tone)
            cv.hline(bx0, bx1, y, blend(tone, ramp.high, 0.45))
            cv.hline(bx0, bx1, by1, blend(tone, ramp.outline, 0.35))
            if cv.rng.random() < 0.25:
                cv.put(cv.rng.randint(bx0, max(bx0, bx1)), cv.rng.randint(y, by1), blend(tone, ramp.outline, 0.5))
            x += bw
        cv.hline(x0, x1, min(y1, y + bh - 1), ramp.outline)
        # vertical mortar
        x = x0 - offset
        while x <= x1:
            if x + bw - 1 <= x1 and x + bw - 1 >= x0:
                cv.vline(x + bw - 1, y, min(y1, y + bh - 1), ramp.outline)
            x += bw
        y += bh
        row += 1


def speckle(cv, x0, y0, x1, y1, base, dots, density=0.18):
    cv.rect(x0, y0, x1, y1, base)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if cv.rng.random() < density:
                cv.put(x, y, cv.rng.choice(dots))


def glow(cv, cx, cy, radius, color, strength=0.55):
    """Soft light: blended over what is already drawn."""
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= radius and 0 <= x < cv.w and 0 <= y < cv.h and cv.px[x, y][3]:
                a = strength * (1 - d / radius) ** 1.6
                if a > 0.02:
                    cv.put(x, y, blend(cv.px[x, y], color, a))


def flame(cv, x, y, big=False):
    """A small torch flame, tip at (x, y)."""
    shape = [
        "..y..",
        ".yy..",
        ".yoy.",
        "yooy.",
        "yorry",
        ".orr.",
    ] if not big else [
        "...y...",
        "..yy...",
        "..yoy..",
        ".yoooy.",
        ".yorroy",
        "yoorrry",
        ".orrrr.",
        "..rrr..",
    ]
    colors = {'y': hexc('#FFE27A'), 'o': hexc('#FFA53A'), 'r': hexc('#E4572E')}
    for dy, line in enumerate(shape):
        for dx, ch in enumerate(line):
            if ch in colors:
                cv.put(x - len(line) // 2 + dx, y + dy, colors[ch])


def torch(cv, x, y):
    """Wall torch: flame on a short stick, with a warm glow."""
    glow(cv, x, y + 3, 11, hexc('#FF9A3C'), 0.5)
    cv.vline(x, y + 6, y + 12, hexc('#6B4424'))
    cv.vline(x + 1, y + 6, y + 12, hexc('#4A2E17'))
    cv.put(x, y + 13, hexc('#2A170D'))
    cv.put(x + 1, y + 13, hexc('#2A170D'))
    flame(cv, x + 1, y)


def lantern(cv, x, y, light, chain_top):
    """Hanging lantern on a chain, with a coloured glow."""
    for cy in range(chain_top, y):
        cv.put(x + 2, cy, hexc('#3B3E46') if (cy - chain_top) % 2 == 0 else hexc('#23252B'))
    glow(cv, x + 2, y + 4, 10, light, 0.55)
    iron_o, iron = hexc('#16171B'), hexc('#4B4F5A')
    cv.rect(x, y, x + 4, y + 7, iron)
    cv.outline(x, y, x + 4, y + 7, iron_o)
    cv.rect(x + 1, y + 2, x + 3, y + 5, light)
    cv.put(x + 2, y + 3, blend(light, (255, 255, 255, 255), 0.6))
    cv.hline(x + 1, x + 3, y + 1, iron_o)


def note(cv, x0, y0, x1, y1, pin, lines=True, seed_shift=0):
    """A pinned parchment note with torn corners and scribbled lines."""
    paper, edge, ink = hexc('#EADCB6'), hexc('#C8B283'), hexc('#8C7650')
    cv.rect(x0, y0, x1, y1, paper)
    cv.outline(x0, y0, x1, y1, hexc('#6D5537'))
    cv.hline(x0 + 1, x1 - 1, y1 - 1, edge)
    cv.vline(x1 - 1, y0 + 1, y1 - 1, edge)
    cv.put(x1, y0, hexc('#6D5537'))
    cv.put(x1 - 1, y0 + 1, edge)
    cv.put(x0, y1, hexc('#6D5537'))
    if lines:
        for ly in range(y0 + 5, y1 - 2, 3):
            length = cv.rng.randint((x1 - x0) // 2, x1 - x0 - 4)
            cv.hline(x0 + 3, x0 + 3 + length - 1, ly, ink)
    px = (x0 + x1) // 2
    cv.put(px, y0, pin)
    cv.put(px, y0 + 1, blend(pin, (0, 0, 0, 255), 0.4))
    cv.put(px - 1, y0, blend(pin, (255, 255, 255, 255), 0.4))


def pinned_cubby(cv, x, y, pin):
    """Quest slot: a parchment square pinned to the board, the item sits on the paper."""
    cv.rect(x - 1, y - 1, x + 18, y + 18, (0, 0, 0, 70))
    note(cv, x, y, x + 17, y + 17, pin, lines=False)
    cv.outline(x + 2, y + 3, x + 15, y + 15, hexc('#D4C193'))


def curtain(cv, x0, x1, y0, y1, left_side, ramp):
    """Velvet curtain gathered towards the outer edge, with folds and a tie-back."""
    width = x1 - x0 + 1
    for y in range(y0, y1 + 1):
        t = (y - y0) / max(1, y1 - y0)
        # gathered around 60% height, flaring at the bottom
        pinch = 1.0 - 0.45 * max(0.0, 1 - abs(t - 0.6) / 0.25) if t < 0.85 else 0.75 + (t - 0.85)
        span = max(4, int(width * min(1.0, pinch)))
        for i in range(span):
            x = x0 + i if left_side else x1 - i
            fold = (i * 7 // max(1, span)) % 3
            tone = ramp.light if fold == 0 else ramp.mid if fold == 1 else ramp.dark
            if i == span - 1:
                tone = ramp.outline
            cv.put(x, y, tone)
    tie_y = y0 + int((y1 - y0) * 0.6)
    t = (tie_y - y0) / max(1, y1 - y0)
    span = max(4, int(width * (1.0 - 0.45 * max(0.0, 1 - abs(t - 0.6) / 0.25))))
    for i in range(span):
        x = x0 + i if left_side else x1 - i
        cv.put(x, tie_y, hexc('#E3B341'))
        cv.put(x, tie_y + 1, hexc('#9C7422'))
    tassel = x0 + span - 1 if left_side else x1 - span + 1
    cv.vline(tassel, tie_y, tie_y + 4, hexc('#E3B341'))
    cv.put(tassel, tie_y + 5, hexc('#9C7422'))


def neon_lettering(cv, x, y, text, color):
    """Neon tube letters: soft glow, a coloured tube and a white-hot core."""
    cells = set()
    cx = x
    for ch in text:
        glyph = [[p == '#' for p in line] for line in FONT[ch]]
        for gy, line in enumerate(glyph):
            for gx, on in enumerate(line):
                if on:
                    cells.add((cx + gx, y + gy))
        cx += len(FONT[ch][0]) + 1
    for (px, py) in cells:
        for dx in (-2, -1, 0, 1, 2):
            for dy in (-2, -1, 0, 1, 2):
                q = (px + dx, py + dy)
                if q not in cells and 0 <= q[0] < cv.w and 0 <= q[1] < cv.h and cv.px[q][3]:
                    a = 0.32 if max(abs(dx), abs(dy)) == 1 else 0.12
                    cv.put(q[0], q[1], blend(cv.px[q], color, a))
    for (px, py) in cells:
        cv.put(px, py, blend(color, (255, 255, 255, 255), 0.55))


def neon_width(text):
    return sum(len(FONT[ch][0]) + 1 for ch in text) - 1


# ------------------------------------------------------------------ themes


def theme_shop(rows, slots, nav, title):
    height = TOP + HEADER + rows * PITCH + BOTTOM_PAD
    cv = Canvas(W, height, seed=11)
    wall_top = TOP + 6
    body_and_posts(cv, OAK, DARK_OAK, wall_top, height - 1)
    green = (hexc('#2F6B3A'), hexc('#46A155'), hexc('#5DBB6B'))
    cream = (hexc('#BFB08A'), hexc('#E2D6B3'), hexc('#F0E7CC'))
    roof_beam(cv, TOP - 1, TAN)
    awning(cv, TOP + 3, TOP + 11, green, cream, hexc('#1C3A22'))
    display(cv, slots, TAN, RECESS, BOX)
    # counter front under the shelf
    x0, y0, x1, y1 = row_span(slots)
    front_top = y1 + 3 + 5
    if front_top < height - 2:
        planks(cv, x0 - 6, front_top, x1 + 6, height - 2, BOX, board=4, vertical=False, grain=0.1)
        cv.outline(x0 - 6, front_top - 1, x1 + 6, height - 1, BOX.outline)
    for s in nav:
        cubby(cv, *slot_xy(s), IRON, STONE_RECESS)
    sign(cv, W // 2, 0, title, TAN, hexc('#F6E9CF'), hexc('#2A170D'), hexc('#FFFFFF'))
    return cv.img


def frame_bottom(cv, height, ramp):
    """A skirting board along the bottom edge."""
    cv.rect(0, height - 4, W - 1, height - 1, ramp.mid)
    cv.hline(0, W - 1, height - 4, ramp.outline)
    cv.hline(0, W - 1, height - 3, ramp.light)
    cv.hline(0, W - 1, height - 1, ramp.outline)


PURPLE_CLOTH = (hexc('#3A1A5C'), hexc('#5B2A8C'), hexc('#7A3FB8'))
PURPLE_CLOTH_DARK = (hexc('#261038'), hexc('#3B1C5C'), hexc('#512A7A'))
BLACKSTONE = Ramp('#0E0C10', '#221E26', '#2E2933', '#3B3542', '#4B4454')
GOLD = Ramp('#3A2608', '#8C6414', '#B8861E', '#DDAE3A', '#F3D27A')
SPRUCE = Ramp('#1E140C', '#3E2A19', '#503721', '#65462B', '#7C5736')
SPRUCE_LIGHT = Ramp('#2A1C10', '#6B4B2E', '#80593A', '#976C47', '#AD8157')
NETHER_BRICK = Ramp('#120708', '#2A1114', '#38171B', '#471E23', '#5A282E')
STAGE_WALL = Ramp('#0B0710', '#170F22', '#1F142D', '#2A1B3C', '#38264F')
VELVET = Ramp('#3A0610', '#6E0F22', '#8E1830', '#B02643', '#C9405B')
STAGE_FLOOR = Ramp('#1C110B', '#4A2C1A', '#5E3A22', '#744A2C', '#8B5A36')


def theme_black_market(rows, slots, nav, title):
    height = TOP + HEADER + rows * PITCH + BOTTOM_PAD
    cv = Canvas(W, height, seed=23)
    body_and_posts(cv, DARK_OAK, BLACKSTONE, TOP + 6, height - 1)
    # dim the wall towards the edges: it is a shady stall
    for y in range(cv.h):
        for x in range(cv.w):
            if cv.px[x, y][3]:
                edge = min(x, W - 1 - x) / (W / 2)
                cv.put(x, y, blend(cv.px[x, y], (6, 4, 10, 255), 0.35 * (1 - edge)))
    roof_beam(cv, TOP - 1, BLACKSTONE)
    awning(cv, TOP + 3, TOP + 10, PURPLE_CLOTH, PURPLE_CLOTH_DARK, hexc('#12081C'), width=7, hem='ragged')
    for run in ([10], [12, 13, 14], [16]):
        chosen = [s for s in run if s in slots]
        if chosen:
            display(cv, chosen, BLACKSTONE, STONE_RECESS, BOX, shelf=False, pad=2)
    lantern(cv, 49, TOP + 20, hexc('#9B6BFF'), TOP + 12)
    lantern(cv, 122, TOP + 20, hexc('#9B6BFF'), TOP + 12)
    for s in nav:
        cubby(cv, *slot_xy(s), IRON, STONE_RECESS)
    sign(cv, W // 2, 0, title, BLACKSTONE, hexc('#D9B8FF'), hexc('#0E0714'), hexc('#FFFFFF'))
    glow(cv, W // 2, 8, 30, hexc('#8E4CFF'), 0.18)
    return cv.img


def theme_quests(rows, slots, nav, title):
    height = TOP + HEADER + rows * PITCH + BOTTOM_PAD
    cv = Canvas(W, height, seed=37)
    # spruce frame around a cork board
    cv.rect(0, TOP + 4, W - 1, height - 1, SPRUCE.mid)
    speckle(cv, 6, TOP + 10, W - 7, height - 6, hexc('#A9774A'),
            [hexc('#8E6139'), hexc('#C08B5A'), hexc('#7A5230'), hexc('#B98553')], density=0.28)
    for (a, b) in ((0, 5), (W - 6, W - 1)):
        planks(cv, a, TOP + 4, b, height - 1, SPRUCE, board=6)
        cv.vline(a, TOP + 4, height - 1, SPRUCE.outline)
        cv.vline(b, TOP + 4, height - 1, SPRUCE.outline)
    planks(cv, 0, height - 5, W - 1, height - 1, SPRUCE, board=5, vertical=False)
    cv.outline(0, height - 6, W - 1, height - 1, SPRUCE.outline)
    cv.outline(5, TOP + 9, W - 6, height - 6, SPRUCE.outline)
    # little shingled roof
    for y in range(TOP - 2, TOP + 9):
        inset = max(0, TOP - y)
        for x in range(inset * 2, W - inset * 2):
            shade = SPRUCE_LIGHT.light if (y - TOP) % 3 == 0 else SPRUCE_LIGHT.mid
            if (x + (y // 3) * 4) % 8 == 0:
                shade = SPRUCE_LIGHT.dark
            cv.put(x, y, shade)
    cv.hline(0, W - 1, TOP + 8, SPRUCE.outline)
    cv.hline(0, W - 1, TOP + 9, blend(SPRUCE.outline, (0, 0, 0, 255), 0.3))
    # decorative notes that are not buttons
    note(cv, 14, TOP + 21, 34, TOP + 39, hexc('#3F7FD9'))
    note(cv, 140, TOP + 18, 160, TOP + 34, hexc('#4CAF50'))
    note(cv, 22, TOP + 48, 44, TOP + 64, hexc('#E0A020'))
    note(cv, 126, TOP + 44, 150, TOP + 62, hexc('#C0392B'))
    for s in slots:
        pinned_cubby(cv, *slot_xy(s), hexc('#D93A3A'))
    for s in nav:
        cubby(cv, *slot_xy(s), SPRUCE, RECESS)
    sign(cv, W // 2, 0, title, SPRUCE_LIGHT, hexc('#F7D774'), hexc('#2A1A0C'), hexc('#FFF4C4'))
    return cv.img


def theme_kill_streaks(rows, slots, nav, title):
    height = TOP + HEADER + rows * PITCH + BOTTOM_PAD
    cv = Canvas(W, height, seed=41)
    cv.rect(0, TOP + 4, W - 1, height - 1, NETHER_BRICK.outline)
    bricks(cv, 1, TOP + 5, W - 2, height - 2, NETHER_BRICK, bw=9, bh=4)
    # blackstone pillars
    for (a, b) in ((0, 7), (W - 8, W - 1)):
        bricks(cv, a, TOP + 4, b, height - 1, BLACKSTONE, bw=8, bh=6, jitter=0.2)
        cv.vline(a, TOP + 4, height - 1, BLACKSTONE.outline)
        cv.vline(b, TOP + 4, height - 1, BLACKSTONE.outline)
    # capstone
    cv.rect(0, TOP - 1, W - 1, TOP + 5, BLACKSTONE.mid)
    cv.hline(0, W - 1, TOP - 1, BLACKSTONE.outline)
    cv.hline(0, W - 1, TOP, BLACKSTONE.high)
    cv.hline(0, W - 1, TOP + 4, BLACKSTONE.dark)
    cv.hline(0, W - 1, TOP + 5, BLACKSTONE.outline)
    for x in range(3, W - 3, 12):
        cv.put(x, TOP + 2, GOLD.light)
    # embers glowing up from the bottom
    for x in range(8, W - 8):
        for y in range(height - 14, height - 1):
            t = (y - (height - 14)) / 13
            cv.put(x, y, blend(cv.px[x, y], hexc('#FF6A1F'), 0.28 * t * t))
    display(cv, slots, GOLD, STONE_RECESS, BLACKSTONE, shelf=False, pad=3)
    torch(cv, 17, TOP + 26)
    torch(cv, W - 19, TOP + 26)
    for s in nav:
        cubby(cv, *slot_xy(s), IRON, STONE_RECESS)
    sign(cv, W // 2, 0, title, BLACKSTONE, hexc('#FFB347'), hexc('#1A0A04'), hexc('#FFE9A8'))
    return cv.img


def theme_media_rank(rows, slots, nav, title):
    height = TOP + HEADER + rows * PITCH + BOTTOM_PAD
    cv = Canvas(W, height, seed=53)
    cv.rect(0, TOP + 4, W - 1, height - 1, STAGE_WALL.mid)
    planks(cv, 0, TOP + 4, W - 1, height - 1, STAGE_WALL, board=11)
    # spotlights from the top onto the display
    for (sx, tx) in ((40, 70), (136, 106)):
        for y in range(TOP + 8, height - 8):
            t = (y - TOP - 8) / (height - TOP - 16)
            cx = sx + (tx - sx) * t
            half = 3 + 16 * t
            for x in range(int(cx - half), int(cx + half) + 1):
                if 0 <= x < W and cv.px[x, y][3]:
                    cv.put(x, y, blend(cv.px[x, y], hexc('#FFE4F4'), 0.10 + 0.05 * (1 - t)))
    # stage floor
    floor_top = height - 12
    planks(cv, 0, floor_top, W - 1, height - 1, STAGE_FLOOR, board=4, vertical=False)
    cv.hline(0, W - 1, floor_top - 1, STAGE_FLOOR.outline)
    cv.hline(0, W - 1, floor_top, STAGE_FLOOR.high)
    curtain(cv, 0, 26, TOP + 4, height - 2, True, VELVET)
    curtain(cv, W - 27, W - 1, TOP + 4, height - 2, False, VELVET)
    # valance with gold fringe
    cv.rect(0, TOP - 1, W - 1, TOP + 6, VELVET.mid)
    for x in range(W):
        fold = (x // 5) % 2
        cv.vline(x, TOP, TOP + 6, VELVET.light if fold else VELVET.mid)
        if x % 10 < 5:
            cv.put(x, TOP + 7, VELVET.dark)
        cv.put(x, TOP + 8 if x % 2 == 0 else TOP + 7, GOLD.light)
    cv.hline(0, W - 1, TOP - 1, VELVET.outline)
    display(cv, slots, Ramp('#2A0C1E', '#7A2458', '#9C3272', '#C04A92', '#E27BB6'), STONE_RECESS, STAGE_WALL, shelf=False, pad=3)
    for s in nav:
        cubby(cv, *slot_xy(s), IRON, STONE_RECESS)
    # neon sign on a dark board
    width = neon_width(title) + 18
    x0 = W // 2 - width // 2
    cv.rect(x0, 0, x0 + width - 1, 16, hexc('#120A18'))
    cv.outline(x0, 0, x0 + width - 1, 16, hexc('#050308'), round_corners=True)
    cv.hline(x0 + 1, x0 + width - 2, 1, hexc('#2A1B36'))
    neon_lettering(cv, W // 2 - neon_width(title) // 2, 5, title, hexc('#FF4FB5'))
    return cv.img


THEMES = {
    "shop": theme_shop,
    "black_market": theme_black_market,
    "quests": theme_quests,
    "kill_streaks": theme_kill_streaks,
    "media_rank": theme_media_rank,
}

MENUS = {
    # id: theme, rows, button slots, back/close slots, sign text
    "quests_gui": ("quests", 3, [12, 14], [], "QUESTS"),
    "black_market_gui": ("black_market", 3, [10, 12, 13, 14, 16], [26], "BLACK MARKET"),
    "shop_gui": ("shop", 3, [11, 12, 13, 14, 15], [26], "SHOP"),
    "kill_streaks_gui": ("kill_streaks", 4, [12, 13, 14, 21, 22, 23], [], "KILL STREAKS"),
    "media_rank_gui": ("media_rank", 3, [12, 13, 14], [22], "MEDIA RANK"),
}


# ------------------------------------------------------------------ preview


def vanilla_gui(rows):
    """Minecraft's default chest GUI, for previews."""
    h = 114 + rows * 18
    cv = Canvas(W, h, seed=1)
    bg = hexc('#C6C6C6')
    cv.rect(0, 0, W - 1, h - 1, bg)
    cv.outline(0, 0, W - 1, h - 1, hexc('#000000'), round_corners=True)
    cv.hline(1, W - 3, 1, hexc('#FFFFFF'))
    cv.vline(1, 1, h - 3, hexc('#FFFFFF'))
    cv.hline(2, W - 2, h - 2, hexc('#555555'))
    cv.vline(W - 2, 2, h - 2, hexc('#555555'))

    def slot(x, y):
        cv.rect(x, y, x + 17, y + 17, hexc('#8B8B8B'))
        cv.hline(x, x + 16, y, hexc('#373737'))
        cv.vline(x, y, y + 16, hexc('#373737'))
        cv.hline(x + 1, x + 17, y + 17, hexc('#FFFFFF'))
        cv.vline(x + 17, y + 1, y + 17, hexc('#FFFFFF'))

    for r in range(rows):
        for c in range(9):
            slot(7 + c * 18, 17 + r * 18)
    base = 17 + rows * 18 + 13
    for r in range(3):
        for c in range(9):
            slot(7 + c * 18, base + r * 18)
    for c in range(9):
        slot(7 + c * 18, base + 58)
    return cv.img


STAND_INS = ['#D94A4A', '#5AA0F0', '#F2C230', '#9AA5B5', '#A98BF5', '#3FCF93', '#F08A3C']


def preview(texture, rows, slots, nav, scale=4):
    gui = vanilla_gui(rows)
    pad = 6
    canvas = Image.new("RGBA", (W + pad * 2, gui.height + TOP + pad * 2), hexc('#2B2F36'))
    canvas.alpha_composite(gui, (pad, pad + TOP))
    canvas.alpha_composite(texture, (pad, pad))
    cv = Canvas(canvas.width, canvas.height, 0)
    cv.img = canvas
    cv.px = canvas.load()
    for i, s in enumerate(list(slots) + list(nav)):
        x, y = slot_xy(s)
        x += pad + 1
        y += pad + 1
        color = hexc(STAND_INS[i % len(STAND_INS)]) if s not in nav else hexc('#B07A52')
        cv.rect(x + 3, y + 3, x + 12, y + 12, color)
        cv.hline(x + 3, x + 12, y + 3, tuple(min(255, v + 60) for v in color[:3]) + (255,))
        cv.rect(x + 3, y + 12, x + 12, y + 12, tuple(max(0, v - 60) for v in color[:3]) + (255,))
    # the title text Minecraft would draw is replaced by the sign; the "Inventory" label stays
    return canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(root, "textures")
    out_preview = os.path.join(root, "previews")
    os.makedirs(out, exist_ok=True)
    os.makedirs(out_preview, exist_ok=True)
    sizes = {}
    for name, (theme, rows, slots, nav, title) in MENUS.items():
        img = THEMES[theme](rows, slots, nav, title)
        img.save(os.path.join(out, name + ".png"))
        preview(img, rows, slots, nav).save(os.path.join(out_preview, name + ".png"))
        sizes[name] = {"rows": rows, "height": img.height, "ascent": 13 + TOP}
    with open(os.path.join(root, "sizes.json"), "w") as f:
        json.dump(sizes, f, indent=2)
    print(json.dumps(sizes))


if __name__ == "__main__":
    main()
