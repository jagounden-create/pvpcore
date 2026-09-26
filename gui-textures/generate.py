#!/usr/bin/env python3
"""
Wooden-stall Nexo GUI textures for chest menus, drawn as pixel art at Minecraft's GUI scale.

Every texture is 176 px wide (a chest GUI) and starts TOP px above the GUI so the sign can sit
on the roof and stick out over the top edge. Cubbies are drawn exactly where the menu puts its
items: slot (row r, column c) has its frame at x = 7 + 18c, y = TOP + 17 + 18r.

The picture also paints the menu's own grey over the chest's empty slot grid, so only the
stall's cubbies show. The player inventory below is left untouched.

Nexo glyph for a texture of height H:  ascent: 13 + TOP   height: H
Menu title:                            <white><shift:-8><glyph:ID>

Run:  python3 generate.py      (needs Pillow: pip install pillow)
"""
import os
import random
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
        """Tint a pixel that is already drawn (shadows and glows)."""
        p = self.get(x, y)
        if p[3]:
            self.px[x, y] = blend(p, color, amount)[:3] + (p[3],)


class Ramp:
    """Five tones of one material, darkest (outline) to brightest (highlight)."""

    def __init__(self, outline, dark, mid, light, high):
        self.outline, self.dark, self.mid, self.light, self.high = (hexc(v) for v in (outline, dark, mid, light, high))


def shape(cv, cells, ramp, outline=True):
    """Fill a set of pixels as one raised piece: lit top-left edge, shaded bottom-right, outline."""
    for (x, y) in cells:
        tone = ramp.mid
        if (x, y - 1) not in cells or (x - 1, y) not in cells:
            tone = ramp.light
        if (x, y + 1) not in cells or (x + 1, y) not in cells:
            tone = ramp.dark
        cv.put(x, y, tone)
    if outline:
        for (x, y) in cells:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if (x + dx, y + dy) not in cells:
                    cv.put(x + dx, y + dy, ramp.outline)


def planks(cv, x0, y0, x1, y1, ramp, board=9, vertical=True, grain=0.05):
    """Wooden boards with gaps, per-board tone, grain streaks and a lit edge."""
    span = (x1 - x0 + 1) if vertical else (y1 - y0 + 1)
    boards = max(1, round(span / board))
    start = x0 if vertical else y0
    edges = [start + round(i * span / boards) for i in range(boards + 1)]
    for b in range(boards):
        tone = ramp.mid if cv.rng.random() < 0.6 else blend(ramp.mid, ramp.light, 0.5)
        a, z = edges[b], edges[b + 1] - 1
        if vertical:
            cv.rect(a, y0, z, y1, tone)
            cv.vline(z, y0, y1, ramp.dark)
            cv.vline(a, y0, y1, blend(tone, ramp.high, 0.35))
            for x in range(a + 1, z):
                y = y0
                while y <= y1:
                    if cv.rng.random() < grain:
                        length = cv.rng.randint(3, 8)
                        streak = blend(tone, ramp.dark, 0.5) if cv.rng.random() < 0.7 else blend(tone, ramp.high, 0.3)
                        cv.vline(x, y, min(y1, y + length - 1), streak)
                        y += length + 3
                    y += 1
            if cv.rng.random() < 0.3 and z - a >= 5 and y1 - y0 >= 8:
                kx, ky = cv.rng.randint(a + 2, z - 2), cv.rng.randint(y0 + 2, y1 - 2)
                cv.put(kx, ky, ramp.dark)
                cv.put(kx + 1, ky, ramp.outline)
        else:
            cv.rect(x0, a, x1, z, tone)
            cv.hline(x0, x1, z, ramp.dark)
            cv.hline(x0, x1, a, blend(tone, ramp.high, 0.35))
            for y in range(a + 1, z):
                x = x0
                while x <= x1:
                    if cv.rng.random() < grain:
                        length = cv.rng.randint(3, 9)
                        streak = blend(tone, ramp.dark, 0.5) if cv.rng.random() < 0.7 else blend(tone, ramp.high, 0.3)
                        cv.hline(x, min(x1, x + length - 1), y, streak)
                        x += length + 3
                    x += 1


