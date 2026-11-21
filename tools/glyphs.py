"""MineScore's music notation symbols (SCOPE.md §9): original pixel art, white so the editor can tint them,
packed into one atlas a resource pack can redraw. One staff space is 8 pixels.

Each glyph has an anchor: the point the editor lines up with the music (a notehead's middle, a treble
clef's G line, the top of a flag where it meets its stem). score_glyphs.json records every glyph's cell
and anchor; the editor reads it, so cells can move as long as the json is regenerated with them.
"""
import math

from textures import Canvas

S = 8          # one staff space, in pixels
WHITE = (255, 255, 255, 255)

# Small bitmap fonts: digits for time signatures (5 x 8), and italic letters for dynamics and trills (5 x 7).
DIGITS = {
    "0": ["01110", "10001", "10011", "10101", "11001", "10001", "10001", "01110"],
    "1": ["00100", "01100", "10100", "00100", "00100", "00100", "00100", "11111"],
    "2": ["01110", "10001", "00001", "00010", "00100", "01000", "10000", "11111"],
    "3": ["11110", "00001", "00001", "01110", "00001", "00001", "00001", "11110"],
    "4": ["00010", "00110", "01010", "10010", "11111", "00010", "00010", "00010"],
    "5": ["11111", "10000", "10000", "11110", "00001", "00001", "10001", "01110"],
    "6": ["00110", "01000", "10000", "11110", "10001", "10001", "10001", "01110"],
    "7": ["11111", "00001", "00010", "00010", "00100", "00100", "01000", "01000"],
    "8": ["01110", "10001", "10001", "01110", "10001", "10001", "10001", "01110"],
    "9": ["01110", "10001", "10001", "10001", "01111", "00001", "00010", "01100"],
}
LETTERS = {
    "p": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "m": ["11010", "10101", "10101", "10101", "10101", "00000", "00000"],
    "f": ["00111", "00100", "01111", "00100", "00100", "00100", "11000"],
    "s": ["01111", "10000", "01110", "00001", "11110", "00000", "00000"],
    "z": ["11111", "00010", "00100", "01000", "11111", "00000", "00000"],
    "r": ["10110", "11001", "10000", "10000", "10000", "00000", "00000"],
    "t": ["00100", "01110", "00100", "00100", "00011", "00000", "00000"],
}


class Glyph:
    def __init__(self, name, w, h, ax, ay, draw):
        self.name, self.w, self.h, self.ax, self.ay, self.draw = name, w, h, ax, ay, draw


class Cell:
    """Drawing into one glyph's cell of the atlas."""

    def __init__(self, canvas, x, y, w, h):
        self.c, self.x, self.y, self.w, self.h = canvas, x, y, w, h

    def set(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.c.set(self.x + int(x), self.y + int(y), WHITE)

    def rect(self, x1, y1, x2, y2):
        for y in range(int(y1), int(y2)):
            for x in range(int(x1), int(x2)):
                self.set(x, y)

    def dot(self, cx, cy, r):
        for y in range(self.h):
            for x in range(self.w):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    self.set(x, y)

    def ellipse(self, cx, cy, rx, ry, tilt=0.0, inner=None):
        """A filled (tilted) ellipse; with inner=(rx, ry, tilt) its middle is left empty."""
        for y in range(self.h):
            for x in range(self.w):
                if inside(x + 0.5 - cx, y + 0.5 - cy, rx, ry, tilt) and not (inner and inside(x + 0.5 - cx, y + 0.5 - cy, *inner)):
                    self.set(x, y)

    def line(self, x0, y0, x1, y1, thick=1.0):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(steps + 1):
            t = i / steps
            px, py = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            if thick <= 1:
                self.set(round(px - 0.5), round(py - 0.5))
            else:
                self.dot(px, py, thick / 2)

    def curve(self, points, thick=1.5):
        """A smooth line through points (Catmull-Rom), for clefs, flags and rests."""
        pts = [points[0]] + list(points) + [points[-1]]
        for i in range(1, len(pts) - 2):
            p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
            for k in range(12):
                t = k / 12
                x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t * t
                           + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t ** 3)
                y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t * t
                           + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t ** 3)
                self.dot(x, y, thick / 2)
        self.dot(points[-1][0], points[-1][1], thick / 2)

    def bitmap(self, rows, x0, y0, scale=1, slant=0.0):
        """A font character; slant leans it right as it goes up (italics)."""
        for r, row in enumerate(rows):
            shift = round((len(rows) - 1 - r) * slant)
            for col, bit in enumerate(row):
                if bit == "1":
                    self.rect(x0 + shift + col * scale, y0 + r * scale, x0 + shift + (col + 1) * scale, y0 + (r + 1) * scale)


