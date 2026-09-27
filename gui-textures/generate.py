#!/usr/bin/env python3
"""
Market-stall Nexo GUI textures for chest menus, drawn as pixel art at Minecraft's GUI scale.

Each menu is a stall: a carved sign on a roof, braces down to two posts, a shelf unit whose
cubbies sit exactly under the menu's items, a ledge, and a panelled counter front with
drawers. Every menu has its own theme on top of that: the wood, the roof (slab, striped
awning, velvet, ragged cloth, canvas), the sign lettering, props on the roof and lights.

Every texture is 176 px wide (a chest GUI) and starts TOP px above the GUI so the sign can sit
on the roof and stick out over the top edge. Slot (row r, column c) has its frame at
x = 7 + 18c, y = TOP + 17 + 18r, and Minecraft draws the item 1 px inside that.

The picture paints the menu's grey over the chest's own slot grid, so only the stall shows.
The player inventory below is left untouched.

Nexo glyph for a texture of height H:  ascent: 13 + TOP   height: H
Menu title:                            <white><shift:-8><glyph:ID>

Run:  python3 generate.py      (needs Pillow: pip install pillow)
"""
import colorsys
import math
import os
from PIL import Image

W = 176
TOP = 6           # rows of art above the GUI's top edge (the sign)
HEADER = 17       # vanilla title bar height
PITCH = 18
LEFT = 7
GUI_GREY = '#C6C6C6'
DROP_SHADOW = '#8B8B8B'