def glow(cv, cx, cy, radius, color, strength=0.55):
    """Soft light blended over what is already drawn."""
    for y in range(cy - radius, cy + radius + 1):
        for x in range(cx - radius, cx + radius + 1):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= radius:
                a = strength * (1 - d / radius) ** 1.6
                if a > 0.02:
                    cv.shade(x, y, color, a)


# ------------------------------------------------------------------ geometry


def ry(rel):
    """Texture row of a row measured from the GUI's top edge."""
    return TOP + rel


def slot_xy(slot):
    row, col = divmod(slot, 9)
    return LEFT + col * PITCH, ry(HEADER + row * PITCH)


def runs(slots):
    """Button slots grouped into horizontal runs of neighbours: [[10], [12, 13, 14], [16]]."""
    out = []
    for s in sorted(slots):
        if out and out[-1][-1] == s - 1 and s // 9 == out[-1][-1] // 9:
            out[-1].append(s)
        else:
            out.append([s])
    return out


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


def bold_cells(text, x, y):
    """Pixels of chunky sign lettering: each lit pixel also lights its right neighbour."""
    cells = set()
    cx = x
    for ch in text:
        rows = FONT[ch]
        width = len(rows[0]) + (1 if ch != ' ' else 0)
        for gy, line in enumerate(rows):
            for gx, p in enumerate(line):
                if p == '#':
                    cells.add((cx + gx, y + gy))
                    if ch != ' ':
                        cells.add((cx + gx + 1, y + gy))
        cx += width + 1
    return cells


def text_width(text):
    return sum(len(FONT[ch][0]) + (1 if ch != ' ' else 0) + 1 for ch in text) - 1


def carved_text(cv, x, y, text, face, shadow, highlight):
    """Raised letters on a board: light face, lit top edge and a dark shadow down and right."""
    cells = bold_cells(text, x, y)
    for (px, py) in cells:
        for (dx, dy) in ((1, 1), (0, 1), (1, 0)):
            if (px + dx, py + dy) not in cells:
                cv.put(px + dx, py + dy, shadow)
    for (px, py) in cells:
        cv.put(px, py, highlight if (px, py - 1) not in cells else face)


# ------------------------------------------------------------------ stall pieces


class Theme:
    def __init__(self, wall, frame, roof, sign, text, cubby, nav=None, seed=1, decorate=None, slot_styles=None):
        self.wall = wall            # back wall planks
        self.frame = frame          # posts, brackets, shelf and front panels
        self.roof = roof            # the slab on top
        self.sign = sign            # sign board
        self.text = text            # (face, shadow, highlight) of the sign letters
        self.cubby = cubby          # default cubby style
        self.nav = nav              # cubby style of back/close buttons
        self.seed = seed
        self.decorate = decorate    # extra drawing: fn(cv, theme, menu)
        self.slot_styles = slot_styles or {}


class Cubby:
    """How a slot looks. kind: 'frame' (coloured frame, dark inside), 'flat' (one dark colour),
    'lit' (bright outline, like a selected slot)."""

    def __init__(self, kind, outline, frame_light, frame, inner, inner_shade):
        self.kind = kind
        self.outline, self.frame_light, self.frame, self.inner, self.inner_shade = (
            hexc(v) for v in (outline, frame_light, frame, inner, inner_shade))


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


POST_W = 6
POST_L = 12             # left post x
POST_R = W - 12 - POST_W  # right post x
ROOF_X0, ROOF_X1 = 5, W - 6
ROOF_TOP = 3            # rows below the GUI's top edge
ROOF_FACE = 9           # top face rows
ROOF_BAND = 5           # front edge rows


def roof_bottom():
    return ROOF_TOP + 1 + ROOF_FACE + ROOF_BAND  # first row under the roof (GUI-relative)


def cover_grid(cv, rows):
    """Paint the menu's grey over the chest slot grid; the stall's cubbies replace it."""
    cv.rect(4, ry(4), W - 5, ry(HEADER + rows * PITCH + 1), hexc(GUI_GREY))