def inside(dx, dy, rx, ry, tilt):
    c, s = math.cos(tilt), math.sin(tilt)
    u, v = dx * c + dy * s, -dx * s + dy * c
    return (u / rx) ** 2 + (v / ry) ** 2 <= 1


# --- the glyphs --------------------------------------------------------------------------------

def head_black(g):
    g.ellipse(5, 4.5, 5.0, 3.7, -0.35)


def head_half(g):
    g.ellipse(5, 4.5, 5.0, 3.7, -0.35, inner=(3.6, 1.6, -0.6))


def head_whole(g):
    g.ellipse(6.5, 4.5, 6.5, 4.3, 0, inner=(3.2, 2.4, 0.7))


def head_breve(g):
    g.ellipse(9.5, 4.5, 6.5, 4.3, 0, inner=(3.2, 2.4, 0.7))
    g.rect(0, 0, 2, 9)
    g.rect(17, 0, 19, 9)


def head_x(g):
    g.line(1, 1, 8, 8, 2)
    g.line(8, 1, 1, 8, 2)


def rest_quarter(g):
    g.curve([(5, 0), (2, 5), (6, 10), (2, 15), (6, 18)], 2.2)
    g.curve([(6, 18), (2, 18), (3, 21.5)], 1.6)


def rest_flagged(n):
    def draw(g):
        top = 1
        for i in range(n):  # one hook per flag, each a dot and a curl back to the stem
            y = top + i * 6
            g.dot(2.5, y + 2.5, 2.2)
            g.curve([(2.5, y + 4), (5, y + 4.5), (g.w - 1, y + 1)], 1.4)
        g.line(g.w - 1, 1, 2 + n, g.h - 1, 1.6)
    return draw


def flag(n, down):
    def draw(g):
        for i in range(n):
            y = i * 5
            pts = [(0.8, y + 0.5), (3.5, y + 4), (6.5, y + 8), (7, y + 12), (5, y + 16)]
            if down:
                pts = [(x, g.h - 1 - py) for x, py in pts]
            g.curve(pts, 1.8 if i == 0 else 1.6)
    return draw


def treble(g):
    gy = 40  # the G line
    g.curve([(12, 2), (15, 6), (14, 12), (8, 20), (5, 28), (6, 36), (11, 43), (16, 40), (15, 34), (10, 33), (8, 38)], 2.2)
    g.line(12, 2, 11, 56, 1.8)
    g.curve([(11, 56), (8, 58), (5, 55), (7, 52), (9, 54)], 1.8)
    return gy


def bass(g):
    g.dot(5, 8, 3.2)
    g.curve([(4, 5), (9, 2), (15, 4), (16, 10), (12, 17), (4, 24)], 2.2)
    g.dot(19.5, 4.5, 1.5)
    g.dot(19.5, 11.5, 1.5)


def c_clef(g):
    g.rect(0, 0, 4, 34)
    g.rect(5, 0, 7, 34)
    for top in (True, False):
        pts = [(7, 16), (10, 12), (14, 9), (15, 5), (12, 1), (9, 3)] if top else [(7, 18), (10, 22), (14, 25), (15, 29), (12, 33), (9, 31)]
        g.curve(pts, 2.0)


def percussion(g):
    g.rect(1, 0, 4, 16)
    g.rect(6, 0, 9, 16)


def treble_8vb(g):
    treble(g)
    g.bitmap(DIGITS["8"], 9, 61, 1)


def sharp(g):
    g.line(2.5, 2, 2.5, 21, 1.4)
    g.line(5.5, 1, 5.5, 20, 1.4)
    g.line(0.5, 8.5, 7.5, 6, 2.4)
    g.line(0.5, 15.5, 7.5, 13, 2.4)


def flat(g):
    g.line(1, 0, 1, 19, 1.5)
    g.curve([(1, 13), (4, 10.5), (6, 12.5), (4, 16), (1, 19)], 1.8)


