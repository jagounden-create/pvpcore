#!/usr/bin/env python3
"""
Slate-grey Nexo GUI textures for chest menus, drawn as pixel art at Minecraft's GUI scale.

Every texture is 176 px wide (a chest GUI) and starts TOP px above the GUI so the sign can sit
on the roof and stick out over the top edge. Cubbies are drawn exactly where the menu puts its
items: slot (row r, column c) has its frame at x = 7 + 18c, y = TOP + 17 + 18r.

Three layouts:
  stall  a roofed stall: cubbies on a shelf, pillars in the gaps, a panelled counter
  bays   the stall body split into three coloured bays with a label each (map switcher)
  bin    an open grid of slots under a lid, for menus where players drop items (trash)

The picture paints the menu's own grey over the chest's empty slot grid (except in bin
menus), so only the cubbies show. The player inventory below is left untouched.

Nexo glyph for a texture of height H:  ascent: 13 + TOP   height: H
Menu title:                            <white><shift:-8><glyph:ID>

Run:  python3 generate.py      (needs Pillow: pip install pillow)
"""
import os
from PIL import Image

W = 176
TOP = 8           # rows of art above the GUI's top edge (the sign)
HEADER = 17       # vanilla title bar height
PITCH = 18
LEFT = 7
GUI_GREY = '#C6C6C6'

# ------------------------------------------------------------------ canvas helpers


def hexc(value, alpha=255):
    value = value.lstrip('#')
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def blend(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, width, height):
        self.img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        self.px = self.img.load()
        self.w, self.h = width, height

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.px[x, y] = c

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[x, y]
        return (0, 0, 0, 0)

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
        r = 1 if round_corners else 0
        self.hline(x0 + r, x1 - r, y0, c)
        self.hline(x0 + r, x1 - r, y1, c)
        self.vline(x0, y0 + r, y1 - r, c)
        self.vline(x1, y0 + r, y1 - r, c)

    def shade(self, x, y, color, amount):
        """Tint a pixel that is already drawn (shadows)."""
        p = self.get(x, y)
        if p[3]:
            self.px[x, y] = blend(p, color, amount)[:3] + (p[3],)


class Ramp:
    """Five tones of one material, darkest (outline) to brightest (highlight)."""

    def __init__(self, outline, dark, mid, light, high):
        self.outline, self.dark, self.mid, self.light, self.high = (hexc(v) for v in (outline, dark, mid, light, high))


BLACK = (0, 0, 0, 255)


def shape(cv, cells, ramp):
    """Fill a set of pixels as one raised piece: lit top-left edge, shaded bottom-right, outline."""
    for (x, y) in cells:
        tone = ramp.mid
        if (x, y - 1) not in cells or (x - 1, y) not in cells:
            tone = ramp.light
        if (x, y + 1) not in cells or (x + 1, y) not in cells:
            tone = ramp.dark
        cv.put(x, y, tone)
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                cv.put(x + dx, y + dy, ramp.outline)


def boards(cv, x0, y0, x1, y1, ramp, width=9):
    """Clean vertical boards: flat face, lit left edge, a groove on the right."""
    x = x0
    i = 0
    while x <= x1:
        z = min(x1, x + width - 1)
        face = ramp.mid if i % 2 == 0 else blend(ramp.mid, ramp.light, 0.35)
        cv.rect(x, y0, z, y1, face)
        cv.vline(x, y0, y1, ramp.light)
        cv.vline(z, y0, y1, ramp.dark)
        x = z + 1
        i += 1


# ------------------------------------------------------------------ geometry


def ry(rel):
    """Texture row of a row measured from the GUI's top edge."""
    return TOP + rel


def slot_xy(slot):
    row, col = divmod(slot, 9)
    return LEFT + col * PITCH, ry(HEADER + row * PITCH)


def slot_center_x(slot):
    return LEFT + (slot % 9) * PITCH + 9


# ------------------------------------------------------------------ lettering

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

# A compact 5-row font for labels where the 7-row one won't fit
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


def text_cells(text, x, y, font=FONT, bold=False):
    """Pixels of a line of text. Bold lights each pixel's right neighbour too (sign lettering)."""
    cells = set()
    cx = x
    for ch in text:
        rows = font[ch]
        extra = 1 if bold and ch != ' ' else 0
        for gy, line in enumerate(rows):
            for gx, p in enumerate(line):
                if p == '#':
                    cells.add((cx + gx, y + gy))
                    if extra:
                        cells.add((cx + gx + 1, y + gy))
        cx += len(rows[0]) + extra + 1
    return cells