def draw_roof(cv, ramp):
    top = ry(ROOF_TOP)
    cells = set()
    for i in range(ROOF_FACE + ROOF_BAND + 1):
        inset = max(0, (ROOF_FACE - 1 - i) // 3) if i < ROOF_FACE else 0
        for x in range(ROOF_X0 + inset, ROOF_X1 - inset + 1):
            cells.add((x, top + 1 + i))
    # top face: light, faint grain, the front edge catches the light
    for (x, y) in cells:
        i = y - top - 1
        if i < ROOF_FACE:
            tone = ramp.light if i > 0 else ramp.high
            if i == ROOF_FACE - 1:
                tone = ramp.high
            elif cv.rng.random() < 0.08:
                tone = blend(ramp.light, ramp.mid, 0.6)
        else:
            j = i - ROOF_FACE
            tone = ramp.mid if j < ROOF_BAND - 1 else ramp.dark
            if j == 0:
                tone = blend(ramp.mid, ramp.dark, 0.5)
        cv.put(x, y, tone)
    # boards on the front edge
    for x in range(ROOF_X0 + 14, ROOF_X1 - 4, 21):
        cv.vline(x, top + 1 + ROOF_FACE + 1, top + ROOF_FACE + ROOF_BAND - 1, blend(ramp.mid, ramp.dark, 0.6))
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, y + dy) not in cells:
                cv.put(x + dx, y + dy, ramp.outline)
    # shadow under the overhang
    under = top + 1 + ROOF_FACE + ROOF_BAND + 1
    for x in range(ROOF_X0, ROOF_X1 + 1):
        cv.shade(x, under, (0, 0, 0, 255), 0.45)
        cv.shade(x, under + 1, (0, 0, 0, 255), 0.22)