def natural(g):
    g.line(1, 0, 1, 15, 1.4)
    g.line(5, 7, 5, 22, 1.4)
    g.line(0.5, 8, 5.5, 6.5, 2.2)
    g.line(0.5, 15, 5.5, 13.5, 2.2)


def double_sharp(g):
    g.line(1, 1, 7, 7, 1.6)
    g.line(7, 1, 1, 7, 1.6)
    for x, y in ((1, 1), (7, 1), (1, 7), (7, 7)):
        g.rect(x - 1, y - 1, x + 1, y + 1)


def double_flat(g):
    for x in (0, 5):
        g.line(x + 1, 0, x + 1, 19, 1.5)
        g.curve([(x + 1, 13), (x + 4, 10.5), (x + 6, 12.5), (x + 4, 16), (x + 1, 19)], 1.8)


def digit(ch):
    return lambda g: g.bitmap(DIGITS[ch], 0, 0, 2)


def common_time(g):
    g.curve([(10, 2), (6, 0.5), (2, 4), (1.5, 9), (3, 14), (7, 15.5), (10.5, 13)], 2.4)


def cut_time(g):
    common_time_shifted = [(10, 5), (6, 3.5), (2, 7), (1.5, 12), (3, 17), (7, 18.5), (10.5, 16)]
    g.curve(common_time_shifted, 2.4)
    g.line(6, 0, 6, 22, 1.6)


def letter(ch):
    return lambda g: g.bitmap(LETTERS[ch], 0, 0, 2, slant=0.35)


def staccatissimo(g):
    for y in range(8):
        half = (y + 1) / 8 * 1.6
        g.rect(2 - half, y, 2 + half, y + 1)


def accent(g):
    g.line(0.5, 0.5, 9.5, 4, 1.6)
    g.line(9.5, 4, 0.5, 7.5, 1.6)


def marcato(g):
    g.line(0.5, 8.5, 4.5, 0.5, 2.0)
    g.line(4.5, 0.5, 8.5, 8.5, 2.0)


def fermata(g):
    g.curve([(1, 9), (3, 3), (9, 0.8), (15, 3), (17, 9)], 1.8)
    g.dot(9, 8, 1.6)


def trill(g):
    g.bitmap(LETTERS["t"], 0, 2, 2, slant=0.3)
    g.bitmap(LETTERS["r"], 8, 4, 1, slant=0.3)
    g.bitmap(LETTERS["r"], 9, 4, 1, slant=0.3)


def mordent(g, line_through):
    g.line(0.5, 6, 4, 1.5, 1.8)
    g.line(4, 1.5, 7.5, 6, 1.8)
    g.line(7.5, 6, 11, 1.5, 1.8)
    g.line(11, 1.5, 14.5, 6, 1.8)
    if line_through:
        g.line(7.5, 0, 7.5, 8, 1.2)


def turn(g):
    g.curve([(1, 5), (2, 1.5), (5, 1.5), (7.5, 4), (10, 6.5), (13, 6.5), (14, 3)], 1.9)
    g.dot(1.5, 6, 1.4)
    g.dot(13.5, 2, 1.4)


def segno(g):
    g.curve([(12, 4), (8, 2), (4, 5), (8, 10), (12, 15), (8, 18), (4, 16)], 2.2)
    g.line(2, 18, 14, 2, 1.4)
    g.dot(3, 7, 1.6)
    g.dot(13, 13, 1.6)


def coda(g):
    for a in range(0, 360, 3):
        r = math.radians(a)
        g.dot(9 + 6 * math.cos(r), 9 + 7 * math.sin(r), 1.0)
    g.line(9, 0, 9, 18, 1.4)
    g.line(0, 9, 18, 9, 1.4)