def text_width(text, font=FONT, bold=False):
    return sum(len(font[ch][0]) + (1 if bold and ch != ' ' else 0) + 1 for ch in text) - 1


def raised_text(cv, x, y, text, face, shadow, highlight, font=FONT, bold=True):
    """Letters standing off a plate: light face, lit top edge, a dark shadow down and right."""
    cells = text_cells(text, x, y, font, bold)
    for (px, py) in cells:
        for (dx, dy) in ((1, 1), (0, 1), (1, 0)):
            if (px + dx, py + dy) not in cells:
                cv.put(px + dx, py + dy, shadow)
    for (px, py) in cells:
        cv.put(px, py, highlight if (px, py - 1) not in cells else face)


# ------------------------------------------------------------------ palette (slate grey)

OUT = '#1F262C'
ROOF = Ramp(OUT, '#6F818E', '#8597A3', '#99A9B4', '#B1BFC8')
SIGN = Ramp(OUT, '#3E4C56', '#4D5D68', '#617380', '#7A8C99')
WALL = Ramp(OUT, '#45535D', '#586B79', '#607583', '#6F8595')
FRAME = Ramp(OUT, '#586B79', '#6F8595', '#8098A9', '#8EA7B8')
SIGN_TEXT = (hexc('#D2DEE7'), hexc('#1A2126'), hexc('#EEF3F7'))
UNDER_ROOF = hexc('#3A4349')
RECESS = hexc('#303C45')


class Cubby:
    """How a slot looks. kind: 'frame' (light frame, dark inside), 'flat' (one dark colour),
    'lit' (bright outline, like a selected slot)."""

    def __init__(self, kind, outline, frame_light, frame, inner, inner_shade):
        self.kind = kind
        self.outline, self.frame_light, self.frame, self.inner, self.inner_shade = (
            hexc(v) for v in (outline, frame_light, frame, inner, inner_shade))


def tinted(accent):
    """A cubby framed in an accent colour."""
    a = hexc(accent)
    return Cubby('frame', OUT, '#%02X%02X%02X' % blend(a, (255, 255, 255, 255), 0.35)[:3],
                 '#%02X%02X%02X' % a[:3], '#303C45', '#28323A')


SLATE = Cubby('frame', OUT, '#A3B8C6', '#8098A9', '#45535D', '#3A4650')
NAV = Cubby('frame', OUT, '#6F8595', '#586B79', '#303C45', '#28323A')
GREEN = Cubby('frame', OUT, '#8FD26A', '#5E9A3C', '#2E4F22', '#243F1B')
GREEN_FLAT = Cubby('flat', OUT, '#8FD26A', '#2A4A1F', '#33582A', '#26431D')
RED = Cubby('frame', OUT, '#E07070', '#A8434A', '#5A1F24', '#4A181D')
RED_FLAT = Cubby('flat', OUT, '#E07070', '#521A1F', '#5E2228', '#4A181D')
GOLD_LIT = Cubby('lit', OUT, '#F8D25A', '#C9A33A', '#8C7A2C', '#6E5F20')


def cubby(cv, x, y, style):
    """An 18x18 slot box. The item Minecraft draws covers the middle 16x16."""
    s = style
    cv.outline(x, y, x + 17, y + 17, s.outline)
    if s.kind == 'frame':
        cv.rect(x + 1, y + 1, x + 16, y + 16, s.frame)
        cv.hline(x + 1, x + 16, y + 1, s.frame_light)
        cv.vline(x + 1, y + 1, y + 16, s.frame_light)
        cv.rect(x + 3, y + 3, x + 14, y + 14, s.inner)
        cv.outline(x + 3, y + 3, x + 14, y + 14, s.outline)
        cv.hline(x + 4, x + 13, y + 4, s.inner_shade)
        cv.vline(x + 4, y + 4, y + 13, s.inner_shade)
    elif s.kind == 'flat':
        cv.rect(x + 1, y + 1, x + 16, y + 16, s.inner)
        cv.hline(x + 1, x + 16, y + 1, s.inner_shade)
        cv.vline(x + 1, y + 1, y + 16, s.inner_shade)
        cv.hline(x + 2, x + 16, y + 16, s.frame)
        cv.vline(x + 16, y + 2, y + 16, s.frame)
    else:  # lit
        cv.rect(x + 1, y + 1, x + 16, y + 16, s.inner)
        cv.outline(x + 1, y + 1, x + 16, y + 16, s.frame_light)
        cv.hline(x + 2, x + 15, y + 2, s.frame)
        cv.vline(x + 2, y + 2, y + 15, s.frame)
        cv.hline(x + 3, x + 15, y + 15, s.inner_shade)
        cv.vline(x + 15, y + 3, y + 15, s.inner_shade)