def draw_sign(cv, text, theme):
    ramp = theme.sign
    width = text_width(text) + 16
    x0 = W // 2 - width // 2
    x1 = x0 + width - 1
    y0, y1 = 0, 16
    # the sign's shadow on the roof
    for x in range(x0 + 1, x1 + 2):
        cv.shade(x, y1 + 1, (0, 0, 0, 255), 0.35)
        cv.shade(x, y1 + 2, (0, 0, 0, 255), 0.15)
    cv.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, ramp.mid)
    planks(cv, x0 + 3, y0 + 3, x1 - 3, y1 - 3, ramp, board=6, vertical=False, grain=0.04)
    cv.outline(x0, y0, x1, y1, ramp.outline, round_corners=True)
    # raised rim
    cv.hline(x0 + 1, x1 - 1, y0 + 1, ramp.high)
    cv.vline(x0 + 1, y0 + 1, y1 - 1, ramp.light)
    cv.hline(x0 + 2, x1 - 1, y1 - 1, ramp.dark)
    cv.vline(x1 - 1, y0 + 2, y1 - 1, ramp.dark)
    cv.hline(x0 + 2, x1 - 2, y0 + 2, ramp.light)
    cv.vline(x0 + 2, y0 + 2, y1 - 2, ramp.light)
    cv.hline(x0 + 3, x1 - 2, y1 - 2, blend(ramp.dark, ramp.outline, 0.4))
    cv.vline(x1 - 2, y0 + 3, y1 - 2, blend(ramp.dark, ramp.outline, 0.4))
    # nails
    for nx in (x0 + 4, x1 - 4):
        cv.put(nx, y0 + 4, ramp.outline)
        cv.put(nx, y1 - 4, ramp.outline)
    face, shadow, highlight = theme.text
    carved_text(cv, W // 2 - text_width(text) // 2, y0 + 4, text, face, shadow, highlight)


def draw_posts(cv, ramp, y0, y1):
    for x in (POST_L, POST_R):
        cv.rect(x, y0, x + POST_W - 1, y1, ramp.mid)
        cv.vline(x + 1, y0, y1, ramp.light)
        cv.vline(x + 2, y0, y1, blend(ramp.mid, ramp.light, 0.4))
        cv.vline(x + POST_W - 2, y0, y1, ramp.dark)
        cv.vline(x, y0, y1, ramp.outline)
        cv.vline(x + POST_W - 1, y0, y1, ramp.outline)
        for gy in range(y0 + 4, y1, 11):
            cv.put(x + 3, gy, ramp.dark)
            cv.put(x + 3, gy + 1, ramp.dark)
        cv.hline(x, x + POST_W - 1, y1, ramp.outline)


def draw_brackets(cv, ramp):
    """Diagonal knee braces from the roof's overhang down across the front of each post."""
    top = ry(roof_bottom())
    length = 14
    for side in (0, 1):
        cells = set()
        for i in range(length):
            cx = ROOF_X0 + 2 + i * (POST_L + POST_W + 2 - ROOF_X0 - 2) // (length - 1)
            for x in range(cx - 1, cx + 2):
                cells.add((x, top + i))
        # a block where it meets the roof
        for x in range(ROOF_X0 + 1, ROOF_X0 + 6):
            cells.add((x, top))
        if side == 1:
            cells = {(W - 1 - x, y) for (x, y) in cells}
        shape(cv, cells, ramp)
        # peg holding it to the post
        px = POST_L + 3 if side == 0 else POST_R + 2
        cv.put(px, top + length - 4, ramp.outline)


def draw_wall(cv, theme, bottom):
    wall_top = ry(roof_bottom())
    planks(cv, POST_L + POST_W, wall_top, POST_R - 1, bottom, theme.wall, board=10, vertical=True)
    # beam under the roof
    b0 = wall_top
    cv.rect(POST_L, b0, POST_R + POST_W - 1, b0 + 3, theme.frame.mid)
    cv.hline(POST_L, POST_R + POST_W - 1, b0, theme.frame.light)
    cv.hline(POST_L, POST_R + POST_W - 1, b0 + 3, theme.frame.dark)
    cv.hline(POST_L, POST_R + POST_W - 1, b0 + 4, theme.frame.outline)
    for x in range(POST_L + 9, POST_R, 16):
        cv.put(x, b0 + 1, theme.frame.dark)
        cv.put(x, b0 + 2, theme.frame.outline)


def draw_display(cv, theme, buttons):
    """Recessed backing behind each run of cubbies, then the cubbies."""
    for run in runs(buttons):
        x0, y0 = slot_xy(run[0])
        x1, y1 = slot_xy(run[-1])
        cv.rect(x0 - 2, y0 - 2, x1 + 19, y1 + 17, theme.wall.outline)
        cv.hline(x0 - 2, x1 + 19, y0 - 2, blend(theme.wall.outline, (0, 0, 0, 255), 0.3))
    for s in buttons:
        style = theme.slot_styles.get(s, theme.cubby)
        cubby(cv, *slot_xy(s), style)


def draw_shelf(cv, ramp, buttons):
    """A ledge under the lowest row of cubbies, a little wider than they are."""
    xs = [slot_xy(s)[0] for s in buttons]
    x0, x1 = min(xs) - 4, max(xs) + 21
    y = slot_xy(max(buttons))[1] + 18
    cv.rect(x0, y, x1, y + 4, ramp.mid)
    cv.hline(x0, x1, y, ramp.outline)
    cv.hline(x0 + 1, x1 - 1, y + 1, ramp.high)
    cv.hline(x0 + 1, x1 - 1, y + 2, ramp.light)
    cv.hline(x0 + 1, x1 - 1, y + 3, ramp.dark)
    cv.hline(x0, x1, y + 4, ramp.outline)
    cv.vline(x0, y, y + 4, ramp.outline)
    cv.vline(x1, y, y + 4, ramp.outline)
    for x in range(x0 + 1, x1):
        cv.shade(x, y + 5, (0, 0, 0, 255), 0.4)
    return y + 5


def draw_front(cv, theme, top, bottom):
    """Counter front under the shelf: one board per column, raised and recessed in turn."""
    f, w = theme.frame, theme.wall
    x0, x1 = POST_L + POST_W, POST_R - 1
    for c in range(9):
        a = max(x0, LEFT + c * PITCH)
        z = min(x1, LEFT + c * PITCH + PITCH - 1)
        if a > z:
            continue
        if c % 2 == 1:
            cv.rect(a, top, z, bottom, f.mid)
            cv.hline(a, z, top, f.high)
            cv.vline(a, top, bottom, f.light)
            cv.vline(z - 1, top + 1, bottom, f.dark)
            cv.hline(a, z, bottom - 1, f.dark)
            cv.vline(a + 5, top + 2, bottom - 3, blend(f.mid, f.dark, 0.6))
            cv.vline(z - 6, top + 2, bottom - 3, blend(f.mid, f.dark, 0.6))
            cv.outline(a - 1, top - 1, z, bottom, f.outline)
            cv.hline(a, z - 1, bottom, f.outline)
        else:
            cv.rect(a, top, z, bottom - 2, w.dark)
            cv.hline(a, z, top, w.mid)
            cv.vline(a + (z - a) // 2, top + 1, bottom - 3, blend(w.dark, w.outline, 0.55))
            cv.hline(a, z, bottom - 2, w.outline)
            for x in range(a, z + 1):
                cv.shade(x, bottom - 1, (0, 0, 0, 255), 0.25)


def draw_stall(cv, menu, theme):
    rows, buttons, nav = menu['rows'], menu['buttons'], menu['nav']
    bottom = ry(HEADER + rows * PITCH)
    cover_grid(cv, rows)
    draw_wall(cv, theme, bottom - 1)
    draw_posts(cv, theme.frame, ry(roof_bottom()), bottom)
    draw_brackets(cv, theme.frame)
    draw_display(cv, theme, buttons)
    front_top = draw_shelf(cv, theme.frame, buttons) + 1
    if front_top < bottom - 2:
        draw_front(cv, theme, front_top, bottom)
    for s in nav:
        x, y = slot_xy(s)
        cv.rect(x - 1, y - 1, x + 18, y + 18, theme.frame.outline)
        cubby(cv, x, y, theme.nav or theme.cubby)
    # ground shadow
    for x in range(POST_L, POST_R + POST_W):
        cv.shade(x, bottom + 1, (0, 0, 0, 255), 0.25)
    if theme.decorate:
        theme.decorate(cv, theme, menu)
    draw_roof(cv, theme.roof)
    draw_sign(cv, menu['title'], theme)


# ------------------------------------------------------------------ decorations


def lantern(cv, x, y, light, chain_top):
    """Hanging lantern on a chain, with a coloured glow."""
    for cy in range(chain_top, y):
        cv.put(x + 2, cy, hexc('#3B3E46') if (cy - chain_top) % 2 == 0 else hexc('#23252B'))
    glow(cv, x + 2, y + 4, 12, light, 0.5)
    iron_o, iron = hexc('#16171B'), hexc('#4B4F5A')
    cv.rect(x, y, x + 4, y + 7, iron)
    cv.outline(x, y, x + 4, y + 7, iron_o)
    cv.rect(x + 1, y + 2, x + 3, y + 5, light)
    cv.put(x + 2, y + 3, blend(light, (255, 255, 255, 255), 0.6))
    cv.hline(x + 1, x + 3, y + 1, iron_o)


def flame(cv, x, y):
    shape_rows = ["..y..", ".yy..", ".yoy.", "yooy.", "yorry", ".orr."]
    colors = {'y': hexc('#FFE27A'), 'o': hexc('#FFA53A'), 'r': hexc('#E4572E')}
    for dy, line in enumerate(shape_rows):
        for dx, ch in enumerate(line):
            if ch in colors:
                cv.put(x - 2 + dx, y + dy, colors[ch])


def torch(cv, x, y):
    """Wall torch mounted on a post: flame on a short stick, with a warm glow."""
    glow(cv, x, y + 3, 12, hexc('#FF9A3C'), 0.45)
    cv.vline(x, y + 6, y + 11, hexc('#6B4424'))
    cv.vline(x + 1, y + 6, y + 11, hexc('#4A2E17'))
    cv.hline(x, x + 1, y + 12, hexc('#2A170D'))
    flame(cv, x + 1, y)


def note(cv, x0, y0, x1, y1, pin):
    """A pinned parchment note with scribbled lines."""
    paper, edge, ink = hexc('#EADCB6'), hexc('#C8B283'), hexc('#8C7650')
    cv.rect(x0, y0, x1, y1, paper)
    cv.outline(x0, y0, x1, y1, hexc('#6D5537'))
    cv.hline(x0 + 1, x1 - 1, y1 - 1, edge)
    cv.vline(x1 - 1, y0 + 1, y1 - 1, edge)
    for ly in range(y0 + 3, y1 - 1, 2):
        length = cv.rng.randint(max(1, (x1 - x0) // 2), max(1, x1 - x0 - 3))
        cv.hline(x0 + 2, x0 + 1 + length, ly, ink)
    cv.put((x0 + x1) // 2, y0, pin)


def deco_black_market(cv, theme, menu):
    top = ry(roof_bottom())
    for x in (POST_L + POST_W + 2, POST_R - 7):
        lantern(cv, x, top + 7, hexc('#A070FF'), top + 4)


def deco_kill_streaks(cv, theme, menu):
    top = ry(roof_bottom())
    torch(cv, POST_L + POST_W + 10, top + 8)
    torch(cv, POST_R - 12, top + 8)


def deco_quests(cv, theme, menu):
    top = ry(roof_bottom())
    note(cv, POST_L + POST_W + 3, top + 7, POST_L + POST_W + 13, top + 16, hexc('#D93A3A'))
    note(cv, POST_R - 14, top + 6, POST_R - 4, top + 14, hexc('#3F7FD9'))


def deco_media_rank(cv, theme, menu):
    top = ry(roof_bottom())
    # two little spotlights under the roof, shining on the display
    for x, direction in ((POST_L + POST_W + 3, 1), (POST_R - 8, -1)):
        cv.rect(x, top + 5, x + 4, top + 8, hexc('#2A2A30'))
        cv.outline(x, top + 5, x + 4, top + 8, hexc('#111114'))
        cv.hline(x + 1, x + 3, top + 8, hexc('#FFF2B8'))
        glow(cv, x + 2 + direction * 10, top + 18, 16, hexc('#FFD6F0'), 0.28)


# ------------------------------------------------------------------ palettes and themes

# The reference stall: reddish wood, tan roof
RED_WOOD = Ramp('#2E1810', '#4E2C1E', '#653A28', '#7A4832', '#8F583E')
RED_FRAME = Ramp('#2E1810', '#5A3423', '#744530', '#8B573C', '#A36A4B')
TAN_ROOF = Ramp('#3A2A1E', '#8A6E55', '#A58A6D', '#BBA081', '#CDB595')
RED_SIGN = Ramp('#2E1810', '#6B3F2A', '#86533A', '#A06A4B', '#B98060')
CARVED = (hexc('#EBCDAE'), hexc('#3F2216'), hexc('#FAE6CF'))

OAK_WALL = Ramp('#2B1A0E', '#5B3E22', '#72502D', '#886038', '#9E7144')
OAK_FRAME = Ramp('#2B1A0E', '#6A4A29', '#855D35', '#9D7042', '#B38352')
OAK_SIGN = Ramp('#2B1A0E', '#6E4D2B', '#8A6238', '#A37648', '#BA8A58')

DARK_WALL = Ramp('#110A06', '#24170D', '#301F13', '#3D291A', '#4C3322')
DARK_FRAME = Ramp('#110A06', '#2E1D10', '#3E2818', '#503422', '#62412C')
SLATE_ROOF = Ramp('#0F0D13', '#29242F', '#37313F', '#474052', '#595066')
DARK_SIGN = Ramp('#0F0A0E', '#2B1D26', '#3A2833', '#4B3542', '#5E4454')

CRIMSON_WALL = Ramp('#1C0910', '#3F1827', '#522033', '#662A40', '#7B354F')
CRIMSON_FRAME = Ramp('#1C0910', '#4A1D2E', '#60283D', '#76334C', '#8D415E')
BLACKSTONE_ROOF = Ramp('#0D0B0F', '#252127', '#312C34', '#3F3943', '#524B57')
CRIMSON_SIGN = Ramp('#1C0910', '#4F1E31', '#662940', '#7D3550', '#944463')

SPRUCE_WALL = Ramp('#1C120A', '#3B2714', '#4D341C', '#614226', '#765131')
SPRUCE_FRAME = Ramp('#1C120A', '#46301A', '#5A3E22', '#704E2C', '#865E37')
SPRUCE_SIGN = Ramp('#1C120A', '#4A331C', '#5E4125', '#745230', '#8A633C')

CHERRY_WALL = Ramp('#4A2A30', '#A9707A', '#BF8791', '#D29DA4', '#E3B5B8')
CHERRY_FRAME = Ramp('#4A2A30', '#8E5664', '#A56A77', '#BC808B', '#D1979F')
WHITE_ROOF = Ramp('#4A3E40', '#BDB1AF', '#D3C9C6', '#E6DEDB', '#F6F1EF')
CHERRY_SIGN = Ramp('#3A1E26', '#6E3A4A', '#86495B', '#9E5B6E', '#B66F83')

GREEN = Cubby('frame', '#1C3014', '#7DBA52', '#5E9A3C', '#2E4F22', '#243F1B')
GREEN_FLAT = Cubby('flat', '#1C3014', '#7DBA52', '#2A4A1F', '#33582A', '#26431D')
RED = Cubby('frame', '#35110F', '#D0605E', '#A8434A', '#5A1F24', '#4A181D')
RED_FLAT = Cubby('flat', '#35110F', '#D0605E', '#521A1F', '#5E2228', '#4A181D')
GOLD_LIT = Cubby('lit', '#3A2A08', '#F8D25A', '#C9A33A', '#8C7A2C', '#6E5F20')
EMERALD = Cubby('frame', '#10301E', '#6FD19A', '#3FA56C', '#1E4A33', '#173C29')
PURPLE = Cubby('frame', '#1A0E26', '#A77BE0', '#7A4FB8', '#2A1840', '#221334')
FIRE = Cubby('frame', '#2E0F08', '#F29B4A', '#D0603A', '#3E1812', '#32130E')
PARCHMENT = Cubby('frame', '#4A3822', '#EFE2BF', '#D2BD8E', '#6E5536', '#5E472C')
PINK = Cubby('frame', '#3A0F2C', '#F08FCB', '#D05AA8', '#4A1A3C', '#3C1431')
WOOD_NAV = Cubby('frame', '#2E1810', '#B38352', '#8A6137', '#3A2418', '#2E1C12')
DARK_NAV = Cubby('frame', '#0E0A0C', '#6A5A6E', '#4A3E4E', '#1E1820', '#171219')

THEMES = {
    "coinflip": Theme(RED_WOOD, RED_FRAME, TAN_ROOF, RED_SIGN, CARVED, GREEN, WOOD_NAV, seed=7,
                      slot_styles={10: GREEN, 11: GREEN_FLAT, 12: GREEN, 13: GOLD_LIT,
                                   14: RED, 15: RED_FLAT, 16: RED}),
    "shop": Theme(OAK_WALL, OAK_FRAME, TAN_ROOF, OAK_SIGN, CARVED, EMERALD, WOOD_NAV, seed=11),
    "black_market": Theme(DARK_WALL, DARK_FRAME, SLATE_ROOF, DARK_SIGN,
                          (hexc('#D9C2F2'), hexc('#07040A'), hexc('#F3E8FF')), PURPLE, DARK_NAV, seed=23,
                          decorate=deco_black_market),
    "quests": Theme(SPRUCE_WALL, SPRUCE_FRAME, TAN_ROOF, SPRUCE_SIGN,
                    (hexc('#F3D98A'), hexc('#24170B'), hexc('#FFF1C2')), PARCHMENT, WOOD_NAV, seed=37,
                    decorate=deco_quests),
    "kill_streaks": Theme(CRIMSON_WALL, CRIMSON_FRAME, BLACKSTONE_ROOF, CRIMSON_SIGN,
                          (hexc('#FFC27A'), hexc('#1A0508'), hexc('#FFE6B8')), FIRE, DARK_NAV, seed=41,
                          decorate=deco_kill_streaks),
    "media_rank": Theme(CHERRY_WALL, CHERRY_FRAME, WHITE_ROOF, CHERRY_SIGN,
                        (hexc('#FFE3F1'), hexc('#2A0E1A'), hexc('#FFFFFF')), PINK, WOOD_NAV, seed=53,
                        decorate=deco_media_rank),
}

MENUS = {
    # id: theme, rows, button slots, back/close slots, sign text
    "coinflip_gui": ("coinflip", 3, [10, 11, 12, 13, 14, 15, 16], [], "COINFLIP"),
    "shop_gui": ("shop", 3, [11, 12, 13, 14, 15], [26], "SHOP"),
    "black_market_gui": ("black_market", 3, [10, 12, 13, 14, 16], [26], "BLACK MARKET"),
    "kill_streaks_gui": ("kill_streaks", 4, [12, 13, 14, 21, 22, 23], [], "KILL STREAKS"),
    "quests_gui": ("quests", 3, [12, 14], [], "QUESTS"),
    "media_rank_gui": ("media_rank", 3, [12, 13, 14], [22], "MEDIA RANK"),
}


def render(name):
    theme_id, rows, buttons, nav, title = MENUS[name]
    theme = THEMES[theme_id]
    height = ry(HEADER + rows * PITCH + 2)
    cv = Canvas(W, height, seed=theme.seed)
    draw_stall(cv, {'rows': rows, 'buttons': buttons, 'nav': nav, 'title': title}, theme)
    return cv.img


# ------------------------------------------------------------------ preview


def vanilla_gui(rows):
    """Minecraft's default chest GUI, for previews."""
    h = 114 + rows * 18
    cv = Canvas(W, h, seed=1)
    cv.rect(0, 0, W - 1, h - 1, hexc(GUI_GREY))
    cv.outline(0, 0, W - 1, h - 1, hexc('#000000'), round_corners=True)
    cv.hline(1, W - 3, 1, hexc('#FFFFFF'))
    cv.hline(1, W - 3, 2, hexc('#FFFFFF'))
    cv.vline(1, 1, h - 3, hexc('#FFFFFF'))
    cv.vline(2, 1, h - 3, hexc('#FFFFFF'))
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
    cv = Canvas(canvas.width, canvas.height, 0)
    cv.img = canvas
    cv.px = canvas.load()
    for i, s in enumerate(list(slots) + list(nav)):
        x, y = slot_xy(s)
        x += pad
        y += pad
        color = hexc(STAND_INS[i % len(STAND_INS)]) if s not in nav else hexc('#C9C9C9')
        # a round gem as a stand-in item
        for dy in range(10):
            for dx in range(10):
                if (dx - 4.5) ** 2 + (dy - 4.5) ** 2 <= 22:
                    tone = color
                    if dy < 3 or dx < 2:
                        tone = blend(color, (255, 255, 255, 255), 0.35)
                    if dy > 7 or dx > 8:
                        tone = blend(color, (0, 0, 0, 255), 0.35)
                    cv.put(x + 4 + dx, y + 4 + dy, tone)
    # Minecraft's "Inventory" label
    ry_label = pad + TOP + 17 + rows * 18 + 3
    cv.hline(pad + 8, pad + 50, ry_label + 3, hexc('#8A8A8A'))
    return canvas.resize((canvas.width * scale, canvas.height * scale), Image.NEAREST)


def contact_sheet(images, columns=3, gap=12, background='#1E2127'):
    widths = [im.width for im in images]
    heights = [im.height for im in images]
    cell_w, cell_h = max(widths), max(heights)
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
    for name, (theme, rows, slots, nav, title) in MENUS.items():
        img = render(name)
        img.save(os.path.join(out, name + ".png"))
        shot = preview(img, rows, slots, nav)
        shot.save(os.path.join(out_preview, name + ".png"))
        shots.append(shot.resize((shot.width // 2, shot.height // 2), Image.NEAREST))
        sizes[name] = {"rows": rows, "height": img.height, "ascent": 13 + TOP}
    contact_sheet(shots).save(os.path.join(out_preview, "all_menus.png"))
    return sizes


if __name__ == "__main__":
    for name, size in main().items():
        print(f"{name}: {size['rows']} rows, {W}x{size['height']}")
