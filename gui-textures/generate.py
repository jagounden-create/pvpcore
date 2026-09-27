#!/usr/bin/env python3
"""
Market-stall Nexo GUI textures for chest menus, drawn as pixel art at Minecraft's GUI scale.

Each menu is a wooden stall: a carved sign on a slab roof, braces down to two posts, a shelf
unit whose cubbies sit exactly under the menu's items, a ledge, and a panelled counter front
with drawers. Every menu also comes in a slate-grey version (same art, other palette).

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
import os
from PIL import Image

W = 176
TOP = 6           # rows of art above the GUI's top edge (the sign)
HEADER = 17       # vanilla title bar height
PITCH = 18
LEFT = 7
GUI_GREY = '#C6C6C6'

# ------------------------------------------------------------------ colours


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def blend(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


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
    'z': '#614C3C',  # the sign's shadow on the roof
    'F': '#7D624D',  # roof outline
    'b': '#917560',  # roof front
    'a': '#B6997B',  # roof top
    'w': '#D1B18E',  # roof front edge
    # on the menu background
    'q': '#8B8B8B',  # drop shadow
    '.': GUI_GREY,
}


def slate(palette):
    """The same palette in slate grey: keep each colour's lightness, swap the hue."""
    out = {}
    for key, value in palette.items():
        if key in 'q.':
            out[key] = value
            continue
        r, g, b, _ = hexc(value)
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        l = min(1.0, l * 1.06 + 0.02)
        s = 0.16 if l < 0.75 else 0.10
        r, g, b = colorsys.hls_to_rgb(0.57, l, s)
        out[key] = '#%02X%02X%02X' % (round(r * 255), round(g * 255), round(b * 255))
    return out


PALETTES = {"": WOOD, "_gray": slate(WOOD)}


class Canvas:
    def __init__(self, width, height, palette):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = width, height
        self.pal = {k: hexc(v) for k, v in palette.items()}

    def color(self, c):
        return self.pal[c] if isinstance(c, str) else c

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = self.color(c)

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


def carved_text(cv, x, y, text):
    """Sign letters: a light face with a darker band through the middle, shadow where the
    upper half overhangs, and a two-row block edge underneath."""
    cells = glyph_cells(text, x, y, SIGN_FONT)
    cx = x
    for ch in text:
        rows = SIGN_FONT[ch]
        for gy, line in enumerate(rows):
            for gx, p in enumerate(line):
                px, py = cx + gx, y + gy
                if p == '#':
                    cv.put(px, py, 's' if gy == 2 else 'l')
                elif gy > 0 and rows[gy - 1][gx] == '#':
                    cv.put(px, py, 'c')
        base = rows[-1]
        for gx, p in enumerate(base):
            if p == '#':
                cv.put(cx + gx, y + 6, 'v')
                cv.put(cx + gx, y + 7, 'v')
                cv.put(cx + gx, y + 8, 's')
        cx += len(rows[0]) + 1
    return cells


def small_text(cv, x, y, text, face, shadow):
    for (px, py) in glyph_cells(text, x, y, SMALL):
        cv.put(px, py + 1, shadow)
    for (px, py) in glyph_cells(text, x, y, SMALL):
        cv.put(px, py, face)


# ------------------------------------------------------------------ roof and sign


def draw_cover(cv, rows):
    """The menu's grey over the chest's own slot grid."""
    cv.rect(3, TOP + 3, W - 4, slot_y(rows), '.')


def draw_roof(cv, y0=TOP + 6):
    """A slab roof seen from a little above: bevelled back corners, a lit front edge."""
    insets = [4, 4, 3, 3, 2, 2, 1, 1, 0, 0]
    cv.hline(5 + insets[0], mirror(5 + insets[0]), y0, 'F')
    for i, inset in enumerate(insets[1:], start=1):
        y = y0 + i
        cv.hline(6 + inset, mirror(6 + inset), y, 'a')
        cv.put(5 + inset, y, 'F')
        cv.put(mirror(5 + inset), y, 'F')
    y = y0 + len(insets)
    cv.put(5, y, 'a')
    cv.hline(6, mirror(6), y, 'w')
    cv.put(mirror(5), y, 'a')
    for y in range(y0 + len(insets) + 1, y0 + len(insets) + 6):
        cv.hline(6, mirror(6), y, 'b')
        cv.put(5, y, 'F')
        cv.put(mirror(5), y, 'F')
    cv.hline(6, mirror(6), y0 + len(insets) + 6, 'x')