GLYPHS = [
    Glyph("head_black", 10, 9, 0, 4.5, head_black),
    Glyph("head_half", 10, 9, 0, 4.5, head_half),
    Glyph("head_whole", 13, 9, 0, 4.5, head_whole),
    Glyph("head_breve", 19, 9, 3, 4.5, head_breve),
    Glyph("head_x", 9, 9, 0, 4.5, head_x),
    Glyph("rest_breve", 5, 8, 0, 0, lambda g: g.rect(0, 0, 5, 8)),
    Glyph("rest_whole", 11, 4, 0, 0, lambda g: g.rect(0, 0, 11, 4)),
    Glyph("rest_half", 11, 4, 0, 4, lambda g: g.rect(0, 0, 11, 4)),
    Glyph("rest_quarter", 8, 22, 0, 11, rest_quarter),
    Glyph("rest_8", 8, 14, 0, 6, rest_flagged(1)),
    Glyph("rest_16", 9, 20, 0, 9, rest_flagged(2)),
    Glyph("rest_32", 10, 26, 0, 13, rest_flagged(3)),
    Glyph("rest_64", 11, 32, 0, 15, rest_flagged(4)),
    Glyph("flag_8_up", 8, 18, 0, 0, flag(1, False)),
    Glyph("flag_16_up", 8, 22, 0, 0, flag(2, False)),
    Glyph("flag_32_up", 8, 26, 0, 0, flag(3, False)),
    Glyph("flag_64_up", 8, 30, 0, 0, flag(4, False)),
    Glyph("flag_8_down", 8, 18, 0, 18, flag(1, True)),
    Glyph("flag_16_down", 8, 22, 0, 22, flag(2, True)),
    Glyph("flag_32_down", 8, 26, 0, 26, flag(3, True)),
    Glyph("flag_64_down", 8, 30, 0, 30, flag(4, True)),
    Glyph("clef_treble", 22, 60, 0, 40, treble),
    Glyph("clef_bass", 22, 26, 0, 8, bass),
    Glyph("clef_c", 17, 34, 0, 17, c_clef),
    Glyph("clef_percussion", 10, 16, 0, 8, percussion),
    Glyph("clef_treble_8vb", 22, 70, 0, 40, treble_8vb),
    Glyph("sharp", 8, 22, 0, 11, sharp),
    Glyph("flat", 7, 20, 0, 15, flat),
    Glyph("natural", 6, 22, 0, 11, natural),
    Glyph("double_sharp", 8, 8, 0, 4, double_sharp),
    Glyph("double_flat", 12, 20, 0, 15, double_flat),
    Glyph("dot", 3, 3, 0, 1.5, lambda g: g.dot(1.5, 1.5, 1.6)),
    *[Glyph("time_" + d, 10, 16, 0, 8, digit(d)) for d in "0123456789"],
    Glyph("time_common", 12, 16, 0, 8, common_time),
    Glyph("time_cut", 12, 22, 0, 11, cut_time),
    *[Glyph("dyn_" + ch, 13, 14, 0, 14, letter(ch)) for ch in "pmfszr"],
    Glyph("staccato", 3, 3, 1.5, 1.5, lambda g: g.dot(1.5, 1.5, 1.6)),
    Glyph("staccatissimo", 4, 8, 2, 4, staccatissimo),
    Glyph("tenuto", 10, 2, 5, 1, lambda g: g.rect(0, 0, 10, 2)),
    Glyph("accent", 10, 8, 5, 4, accent),
    Glyph("marcato", 9, 9, 4.5, 4.5, marcato),
    Glyph("fermata", 18, 10, 9, 10, fermata),
    Glyph("trill", 16, 16, 8, 8, trill),
    Glyph("mordent", 15, 8, 7.5, 4, lambda g: mordent(g, True)),
    Glyph("inverted_mordent", 15, 8, 7.5, 4, lambda g: mordent(g, False)),
    Glyph("turn", 15, 8, 7.5, 4, turn),
    Glyph("segno", 16, 20, 8, 10, segno),
    Glyph("coda", 18, 18, 9, 9, coda),
]


def atlas():
    """Draws every glyph into a 256 × 256 atlas; returns it and each glyph's [u, v, w, h, anchor x, anchor y]."""
    c = Canvas(0, size=256)
    layout = {}
    x, y, row = 1, 1, 0
    for glyph in GLYPHS:
        if x + glyph.w + 1 > 256:
            x, y, row = 1, y + row + 1, 0
        glyph.draw(Cell(c, x, y, glyph.w, glyph.h))
        layout[glyph.name] = [x, y, glyph.w, glyph.h, glyph.ax, glyph.ay]
        x += glyph.w + 1
        row = max(row, glyph.h)
    assert y + row <= 256, "the glyph atlas is full"
    return c, layout