# ------------------------------------------------------------------ shared pieces

ROOF_X0, ROOF_X1 = 5, W - 6


class Roof:
    def __init__(self, top=3, face=9, band=5):
        self.top, self.face, self.band = top, face, band

    def bottom(self):
        """First row under the roof, GUI-relative."""
        return self.top + 1 + self.face + self.band + 1


STALL_ROOF = Roof()
LID = Roof(top=1, face=6, band=4)


def cover_grid(cv, rows):
    """Paint the menu's grey over the chest slot grid; the cubbies replace it."""
    cv.rect(4, ry(4), W - 5, ry(HEADER + rows * PITCH + 1), hexc(GUI_GREY))


def draw_roof(cv, roof):
    top = ry(roof.top)
    cells = set()
    for i in range(roof.face + roof.band + 1):
        inset = max(0, (roof.face - 1 - i) // 3) if i < roof.face else 0
        for x in range(ROOF_X0 + inset, ROOF_X1 - inset + 1):
            cells.add((x, top + 1 + i))
    for (x, y) in cells:
        i = y - top - 1
        if i < roof.face:
            tone = ROOF.light
            if i == 0 or i == roof.face - 1:
                tone = ROOF.high
        else:
            j = i - roof.face
            tone = ROOF.mid if j < roof.band - 1 else ROOF.dark
            if j == 0:
                tone = blend(ROOF.mid, ROOF.dark, 0.5)
        cv.put(x, y, tone)
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                cv.put(x + dx, y + dy, ROOF.outline)
    under = ry(roof.bottom())
    for x in range(ROOF_X0, ROOF_X1 + 1):
        cv.shade(x, under, BLACK, 0.4)
        cv.shade(x, under + 1, BLACK, 0.2)


def draw_sign(cv, text):
    width = text_width(text, bold=True) + 18
    x0 = W // 2 - width // 2
    x1 = x0 + width - 1
    y0, y1 = 0, 16
    for x in range(x0 + 1, x1 + 2):
        cv.shade(x, y1 + 1, BLACK, 0.35)
        cv.shade(x, y1 + 2, BLACK, 0.15)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SIGN.mid)
    cv.outline(x0, y0, x1, y1, SIGN.outline, round_corners=True)
    # bevelled rim around a recessed face
    cv.hline(x0 + 1, x1 - 1, y0 + 1, SIGN.high)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, SIGN.light)
    cv.hline(x0 + 2, x1 - 1, y1 - 1, SIGN.dark)
    cv.vline(x1 - 1, y0 + 2, y1 - 1, SIGN.dark)
    cv.outline(x0 + 3, y0 + 3, x1 - 3, y1 - 3, SIGN.dark)
    cv.rect(x0 + 4, y0 + 4, x1 - 4, y1 - 4, blend(SIGN.mid, SIGN.dark, 0.35))
    cv.hline(x0 + 4, x1 - 4, y1 - 3, SIGN.light)
    cv.vline(x1 - 3, y0 + 4, y1 - 3, SIGN.light)
    # rivets
    for rx in (x0 + 2, x1 - 2):
        cv.put(rx, y0 + 3, SIGN.high)
        cv.put(rx, y1 - 3, SIGN.dark)
    face, shadow, highlight = SIGN_TEXT
    raised_text(cv, W // 2 - text_width(text, bold=True) // 2, y0 + 4, text, face, shadow, highlight)


def draw_posts(cv, x_left, x_right, width, y0, y1):
    for x in (x_left, x_right):
        cv.rect(x, y0, x + width - 1, y1, FRAME.mid)
        cv.vline(x + 1, y0, y1, FRAME.light)
        cv.vline(x + width - 2, y0, y1, FRAME.dark)
        cv.vline(x, y0, y1, FRAME.outline)
        cv.vline(x + width - 1, y0, y1, FRAME.outline)
        cv.hline(x, x + width - 1, y1, FRAME.outline)


def draw_brackets(cv, roof, post_l, post_w):
    """Diagonal braces from the roof's overhang down across the front of each post."""
    top = ry(roof.bottom())
    length = 13
    for side in (0, 1):
        cells = set()
        for i in range(length):
            cx = ROOF_X0 + 2 + i * (post_l + post_w + 1 - ROOF_X0 - 2) // (length - 1)
            for x in range(cx - 1, cx + 2):
                cells.add((x, top + i))
        for x in range(ROOF_X0 + 1, ROOF_X0 + 6):
            cells.add((x, top))
        if side == 1:
            cells = {(W - 1 - x, y) for (x, y) in cells}
        shape(cv, cells, FRAME)


def draw_beam(cv, roof, x0, x1):
    """A rail under the roof across the body."""
    b = ry(roof.bottom())
    cv.rect(x0, b, x1, b + 2, UNDER_ROOF)
    cv.hline(x0, x1, b + 3, FRAME.dark)
    cv.hline(x0, x1, b + 4, FRAME.outline)


# ------------------------------------------------------------------ layouts

POST_W = 6
POST_L = 12
POST_R = W - POST_L - POST_W


def gap_columns(buttons):
    """Per row: the empty columns between that row's first and last button (they get pillars)."""
    rows = {}
    for s in buttons:
        rows.setdefault(s // 9, set()).add(s % 9)
    gaps = {}
    for r, cols in rows.items():
        for c in range(min(cols), max(cols) + 1):
            if c not in cols:
                gaps.setdefault(c, []).append(r)
    return rows, gaps


def draw_pillar(cv, col, row0, row1):
    """A slim raised panel filling an empty column between cubbies."""
    x0 = LEFT + col * PITCH + 2
    x1 = x0 + 13
    y0 = slot_xy(row0 * 9)[1] + 1
    y1 = slot_xy(row1 * 9)[1] + 16
    cv.rect(x0, y0, x1, y1, FRAME.dark)
    cv.outline(x0, y0, x1, y1, FRAME.outline)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, FRAME.mid)
    cv.hline(x0 + 1, x1 - 1, y0 + 1, FRAME.mid)
    cv.rect(x0 + 4, y0 + 3, x1 - 4, y1 - 3, WALL.dark)
    cv.outline(x0 + 4, y0 + 3, x1 - 4, y1 - 3, RECESS)
    cv.hline(x0 + 5, x1 - 4, y1 - 3, FRAME.mid)
    cv.vline(x1 - 4, y0 + 4, y1 - 3, FRAME.mid)


def draw_display(cv, buttons, styles):
    """A recessed box behind each row's run of cubbies, pillars in the gaps, then the cubbies."""
    rows, gaps = gap_columns(buttons)
    for r, cols in rows.items():
        x0 = LEFT + min(cols) * PITCH
        x1 = LEFT + max(cols) * PITCH + 17
        y0 = slot_xy(r * 9)[1]
        cv.rect(x0 - 2, y0 - 2, x1 + 2, y0 + 17, RECESS)
        cv.hline(x0 - 2, x1 + 2, y0 - 2, blend(RECESS, BLACK, 0.3))
    for col, rs in gaps.items():
        rs = sorted(rs)
        start = rs[0]
        for i, r in enumerate(rs):
            if i + 1 == len(rs) or rs[i + 1] != r + 1:
                draw_pillar(cv, col, start, r)
                if i + 1 < len(rs):
                    start = rs[i + 1]
    for s in buttons:
        cubby(cv, *slot_xy(s), styles.get(s, SLATE))


def draw_shelf(cv, buttons):
    """A ledge under the lowest row of cubbies, a little wider than they are."""
    xs = [slot_xy(s)[0] for s in buttons]
    x0, x1 = min(xs) - 4, max(xs) + 21
    y = slot_xy(max(buttons))[1] + 18
    cv.rect(x0, y, x1, y + 4, FRAME.mid)
    cv.outline(x0, y, x1, y + 4, FRAME.outline)
    cv.hline(x0 + 1, x1 - 1, y + 1, FRAME.high)
    cv.hline(x0 + 1, x1 - 1, y + 2, FRAME.light)
    cv.hline(x0 + 1, x1 - 1, y + 3, FRAME.dark)
    for x in range(x0 + 1, x1):
        cv.shade(x, y + 5, BLACK, 0.35)
    return y + 5


def draw_front(cv, top, bottom, x0, x1):
    """Counter front: one panel per column, raised and recessed in turn."""
    for c in range(9):
        a = max(x0, LEFT + c * PITCH)
        z = min(x1, LEFT + c * PITCH + PITCH - 1)
        if a > z:
            continue
        if c % 2 == 1:
            cv.rect(a, top, z, bottom, FRAME.mid)
            cv.hline(a, z, top, FRAME.high)
            cv.vline(a, top, bottom, FRAME.light)
            cv.vline(z - 1, top + 1, bottom, FRAME.dark)
            cv.hline(a, z, bottom - 1, FRAME.dark)
            if bottom - top >= 8:
                cv.outline(a + 3, top + 3, z - 4, bottom - 3, FRAME.dark)
                cv.hline(a + 4, z - 4, bottom - 3, FRAME.high)
                cv.vline(z - 4, top + 4, bottom - 3, FRAME.high)
            cv.outline(a - 1, top - 1, z, bottom, FRAME.outline)
        else:
            cv.rect(a, top, z, bottom - 2, WALL.dark)
            cv.hline(a, z, top, WALL.mid)
            cv.vline(a + (z - a) // 2, top + 1, bottom - 3, RECESS)
            cv.hline(a, z, bottom - 2, WALL.outline)
            for x in range(a, z + 1):
                cv.shade(x, bottom - 1, BLACK, 0.25)


def draw_nav(cv, slot):
    x, y = slot_xy(slot)
    cv.rect(x - 1, y - 1, x + 18, y + 18, FRAME.outline)
    cubby(cv, x, y, NAV)


def stall(cv, menu):
    rows, buttons = menu['rows'], menu['buttons']
    bottom = ry(HEADER + rows * PITCH)
    cover_grid(cv, rows)
    body_top = ry(STALL_ROOF.bottom())
    boards(cv, POST_L + POST_W, body_top, POST_R - 1, bottom - 1, WALL)
    draw_beam(cv, STALL_ROOF, POST_L, POST_R + POST_W - 1)
    draw_posts(cv, POST_L, POST_R, POST_W, body_top, bottom)
    draw_brackets(cv, STALL_ROOF, POST_L, POST_W)
    draw_display(cv, buttons, menu.get('styles', {}))
    front_top = draw_shelf(cv, buttons) + 1
    if front_top < bottom - 2:
        draw_front(cv, front_top, bottom, POST_L + POST_W, POST_R - 1)
    for s in menu.get('nav', []):
        draw_nav(cv, s)
    for x in range(POST_L, POST_R + POST_W):
        cv.shade(x, bottom + 1, BLACK, 0.25)
    draw_roof(cv, STALL_ROOF)
    draw_sign(cv, menu['title'])


def bays(cv, menu):
    """The body split into bays, one per button: a coloured strip on top, the cubby, a label."""
    rows = menu['rows']
    bottom = ry(HEADER + rows * PITCH)
    cover_grid(cv, rows)
    body_top = ry(STALL_ROOF.bottom())
    post_w = 4
    boards(cv, ROOF_X0, body_top, ROOF_X1, bottom - 1, WALL)
    draw_beam(cv, STALL_ROOF, ROOF_X0, ROOF_X1)
    draw_posts(cv, ROOF_X0, ROOF_X1 - post_w + 1, post_w, body_top, bottom)
    edges = menu['bay_edges']
    # raised side panels where the bays don't reach
    for a, z in ((ROOF_X0 + post_w, edges[0] - 2), (edges[-1] - 2, ROOF_X1 - post_w)):
        if z - a >= 6:
            cv.rect(a, body_top + 5, z, bottom, FRAME.mid)
            cv.outline(a, body_top + 5, z, bottom, FRAME.outline)
            cv.hline(a + 1, z - 1, body_top + 6, FRAME.high)
            cv.vline(a + 1, body_top + 6, bottom - 1, FRAME.light)
            cv.outline(a + 4, body_top + 9, z - 4, bottom - 4, FRAME.dark)
            cv.hline(a + 5, z - 4, bottom - 4, FRAME.high)
            cv.vline(z - 4, body_top + 10, bottom - 4, FRAME.high)
    for i, (slot, text, color) in enumerate(menu['bays']):
        x0, x1 = edges[i], edges[i + 1] - 4
        y0, y1 = body_top + 6, bottom - 3
        accent = hexc(color)
        cv.rect(x0, y0, x1, y1, RECESS)
        cv.outline(x0, y0, x1, y1, hexc(OUT))
        cv.hline(x0 + 1, x1 - 1, y1 - 1, WALL.dark)
        cv.hline(x0 + 1, x1 - 1, y0 + 1, blend(accent, (255, 255, 255, 255), 0.35))
        cv.hline(x0 + 1, x1 - 1, y0 + 2, accent)
        cv.hline(x0 + 1, x1 - 1, y0 + 3, blend(accent, BLACK, 0.45))
        # divider after the bay
        if i + 1 < len(menu['bays']):
            d0 = x1 + 1
            cv.rect(d0, body_top + 5, d0 + 2, bottom, FRAME.mid)
            cv.vline(d0, body_top + 5, bottom, FRAME.outline)
            cv.vline(d0 + 2, body_top + 5, bottom, FRAME.outline)
            cv.vline(d0 + 1, body_top + 5, bottom - 1, FRAME.light)
        cubby(cv, *slot_xy(slot), tinted(color))
        font = SMALL if menu.get('small_labels') else FONT
        tw = text_width(text, font)
        ty = ry(HEADER + 2 * PITCH) + (9 - len(font['A']) // 2)
        raised_text(cv, slot_center_x(slot) - tw // 2, ty, text, accent, hexc('#12171B'),
                    blend(accent, (255, 255, 255, 255), 0.4), font, bold=False)
    for x in range(ROOF_X0, ROOF_X1 + 1):
        cv.shade(x, bottom + 1, BLACK, 0.25)
    draw_roof(cv, STALL_ROOF)
    draw_sign(cv, menu['title'])


def slot_cell(cv, x, y):
    """An open slot to drop items in: a recessed square like vanilla's, in slate."""
    cv.rect(x, y, x + 17, y + 17, WALL.dark)
    cv.hline(x, x + 16, y, RECESS)
    cv.vline(x, y, y + 16, RECESS)
    cv.hline(x + 1, x + 15, y + 1, blend(RECESS, WALL.dark, 0.5))
    cv.vline(x + 1, y + 1, y + 15, blend(RECESS, WALL.dark, 0.5))
    cv.hline(x + 1, x + 17, y + 17, FRAME.light)
    cv.vline(x + 17, y + 1, y + 17, FRAME.light)


def bin_layout(cv, menu):
    """Every slot stays open (players drop items anywhere) inside a slate bin under a lid."""
    rows = menu['rows']
    bottom = ry(HEADER + rows * PITCH)
    top = ry(LID.bottom())
    x0, x1 = 4, W - 5
    cv.rect(x0, top, x1, bottom + 2, FRAME.mid)
    boards(cv, x0 + 1, top, x1 - 1, bottom + 1, FRAME, width=6)
    cv.outline(x0, top - 1, x1, bottom + 2, FRAME.outline)
    cv.rect(LEFT - 1, ry(HEADER) - 1, LEFT + 9 * PITCH, bottom, RECESS)
    for r in range(rows):
        for c in range(9):
            slot_cell(cv, LEFT + c * PITCH, ry(HEADER + r * PITCH))
    draw_roof(cv, LID)
    draw_sign(cv, menu['title'])


LAYOUTS = {'stall': stall, 'bays': bays, 'bin': bin_layout}

# ------------------------------------------------------------------ menus

MENUS = {
    "shop_gui": dict(rows=3, buttons=[11, 12, 13, 14, 15], nav=[26], title="SHOP"),
    "black_market_gui": dict(rows=3, buttons=[10, 12, 13, 14, 16], nav=[26], title="BLACK MARKET"),
    "kill_streaks_gui": dict(rows=4, buttons=[12, 13, 14, 21, 22, 23], title="KILL STREAKS"),
    "media_rank_gui": dict(rows=3, buttons=[12, 13, 14], nav=[22], title="MEDIA RANK"),
    "kits_gui": dict(rows=3, buttons=[10, 11, 12, 13, 14, 15, 16], title="KITS"),
    "settings_gui": dict(rows=4, buttons=[10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25], nav=[31],
                         title="SETTINGS"),
    "trash_bin_gui": dict(rows=4, layout='bin', title="TRASH BIN"),
    "map_switcher_gui": dict(rows=3, layout='bays', title="CURRENT MAP", bay_edges=[9, 63, 117, 171],
                             bays=[(10, "TELEPORT", '#5ED36A'), (13, "MAP INFO", '#D77CF0'),
                                   (16, "CANCEL", '#F05D5D')]),
    "booster_gui": dict(rows=3, layout='bays', title="BOOSTER REWARDS", bay_edges=[36, 72, 108, 144],
                        small_labels=True,
                        bays=[(11, "DAILY", '#F47FFF'), (13, "WEEKLY", '#F47FFF'), (15, "MONTHLY", '#F47FFF')]),
    "coinflip_gui": dict(rows=3, buttons=[10, 11, 12, 13, 14, 15, 16], title="COINFLIP",
                         styles={10: GREEN, 11: GREEN_FLAT, 12: GREEN, 13: GOLD_LIT, 14: RED, 15: RED_FLAT, 16: RED}),
    "quests_gui": dict(rows=3, buttons=[12, 14], title="QUESTS"),
}


def render(name):
    menu = MENUS[name]
    cv = Canvas(W, ry(HEADER + menu['rows'] * PITCH + 3))
    LAYOUTS[menu.get('layout', 'stall')](cv, menu)
    return cv.img


def buttons_of(menu):
    """Slots a menu puts its items in (for previews and the README)."""
    if menu.get('layout') == 'bays':
        return [b[0] for b in menu['bays']], []
    if menu.get('layout') == 'bin':
        return [], []
    return menu['buttons'], menu.get('nav', [])


# ------------------------------------------------------------------ preview


def vanilla_gui(rows):
    """Minecraft's default chest GUI, for previews."""
    h = 114 + rows * 18
    cv = Canvas(W, h)
    cv.rect(0, 0, W - 1, h - 1, hexc(GUI_GREY))
    cv.outline(0, 0, W - 1, h - 1, hexc('#000000'), round_corners=True)
    for i in (1, 2):
        cv.hline(1, W - 3, i, hexc('#FFFFFF'))
        cv.vline(i, 1, h - 3, hexc('#FFFFFF'))
    cv.hline(2, W - 2, h - 2, hexc('#555555'))
    cv.hline(3, W - 2, h - 3, hexc('#555555'))
    cv.vline(W - 2, 2, h - 2, hexc('#555555'))
    cv.vline(W - 3, 3, h - 2, hexc('#555555'))

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


STAND_INS = ['#F2C230', '#5AA0F0', '#D94A4A', '#9AA5B5', '#A98BF5', '#3FCF93', '#F08A3C']


def preview(texture, rows, slots, nav, scale=4):
    gui = vanilla_gui(rows)
    pad = 6
    canvas = Image.new("RGBA", (W + pad * 2, gui.height + TOP + pad * 2), hexc('#2B2F36'))
    canvas.alpha_composite(gui, (pad, pad + TOP))
    canvas.alpha_composite(texture, (pad, pad))
    cv = Canvas(canvas.width, canvas.height)
    cv.img = canvas
    cv.px = canvas.load()
    for i, s in enumerate(list(slots) + list(nav)):
        x, y = slot_xy(s)
        x += pad
        y += pad
        color = hexc(STAND_INS[i % len(STAND_INS)]) if s not in nav else hexc('#E04848')
        for dy in range(10):
            for dx in range(10):
                if (dx - 4.5) ** 2 + (dy - 4.5) ** 2 <= 22:
                    tone = color
                    if dy < 3 or dx < 2:
                        tone = blend(color, (255, 255, 255, 255), 0.35)
                    if dy > 7 or dx > 8:
                        tone = blend(color, BLACK, 0.35)
                    cv.put(x + 4 + dx, y + 4 + dy, tone)
    # Minecraft's "Inventory" label
    label_y = pad + TOP + 17 + rows * 18 + 3
    cv.hline(pad + 8, pad + 50, label_y + 3, hexc('#8A8A8A'))
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
        slots, nav = buttons_of(menu)
        shot = preview(img, menu['rows'], slots, nav)
        shot.save(os.path.join(out_preview, name + ".png"))
        shots.append(shot.resize((shot.width // 2, shot.height // 2), Image.NEAREST))
        sizes[name] = {"rows": menu['rows'], "height": img.height, "ascent": 13 + TOP}
    contact_sheet(shots).save(os.path.join(out_preview, "all_menus.png"))
    return sizes


if __name__ == "__main__":
    for name, size in main().items():
        print(f"{name}: {size['rows']} rows, {W}x{size['height']}")