def draw_sign(cv, text):
    tw = text_width(text, SIGN_FONT)
    width = max(78, tw + 22)
    x0 = W // 2 - width // 2
    x1 = x0 + width - 1
    # its shadow on the roof
    cv.hline(x0 - 1, x1 + 1, 15, 'z')
    cv.hline(x0 - 1, x1 + 1, 16, 'z')
    cv.hline(x0 - 1, x1 + 1, 17, 'z')
    cv.hline(x0, x1, 18, 'z')
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
    carved_text(cv, W // 2 - tw // 2, 4, text)


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


def cubby_opening(cv, x, y, boarded=False):
    """One wooden cubby of the shelf unit, 18 px wide, frame at (x, y)."""
    inner0, inner1 = x + 2, x + 15
    cv.put(x + 1, y, 'y')
    cv.put(x + 16, y, 'y')
    for yy in range(y + 1, y + 17):
        cv.put(x + 1, yy, 's')
        cv.put(x + 16, yy, 's')
    if boarded:
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


def draw_unit(cv, cubby_rows, buttons, boxes):
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
            cubby_opening(cv, slot_x(c), y, boarded=s not in buttons)
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


# ------------------------------------------------------------------ layouts


def stall(cv, menu):
    rows, buttons = menu['rows'], set(menu.get('buttons', []))
    boxes = menu.get('boxes', {})
    buttons |= set(boxes)
    cubby_rows = sorted({s // 9 for s in buttons})
    bottom = bottom_of(rows)
    draw_cover(cv, rows)
    front_top = draw_unit(cv, cubby_rows, buttons, boxes)
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
    draw_roof(cv)
    draw_sign(cv, menu['title'])


def crate(cv, menu):
    """Every slot left open (players drop items anywhere): a crate of cubbies under a lid."""
    rows = menu['rows']
    bottom = bottom_of(rows)
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
    del bottom
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
    draw_sign(cv, menu['title'])


LAYOUTS = {'stall': stall, 'crate': crate}

# ------------------------------------------------------------------ menus

GREEN = Box('frame', '#87BA65', '#5A823C', '#243B19', '#334D20', '#669448')
GREEN_FLAT = Box('flat', '#87BA65', '#5A823C', '#243B19', '#334D20', '#669448')
RED = Box('frame', '#BA5D64', '#82353E', '#471616', '#5C2121', '#944047')
RED_FLAT = Box('flat', '#BA5D64', '#82353E', '#471616', '#5C2121', '#944047')
GOLD = Box('lit', '#FFD160', '#67521F', '#67521F', '#876B28', '#876B28')
PURPLE = Box('frame', '#A98AE0', '#6C4FA3', '#24163D', '#35255A', '#8468C2')
PINK = Box('frame', '#EE93D6', '#A8528F', '#3A1230', '#58204A', '#C66DAE')

MENUS = {
    "shop_gui": dict(rows=3, buttons=[11, 12, 13, 14, 15], nav=[26], title="SHOP"),
    "black_market_gui": dict(rows=3, buttons=[10, 12, 13, 14, 16], nav=[26], title="BLACK MARKET"),
    "kill_streaks_gui": dict(rows=4, buttons=[12, 13, 14, 21, 22, 23], title="KILL STREAKS"),
    "media_rank_gui": dict(rows=3, buttons=[12, 13, 14], nav=[22], title="MEDIA RANK"),
    "kits_gui": dict(rows=3, buttons=[10, 11, 12, 13, 14, 15, 16], title="KITS"),
    "settings_gui": dict(rows=4, buttons=[10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25], nav=[31],
                         title="SETTINGS"),
    "trash_bin_gui": dict(rows=4, layout='crate', title="TRASH BIN"),
    "map_switcher_gui": dict(rows=3, title="CURRENT MAP", boxes={10: GREEN, 13: PURPLE, 16: RED},
                             plaques={10: "TELEPORT", 13: "MAP INFO", 16: "CANCEL"}),
    "booster_gui": dict(rows=3, title="BOOSTER REWARDS", boxes={11: PINK, 13: PINK, 15: PINK},
                        plaques={11: "DAILY", 13: "WEEKLY", 15: "MONTHLY"}),
    "coinflip_gui": dict(rows=3, title="COINFLIP",
                         boxes={10: GREEN, 11: GREEN_FLAT, 12: GREEN, 13: GOLD, 14: RED, 15: RED_FLAT, 16: RED}),
    "quests_gui": dict(rows=3, buttons=[12, 14], title="QUESTS"),
}


def render(name, palette=WOOD):
    menu = MENUS[name]
    cv = Canvas(W, bottom_of(menu['rows']) + 3, palette)
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
    for suffix, palette in PALETTES.items():
        shots = []
        for name, menu in MENUS.items():
            img = render(name, palette)
            img.save(os.path.join(out, name + suffix + ".png"))
            slots, nav = item_slots(menu)
            shot = preview(img, menu['rows'], slots, nav)
            shot.save(os.path.join(out_preview, name + suffix + ".png"))
            shots.append(shot.resize((shot.width // 2, shot.height // 2), Image.NEAREST))
            sizes[name + suffix] = {"rows": menu['rows'], "height": img.height, "ascent": 13 + TOP}
        contact_sheet(shots).save(os.path.join(out_preview, "all_menus" + suffix + ".png"))
    return sizes


if __name__ == "__main__":
    for name, size in main().items():
        print(f"{name}: {size['rows']} rows, {W}x{size['height']}")