# ------------------------------------------------------------------ colours


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def blend(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


def to_hex(c):
    return '#%02X%02X%02X' % tuple(c[:3])


# Wood, darkest to lightest. One letter per colour keeps the drawing code short.
WOOD = {
    'G': '#3D1816',  # deepest outline
    'e': '#461F18',  # outline, shadow under the roof, cubby tops
    'd': '#4D221B',  # outline, ledge front
    'v': '#512614',  # sign outline, letter shadow
    'g': '#573129',  # drawer panels
    'K': '#522821',  # shade beside the braces
    'k': '#5E3428',  # beam under the roof
    'L': '#5E3028',  # brace side
    'H': '#603620',  # post outline
    '1': '#603928',  # inside a cubby
    'c': '#693E2C',  # grooves
    ':': '#7A4A30',  # boards
    'y': '#825035',  # shelf rails
    'i': '#855436',  # light boards
    'j': '#8A5838',  # sign face, brace front
    '2': '#9A6442',  # cubby floor edge
    'h': '#AB724F',  # lit edges
    's': '#BA815D',  # lit edges, letter band
    'l': '#DB9D76',  # highlights, letters
    # roof slab
    'x': '#4A3729',  # under the roof
    'z': '#614C3C',  # shade on the roof
    'F': '#7D624D',  # roof outline
    'b': '#917560',  # roof front
    'a': '#B6997B',  # roof top
    'w': '#D1B18E',  # roof front edge
    # on the menu background
    'q': DROP_SHADOW,
    '.': GUI_GREY,
}
WOOD_KEYS = 'GedvgKkLH1c:yij2hsl'
ROOF_KEYS = 'xzFbaw'


def tint(base, keys, hue=None, sat=None, sat_mul=1.0, light_mul=1.0, light_add=0.0, extra=None):
    """A new palette: the given keys moved to another hue, saturation and lightness."""
    out = dict(base)
    for k in keys:
        r, g, b, _ = hexc(base[k])
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        h = h if hue is None else hue
        s = min(1.0, s * sat_mul) if sat is None else sat
        l = max(0.0, min(1.0, l * light_mul + light_add))
        r, g, b = colorsys.hls_to_rgb(h, l, s)
        out[k] = '#%02X%02X%02X' % (round(r * 255), round(g * 255), round(b * 255))
    out.update(extra or {})
    return out


def slate(palette):
    """The same palette in slate grey: keep each colour's lightness, swap the hue."""
    out = dict(palette)
    for key in WOOD_KEYS + ROOF_KEYS:
        r, g, b, _ = hexc(palette[key])
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        l = min(1.0, l * 1.06 + 0.02)
        s = 0.16 if l < 0.75 else 0.10
        r, g, b = colorsys.hls_to_rgb(0.57, l, s)
        out[key] = '#%02X%02X%02X' % (round(r * 255), round(g * 255), round(b * 255))
    return out


class Canvas:
    def __init__(self, width, height, palette):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = width, height
        self.pal = {k: hexc(v) for k, v in palette.items()}
        self.background = {hexc(GUI_GREY), hexc(DROP_SHADOW)}

    def color(self, c):
        return self.pal[c] if isinstance(c, str) and len(c) == 1 else (hexc(c) if isinstance(c, str) else c)

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = self.color(c)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return (0, 0, 0, 0)

    def is_stall(self, x, y):
        p = self.get(x, y)
        return p[3] > 0 and p not in self.background

    def shade(self, x, y, color, amount):
        """Tint a pixel that is already drawn (shadows and light)."""
        p = self.get(x, y)
        if p[3]:
            self.px[x, y] = blend(p, self.color(color), amount)[:3] + (p[3],)

    def rect(self, x0, y0, x1, y1, c):
        c = self.color(c)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.put(x, y, c)

    def hline(self, x0, x1, y, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def row(self, x, y, pattern):
        """Draw a run of palette letters; spaces are skipped."""
        for i, ch in enumerate(pattern):
            if ch != ' ':
                self.put(x + i, y, ch)


def mirror(x):
    """The same spot on the other side of the stall."""
    return W - x


# ------------------------------------------------------------------ geometry


def slot_x(col):
    return LEFT + col * PITCH


def slot_y(row):
    return TOP + HEADER + row * PITCH


def bottom_of(rows):
    """Lowest row of the stall; the menu's "Inventory" label starts just under it."""
    return TOP + HEADER + rows * PITCH + 2


POST_L, POST_R = 13, 163          # outer edges of the two posts
UNIT_L, UNIT_R = 23, 153          # the shelf unit between them


# ------------------------------------------------------------------ sign lettering

# Chunky 6-row letters with 3 px strokes, carved to stand off the sign.
SIGN_FONT = {
 'A': [".#####.", "###.###", "###.###", "#######", "###.###", "###.###"],
 'B': ["######.", "###.###", "######.", "###.###", "###.###", "######."],
 'C': [".######", "#######", "###....", "###....", "#######", ".######"],
 'D': ["######.", "###.###", "###.###", "###.###", "###.###", "######."],
 'E': ["#######", "#######", "###....", "#####..", "###....", "#######"],
 'F': ["#######", "#######", "###....", "######.", "###....", "###...."],
 'G': [".######", "###....", "###.###", "###..##", "###.###", ".######"],
 'H': ["###.###", "###.###", "###.###", "#######", "###.###", "###.###"],
 'I': ["###", "###", "###", "###", "###", "###"],
 'J': ["....###", "....###", "....###", "....###", "###.###", ".#####."],
 'K': ["###.###", "###.##.", "#####..", "######.", "###.###", "###.###"],
 'L': ["###....", "###....", "###....", "###....", "#######", "#######"],
 'M': ["##...##", "###.###", "#######", "##.#.##", "##...##", "##...##"],
 'N': ["###..##", "####.##", "#######", "##.####", "##..###", "##...##"],
 'O': [".#####.", "###.###", "###.###", "###.###", "###.###", ".#####."],
 'P': ["######.", "###.###", "###.###", "######.", "###....", "###...."],
 'Q': [".#####.", "###.###", "###.###", "###.###", "###.###", ".######"],
 'R': ["######.", "###.###", "###.###", "######.", "###.###", "###.###"],
 'S': [".######", "###....", "######.", ".######", "....###", "######."],
 'T': ["#######", "#######", "..###..", "..###..", "..###..", "..###.."],
 'U': ["###.###", "###.###", "###.###", "###.###", "###.###", ".#####."],
 'V': ["###.###", "###.###", "###.###", "###.###", ".#####.", "..###.."],
 'W': ["##...##", "##...##", "##.#.##", "#######", "###.###", "##...##"],
 'X': ["###.###", "###.###", ".#####.", ".#####.", "###.###", "###.###"],
 'Y': ["###.###", "###.###", ".#####.", "..###..", "..###..", "..###.."],
 'Z': ["#######", "....###", "..####.", ".###...", "###....", "#######"],
 ' ': ["...", "...", "...", "...", "...", "..."],
}

# The 5x7 font (hologram logos use it) and a compact 3x5 one for plaques
FONT = {
 'A': [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
 'B': ["####.", "#...#", "####.", "#...#", "#...#", "#...#", "####."],
 'C': [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
 'D': ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
 'E': ["#####", "#....", "####.", "#....", "#....", "#....", "#####"],
 'F': ["#####", "#....", "####.", "#....", "#....", "#....", "#...."],
 'G': [".####", "#....", "#....", "#..##", "#...#", "#...#", ".###."],
 'H': ["#...#", "#...#", "#####", "#...#", "#...#", "#...#", "#...#"],
 'I': ["###", ".#.", ".#.", ".#.", ".#.", ".#.", "###"],
 'J': ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
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
 'X': ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
 'Y': ["#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..", "..#.."],
 'Z': ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
 ' ': ["...", "...", "...", "...", "...", "...", "..."],
}

SMALL = {
 'A': [".#.", "#.#", "###", "#.#", "#.#"], 'B': ["##.", "#.#", "##.", "#.#", "##."],
 'C': [".##", "#..", "#..", "#..", ".##"], 'D': ["##.", "#.#", "#.#", "#.#", "##."],
 'E': ["###", "#..", "##.", "#..", "###"], 'F': ["###", "#..", "##.", "#..", "#.."],
 'G': [".##", "#..", "#.#", "#.#", ".##"], 'H': ["#.#", "#.#", "###", "#.#", "#.#"],
 'I': ["###", ".#.", ".#.", ".#.", "###"], 'K': ["#.#", "#.#", "##.", "#.#", "#.#"],
 'L': ["#..", "#..", "#..", "#..", "###"], 'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
 'N': ["#..#", "##.#", "#.##", "#..#", "#..#"], 'O': [".#.", "#.#", "#.#", "#.#", ".#."],
 'P': ["##.", "#.#", "##.", "#..", "#.."], 'R': ["##.", "#.#", "##.", "#.#", "#.#"],
 'S': [".##", "#..", ".#.", "..#", "##."], 'T': ["###", ".#.", ".#.", ".#.", ".#."],
 'U': ["#.#", "#.#", "#.#", "#.#", "###"], 'V': ["#.#", "#.#", "#.#", "#.#", ".#."],
 'W': ["#...#", "#...#", "#.#.#", "##.##", "#...#"], 'Y': ["#.#", "#.#", ".#.", ".#.", ".#."],
 ' ': ["..", "..", "..", "..", ".."],
}


def text_width(text, font):
    return sum(len(font[ch][0]) + 1 for ch in text) - 1


def glyph_cells(text, x, y, font):
    cells = set()
    for ch in text:
        for gy, line in enumerate(font[ch]):
            for gx, p in enumerate(line):
                if p == '#':
                    cells.add((x + gx, y + gy))
        x += len(font[ch][0]) + 1
    return cells


CARVED = dict(face='l', band='s', overhang='c', edge='v', lip='s')


def carved_text(cv, x, y, text, colors=CARVED):
    """Sign letters: a light face with a darker band through the middle, shadow where the
    upper half overhangs, and a two-row block edge underneath."""
    c = dict(CARVED, **colors)
    cx = x
    for ch in text:
        rows = SIGN_FONT[ch]
        for gy, line in enumerate(rows):
            for gx, p in enumerate(line):
                px, py = cx + gx, y + gy
                if p == '#':
                    cv.put(px, py, c['band'] if gy == 2 else c['face'])
                elif gy > 0 and rows[gy - 1][gx] == '#':
                    cv.put(px, py, c['overhang'])
        for gx, p in enumerate(rows[-1]):
            if p == '#':
                cv.put(cx + gx, y + 6, c['edge'])
                cv.put(cx + gx, y + 7, c['edge'])
                cv.put(cx + gx, y + 8, c['lip'])
        cx += len(rows[0]) + 1
    return glyph_cells(text, x, y, SIGN_FONT)


def small_text(cv, x, y, text, face, shadow):
    for (px, py) in glyph_cells(text, x, y, SMALL):
        cv.put(px, py + 1, shadow)
    for (px, py) in glyph_cells(text, x, y, SMALL):
        cv.put(px, py, face)


def glow(cv, cx, cy, radius, color, strength=0.5):
    """Soft light on the stall (never on the menu background)."""
    color = cv.color(color)
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            d = math.hypot(x - cx, y - cy)
            if d <= radius and cv.is_stall(x, y):
                a = strength * (1 - d / radius) ** 2
                if a > 0.02:
                    cv.shade(x, y, color, a)


# ------------------------------------------------------------------ roofs


def draw_cover(cv, rows):
    """The menu's grey over the chest's own slot grid."""
    cv.rect(3, TOP + 3, W - 4, slot_y(rows), '.')


ROOF_Y = TOP + 6
ROOF_INSETS = [4, 4, 3, 3, 2, 2, 1, 1, 0, 0]


def roof_cells():
    """(x, y, part) for every pixel of the roof: 'outline', 'top', 'edge' or 'front'."""
    y0 = ROOF_Y
    for x in range(5 + ROOF_INSETS[0], mirror(5 + ROOF_INSETS[0]) + 1):
        yield x, y0, 'outline'
    for i, inset in enumerate(ROOF_INSETS[1:], start=1):
        yield 5 + inset, y0 + i, 'outline'
        yield mirror(5 + inset), y0 + i, 'outline'
        for x in range(6 + inset, mirror(6 + inset) + 1):
            yield x, y0 + i, 'top' if i > 1 else 'top1'
    n = len(ROOF_INSETS)
    for x in range(6, mirror(6) + 1):
        yield x, y0 + n, 'edge'
    yield 5, y0 + n, 'top'
    yield mirror(5), y0 + n, 'top'
    for y in range(y0 + n + 1, y0 + n + 6):
        yield 5, y, 'outline'
        yield mirror(5), y, 'outline'
        for x in range(6, mirror(6) + 1):
            yield x, y, 'front' if y < y0 + n + 5 else 'front_low'


def draw_roof(cv, roof):
    style = roof.get('style', 'slab')
    hem_y = ROOF_Y + len(ROOF_INSETS) + 6
    if style == 'slab':
        tones = {'outline': 'F', 'top': 'a', 'top1': 'a', 'edge': roof.get('edge', 'w'),
                 'front': 'b', 'front_low': 'b'}
        for x, y, part in roof_cells():
            cv.put(x, y, tones[part])
        cv.hline(6, mirror(6), hem_y, 'x')
        if roof.get('glow_edge'):
            for x in range(6, mirror(6) + 1):
                cv.shade(x, hem_y - 6, roof['glow_edge'], 0.35)
        return
    ramps = [tuple(hexc(c) for c in ramp) for ramp in roof['ramps']]
    width = roof.get('stripe')

    def ramp_at(x):
        return ramps[((x - 5) // width) % len(ramps)] if width else ramps[0]

    def fold(x, ramp):
        """Velvet and cloth: soft vertical folds."""
        if width:
            return ramp[3]
        k = (x // 2 + (x // 7)) % 5
        return (ramp[2], ramp[3], ramp[4], ramp[3], ramp[2])[k]

    for x, y, part in roof_cells():
        ramp = ramp_at(x)
        if part == 'outline':
            c = ramp[0]
        elif part == 'top1':
            c = ramp[4]
        elif part == 'top':
            c = fold(x, ramp)
        elif part == 'edge':
            c = ramp[4]
        elif part == 'front':
            c = ramp[2] if width else (ramp[1], ramp[2], ramp[3], ramp[2], ramp[1])[(x // 2 + (x // 7)) % 5]
        else:
            c = ramp[1]
        cv.put(x, y, c)
    hem = roof.get('hem', 'straight')
    x0, x1 = 6, mirror(6)
    if hem == 'scallop':
        for x in range(x0, x1 + 1):
            ramp = ramp_at(x)
            local = (x - 5) % width
            depth = 3 if 1 < local < width - 2 else (2 if local in (1, width - 2) else 1)
            for d in range(depth):
                cv.put(x, hem_y + d, ramp[1] if d < depth - 1 else ramp[2])
            cv.put(x, hem_y + depth, ramp[0])
    elif hem == 'ragged':
        depths = [1, 2, 4, 3, 1, 2, 5, 3, 2, 1, 3, 4, 2, 1, 2, 3, 5, 2, 1, 3]
        for x in range(x0, x1 + 1):
            ramp = ramp_at(x)
            depth = depths[(x * 7) % len(depths)] if x % 3 else max(1, depths[(x * 7) % len(depths)] - 1)
            for d in range(depth):
                cv.put(x, hem_y + d, ramp[1] if d < depth - 1 else ramp[0])
    elif hem == 'fringe':
        gold, dark = hexc(roof.get('trim', '#E8B84A')), hexc(roof.get('trim_dark', '#9A6A1C'))
        cv.hline(x0, x1, hem_y, gold)
        for x in range(x0, x1 + 1):
            cv.put(x, hem_y + 1, gold if x % 2 == 0 else dark)
            if x % 2 == 0:
                cv.put(x, hem_y + 2, dark)
    elif hem == 'rope':
        for x in range(x0, x1 + 1):
            cv.put(x, hem_y, hexc('#C8A26A') if x % 3 else hexc('#7A5A30'))
            cv.put(x, hem_y + 1, hexc('#8A6A3A') if x % 3 != 1 else hexc('#5A3E20'))
    else:
        for x in range(x0, x1 + 1):
            cv.put(x, hem_y, ramp_at(x)[0])


def draw_sign(cv, text, style=None):
    """The sign on the roof. Returns its left and right x, for placing props beside it."""
    style = style or {}
    tw = text_width(text, SIGN_FONT)
    width = max(78, tw + 22)
    x0 = W // 2 - width // 2
    x1 = x0 + width - 1
    # its shadow on the roof
    for y, a, b in ((15, x0 - 1, x1 + 1), (16, x0 - 1, x1 + 1), (17, x0 - 1, x1 + 1), (18, x0, x1)):
        for x in range(a, b + 1):
            cv.shade(x, y, (0, 0, 0, 255), 0.42)
    # board
    cv.hline(x0 + 2, x1 - 2, 0, 'c')
    cv.row(x0 + 1, 1, 'c')
    cv.hline(x0 + 2, x1 - 2, 1, 'l')
    cv.put(x1 - 1, 1, 'c')
    for y in range(2, 14):
        cv.row(x0, y, 'vsj')
        cv.hline(x0 + 3, x1 - 3, y, 'j')
        cv.row(x1 - 2, y, 'jsv')
    cv.hline(x0 + 3, x1 - 3, 2, 'c')
    cv.put(x0 + 2, 3, 'c')
    cv.put(x1 - 2, 3, 'c')
    cv.hline(x0 + 3, x1 - 3, 13, 'c')
    cv.row(x0, 14, 'vc')
    cv.hline(x0 + 2, x1 - 2, 14, 's')
    cv.row(x1 - 1, 14, 'cv')
    cv.put(x0, 15, 'v')
    cv.hline(x0 + 1, x1 - 1, 15, 'c')
    cv.put(x1, 15, 'v')
    cv.hline(x0 + 1, x1 - 1, 16, 'v')
    tx = W // 2 - tw // 2
    if style.get('glow'):
        # light spilling from the letters onto the board
        cells = glyph_cells(text, tx, 4, SIGN_FONT)
        for y in range(3, 13):
            for x in range(x0 + 3, x1 - 2):
                if (x, y) in cells:
                    continue
                d = min((abs(x - cx) + abs(y - cy) for cx, cy in cells if abs(x - cx) <= 3 and abs(y - cy) <= 3), default=9)
                if d <= 3:
                    cv.shade(x, y, style['glow'], (0.55, 0.32, 0.14)[d - 1])
    carved_text(cv, tx, 4, text, style.get('letters', {}))
    if style.get('bulbs'):
        warm, bright = hexc('#FFB23E'), hexc('#FFF4C2')
        for i, x in enumerate(range(x0 + 3, x1 - 2, 4)):
            cv.put(x, 1, bright if i % 2 == 0 else warm)
            cv.put(x, 14, warm if i % 2 == 0 else bright)
        for i, y in enumerate(range(4, 13, 4)):
            cv.put(x0 + 1, y, bright if i % 2 else warm)
            cv.put(x1 - 1, y, warm if i % 2 else bright)
    return x0, x1


# ------------------------------------------------------------------ stall body


def draw_underside(cv, y0=TOP + 23):
    """Shadow under the roof, the beam, and the braces running down to the posts."""
    for y in range(y0, y0 + 3):
        cv.hline(7, mirror(7), y, 'e')
    cv.hline(17, mirror(17), y0 + 3, 'k')
    cv.hline(17, mirror(17), y0 + 4, 'k')
    for side in (0, 1):
        def put(x, y, c):
            cv.put(x if side == 0 else mirror(x), y, c)

        def row(x, y, pattern):
            for i, ch in enumerate(pattern):
                if ch != ' ':
                    put(x + i, y, ch)

        # the brace: a board stepping in one pixel every two rows
        for i in range(15):
            y = y0 + i
            x = 7 + (i + 1) // 2 if i < 13 else 13
            if i < 3:
                row(x, y, 'eHHHKKKKe')
            elif i < 13:
                front = 'jj' if (i // 2) % 2 == 0 else '::'
                side_ = 'LLLL' if (i // 2) % 3 != 2 else 'cccc'
                row(x, y, 'e' + front + 's' + side_ + 'e')
            else:
                row(x, y, ('Ge' if i == 13 else 'kG') + '::sLe')
        # shade between the brace and the shelf unit
        for i in range(9, 15):
            end = (7 + (i + 1) // 2 + 8) if i < 13 else 19
            for x in range(end + 1, UNIT_L):
                put(x, y0 + i, 'K')


def draw_wall(cv, y0, y1):
    """Back wall above the shelf unit: boards between the column posts."""
    for y in range(y0, y1 + 1):
        cv.hline(18, mirror(18), y, ':')
    for c in range(1, 9):
        b = slot_x(c)
        # boards between this post and the next
        start = b + 2
        for x in (start + 4, start + 5, start + 9, start + 10):
            if 18 <= x <= mirror(19):
                cv.vline(x, y0, y1, 'c')
        if c % 2 == 1 and c < 8:
            for y in range(y0 + 2, y0 + 4):
                cv.hline(start + 4, start + 10, y, 'c')
        cv.vline(b - 1, y0, y1, 'h')
        cv.vline(b, y0, y1, 'G')
        cv.vline(b + 1, y0, y1, 'h')
    # left of the first post
    cv.vline(20, y0, y1, 'c')
    cv.vline(21, y0, y1, 'c')
    cv.vline(mirror(21), y0, y1, 'c')
    cv.vline(mirror(22), y0, y1, 'c')


def draw_post(cv, top, joint, drawer_top, bottom):
    """The two corner posts: boards with a joint, and a drawer box at the foot."""
    for side in (0, 1):
        def put(x, y, c):
            cv.put(x if side == 0 else mirror(x), y, c)

        def row(x, y, pattern):
            for i, ch in enumerate(pattern):
                if ch != ' ':
                    put(x + i, y, ch)

        for y in range(top, drawer_top):
            if y < joint:
                row(POST_L, y, 'Hh::cc::gg')
            elif y == joint:
                row(POST_L, y, 'Hihhhhhhgg')
            elif y == joint + 1:
                row(POST_L, y, 'HHHHHHHHgg')
            else:
                row(POST_L, y, 'Hhcckkccgg')
        row(POST_L, drawer_top, 'Hihhhhhhhhhhhi')
        for y in range(drawer_top + 1, bottom):
            row(POST_L, y, 'd:' + 'g' * 11 + ':d')
        row(POST_L, bottom - 1, 'd' + ':' * 13 + 'd')
        row(POST_L, bottom, 'q' + 'd' * 14)
        row(POST_L + 1, bottom + 1, 'q' * 14)
        row(POST_L + 1, bottom + 2, 'q' * 14)


def cubby_opening(cv, x, y, bay=None):
    """One cubby of the shelf unit, 18 px wide, frame at (x, y). bay is how a spot with no
    item looks: 'boards' (default), or 'curtain' / 'curtain_eyes'."""
    inner0, inner1 = x + 2, x + 15
    cv.put(x + 1, y, 'y')
    cv.put(x + 16, y, 'y')
    for yy in range(y + 1, y + 17):
        cv.put(x + 1, yy, 's')
        cv.put(x + 16, yy, 's')
    if bay in ('boards', None) and bay is not None:
        # boarded up: planks with a batten across
        for yy in range(y, y + 17):
            for xx in range(inner0, inner1 + 1):
                k = (xx - inner0) % 5
                cv.put(xx, yy, 'c' if k == 4 else ('i' if k == 0 else ':'))
        cv.hline(inner0, inner1, y, 'l')
        for yy, c in ((y + 7, 'h'), (y + 8, 'y'), (y + 9, 'y'), (y + 10, 'c')):
            cv.hline(inner0, inner1, yy, c)
        cv.put(inner0 + 1, y + 8, 'd')
        cv.put(inner1 - 1, y + 8, 'd')
        return
    if bay in ('curtain', 'curtain_eyes'):
        folds = [hexc(c) for c in ('#2A1238', '#3D1A52', '#55246F', '#6C3190', '#55246F', '#3D1A52')]
        gap = 2 if bay == 'curtain' else 4
        mid = (inner0 + inner1) // 2
        for yy in range(y, y + 17):
            spread = gap + (yy - y) // 6
            for xx in range(inner0, inner1 + 1):
                if mid - spread // 2 < xx <= mid + (spread + 1) // 2:
                    cv.put(xx, yy, hexc('#0B0610'))
                else:
                    cv.put(xx, yy, folds[(xx - inner0) % len(folds)])
        cv.hline(inner0, inner1, y, hexc('#C9A040'))
        cv.hline(inner0, inner1, y + 1, hexc('#6A4A1A'))
        if bay == 'curtain_eyes':
            for ex in (mid - 1, mid + 2):
                cv.put(ex, y + 7, hexc('#E6B8FF'))
                cv.put(ex, y + 8, hexc('#9A5CFF'))
        return
    cv.hline(inner0, inner1, y, 'l')
    cv.hline(inner0, inner1, y + 1, 'e')
    cv.hline(inner0, inner1, y + 2, 'e')
    for yy in range(y + 3, y + 15):
        cv.put(inner0, yy, 'e')
        cv.hline(inner0 + 1, inner1 - 1, yy, '1')
        cv.put(inner1, yy, 'e')
    cv.hline(inner0, inner1, y + 15, '2')
    cv.hline(inner0, inner1, y + 16, '1')


class Box:
    """A coloured cubby (coinflip style): light edge, framed inner box, lit bottom."""

    def __init__(self, kind, light, frame, inner_line, inner, lower):
        self.kind = kind
        self.light, self.frame, self.inner_line, self.inner, self.lower = (
            hexc(v) for v in (light, frame, inner_line, inner, lower))


def colored_box(cv, x, y, b):
    if b.kind == 'lit':
        cv.rect(x, y, x + 17, y + 16, b.inner)
        cv.vline(x, y, y + 16, b.light)
        cv.vline(x + 17, y, y + 16, b.light)
        cv.hline(x, x + 17, y + 16, b.light)
        cv.hline(x + 1, x + 16, y, b.frame)
        cv.vline(x + 1, y, y + 15, b.frame)
        return
    if b.kind == 'flat':
        cv.rect(x, y, x + 17, y + 16, b.inner)
        cv.vline(x, y, y + 15, b.light)
        cv.hline(x + 1, x + 17, y, b.inner_line)
        cv.hline(x + 1, x + 17, y + 1, b.inner_line)
        cv.vline(x + 1, y, y + 15, b.inner_line)
        cv.vline(x + 17, y, y + 15, b.inner_line)
        cv.hline(x, x + 17, y + 16, b.light)
        return
    cv.rect(x + 1, y, x + 17, y + 13, b.frame)
    cv.vline(x, y, y + 15, b.light)
    for yy in range(y + 11, y + 15):
        cv.hline(x + 1, x + 17, yy, b.lower)
    cv.rect(x + 4, y + 2, x + 14, y + 12, b.inner)
    cv.hline(x + 4, x + 14, y + 2, b.inner_line)
    cv.hline(x + 4, x + 14, y + 3, b.inner_line)
    cv.vline(x + 4, y + 2, y + 12, b.inner_line)
    cv.vline(x + 14, y + 2, y + 12, b.inner_line)
    cv.hline(x + 4, x + 14, y + 12, b.inner_line)
    cv.hline(x + 4, x + 14, y + 13, b.light)
    cv.hline(x, x + 17, y + 15, b.light)


def draw_unit(cv, cubby_rows, buttons, boxes, bays):
    """The shelf unit: a rail, a row of cubbies per cubby row, the shelf and its ledge.
    Returns the first row of the counter front."""
    top = slot_y(cubby_rows[0]) - 1
    last = slot_y(cubby_rows[-1])
    cv.rect(UNIT_L + 1, top, UNIT_R - 1, last + 18, 'y')
    cv.vline(UNIT_L, top, last + 18, 'd')
    cv.vline(UNIT_R, top, last + 18, 'd')
    for r in cubby_rows:
        y = slot_y(r)
        for c in range(1, 8):
            s = r * 9 + c
            if s in boxes:
                continue
            cubby_opening(cv, slot_x(c), y, None if s in buttons else bays.get(s, bays.get('*', 'boards')))
    for r in cubby_rows:
        y = slot_y(r)
        coloured = [c for c in range(1, 8) if r * 9 + c in boxes]
        if coloured:
            x0, x1 = slot_x(min(coloured)), slot_x(max(coloured)) + 17
            cv.hline(x0 - 1, x1 + 1, y - 1, 'd')
            cv.vline(x0 - 1, y, y + 16, 'e')
            cv.vline(x1 + 1, y, y + 16, 'e')
            cv.hline(x0 - 1, x1 + 1, y + 16, 'e')
        for c in coloured:
            colored_box(cv, slot_x(c), y, boxes[r * 9 + c])
    # shelf top with its lit edge, then the dark ledge
    sy = last + 17
    cv.hline(UNIT_L + 1, UNIT_R - 1, sy, 'y')
    cv.hline(UNIT_L + 1, UNIT_R - 1, sy + 1, 'y')
    for c in range(1, 8):
        cv.hline(slot_x(c) + 2, slot_x(c) + 15, sy + 1, 'l')
    cv.hline(UNIT_L - 1, UNIT_R + 1, sy + 2, 'd')
    cv.hline(UNIT_L - 1, UNIT_R + 1, sy + 3, 'd')
    cv.hline(UNIT_L - 1, UNIT_R + 1, sy + 4, 'd')
    for side in (0, 1):
        f = (lambda x: x) if side == 0 else mirror
        cv.put(f(UNIT_L - 2), sy + 2, 'g')
        cv.put(f(UNIT_L - 2), sy + 3, 'h')
        cv.put(f(UNIT_L - 2), sy + 4, 'k')
        cv.put(f(UNIT_L - 1), sy + 4, 'h')
    cv.hline(UNIT_L, UNIT_R, sy + 5, 'h')
    return sy + 6


def draw_front(cv, top, bottom):
    """Counter front: a section per column between light posts. Odd columns reach the floor
    with a low drawer; even columns stop short with a high one."""
    for c in range(1, 8):
        x0, x1 = slot_x(c) + 2, slot_x(c + 1) - 2
        tall = c % 2 == 1
        end = bottom if tall else bottom - 4
        drawer = end - 6 if tall else top + 3
        for y in range(top, drawer):
            for x in range(x0, x1 + 1):
                k = x - x0
                if tall:
                    cv.put(x, y, ':' if k in (2, 3, 9, 10) else 'i')
                else:
                    cv.put(x, y, 'c' if k in (3, 4, 9, 10) else ':')
        cv.hline(x0 - 1, x1 + 1, drawer, 'h')
        cv.put(x0 - 1, drawer, 'i')
        cv.put(x1 + 1, drawer, 'i')
        for y in range(drawer + 1, end - 1):
            cv.row(x0 - 1, y, ':' + 'g' * (x1 - x0 + 1) + ':')
        cv.hline(x0 - 1, x1 + 1, end - 1, ':')
        cv.hline(x0 - 1, x1 + 1, end, 'd')
        cv.hline(x0 - 1, x1 + 1, end + 1, 'q')
        cv.hline(x0 - 1, x1 + 1, end + 2, 'q')
    # posts between the sections
    for c in range(1, 9):
        b = slot_x(c)
        for y in range(top, bottom - 3):
            cv.row(b - 1, y, 'h' + ('H' if c % 2 else 'k') + 'h')
    for c in range(1, 9):
        b = slot_x(c)
        tall_left = (c - 1) % 2 == 1
        tall_right = c % 2 == 1 and c < 8
        end = bottom if (tall_left or tall_right) else bottom - 4
        cv.vline(b, bottom - 4, end, 'd')


def draw_nav(cv, slot):
    """A back/close button slot set into the front: a dark framed opening."""
    x, y = slot_x(slot % 9), slot_y(slot // 9)
    cv.rect(x - 1, y - 1, x + 18, y + 17, 'd')
    cv.hline(x, x + 17, y, 'h')
    cv.vline(x, y, y + 16, 's')
    cv.vline(x + 17, y, y + 16, 'y')
    cubby_opening(cv, x, y)
    cv.hline(x - 1, x + 18, y + 17, 'd')
    cv.hline(x - 1, x + 18, y + 18, 'q')


def plaque(cv, cx, y, text):
    """A small wooden name plate hung on the front."""
    tw = text_width(text, SMALL)
    x0, x1 = cx - tw // 2 - 3, cx - tw // 2 + tw + 2
    cv.rect(x0, y, x1, y + 8, 'j')
    cv.hline(x0 + 1, x1 - 1, y, 'v')
    cv.hline(x0 + 1, x1 - 1, y + 8, 'v')
    cv.vline(x0, y + 1, y + 7, 'v')
    cv.vline(x1, y + 1, y + 7, 'v')
    cv.hline(x0 + 1, x1 - 1, y + 1, 'l')
    cv.hline(x0 + 1, x1 - 1, y + 7, 'c')
    small_text(cv, cx - tw // 2, y + 2, text, cv.color('l'), cv.color('v'))
    cv.hline(x0 + 1, x1 + 1, y + 9, 'e')


# ------------------------------------------------------------------ props and decorations

# Small pixel sprites for the roof and the stall. '.' is transparent.
SPRITES = {
    'potion': ([".cc.", ".gg.", "gPpg", "gppg", "gppg", ".gg."],
               {'c': '#9A6442', 'g': '#241830', 'P': '#F2D6FF', 'p': '#A855F7'}),
    'potion_green': (["..cc..", "..gg..", ".gPpg.", "gPpppg", "gppppg", "gppppg", ".gggg."],
                     {'c': '#9A6442', 'g': '#1C2A1C', 'P': '#D8FFD0', 'p': '#3FCF63'}),
    'skull': ([".bbbbb.", "bbbbbbs", "bEbbEbs", "bbbnbbs", ".bbbbs.", ".b.b.s."],
              {'b': '#E8E0CC', 's': '#B3A892', 'E': '#1E1418', 'n': '#6A5E50'}),
    'candle': ([".f", "fF", "ww", "wW", "wW", "WW"],
               {'f': '#FFF3A0', 'F': '#FF9A2E', 'w': '#EDE4D2', 'W': '#B9AD96'}),
    'coins': ([".yyyy.", "YyyyyY", "dYYYYd", ".yyyy.", "YyyyyY", "dYYYYd", ".yyyy.", "YyyyyY", "dYYYYd"],
              {'y': '#FFE07A', 'Y': '#E0A82A', 'd': '#8A6414'}),
    'coins_low': ([".yyyy.", "YyyyyY", "dYYYYd", ".yyyy.", "YyyyyY", "dYYYYd"],
                  {'y': '#FFE07A', 'Y': '#E0A82A', 'd': '#8A6414'}),
    'crate': (["oooooooo", "ohhhhhho", "ohbLLbho", "ohLbbLho", "ohLbbLho", "ohbLLbho", "ohhhhhho", "oooooooo"],
              {'o': '#3E2614', 'h': '#C08A52', 'L': '#A06A3A', 'b': '#6E4424'}),
    'barrel': ([".ooooo.", "oBbBbBo", "oiiiiio", "oBbBbBo", "oBbBbBo", "oBbBbBo", "oiiiiio", "oBbBbBo", ".ooooo."],
               {'o': '#2E1C10', 'B': '#A4683A', 'b': '#84522C', 'i': '#5C5E66'}),
    'sword': (["..p..", "..h..", "..h..", "gGgGg", ".lLo.", ".lLo.", ".lLo.", ".lLo.", ".lLo."],
              {'p': '#F2C94C', 'h': '#6A4222', 'g': '#E0B040', 'G': '#A87C1C', 'l': '#F2F6FA', 'L': '#AAB6C4',
               'o': '#4A525E'}),
    'shield': (["ooooooo", "oRRyRRo", "oRRyRRo", "oyyyyyo", "oRRyRRo", "oRRyRRo", ".oRyRo.", "..oyo..", "...o..."],
               {'o': '#3A2418', 'R': '#B83A3A', 'y': '#EED48A'}),
    'gear': (["...gg...", ".g.gg.g.", "..gggg..", "gggddggg", "gggddggg", "..gggg..", ".g.gg.g.", "...gg..."],
             {'g': '#B4BCC6', 'd': '#3E444C'}),
    'gear_small': (["..gg..", ".gggg.", "ggddgg", "ggddgg", ".gggg.", "..gg.."],
                   {'g': '#8E98A4', 'd': '#3E444C'}),
    'globe': ([".ooooo.", "oBBGGBo", "oBGGBBo", "oBBBGGo", "oGBBBBo", "oBBGBBo", ".ooooo.", "...w...", "..www..",
               ".wwwww."],
              {'B': '#3F86D6', 'G': '#5DB85A', 'o': '#1B3553', 'w': '#7A4A30'}),
    'map': (["oooooooooo", "rPPPPPPPPr", "rPlPPPPxPr", "rPPlPlPPPr", "rPPPPlPPPr", "oooooooooo"],
            {'P': '#EEDDB0', 'l': '#B84A3A', 'x': '#D0302A', 'r': '#B89458', 'o': '#6A4E2A'}),
    'book': (["oooooooo.", "oRRRRRRop", "oRyyyRRop", "oRRRRRRop", "oRRRRRRop", "oooooooo."],
             {'R': '#7A2E2E', 'y': '#E8C050', 'o': '#2E1010', 'p': '#F0E6CC'}),
    'quill': (["....w.", "...ww.", "..ww..", ".ow...", "ooooo.", "oKKKo.", "oKKKo.", ".ooo.."],
              {'w': '#F4F4F4', 'o': '#1E2230', 'K': '#3A4A8A'}),
    'camera': ([".ooo..ooo..", "oRcRooRcRo.", ".ooo..ooo..", "ooooooooo.o", "oBBBBBBBoLo", "oBHHBBBBooo",
                "ooooooooo.o"],
               {'R': '#5A5A6A', 'c': '#B0B0C0', 'o': '#141418', 'B': '#3A3A48', 'H': '#6A6A80', 'L': '#8FD0FF'}),
    'star': (["...y...", "..yyy..", "yyyyyyy", ".yyyyy.", "..yYy..", ".yy.yy.", "y.....y"],
             {'y': '#FFD84A', 'Y': '#E8A820'}),
    'gift': (["..w...w..", "...w.w...", "ooooooooo", "oPPPwPPPo", "ooooooooo", ".oPPwPPo.", ".oPPwPPo.", ".ooooooo."],
             {'P': '#E86FB8', 'w': '#FFF0F8', 'o': '#6A2050'}),
    'boot': (["oo....", "ob....", "ob....", "obbbo.", "obbbbo", "oooooo"],
             {'o': '#2E1E10', 'b': '#6A4A2A'}),
    'can': ([".sss.", "sSSSs", "sRRRs", "sRWRs", "sSSSs", ".sss."],
            {'s': '#6E747C', 'S': '#C8CED6', 'R': '#C84A3A', 'W': '#F0E0D0'}),
    'rocket': (["..w..", ".wWw.", ".wWw.", ".wBw.", ".wWw.", ".wWw.", "pwWwp", "pwwwp", "..f..", ".fFf.", "..F.."],
               {'w': '#F2F2F6', 'W': '#C4C4D0', 'B': '#6AB8FF', 'p': '#E86FB8', 'f': '#FFD04A', 'F': '#FF7A3A'}),
}


def sprite_size(name):
    rows, _ = SPRITES[name]
    return max(len(r) for r in rows), len(rows)


def sprite(cv, name, x, bottom, shadow=True):
    """Draw a sprite with its bottom row on `bottom`, and a small shadow to its lower right."""
    rows, colors = SPRITES[name]
    y0 = bottom - len(rows) + 1
    cells = {(x + gx, y0 + gy): colors[ch] for gy, line in enumerate(rows) for gx, ch in enumerate(line) if ch != '.'}
    if shadow:
        for (px, py) in cells:
            if (px + 1, py + 1) not in cells and py + 1 <= bottom + 1:
                cv.shade(px + 1, py + 1, (0, 0, 0, 255), 0.35)
    for (px, py), c in cells.items():
        cv.put(px, py, c)


def roof_props(cv, sign, left, right, bottom=ROOF_Y + 8):
    """Props standing on the roof on each side of the sign, packed outwards from it."""
    x0, x1 = sign
    x = x0 - 4
    for name in left:
        w, _ = sprite_size(name)
        x -= w
        if x < 11:
            break
        sprite(cv, name, x, bottom)
        x -= 2
    x = x1 + 4
    for name in right:
        w, _ = sprite_size(name)
        if x + w > mirror(11):
            break
        sprite(cv, name, x, bottom)
        x += w + 2


def lantern(cv, x, y, light, core='#FFFFFF', chain_top=TOP + 23):
    """An iron lantern hanging on a chain under the roof."""
    for cy in range(chain_top, y):
        cv.put(x + 2, cy, hexc('#3B3E46') if (cy - chain_top) % 2 == 0 else hexc('#1E2026'))
    iron, dark = hexc('#2A2830'), hexc('#121016')
    body = [".ooo.", "ooooo", "oGfGo", "oGFGo", "oGfGo", "ooooo", ".o.o."]
    colors = {'o': iron, 'G': blend(hexc(light), dark, 0.45), 'f': hexc(light), 'F': hexc(core)}
    for gy, line in enumerate(body):
        for gx, ch in enumerate(line):
            if ch != '.':
                cv.put(x + gx, y + gy, colors[ch])
    cv.put(x + 1, y, dark)
    cv.put(x + 3, y, dark)


def wall_torch(cv, x, y):
    """A torch on a post: a flame over a short stick in an iron bracket."""
    flame = ["..y..", ".yoy.", ".yor.", "..r.."]
    colors = {'y': hexc('#FFE27A'), 'o': hexc('#FFA53A'), 'r': hexc('#E4572E')}
    for gy, line in enumerate(flame):
        for gx, ch in enumerate(line):
            if ch != '.':
                cv.put(x - 2 + gx, y + gy, colors[ch])
    cv.vline(x, y + 4, y + 8, hexc('#7A4A24'))
    cv.put(x, y + 4, hexc('#3A2412'))
    cv.hline(x - 1, x + 1, y + 7, hexc('#2A2A30'))
    cv.hline(x - 1, x + 1, y + 8, hexc('#1A1A20'))


def bunting(cv, colors, y0=TOP + 24, sag=3):
    """A string of pennants across the stall under the roof."""
    string = hexc('#EDE6F2')
    spans = [(9, 88), (88, mirror(9))]
    k = 0
    for a, b in spans:
        for x in range(a, b + 1):
            t = (x - a) / (b - a)
            y = y0 + round(sag * math.sin(math.pi * t))
            cv.put(x, y, string)
            if (x - a) % 8 == 3 and b - x > 3:
                c = hexc(colors[k % len(colors)])
                edge = blend(c, (0, 0, 0, 255), 0.3)
                for dy, half in ((1, 2), (2, 2), (3, 1), (4, 0)):
                    for dx in range(-half, half + 1):
                        cv.put(x + dx, y + dy, edge if abs(dx) == half and half else c)
                k += 1


def pinned_note(cv, x, y, pin, tilt=0):
    """A parchment note pinned over a closed cupboard."""
    paper, edge, ink, line = hexc('#EADCB6'), hexc('#7A5E3A'), hexc('#C8B283'), hexc('#8C7650')
    x0, y0, x1, y1 = x + 3, y + 2 + tilt, x + 14, y + 13 + tilt
    cv.rect(x0, y0, x1, y1, paper)
    cv.hline(x0, x1, y0, edge)
    cv.hline(x0, x1, y1, edge)
    cv.vline(x0, y0, y1, edge)
    cv.vline(x1, y0, y1, edge)
    cv.vline(x1 - 1, y0 + 1, y1 - 1, ink)
    for ly in range(y0 + 3, y1 - 1, 2):
        cv.hline(x0 + 2, x1 - 3 - (ly % 3), ly, line)
    cv.put((x0 + x1) // 2, y0, hexc(pin))
    cv.put((x0 + x1) // 2, y0 + 1, blend(hexc(pin), (0, 0, 0, 255), 0.4))
    cv.hline(x0 + 1, x1 + 1, y1 + 1, blend(cv.get(x0 + 1, y1 + 1), (0, 0, 0, 255), 0.3))


def iron_band(cv, y):
    """An iron strap round both posts, with rivets."""
    for side in (0, 1):
        f = (lambda x: x) if side == 0 else mirror
        for x in range(POST_L, POST_L + 10):
            cv.put(f(x), y, hexc('#6A707A'))
            cv.put(f(x), y + 1, hexc('#34383F'))
        for x in (POST_L + 2, POST_L + 7):
            cv.put(f(x), y, hexc('#C4CAD2'))


def fog(cv, ctx, color, height=8, strength=0.4):
    """Coloured mist rising from the floor along the counter front."""
    bottom = ctx['bottom']
    for y in range(bottom - height, bottom + 1):
        a = strength * ((y - (bottom - height)) / height) ** 1.5
        for x in range(POST_L, mirror(POST_L) + 1):
            if cv.is_stall(x, y):
                cv.shade(x, y, color, a)


def sparks(cv, points, color, core='#FFFFFF'):
    """Tiny floating motes of light."""
    for i, (x, y) in enumerate(points):
        if cv.is_stall(x, y):
            cv.put(x, y, core if i % 3 == 0 else color)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                cv.shade(x + dx, y + dy, color, 0.35)


def shelf_candles(cv, ctx, cols):
    """Candles standing on the shelf between cubbies (on the dividers, never over an item)."""
    for c in cols:
        x = slot_x(c) - 1
        sprite(cv, 'candle', x, ctx['shelf'], shadow=False)
        glow(cv, x + 1, ctx['shelf'] - 5, 6, '#FFB347', 0.35)


# ------------------------------------------------------------------ layouts


def stall(cv, menu):
    theme = menu.get('theme', {})
    rows, buttons = menu['rows'], set(menu.get('buttons', []))
    boxes = menu.get('boxes', {})
    buttons |= set(boxes)
    cubby_rows = sorted({s // 9 for s in buttons})
    bottom = bottom_of(rows)
    draw_cover(cv, rows)
    front_top = draw_unit(cv, cubby_rows, buttons, boxes, theme.get('bays', {}))
    draw_wall(cv, TOP + 28, slot_y(cubby_rows[0]) - 2)
    draw_underside(cv)
    unit_top = slot_y(cubby_rows[0]) - 1
    draw_post(cv, TOP + 38, front_top - 11, bottom - 10, bottom - 2)
    draw_front(cv, front_top, bottom)
    # the unit's side edges run down to the drawers at the foot of the posts
    for side_x in (UNIT_L, UNIT_R):
        cv.vline(side_x, unit_top, front_top - 7, 'd')
        cv.vline(side_x, front_top - 1, bottom - 11, 'd')
    for s, text in menu.get('plaques', {}).items():
        plaque(cv, slot_x(s % 9) + 9, front_top + 2, text)
    for s in menu.get('nav', []):
        draw_nav(cv, s)
    boarded = [r * 9 + c for r in cubby_rows for c in range(1, 8) if r * 9 + c not in buttons]
    ctx = {'front_top': front_top, 'bottom': bottom, 'shelf': front_top - 6, 'boarded': boarded,
           'cubby_rows': cubby_rows}
    for deco in theme.get('decor', []):
        deco(cv, ctx)
    draw_roof(cv, theme.get('roof', {}))
    ctx['sign'] = draw_sign(cv, menu['title'], theme.get('sign'))
    left, right = theme.get('props', ([], []))
    roof_props(cv, ctx['sign'], left, right)
    for deco in theme.get('lights', []):
        deco(cv, ctx)


def crate(cv, menu):
    """Every slot left open (players drop items anywhere): a crate of cubbies under a lid."""
    theme = menu.get('theme', {})
    rows = menu['rows']
    x0, x1 = 4, W - 5
    top = slot_y(0) - 1
    last = slot_y(rows - 1)
    cv.rect(x0 + 1, top, x1 - 1, last + 18, 'y')
    cv.vline(x0, top, last + 20, 'd')
    cv.vline(x1, top, last + 20, 'd')
    for r in range(rows):
        for c in range(9):
            cubby_opening(cv, slot_x(c), slot_y(r))
    cv.hline(x0 + 1, x1 - 1, last + 18, 'l')
    cv.hline(x0 + 1, x1 - 1, last + 19, 'd')
    cv.hline(x0, x1, last + 20, 'd')
    cv.hline(x0 + 1, x1 - 1, last + 21, 'q')
    cv.hline(x0 + 1, x1 - 1, last + 22, 'q')
    if theme.get('iron_corners'):
        iron, dark, rivet = hexc('#5E646E'), hexc('#2E3238'), hexc('#C4CAD2')
        for (cx, cy, sx, sy) in ((x0, top, 1, 1), (x1, top, -1, 1), (x0, last + 20, 1, -1), (x1, last + 20, -1, -1)):
            for i in range(7):
                cv.put(cx + sx * i, cy, dark)
                cv.put(cx, cy + sy * i, dark)
                cv.put(cx + sx * i, cy + sy, iron)
                cv.put(cx + sx, cy + sy * i, iron)
            cv.put(cx + sx * 4, cy + sy, rivet)
            cv.put(cx + sx, cy + sy * 4, rivet)
    # lid
    y0 = TOP + 3
    insets = [3, 2, 2, 1, 1, 0]
    cv.hline(3 + insets[0], mirror(3 + insets[0]), y0, 'F')
    for i, inset in enumerate(insets[1:], start=1):
        cv.hline(4 + inset, mirror(4 + inset), y0 + i, 'a')
        cv.put(3 + inset, y0 + i, 'F')
        cv.put(mirror(3 + inset), y0 + i, 'F')
    cv.hline(4, mirror(4), y0 + len(insets), 'w')
    for y in range(y0 + len(insets) + 1, top):
        cv.hline(4, mirror(4), y, 'b')
        cv.put(3, y, 'F')
        cv.put(mirror(3), y, 'F')
    cv.hline(4, mirror(4), top, 'x')
    sign = draw_sign(cv, menu['title'], theme.get('sign'))
    left, right = theme.get('props', ([], []))
    roof_props(cv, sign, left, right, bottom=y0 + 4)


LAYOUTS = {'stall': stall, 'crate': crate}

# ------------------------------------------------------------------ themes

GREEN = Box('frame', '#87BA65', '#5A823C', '#243B19', '#334D20', '#669448')
GREEN_FLAT = Box('flat', '#87BA65', '#5A823C', '#243B19', '#334D20', '#669448')
RED = Box('frame', '#BA5D64', '#82353E', '#471616', '#5C2121', '#944047')
RED_FLAT = Box('flat', '#BA5D64', '#82353E', '#471616', '#5C2121', '#944047')
GOLD = Box('lit', '#FFD160', '#67521F', '#67521F', '#876B28', '#876B28')
PURPLE = Box('frame', '#A98AE0', '#6C4FA3', '#24163D', '#35255A', '#8468C2')
PINK = Box('frame', '#EE93D6', '#A8528F', '#3A1230', '#58204A', '#C66DAE')
CYAN = Box('frame', '#7FE0E0', '#3A9CA8', '#0F2E34', '#1A454E', '#58C0C8')

OAK = tint(WOOD, WOOD_KEYS, hue=0.075, sat_mul=0.95, light_mul=1.07)
SPRUCE = tint(WOOD, WOOD_KEYS, hue=0.065, sat_mul=0.72, light_mul=0.8)
BIRCH = tint(WOOD, WOOD_KEYS, hue=0.1, sat_mul=0.55, light_mul=1.22)
WALNUT = tint(WOOD, WOOD_KEYS, hue=0.02, sat_mul=0.8, light_mul=0.7, extra={'2': '#E8B84A'})
IRONWOOD = tint(tint(WOOD, WOOD_KEYS, hue=0.08, sat_mul=0.45, light_mul=0.85),
                ROOF_KEYS, hue=0.6, sat=0.04, light_mul=0.92)
CRIMSON = tint(tint(WOOD, WOOD_KEYS, hue=0.985, sat_mul=1.1, light_mul=0.82),
               ROOF_KEYS, hue=0.72, sat=0.07, light_mul=0.42, extra={'2': '#E0562E'})
EBONY = tint(tint(WOOD, WOOD_KEYS, hue=0.77, sat=0.2, light_mul=0.55),
             ROOF_KEYS, hue=0.77, sat=0.25, light_mul=0.5, extra={'2': '#9B5CFF', '1': '#1C1026'})
LAVENDER = tint(WOOD, WOOD_KEYS, hue=0.8, sat=0.18, light_mul=1.02)

SHOP = dict(
    palette=OAK,
    roof=dict(style='awning', stripe=8, hem='scallop',
              ramps=[('#1E4A28', '#2F6B3A', '#3F8F4F', '#4FA861', '#6CC27C'),
                     ('#7A6A48', '#C9BC98', '#E6DCC0', '#F1E9D2', '#FBF6E8')]),
    props=(['barrel', 'crate'], ['coins_low', 'coins']),
)

BLACK_MARKET = dict(
    palette=EBONY,
    roof=dict(style='cloth', hem='ragged', ramps=[('#12081C', '#24123A', '#34195A', '#472675', '#5E3596')]),
    sign=dict(letters=dict(face='#F4E6FF', band='#C9A0FF', overhang='#3A1E5A', edge='#6E3FB0', lip='#2E1A48'),
              glow='#B070FF'),
    bays={'*': 'curtain', 15: 'curtain_eyes'},
    props=(['potion', 'skull'], ['potion_green', 'potion']),
    decor=[lambda cv, ctx: shelf_candles(cv, ctx, (4, 5))],
    lights=[
        lambda cv, ctx: glow(cv, W // 2, TOP + 26, 60, '#6A3AB0', 0.22),
        lambda cv, ctx: fog(cv, ctx, '#7A40D0', 9, 0.45),
        lambda cv, ctx: [glow(cv, x, TOP + 33, 18, '#A060FF', 0.6) for x in (17, mirror(17))],
        lambda cv, ctx: [lantern(cv, x, TOP + 30, '#C08AFF', '#FFFFFF') for x in (15, mirror(15) - 4)],
        lambda cv, ctx: sparks(cv, [(22, TOP + 44), (27, TOP + 31), (150, TOP + 29), (155, TOP + 45),
                                    (60, TOP + 31), (118, TOP + 32), (98, TOP + 30)], '#C9A0FF'),
    ],
)

KILL_STREAKS = dict(
    palette=CRIMSON,
    roof=dict(style='slab', edge='#E8602E', glow_edge='#FF7A30'),
    sign=dict(letters=dict(face='#FFE2B0', band='#FF9A3C', overhang='#5A1408', edge='#9A2A10', lip='#4A1008'),
              glow='#FF6A2A'),
    props=(['candle', 'skull'], ['skull', 'candle']),
    lights=[
        lambda cv, ctx: [wall_torch(cv, x, TOP + 41) for x in (POST_L + 4, mirror(POST_L + 4))],
        lambda cv, ctx: [glow(cv, x, TOP + 43, 14, '#FF8A3A', 0.5) for x in (POST_L + 4, mirror(POST_L + 4))],
    ],
)

MEDIA_RANK = dict(
    palette=WALNUT,
    roof=dict(style='velvet', hem='fringe', ramps=[('#3A0610', '#6E0F22', '#8E1830', '#A8223C', '#C23A55')]),
    sign=dict(letters=dict(face='#FFE9A8', band='#E8B040', overhang='#5A3408', edge='#8A5A12', lip='#4A2E08'),
              bulbs=True),
    props=(['camera'], ['star']),
    lights=[lambda cv, ctx: glow(cv, W // 2, 8, 40, '#FFD27A', 0.18)],
)

KITS = dict(
    palette=IRONWOOD,
    props=(['sword'], ['shield']),
    decor=[lambda cv, ctx: [iron_band(cv, y) for y in (TOP + 46, ctx['front_top'] - 3)]],
)

SETTINGS = dict(
    palette=slate(WOOD),
    props=(['gear'], ['gear_small', 'gear']),
    decor=[lambda cv, ctx: [iron_band(cv, y) for y in (TOP + 46, ctx['front_top'] - 3)]],
)

TRASH = dict(palette=SPRUCE, iron_corners=True, props=(['boot'], ['can']))

MAP_SWITCHER = dict(
    palette=BIRCH,
    roof=dict(style='canvas', stripe=11, hem='rope',
              ramps=[('#6A5A3A', '#B8A578', '#D8C9A0', '#E6DAB6', '#F2EAD0'),
                     ('#5A4228', '#9A7E54', '#B89A6A', '#C8AC7C', '#D8BE90')]),
    props=(['map'], ['globe']),
)

BOOSTER = dict(
    palette=LAVENDER,
    roof=dict(style='awning', stripe=8, hem='scallop',
              ramps=[('#6A1E50', '#B8458E', '#E06AB2', '#EE8AC6', '#F8B0DA'),
                     ('#6A5A7A', '#D8CCE6', '#EEE6F6', '#F6F0FB', '#FFFFFF')]),
    sign=dict(letters=dict(face='#FFFFFF', band='#F7B0DC', overhang='#6A2A58', edge='#A8457E', lip='#5A2048'),
              glow='#FF8AD0'),
    props=(['gift'], ['rocket']),
    decor=[lambda cv, ctx: bunting(cv, ['#E86FB8', '#A77BE0', '#FFFFFF'])],
)

COINFLIP = dict(palette=WOOD, props=(['coins_low', 'coins'], ['coins', 'coins_low']))

QUESTS = dict(
    palette=SPRUCE,
    props=(['book'], ['quill']),
    decor=[lambda cv, ctx: [pinned_note(cv, slot_x(s % 9), slot_y(s // 9), pin, tilt)
                            for s, pin, tilt in zip(ctx['boarded'], ['#D93A3A', '#3F7FD9', '#E0A020', '#4CAF50', '#D93A3A'],
                                                    [0, 1, 0, 1, 0])]],
)

MENUS = {
    "shop_gui": dict(rows=3, buttons=[11, 12, 13, 14, 15], nav=[26], title="SHOP", theme=SHOP),
    "black_market_gui": dict(rows=3, buttons=[10, 12, 13, 14, 16], nav=[26], title="BLACK MARKET",
                             theme=BLACK_MARKET),
    "kill_streaks_gui": dict(rows=4, buttons=[12, 13, 14, 21, 22, 23], title="KILL STREAKS", theme=KILL_STREAKS),
    "media_rank_gui": dict(rows=3, boxes={12: RED, 13: PURPLE, 14: CYAN}, nav=[22], title="MEDIA RANK",
                           theme=MEDIA_RANK),
    "kits_gui": dict(rows=3, buttons=[10, 11, 12, 13, 14, 15, 16], title="KITS", theme=KITS),
    "settings_gui": dict(rows=4, buttons=[10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25], nav=[31],
                         title="SETTINGS", theme=SETTINGS),
    "trash_bin_gui": dict(rows=4, layout='crate', title="TRASH BIN", theme=TRASH),
    "map_switcher_gui": dict(rows=3, title="CURRENT MAP", boxes={10: GREEN, 13: PURPLE, 16: RED},
                             plaques={10: "TELEPORT", 13: "MAP INFO", 16: "CANCEL"}, theme=MAP_SWITCHER),
    "booster_gui": dict(rows=3, title="BOOST REWARDS", boxes={11: PINK, 13: PINK, 15: PINK},
                        plaques={11: "DAILY", 13: "WEEKLY", 15: "MONTHLY"}, theme=BOOSTER),
    "coinflip_gui": dict(rows=3, title="COINFLIP",
                         boxes={10: GREEN, 11: GREEN_FLAT, 12: GREEN, 13: GOLD, 14: RED, 15: RED_FLAT, 16: RED},
                         theme=COINFLIP),
    "quests_gui": dict(rows=3, buttons=[12, 14], title="QUESTS", theme=QUESTS),
}


def render(name):
    menu = MENUS[name]
    cv = Canvas(W, bottom_of(menu['rows']) + 3, menu.get('theme', {}).get('palette', WOOD))
    LAYOUTS[menu.get('layout', 'stall')](cv, menu)
    return cv.img


def item_slots(menu):
    """Slots a menu puts its items in (for previews)."""
    if menu.get('layout') == 'crate':
        return [], []
    return sorted(set(menu.get('buttons', [])) | set(menu.get('boxes', {}))), menu.get('nav', [])


# ------------------------------------------------------------------ preview


def vanilla_gui(rows):
    """Minecraft's default chest GUI, for previews."""
    h = 114 + rows * 18
    img = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    cv = Canvas(W, h, {'.': GUI_GREY})
    cv.img, cv.px = img, img.load()
    cv.rect(0, 0, W - 1, h - 1, hexc(GUI_GREY))
    cv.hline(2, W - 3, 0, hexc('#000000'))
    cv.hline(2, W - 3, h - 1, hexc('#000000'))
    cv.vline(0, 2, h - 3, hexc('#000000'))
    cv.vline(W - 1, 2, h - 3, hexc('#000000'))
    for i in (1, 2):
        cv.hline(2, W - 4, i, hexc('#FFFFFF'))
        cv.vline(i, 2, h - 4, hexc('#FFFFFF'))
    cv.put(1, 1, hexc('#000000'))
    for i in (h - 3, h - 2):
        cv.hline(3, W - 3, i, hexc('#555555'))
    for i in (W - 3, W - 2):
        cv.vline(i, 3, h - 3, hexc('#555555'))

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
    return img


STAND_INS = ['#F2C230', '#5AA0F0', '#D94A4A', '#9AA5B5', '#A98BF5', '#3FCF93', '#F08A3C']


def preview(texture, rows, slots, nav, scale=4):
    gui = vanilla_gui(rows)
    pad = 6
    canvas = Image.new("RGBA", (W + pad * 2, gui.height + TOP + pad * 2), hexc('#1E1E1E'))
    canvas.alpha_composite(gui, (pad, pad + TOP))
    canvas.alpha_composite(texture, (pad, pad))
    px = canvas.load()
    for i, s in enumerate(list(slots) + list(nav)):
        x, y = slot_x(s % 9) + pad, slot_y(s // 9) + pad
        color = hexc(STAND_INS[i % len(STAND_INS)]) if s not in nav else hexc('#E04848')
        for dy in range(10):
            for dx in range(10):
                if (dx - 4.5) ** 2 + (dy - 4.5) ** 2 <= 22:
                    tone = color
                    if dy < 3 or dx < 2:
                        tone = blend(color, (255, 255, 255, 255), 0.35)
                    if dy > 7 or dx > 8:
                        tone = blend(color, (0, 0, 0, 255), 0.35)
                    px[x + 4 + dx, y + 4 + dy] = tone
    return canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)


def contact_sheet(images, columns=3, gap=12, background='#1E2127'):
    cell_w, cell_h = max(im.width for im in images), max(im.height for im in images)
    rows = (len(images) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * cell_w + (columns + 1) * gap, rows * cell_h + (rows + 1) * gap), hexc(background))
    for i, im in enumerate(images):
        r, c = divmod(i, columns)
        sheet.alpha_composite(im, (gap + c * (cell_w + gap), gap + r * (cell_h + gap)))
    return sheet


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(root, "textures")
    out_preview = os.path.join(root, "previews")
    os.makedirs(out, exist_ok=True)
    os.makedirs(out_preview, exist_ok=True)
    sizes = {}
    shots = []
    for name, menu in MENUS.items():
        img = render(name)
        img.save(os.path.join(out, name + ".png"))
        slots, nav = item_slots(menu)
        shot = preview(img, menu['rows'], slots, nav)
        shot.save(os.path.join(out_preview, name + ".png"))
        shots.append(shot.resize((shot.width // 2, shot.height // 2), Image.NEAREST))
        sizes[name] = {"rows": menu['rows'], "height": img.height, "ascent": 13 + TOP}
    contact_sheet(shots).save(os.path.join(out_preview, "all_menus.png"))
    return sizes


if __name__ == "__main__":
    for name, size in main().items():
        print(f"{name}: {size['rows']} rows, {W}x{size['height']}")
