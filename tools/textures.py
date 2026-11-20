"""Original MineScore textures, drawn procedurally in a Minecraft-style pixel-art look.

Everything here is 32x32 (the default Minecraft-native set). Shading follows Minecraft's conventions:
light from the top-left, a few tones per material, subtle per-pixel noise, and darker item outlines.

Each style (SCOPE.md §11.1) is the same drawings in its own palette: MINESCORE_STYLE picks it, and
gen_resources.py draws each built-in pack in a fresh process so every default argument picks it up too.
"""
import math
import os
import random

SIZE = 32
CLEAR = (0, 0, 0, 0)


def clamp(v):
    return max(0, min(255, int(round(v))))


def tone(color, f):
    """Lighten (f > 1) or darken (f < 1) a colour."""
    return (clamp(color[0] * f), clamp(color[1] * f), clamp(color[2] * f), color[3] if len(color) > 3 else 255)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, seed, size=SIZE, fill=CLEAR):
        self.size = size
        self.px = [[fill] * size for _ in range(size)]
        self.rng = random.Random(seed)

    def set(self, x, y, c):
        if 0 <= x < self.size and 0 <= y < self.size:
            self.px[y][x] = c if len(c) == 4 else tuple(c) + (255,)

    def get(self, x, y):
        return self.px[y][x]

    def noise_fill(self, x1, y1, x2, y2, base, amount=0.06):
        """Fill [x1,x2) x [y1,y2) with a material colour plus per-pixel variation."""
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, tone(base, 1 + self.rng.uniform(-amount, amount) * NOISE))

    def rect(self, x1, y1, x2, y2, c):
        for y in range(y1, y2):
            for x in range(x1, x2):
                self.set(x, y, c)

    def bevel(self, x1, y1, x2, y2, light, dark):
        """A raised edge: light top/left, dark bottom/right."""
        for x in range(x1, x2):
            self.set(x, y1, light)
            self.set(x, y2 - 1, dark)
        for y in range(y1, y2):
            self.set(x1, y, light)
            self.set(x2 - 1, y, dark)

    def inset(self, x1, y1, x2, y2, light, dark):
        """A sunken edge: dark top/left, light bottom/right."""
        self.bevel(x1, y1, x2, y2, dark, light)

    def disc(self, cx, cy, r, color_fn):
        """color_fn(distance, angle) -> colour or None, for every pixel within radius r."""
        for y in range(self.size):
            for x in range(self.size):
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                if d <= r:
                    c = color_fn(d, math.atan2(dy, dx))
                    if c is not None:
                        self.set(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / steps
            self.set(int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t)), c)

    def screw(self, x, y):
        self.set(x, y, (205, 205, 210))
        self.set(x + 1, y, (150, 150, 158))
        self.set(x, y + 1, (130, 130, 138))
        self.set(x + 1, y + 1, (80, 80, 88))

    def outline(self, f=0.55):
        """Darken opaque pixels that touch transparency, like vanilla item sprites."""
        edge = []
        for y in range(self.size):
            for x in range(self.size):
                if self.px[y][x][3] == 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if not (0 <= nx < self.size and 0 <= ny < self.size) or self.px[ny][nx][3] == 0:
                        edge.append((x, y))
                        break
        for x, y in edge:
            self.px[y][x] = tone(self.px[y][x], f)

    def rows(self):
        return self.px


# --- materials ---------------------------------------------------------------------------------

WALNUT = (118, 80, 50)
IRON = (150, 153, 160)
PANEL = (52, 54, 61)
CONE = (46, 46, 52)
RUBBER = (26, 26, 30)
PAPER = (236, 228, 205)
INK = (40, 36, 48)
COPPER = (198, 116, 64)
GOLD = (232, 190, 64)

# The colours a style changes, then each style's own. "minecraft" is the default look.
STYLE = os.environ.get("MINESCORE_STYLE", "minecraft")
BAFFLE = (36, 37, 42)            # speaker baffles
CAP = (70, 70, 78)               # speaker dust caps
TWEETER_DOME = (190, 192, 200)
KNOB, KNOB_MARK = (175, 178, 186), (40, 40, 46)
FADER = (205, 205, 212)
LED_GREEN, LED_AMBER, LED_RED = (90, 230, 90), (230, 210, 70), (255, 70, 50)
PILOT = (255, 120, 60)           # the Vinyl Player's power light
LAMPS = ((70, 140, 255), (90, 230, 90), (255, 150, 60))  # the Channel Manager's L, M and R
VU_LIGHT = (226, 214, 170)
WINDOW = (30, 31, 36)            # dark windows on block faces
BRASS = (220, 175, 70)
LEATHER = (52, 78, 58)           # the desk's inlay
BOOK_COVER = (120, 40, 40)       # the Manual
MEU_BODY, MEU_SCREEN = (40, 42, 48), (12, 18, 10)
MP3_BODY, MP3_SCREEN = (226, 228, 234), ((40, 70, 90), (90, 190, 200), (30, 60, 80))
GUI_MP3 = ((46, 48, 54), (16, 24, 26))       # (body, display) of each device screen
GUI_AMP = ((66, 46, 32), (14, 16, 18))
GUI_CM = ((64, 68, 76), (15, 20, 22))
GUI_SPEAKER = ((88, 60, 40), (16, 22, 22))
GUI_GREY = (198, 198, 198)       # the vanilla-style container screens
GUI_WELL = (30, 28, 34)
NOISE = 1.0                      # how grainy surfaces are
SCREWS = True
BRUSHED = False                  # brushed-metal streaks on metal panels

if STYLE == "retro":  # 80s hi-fi: brushed silver, rosewood, chrome, teal VFDs and amber lamps
    WALNUT = (150, 82, 48)
    IRON = (196, 198, 204)
    PANEL = (164, 166, 172)
    CONE = (92, 76, 60)
    BAFFLE = (34, 32, 30)
    CAP = (190, 192, 198)
    TWEETER_DOME = (214, 204, 176)
    KNOB, KNOB_MARK = (218, 220, 226), (30, 30, 34)
    FADER = (232, 232, 238)
    LED_GREEN, LED_AMBER, LED_RED = (80, 235, 210), (255, 180, 60), (255, 110, 40)
    PILOT = (255, 150, 50)
    LAMPS = ((80, 235, 210), (255, 180, 60), (255, 110, 40))
    VU_LIGHT = (250, 206, 120)
    WINDOW = (14, 28, 28)
    LEATHER = (112, 40, 36)
    BOOK_COVER = (96, 52, 30)
    MEU_BODY, MEU_SCREEN = (160, 162, 168), (8, 28, 26)
    MP3_BODY, MP3_SCREEN = (176, 178, 186), ((20, 40, 40), (80, 235, 210), (16, 60, 56))
    GUI_MP3 = ((164, 166, 172), (10, 26, 26))
    GUI_AMP = ((120, 66, 40), (10, 24, 24))
    GUI_CM = ((172, 174, 180), (10, 24, 24))
    GUI_SPEAKER = ((120, 66, 40), (10, 24, 24))
    GUI_GREY = (206, 206, 212)
    GUI_WELL = (14, 28, 28)
    BRUSHED = True
elif STYLE == "modern":  # studio: matte black, pale birch, white cones, ice-blue and white LEDs
    WALNUT = (206, 180, 140)
    IRON = (72, 74, 80)
    PANEL = (34, 35, 38)
    CONE = (214, 214, 218)
    BAFFLE = (24, 24, 26)
    CAP = (226, 226, 230)
    TWEETER_DOME = (232, 232, 236)
    KNOB, KNOB_MARK = (62, 64, 70), (230, 240, 255)
    FADER = (232, 234, 242)
    LED_GREEN, LED_AMBER, LED_RED = (120, 200, 255), (200, 230, 255), (245, 245, 252)
    PILOT = (120, 200, 255)
    LAMPS = ((120, 200, 255), (245, 245, 252), (255, 170, 120))
    VU_LIGHT = (220, 232, 244)
    WINDOW = (8, 8, 10)
    BRASS = (182, 184, 192)
    LEATHER = (42, 44, 48)
    BOOK_COVER = (38, 40, 44)
    MEU_BODY, MEU_SCREEN = (30, 31, 34), (4, 6, 8)
    MP3_BODY, MP3_SCREEN = (40, 40, 44), ((10, 10, 12), (200, 225, 255), (40, 60, 90))
    GUI_MP3 = ((34, 35, 38), (6, 8, 10))
    GUI_AMP = ((34, 35, 38), (6, 8, 10))
    GUI_CM = ((40, 41, 45), (6, 8, 10))
    GUI_SPEAKER = ((150, 126, 92), (6, 8, 10))
    GUI_GREY = (186, 188, 194)
    GUI_WELL = (16, 16, 20)
    NOISE = 0.4
    SCREWS = False

# Tiny 3x5 font for panel labels.
FONT = {
    "L": ["X..", "X..", "X..", "X..", "XXX"],
    "R": ["XX.", "X.X", "XX.", "X.X", "X.X"],
    "M": ["X.X", "XXX", "XXX", "X.X", "X.X"],
}


def text(c, s, x, y, color):
    for i, ch in enumerate(s):
        for row, bits in enumerate(FONT[ch]):
            for col, bit in enumerate(bits):
                if bit == "X":
                    c.set(x + i * 4 + col, y + row, color)


def planks(seed, base=WALNUT, horizontal=True):
    """Four planks with grain streaks and dark seams."""
    c = Canvas(seed)
    for p in range(4):
        tint = tone(base, 1 + c.rng.uniform(-0.07, 0.07) * NOISE)
        for i in range(8):
            for j in range(SIZE):
                x, y = (j, p * 8 + i) if horizontal else (p * 8 + i, j)
                grain = 0.93 if (j + p * 5 + i * 3) % 11 < 2 else 1.0
                c.set(x, y, tone(tint, grain * (1 + c.rng.uniform(-0.04, 0.04) * NOISE)))
        for j in range(SIZE):
            x, y = (j, p * 8 + 7) if horizontal else (p * 8 + 7, j)
            c.set(x, y, tone(tint, 0.68))
            x, y = (j, p * 8) if horizontal else (p * 8, j)
            c.set(x, y, tone(tint, 1.1))
        # a knot per plank
        kx, ky = c.rng.randrange(4, 28), p * 8 + 3
        if not horizontal:
            kx, ky = p * 8 + 3, c.rng.randrange(4, 28)
        c.set(kx, ky, tone(tint, 0.7))
        c.set(kx + 1, ky, tone(tint, 0.8))
    return c


def round_edges(c, width=6, light=0.17, dark=0.3):
    """Shades a face's rim so neighbouring faces of a chamfered block blend into a rounded edge:
    lighter toward the top-left, darker toward the bottom-right, fading in over `width` pixels."""
    n = c.size
    for y in range(n):
        for x in range(n):
            p = c.px[y][x]
            if p[3] == 0:
                continue
            f = 1.0
            for d, sign in ((x, light), (y, light), (n - 1 - x, -dark), (n - 1 - y, -dark)):
                if d < width:
                    f += sign * (width - d) / width
            c.px[y][x] = tone(p, f)
    return c


def iron_plate(seed, base=IRON, screws=True):
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, base, 0.05)
    if BRUSHED:  # fine horizontal streaks
        for y in range(SIZE):
            f = 1 + 0.05 * math.sin(y * 2.3 + seed) + c.rng.uniform(-0.02, 0.02)
            for x in range(SIZE):
                c.set(x, y, tone(c.get(x, y), f))
    if screws and SCREWS:
        for x, y in ((4, 4), (26, 4), (4, 26), (26, 26)):
            c.screw(x, y)
    return c


def dark_panel(seed, base=PANEL):
    return iron_plate(seed, base, screws=True)


def arrow(c, cx, top, pointing_down, color):
    """A 5-wide, 3-tall chevron: down for inputs (signal goes in), up for outputs."""
    rows = [(-2, 2), (-1, 1), (0, 0)] if pointing_down else [(0, 0), (-1, 1), (-2, 2)]
    for i, (a, b) in enumerate(rows):
        for dx in range(a, b + 1):
            c.set(cx + dx, top + i, color)


def back_plate(seed, base, jacks, frame, hub):
    """The back of a block with jacks, drawn from its jack layout.

    base:  "panel", "iron" or "wood"
    jacks: (x1, y1, x2, y2, output) in model pixels on the back face
    frame: (x1, y1, x2, y2) of the back face in model pixels, mapped onto the whole texture
    hub:   draw the divider between the hub-link half (left) and the speaker-band half (right)
    """
    if base == "wood":
        c = planks(seed)
        plate = (44, 45, 51)
    else:
        c = iron_plate(seed, PANEL if base == "panel" else IRON, screws=True)
        plate = tone(PANEL if base == "panel" else IRON, 0.78)
    fx1, fy1, fx2, fy2 = frame
    sx, sy = SIZE / (fx2 - fx1), SIZE / (fy2 - fy1)

    def tex(x, y):
        return int(round((x - fx1) * sx)), int(round((fy2 - y) * sy))

    if base == "wood" and jacks:  # a dark terminal plate behind a speaker's jacks
        x1 = min(j[0] for j in jacks) - 1.5
        x2 = max(j[2] for j in jacks) + 1.5
        y1 = min(j[1] for j in jacks) - 1
        y2 = max(j[3] for j in jacks) + 2.5
        (a, b), (d, e) = tex(x1, y2), tex(x2, y1)
        c.rect(a, b, d, e, plate)
        c.inset(a, b, d, e, (30, 30, 34), (80, 82, 90))
    if hub:
        mid, _ = tex((fx1 + fx2) / 2, 0)
        for y in range(3, SIZE - 3):
            c.set(mid - 1, y, tone(plate, 0.6))
            c.set(mid, y, tone(plate, 1.35))
    for x1, y1, x2, y2, output in jacks:
        (a, b), (d, e) = tex(x1, y2), tex(x2, y1)
        c.rect(a - 1, b - 1, d + 1, e + 1, tone(plate, 0.55))  # recess around the jack
        arrow(c, (a + d) // 2, b - 4, not output, (250, 250, 240) if output else (150, 205, 255))
    return c


# --- speakers ----------------------------------------------------------------------------------

def speaker_front(seed, radius, cap):
    """A wooden cabinet with a dark baffle and the driver's rubber surround. Inside the surround it's clear:
    the cone itself is 3D (SpeakerRenderer), sunk into a pocket behind the face."""
    c = planks(seed)
    c.rect(2, 2, 30, 30, PANEL)
    c.noise_fill(2, 2, 30, 30, BAFFLE, 0.05)
    c.inset(2, 2, 30, 30, (24, 24, 28), (70, 72, 80))
    cx = cy = 16

    def driver(d, a):
        if d > radius - 1.2:
            return tone(RUBBER, 1.25 if math.sin(a) < 0 else 0.9)  # surround, lit from top-left
        if d > radius - 2.6:
            return tone(RUBBER, 0.8)
        return CLEAR

    c.disc(cx, cy, radius, driver)
    if SCREWS:
        for x, y in ((3, 3), (27, 3), (3, 27), (27, 27)):
            c.screw(x, y)
    return c


def tweeter_front(seed):
    c = planks(seed)
    c.rect(3, 3, 29, 29, BAFFLE)
    c.inset(3, 3, 29, 29, (24, 24, 28), (70, 72, 80))

    def dome(d, a):
        if d > 9:
            return tone((60, 62, 70), 1.05 if math.sin(a) < 0 else 0.85)  # waveguide
        if d > 7.6:
            return RUBBER
        return CLEAR  # the dome itself is 3D (SpeakerRenderer)

    c.disc(16, 16, 11, dome)
    return c


def speaker_back(seed):
    c = planks(seed)
    c.rect(6, 11, 26, 21, PANEL)
    c.inset(6, 11, 26, 21, (30, 30, 34), (80, 82, 90))
    c.screw(7, 12)
    c.screw(23, 18)
    return c


# --- hubs --------------------------------------------------------------------------------------

def knob(c, cx, cy, r, angle):
    c.disc(cx, cy, r, lambda d, a: tone(KNOB, 1.2 - 0.35 * d / r + (0.12 if a < -1 else 0)))
    c.line(cx, cy, cx + math.cos(angle) * (r - 0.6), cy + math.sin(angle) * (r - 0.6), KNOB_MARK)


def amplifier_front(seed, on=True):
    """Unpowered (on=False): the VU meter's backlight off and its needle resting at the left, LED dark."""
    c = dark_panel(seed)
    # VU meter window
    c.rect(4, 5, 17, 14, VU_LIGHT if on else tone(VU_LIGHT, 0.5))
    c.inset(4, 5, 17, 14, (30, 30, 34), (90, 92, 100))
    for i in range(6):
        c.set(6 + i * 2, 7, (60, 50, 40) if i < 4 else (190, 40, 30) if on else (84, 64, 58))
    if on:
        c.line(10, 12, 13, 8, (30, 28, 26))
    else:
        c.line(10, 12, 6, 9, (30, 28, 26))
    # knobs
    knob(c, 22, 9.5, 3.4, -2.2)
    knob(c, 22, 20.5, 3.4, -0.6)
    knob(c, 11, 20.5, 3.4, -1.4)
    # power LED
    c.set(27, 26, LED_RED if on else tone(LED_RED, 0.26))
    c.set(28, 26, tone(LED_RED, 0.78) if on else tone(LED_RED, 0.21))
    # grille along the bottom
    for x in range(4, 18, 2):
        c.rect(x, 25, x + 1, 28, (26, 26, 30))
    return c


def vents(seed, base):
    c = iron_plate(seed, base)
    for y in range(7, 26, 3):
        c.rect(6, y, 26, y + 1, tone(base, 0.45))
        c.rect(6, y + 1, 26, y + 2, tone(base, 1.15))
    return c


def channel_manager_front(seed, on=True):
    """Unpowered (on=False): the L/M/R lamps dark grey and their letters dim."""
    c = dark_panel(seed)
    c.rect(4, 6, 28, 26, WINDOW)
    c.inset(4, 6, 28, 26, (18, 18, 22), (90, 92, 100))
    lamps = (("L", 9, LAMPS[0]), ("M", 16, LAMPS[1]), ("R", 23, LAMPS[2]))
    for label, x, color in lamps:
        if not on:
            color = (64, 66, 72)
        text(c, label, x - 1, 9, (220, 220, 226) if on else (120, 120, 126))
        c.disc(x + 0.5, 19, 2.8, lambda d, a, col=color: tone(col, 1.3 - d / 4 + (0.2 if a < -1 else 0)))
    return c


def mixer_top(seed):
    c = dark_panel(seed)
    for i, (x, h) in enumerate(((6, 18), (11, 12), (16, 21), (21, 15), (26, 9))):
        c.rect(x, 9, x + 1, 27, (16, 16, 20))  # fader track
        c.rect(x - 1, h, x + 2, h + 2, FADER)  # fader cap
        c.set(x - 1, h + 1, (140, 140, 148))
        c.disc(x + 0.5, 5.5, 1.6, lambda d, a: KNOB + (255,) if d < 1.2 else (90, 92, 100, 255))
    return c


def mixer_front(seed, on=True, docked=False):
    """Unpowered (on=False): its level LEDs dark. Docked: an MP3 Player standing in the dock, its screen lit
    while the system has power and dark when it hasn't."""
    c = dark_panel(seed)
    # MP3 Player dock
    c.rect(8, 8, 24, 18, (20, 20, 24))
    c.inset(8, 8, 24, 18, (12, 12, 14), (90, 92, 100))
    c.rect(14, 16, 18, 18, (120, 120, 128))
    # transport buttons and level LEDs
    for i, color in enumerate((LED_GREEN, LED_GREEN, LED_AMBER, LED_RED)):
        c.set(8 + i * 2, 22, color if on else (64, 66, 72))
    for x in (18, 21, 24):
        c.rect(x, 21, x + 2, 24, tone(KNOB, 0.97))
        c.set(x + 1, 23, (110, 110, 118))
    if docked:  # the player stands in the dock, its lower end hidden behind the dock's lip
        c.rect(11, 3, 21, 16, MP3_BODY)
        c.bevel(11, 3, 21, 16, tone(MP3_BODY, 1.1), tone(MP3_BODY, 0.7))
        rim, glow, text_line = MP3_SCREEN
        c.rect(12, 4, 20, 9, rim)
        if on:
            c.rect(13, 5, 19, 8, glow)
            c.rect(14, 6, 18, 7, text_line)
        else:
            c.rect(13, 5, 19, 8, tone(rim, 0.6))
        c.disc(16, 12.5, 2.6, lambda d, a: tone(MP3_BODY, 0.82) if d > 1 else tone(MP3_BODY, 1.05))
        c.rect(9, 15, 23, 18, (20, 20, 24))  # the dock's lip in front of it
        c.inset(8, 15, 24, 18, (12, 12, 14), (90, 92, 100))
    return c


# --- lathe -------------------------------------------------------------------------------------

def lathe_top(seed):
    c = iron_plate(seed)

    def platter(d, a):
        if d < 1.6:
            return (220, 222, 228, 255)
        if d < 3.4:
            return tone((180, 50, 40), 1.0 if a < 0 else 0.85)  # record label
        if d > 12.2:
            return tone((150, 152, 160), 0.6)
        groove = 0.82 if int(d * 2) % 2 else 1.0
        return tone((30, 30, 34), groove * (1.35 if -2.2 < a < -1.2 else 1.0))

    c.disc(14, 17, 13, platter)
    # cutting arm from the top-right corner to the record
    c.line(28, 4, 20, 13, (200, 202, 208))
    c.line(29, 5, 21, 14, (120, 122, 130))
    c.rect(18, 12, 22, 15, (230, 180, 60))  # cutting head
    c.set(19, 15, (255, 240, 200))
    c.disc(28, 4, 2.6, lambda d, a: tone((110, 112, 120), 1.2 - d / 3))
    return c


def lathe_side(seed):
    """The half-height Lathe's sides show the bottom half. The top half is plain iron rather than clear, so no
    face that reaches into it (the bevels' edges, the deck's sides) can be seen through."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, 16, IRON, 0.05)
    c.noise_fill(0, 16, SIZE, SIZE, IRON, 0.05)
    c.bevel(0, 16, SIZE, SIZE, tone(IRON, 1.18), tone(IRON, 0.7))
    c.rect(2, 22, 30, 23, tone(IRON, 0.6))
    c.rect(2, 23, 30, 24, tone(IRON, 1.15))
    c.screw(3, 18)
    c.screw(27, 18)
    c.screw(3, 27)
    c.screw(27, 27)
    return c


def lathe_front(seed):
    c = lathe_side(seed)
    knob(c, 9, 24, 3.2, -2.0)
    c.rect(17, 21, 27, 27, (30, 30, 34))
    c.inset(17, 21, 27, 27, (20, 20, 24), (90, 92, 100))
    c.rect(18, 23, 22, 25, LED_GREEN)  # speed readout
    return c


def lathe_back(seed):
    """Back of the Lathe, with the recess and arrow for its one Amplifier input (model x 7-9, y 3-5)."""
    c = lathe_side(seed)
    c.rect(11, 19, 21, 28, PANEL)
    c.inset(11, 19, 21, 28, (30, 30, 34), (90, 92, 100))
    c.rect(13, 21, 19, 27, tone(PANEL, 0.55))
    arrow(c, 16, 18, True, (150, 205, 255))
    return c


# --- writer's desk -----------------------------------------------------------------------------

def staff_lines(c, x1, x2, y, color):
    for i in range(5):
        c.rect(x1, y + i * 2, x2, y + i * 2 + 1, color)


def desk_tops(seed):
    """The whole desk's top as seen from above, front edge at the top of the picture, split into the two
    halves' textures: (left half, right half) as you face the desk. A green leather inlay framed in walnut
    runs across both, with the score sheet in the middle, straddling the seam."""
    wide = Canvas(seed, size=64)
    for y in range(SIZE):
        for x in range(64):
            grain = 1 + 0.06 * math.sin(x * 0.7 + (y // 4) * 1.9) + wide.rng.uniform(-0.04, 0.04)
            wide.set(x, y, tone(WALNUT, grain))
    leather = LEATHER
    wide.noise_fill(4, 3, 60, 29, leather, 0.05)
    wide.inset(4, 3, 60, 29, tone(leather, 0.6), tone(leather, 1.3))
    for x in range(6, 58, 2):  # gold tooling round the inlay
        wide.set(x, 5, (190, 160, 80))
        wide.set(x, 26, (190, 160, 80))
    # the score sheet, square in the middle
    wide.rect(22, 7, 42, 25, PAPER)
    wide.noise_fill(22, 7, 42, 25, PAPER, 0.02)
    wide.bevel(22, 7, 42, 25, (250, 246, 230), (190, 180, 150))
    staff_lines(wide, 24, 40, 9, (150, 140, 160))
    staff_lines(wide, 24, 40, 17, (150, 140, 160))
    for x, y in ((26, 12), (29, 11), (33, 13), (36, 10), (27, 20), (34, 21)):
        wide.set(x, y, INK)
        wide.set(x + 1, y, INK)
        wide.rect(x + 1, y - 3, x + 2, y, INK)
    # an inkwell at each end, matching
    for cx in (12.5, 51.5):
        wide.disc(cx, 16.5, 2.8, lambda d, a: tone((30, 30, 40), 1.4 - d / 3))
    # the picture's left (west) is the right half as you face a north-facing desk from its front
    right, left = Canvas(seed), Canvas(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            right.set(x, y, wide.get(x, y))
            left.set(x, y, wide.get(x + SIZE, y))
    return left, right


def desk_top(seed):
    c = planks(seed, horizontal=False)
    # score sheet
    c.rect(5, 4, 22, 28, PAPER)
    c.noise_fill(5, 4, 22, 28, PAPER, 0.02)
    c.bevel(5, 4, 22, 28, (250, 246, 230), (190, 180, 150))
    staff_lines(c, 7, 20, 7, (150, 140, 160))
    staff_lines(c, 7, 20, 18, (150, 140, 160))
    for x, y in ((9, 10), (12, 9), (15, 11), (18, 8), (10, 20), (14, 21)):
        c.set(x, y, INK)
        c.set(x + 1, y, INK)
        c.rect(x + 1, y - 4, x + 2, y, INK)
    # inkwell and quill
    c.disc(26.5, 22.5, 2.8, lambda d, a: tone((30, 30, 40), 1.4 - d / 3))
    c.line(26, 21, 29, 7, (240, 240, 235))
    c.line(27, 21, 30, 8, (200, 200, 195))
    return c


def desk_front(seed):
    """The fronts of the desk's drawer pedestals: three drawers with brass handles at each side (the left
    half's pedestal shows the left 14 pixels, the right half's the right 14), mirror images."""
    c = planks(seed)
    for x1 in (1, 18):
        for y1 in (7, 15, 23):
            c.rect(x1, y1, x1 + 13, y1 + 7, tone(WALNUT, 0.9))
            c.bevel(x1, y1, x1 + 13, y1 + 7, tone(WALNUT, 1.2), tone(WALNUT, 0.6))
            c.rect(x1 + 5, y1 + 3, x1 + 8, y1 + 4, (205, 160, 70))  # brass handle
            c.set(x1 + 5, y1 + 4, (150, 110, 40))
    return c


ANCHOR_IRON = (104, 106, 113)


def anchor_post(seed):
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, ANCHOR_IRON, 0.05)
    return round_edges(c, width=8)


def anchor_flow(seed):
    """Post face that shows which way cables run: a faint engraved arrow from input (top) to output."""
    c = anchor_post(seed)
    groove = tone(ANCHOR_IRON, 0.78)
    c.rect(15, 5, 17, 22, groove)
    for i in range(7):
        c.set(10 + i, 18 + i, groove)
        c.set(11 + i, 18 + i, groove)
        c.set(21 - i, 18 + i, groove)
        c.set(20 - i, 18 + i, groove)
    return c


def anchor_end(seed, output):
    """Input stub a touch darker, output stub a touch lighter: enough to tell apart, nothing loud."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, tone(ANCHOR_IRON, 1.15 if output else 0.8), 0.04)
    c.disc(16, 16, 7, lambda d, a: tone(ANCHOR_IRON, 1.35 if output else 0.55))
    return c


def lathe_deck(seed):
    """Top of the Lathe's frame: brushed iron with fine grip lines."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, IRON, 0.04)
    for y in range(2, SIZE, 4):
        c.rect(0, y, SIZE, y + 1, tone(IRON, 0.9))
    return c


def lathe_well(seed):
    """The recess the platter sits in: dark, so the empty Lathe reads as a hollow."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, (30, 31, 36), 0.08)
    return c


def lathe_platter(seed):
    """An empty platter: a rubber mat with strobe dots round its rim and a bare spindle hole."""
    c = lathe_well(seed)

    def mat(d, a):
        if d > 14.5:
            return None
        if d > 13:
            dot = int((a + math.pi) / (2 * math.pi) * 36) % 2 == 0
            return (210, 212, 218, 255) if dot else (120, 122, 130, 255)  # strobe dots on the rim
        if d < 1.6:
            return (12, 12, 14, 255)
        ring = 0.9 if int(d * 1.3) % 2 else 1.0
        return tone((58, 58, 64), ring * (1.2 if -2.3 < a < -1.0 else 1.0))

    c.disc(16, 16, 15, mat)
    return c


def vinyl_front(seed):
    """Front of the Vinyl Player: walnut, with a brass speed knob and a little power light (bottom half shows)."""
    c = planks(seed)
    knob(c, 9, 24, 3.2, -2.0)
    c.disc(9, 24, 3.6, lambda d, a: None if d < 3.2 else (200, 160, 70, 255))  # brass ring
    c.set(24, 24, PILOT)
    c.set(25, 24, tone(PILOT, 0.75))
    c.rect(20, 27, 28, 28, tone(WALNUT, 0.6))
    return c


def tonearm(seed):
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, (196, 198, 206), 0.04)
    return round_edges(c, width=10)


def gui_mp3():
    """The MP3 Player screen, 256 × 200: a dark device body with a recessed display."""
    c = Canvas(201, size=256)
    W, H = 256, 200
    body, display = GUI_MP3
    c.rect(2, 0, W - 2, H, body)
    c.rect(0, 2, W, H - 2, body)
    c.noise_fill(2, 2, W - 2, H - 2, body, 0.03)
    for x in range(2, W - 2):
        c.set(x, 0, (20, 20, 24))
        c.set(x, H - 1, (20, 20, 24))
        c.set(x, 1, (96, 98, 106))
        c.set(x, H - 2, (28, 29, 33))
    for y in range(2, H - 2):
        c.set(0, y, (20, 20, 24))
        c.set(W - 1, y, (20, 20, 24))
        c.set(1, y, (96, 98, 106))
        c.set(W - 2, y, (28, 29, 33))
    # display
    c.rect(8, 26, W - 8, H - 26, display)
    c.inset(8, 26, W - 8, H - 26, tone(display, 0.6), tone(body, 1.6))
    # lower strip under the display, where the bottom buttons sit
    c.rect(8, H - 26, W - 8, H - 4, tone(body, 0.9))
    return c


def gui_device(seed, W, H, body, display, display_top=22, display_bottom=None, screws=True):
    """A device faceplate for a block's screen: a bevelled body with a recessed dark display."""
    c = Canvas(seed, size=256)
    c.rect(2, 0, W - 2, H, body)
    c.rect(0, 2, W, H - 2, body)
    c.noise_fill(2, 2, W - 2, H - 2, body, 0.035)
    light, dark, edge = tone(body, 1.45), tone(body, 0.6), (18, 18, 20)
    for x in range(2, W - 2):
        c.set(x, 0, edge)
        c.set(x, H - 1, edge)
        c.set(x, 1, light)
        c.set(x, H - 2, dark)
    for y in range(2, H - 2):
        c.set(0, y, edge)
        c.set(W - 1, y, edge)
        c.set(1, y, light)
        c.set(W - 2, y, dark)
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        c.set(x, y, edge)
    bottom = H - 6 if display_bottom is None else display_bottom
    c.rect(6, display_top, W - 6, bottom, display)
    c.inset(6, display_top, W - 6, bottom, tone(display, 0.6), tone(body, 1.25))
    if screws:
        for x, y in ((4, 4), (W - 6, 4), (4, H - 6), (W - 6, H - 6)):
            c.rect(x, y, x + 2, y + 2, tone(body, 0.75))
            c.set(x, y, tone(body, 1.5))
    return c


def gui_amplifier():
    """The Amplifier's screen, 256 × 220: walnut cheeks round a black brushed faceplate."""
    c = gui_device(210, 256, 220, *GUI_AMP, display_top=22)
    for y in range(24, 220 - 8):  # faint brushed lines on the display
        if y % 3 == 0:
            for x in range(8, 248):
                if (x * 7 + y * 13) % 11 == 0:
                    c.set(x, y, tone(GUI_AMP[1], 1.5))
    return c


def gui_channel_manager():
    """The Channel Manager's screen, 256 × 226: a steel panel with a dark list window."""
    return gui_device(211, 256, 226, *GUI_CM, display_top=46, display_bottom=202)


def gui_speaker():
    """A speaker's screen, 220 × 124: walnut, like the cabinets."""
    return gui_device(212, 220, 124, *GUI_SPEAKER, display_top=44, display_bottom=118)


def brass(seed):
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, BRASS, 0.06)
    return round_edges(c, width=10)


def jack(color, output):
    """A jack's face. Inputs: a thin coloured ring round a deep black socket.
    Outputs: a solid coloured plug body with a bright metal pin."""
    c = Canvas(0, size=16)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if output:
                if d > 6.5:
                    p = tone(color, 0.6)
                elif d < 2.5:
                    p = (245, 245, 235, 255) if x + y < 16 else (190, 190, 182, 255)
                else:
                    p = tone(color, 1.15 if x + y < 15 else 0.95)
            else:
                if d > 6.5:
                    p = tone(color, 0.7)
                elif d > 4.5:
                    p = tone(color, 1.05)
                else:
                    p = (8, 8, 10, 255) if d < 3.5 else (35, 35, 40, 255)
            c.set(x, y, p)
    return c


def bottom(seed):
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, (40, 41, 46), 0.07)
    return c


RUBBER = (26, 26, 28)


def foot(c, cx, cy, r=2.6):
    """A round rubber foot, lit from the top-left."""
    c.disc(cx, cy, r, lambda d, a: tone(RUBBER, 1.5 - 0.5 * d / r + (0.25 if a < -1.2 else 0)))


def plate(c, x1, y1, x2, y2, base):
    """A small maker's plate with a few lines of 'print' on it."""
    c.rect(x1, y1, x2, y2, tone(base, 1.25))
    c.bevel(x1, y1, x2, y2, tone(base, 1.5), tone(base, 0.8))
    for i, y in enumerate(range(y1 + 2, y2 - 1, 2)):
        c.rect(x1 + 2, y, x2 - 2 - (i % 2) * 3, y + 1, tone(base, 0.75))


def underside(seed, kind):
    """A block's underside (SCOPE.md §11): its body material with rubber feet in the corners, a maker's
    plate, and whatever that block has underneath."""
    wood = kind in ("tweeter_speaker", "middle_speaker", "subwoofer", "vinyl_player", "writers_desk", "music_cabinet")
    if wood:
        c = planks(seed)
    elif kind == "disc_cutting_lathe":
        c = iron_plate(seed, IRON, screws=False)
    elif kind == "vinyl_display":
        c = Canvas(seed)
        c.noise_fill(0, 0, SIZE, SIZE, tone(DISPLAY_SURFACE, 1.25), 0.05)
    elif kind == "cable_anchor":
        c = Canvas(seed)
        c.noise_fill(0, 0, SIZE, SIZE, ANCHOR_IRON, 0.06)
        c.screw(15, 15)
        return c
    else:
        c = iron_plate(seed, PANEL, screws=False)
    base = c.get(16, 16)[:3]
    c.bevel(0, 0, SIZE, SIZE, tone(base, 1.2), tone(base, 0.7))
    if kind == "vinyl_display":  # two felt strips, so it doesn't scratch what it stands on
        c.rect(3, 6, 29, 9, (70, 40, 44))
        c.rect(3, 23, 29, 26, (70, 40, 44))
        return c
    for fx, fy in ((5, 5), (26, 5), (5, 26), (26, 26)):
        if kind == "writers_desk":  # square pads under its legs
            c.rect(fx - 3, fy - 3, fx + 4, fy + 4, RUBBER)
            c.bevel(fx - 3, fy - 3, fx + 4, fy + 4, tone(RUBBER, 1.6), tone(RUBBER, 0.7))
        else:
            foot(c, fx + 0.5, fy + 0.5)
    if kind == "amplifier":  # heat-sink fins
        for x in range(9, 24, 3):
            c.rect(x, 9, x + 2, 23, tone(base, 0.55))
            c.rect(x, 9, x + 1, 23, tone(base, 1.3))
    elif kind == "channel_manager":  # a grid of vent holes round the plate
        for y in range(8, 25, 3):
            for x in range(8, 25, 3):
                if not (12 <= x <= 20 and 13 <= y <= 19):
                    c.set(x, y, tone(base, 0.35))
        plate(c, 12, 13, 21, 20, base)
    elif kind == "multi_effects_unit":  # a screwed-on access panel for the card slots, olive trim
        c.rect(8, 10, 24, 22, tone(base, 0.85))
        c.inset(8, 10, 24, 22, tone(base, 0.55), tone(base, 1.3))
        for sx, sy in ((9, 11), (22, 11), (9, 20), (22, 20)):
            c.set(sx, sy, (150, 150, 158))
        c.rect(10, 15, 22, 17, OLIVE)
    elif kind == "audio_mixer":  # long vent slots and a plate
        for y in (9, 12, 21, 24):
            c.rect(8, y, 24, y + 1, tone(base, 0.4))
        plate(c, 11, 14, 21, 19, base)
    elif kind == "disc_cutting_lathe":  # the bearing the platter turns on, with a socket a shaft fits
        c.disc(16, 16, 6.5, lambda d, a: tone(IRON, 0.75 if d > 5.5 else 1.1 - 0.2 * d / 5.5))
        c.rect(14, 14, 18, 18, (30, 30, 34))
        c.rect(15, 15, 17, 17, (60, 62, 68))
    elif kind == "subwoofer":  # a bass port
        c.disc(16, 16, 5, lambda d, a: (14, 12, 10, 255) if d < 4 else tone(base, 0.6))
    elif kind == "middle_speaker":  # a smaller port beside its plate
        c.disc(11, 16, 3, lambda d, a: (14, 12, 10, 255) if d < 2.2 else tone(base, 0.6))
        plate(c, 16, 13, 24, 19, base)
    elif kind == "tweeter_speaker":  # just a narrow plate: it's small
        plate(c, 12, 14, 20, 18, base)
    elif kind == "music_cabinet":  # a plinth round the edge, sunk a little, and a plain plate
        c.inset(2, 2, 30, 30, tone(base, 0.6), tone(base, 1.25))
        plate(c, 11, 13, 21, 19, base)
    elif kind == "vinyl_player":  # felt-padded feet and a brass plate
        plate(c, 11, 13, 21, 19, (170, 130, 60))
    else:
        plate(c, 11, 13, 21, 19, base)
    return c


# --- items -------------------------------------------------------------------------------------

TRIM_PATTERNS = ["sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib", "spire", "wayfinder",
                 "shaper", "silence", "raiser", "host"]


def vinyl_body(seed, grooves):
    """The record without its label (the label is a separate, dyeable layer)."""
    c = vinyl(seed, (60, 60, 66), grooves)
    for y in range(c.size):
        for x in range(c.size):
            if math.hypot(x + 0.5 - 16, y + 0.5 - 16) < 6 and c.px[y][x][3]:
                c.px[y][x] = (40, 40, 46, 255)  # under the label
    return c


def vinyl_label(seed, printed):
    """The label in light greys, tinted by the record's dye colour in game."""
    c = Canvas(seed)
    c.disc(16, 16, 6, lambda d, a: None if d < 1.4 else tone((235, 235, 235), 1.0 if a < -0.3 else 0.86))
    if printed:
        for y in (13, 15, 18):
            c.rect(13, y, 20 if y != 15 else 18, y + 1, (255, 255, 255))
    return c


def vinyl_trim(index):
    """A trim pattern round the record's outer band, in greys tinted by the trim material's colour."""
    c = Canvas(100 + index)
    kind = index % 8
    count = 4 + (index // 8) * 4  # first eight patterns use 4 repeats, the rest 8

    def mark(d, a):
        if not 9.5 < d < 14.6:
            return None
        t = (a + math.pi) / (2 * math.pi) * count  # position round the ring in repeats
        f = t - math.floor(t)
        r = (d - 9.5) / 5.1  # 0 at the inner edge, 1 at the rim
        hit = {
            0: abs(f - 0.5) < 0.12 and 0.3 < r < 0.7,                     # dots
            1: abs(r - 0.5 - 0.25 * math.sin(f * 2 * math.pi)) < 0.14,     # wave
            2: abs(r - 0.25) < 0.1 or abs(r - 0.8) < 0.1,                  # twin rings
            3: abs(r - abs(2 * f - 1)) < 0.16,                             # zigzag
            4: 0.2 < r < 0.8 and f < 0.35,                                 # blocks
            5: math.hypot((f - 0.5) * 4, (r - 0.5) * 2.2) < 0.75,         # eyes
            6: abs((f + r * 0.6) % 0.5 - 0.25) < 0.07,                     # slashes
            7: r > 0.75 or (f < 0.15 and r > 0.2),                         # rim with ticks
        }[kind]
        if not hit:
            return None
        return tone((235, 235, 235), 1.08 if math.sin(a) < 0 else 0.85)

    c.disc(16, 16, 14.6, mark)
    return c


def vanilla_panel(c, W, H):
    """A vanilla container background: light grey with a black outline, white rim top-left and grey rim
    bottom-right."""
    base = GUI_GREY
    c.rect(2, 1, W - 2, H - 1, base)
    c.rect(1, 2, W - 1, H - 2, base)
    for x in range(2, W - 2):  # outer dark line, then a white rim top-left and a grey rim bottom-right
        c.set(x, 0, (0, 0, 0))
        c.set(x, H - 1, (0, 0, 0))
        c.set(x, 1, (255, 255, 255))
        c.set(x, 2, (255, 255, 255))
        c.set(x, H - 2, (85, 85, 85))
        c.set(x, H - 3, (85, 85, 85))
    for y in range(2, H - 2):
        c.set(0, y, (0, 0, 0))
        c.set(W - 1, y, (0, 0, 0))
        c.set(1, y, (255, 255, 255))
        c.set(2, y, (255, 255, 255))
        c.set(W - 2, y, (85, 85, 85))
        c.set(W - 3, y, (85, 85, 85))
    for x, y in ((1, 1), (W - 2, 1), (1, H - 2), (W - 2, H - 2)):
        c.set(x, y, (0, 0, 0))


def vanilla_well(c, x1, y1, x2, y2, fill):
    """A sunken area: dark edge top-left, white edge bottom-right."""
    c.rect(x1, y1, x2, y2, fill)
    for x in range(x1 - 1, x2 + 1):
        c.set(x, y1 - 1, (55, 55, 55))
        c.set(x, y2, (255, 255, 255))
    for y in range(y1 - 1, y2 + 1):
        c.set(x1 - 1, y, (55, 55, 55))
        c.set(x2, y, (255, 255, 255))


def vanilla_slot(c, x, y):
    vanilla_well(c, x, y, x + 16, y + 16, (139, 139, 139))


def vanilla_inventory(c, x, y):
    """The player's inventory: three rows, then the hotbar 4 pixels lower."""
    for col in range(9):
        for row in range(3):
            vanilla_slot(c, x + col * 18, y + row * 18)
        vanilla_slot(c, x + col * 18, y + 58)


def gui_lathe():
    """The Disc Cutting Lathe screen, 256 × 222, with its sprites below (progress arrow, scroll thumb)."""
    c = Canvas(200, size=256)
    vanilla_panel(c, 256, 222)

    def well(x1, y1, x2, y2, fill):
        vanilla_well(c, x1, y1, x2, y2, fill)

    def slot(x, y):
        vanilla_slot(c, x, y)

    well(9, 19, 122, 30, (16, 15, 19))         # search box
    well(8, 34, 123, 45, (56, 53, 62))         # sort buttons
    well(8, 46, 115, 118, GUI_WELL)            # song list
    well(116, 46, 123, 118, (20, 19, 23))      # scrollbar track
    slot(136, 50)
    slot(136, 76)
    c.rect(200, 61, 220, 81, (120, 120, 120))  # bigger output slot frame
    slot(202, 63)
    well(236, 48, 244, 96, (40, 22, 20))      # FE gauge (LatheScreen draws the charge)
    # progress arrow: empty in the panel, filled as a sprite at (0, 224)
    arrow_shape = [(x, y) for x in range(24) for y in range(16)
                   if (x < 15 and 5 <= y <= 10) or (x >= 15 and abs(y - 7.5) <= (23 - x) * 0.9)]
    for x, y in arrow_shape:
        c.set(168 + x, 62 + y, (139, 139, 139))
        c.set(x, 224 + y, (232, 190, 64) if (x + y) % 5 else (250, 220, 110))  # filled version, as a sprite
    vanilla_inventory(c, 48, 140)
    # scroll thumb sprite
    c.rect(24, 224, 30, 239, (198, 198, 198))
    c.bevel(24, 224, 30, 239, (255, 255, 255), (85, 85, 85))
    return c


def vinyl(seed, label, grooves):
    """A record. Blank ones are smooth lacquer with a plain label; cut ones have grooves and print."""
    c = Canvas(seed)
    surface = (24, 24, 28) if grooves else (34, 34, 40)

    def disc(d, a):
        if d < 1.4:
            return None  # spindle hole
        if d < 6:
            return tone(label, 1.08 if a < -0.3 else 0.92)
        if d > 14.2:
            return tone(surface, 1.4)  # rim
        f = 1.0
        if grooves:
            f = 0.82 if int(d * 2.2) % 2 else 1.0
            if 6.6 < d < 7.4:
                f = 0.6  # run-out groove next to the label
        # Two soft highlights where the grooves catch the light, top-left and bottom-right.
        shine = max(0.0, math.cos(2 * (a + 2.3)))
        return tone(surface, f * (1 + 0.9 * shine ** 6))

    c.disc(16, 16, 15, disc)
    if grooves:  # printed lines on the label
        for y in (13, 15, 18):
            c.rect(13, y, 20 if y != 15 else 18, y + 1, tone(label, 1.45))
    c.outline(0.5)
    return c


DISPLAY_SURFACE = (36, 36, 43)
DISPLAY_LABEL = (190, 50, 60)


def vinyl_display_label(seed):
    """The Vinyl Display's half label in light greys, laid over its face and tinted in game: red when
    empty, the dye colour of the record it holds otherwise."""
    c = Canvas(seed)
    cx, cy = 16, 14
    for y in range(cy + 1, SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < 5:
                c.set(x, y, tone((235, 235, 235), 1.0 if x < cx else 0.86))
    return c


def vinyl_display_front(seed):
    """The Vinyl Display's face: the lower half of a record (grooves, rim, half a red label), the half the
    displayed record would show if it sank all the way in. Centre at block (8, 9), radius 7; clear outside.
    The label is also drawn on its own (vinyl_display_label) to be tinted."""
    c = Canvas(seed)
    cx, cy, r = 16, 14, 14
    for y in range(cy, SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d > r:
                continue
            if d < 5:
                p = tone(DISPLAY_LABEL, 1.05 if x < cx else 0.9)
            elif d > r - 1.3:
                p = tone(DISPLAY_SURFACE, 1.6)  # rim
            else:
                f = 0.72 if int(d * 2.2) % 2 else 1.12
                if 5.6 < d < 6.4:
                    f = 0.6  # run-out groove
                a = math.atan2(y + 0.5 - cy, x + 0.5 - cx)
                f *= 1 + 0.7 * max(0.0, math.cos(2 * (a - 0.9))) ** 6  # a soft shine on the grooves
                p = tone(DISPLAY_SURFACE, f)
            c.set(x, y, p)
    # the lip of the slot along the top edge
    for x in range(SIZE):
        if c.get(x, cy)[3]:
            c.set(x, cy, tone(DISPLAY_SURFACE, 1.9))
    # The plaque is built of stepped boxes that reach a little past the round edge at their corners: fill those
    # with the rim's black so nothing there is clear.
    for y in range(cy, SIZE):
        for x in range(SIZE):
            if not c.get(x, y)[3]:
                c.set(x, y, tone(DISPLAY_SURFACE, 1.25))
    return c


def vinyl_display_edge(seed):
    """The Vinyl Display's edges: the record's rim, glossy black plastic."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, tone(DISPLAY_SURFACE, 1.25), 0.05)
    for x in range(SIZE):
        c.set(x, 0, tone(DISPLAY_SURFACE, 1.8))
    return c


# The Music Cabinet's sleeves are 12 x 5.5 pixels from the side; these drawings fill the whole face, so
# circles on it are drawn as ellipses that come out round in game. Must match gen_resources.py.
SLEEVE_SIDE = (12, 5.5)
SLEEVE_HOLE, SLEEVE_LABEL = 1.9, 0.85
SLEEVE_CARD = (222, 222, 218)


def sleeve_distance(x, y):
    """How far texel (x, y) is from the middle of a sleeve's side, in block pixels."""
    w, h = SLEEVE_SIDE
    return math.hypot((x + 0.5 - 16) * w / SIZE, (y + 0.5 - 16) * h / SIZE)


def cabinet_inside(seed):
    """The inside of the Music Cabinet: its walnut in shadow, darkest toward the back."""
    c = planks(seed, base=tone(WALNUT, 0.55))
    for y in range(SIZE):
        for x in range(SIZE):
            c.set(x, y, tone(c.get(x, y), 0.9 + 0.1 * y / SIZE))
    return c


def cabinet_score(seed):
    """A Score's spine on the cabinet's shelf: cream paper with a plum label strip and a fine ink line."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, PAPER, 0.03)
    plum = mix(INK, (150, 70, 110), 0.6)
    c.rect(0, 8, SIZE, 10, plum)
    for x in range(SIZE):
        c.set(x, 7, tone(PAPER, 0.85))
        c.set(x, 13, tone(INK, 1.6))
    return c


def cabinet_pages(seed):
    """The tops of the Scores: page edges running back into the cabinet."""
    c = Canvas(seed)
    for x in range(SIZE):
        f = 0.88 if x % 3 == 2 else 1.0
        for y in range(SIZE):
            c.set(x, y, tone(PAPER, f * (1 + c.rng.uniform(-0.03, 0.03))))
    return c


def cabinet_sleeve(seed):
    """A record sleeve's edges: pale card, tinted in game with its record's dye."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, SLEEVE_CARD, 0.04)
    for x in range(SIZE):
        c.set(x, 0, tone(SLEEVE_CARD, 1.12))  # the fold along the top
    return c


def cabinet_sleeve_side(seed):
    """A sleeve's side, with the round hole the record shows through (clear)."""
    c = cabinet_sleeve(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            d = sleeve_distance(x, y)
            if d < SLEEVE_HOLE:
                c.set(x, y, CLEAR)
            elif d < SLEEVE_HOLE + 0.25:
                c.set(x, y, tone(SLEEVE_CARD, 0.78))  # the hole's cut edge
    return c


def cabinet_record(seed):
    """The record inside a sleeve, seen through its hole: black grooves (the label is drawn on its own)."""
    c = Canvas(seed)
    surface = (24, 24, 28)
    for y in range(SIZE):
        for x in range(SIZE):
            d = sleeve_distance(x, y)
            f = 0.8 if int(d * 6) % 2 else 1.05
            if x < 16 and y < 16:
                f *= 1.3  # a little shine top-left
            c.set(x, y, tone(surface, f))
    return c


def cabinet_record_label(seed):
    """The record's label in light grey, tinted in game with its dye; clear everywhere else."""
    c = Canvas(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            d = sleeve_distance(x, y)
            if d < 0.12:
                c.set(x, y, (20, 20, 22))  # spindle hole
            elif d < SLEEVE_LABEL:
                c.set(x, y, tone((240, 240, 240), 1.0 if y < 16 else 0.88))
    return c


def vinyl_display_top(seed):
    """The Vinyl Display's top, with the slot the record stands in (where the wall and the floor stand put it)."""
    c = vinyl_display_edge(seed)
    for row in (15, 28):  # the floor stand's slot, then the wall plaque's
        for x in range(4, 28):
            c.set(x, row, (6, 6, 8))
            c.set(x, row + 1, (14, 14, 17))
    return c


OLIVE = (128, 128, 32)


def meu_front(seed, on=True):
    """The Multi-Effects Unit's face: a dark rack panel with a lit display above six card slots.
    Slot i is centred 2.6 + 2.16 i pixels from the left (MeuRenderer draws the cards in them).
    Unpowered (on=False): the slot lamps dark (MeuRenderer leaves the display blank too)."""
    c = Canvas(seed)
    body = MEU_BODY
    c.noise_fill(0, 0, SIZE, SIZE, body, 0.04)
    # display window
    c.rect(4, 3, 28, 11, MEU_SCREEN)
    c.inset(4, 3, 28, 11, (6, 8, 6), tone(OLIVE, 0.9 if on else 0.45))
    for x in range(5, 27, 3):  # faint pixel grid of the screen
        c.set(x, 9, (20, 30, 16))
    # card slots
    for i in range(6):
        cx = round((2.6 + i * 2.16) * 2)
        c.rect(cx - 2, 14, cx + 2, 24, (12, 12, 14))
        for y in range(14, 24):
            c.set(cx - 2, y, (8, 8, 9))
        c.rect(cx - 2, 24, cx + 2, 25, tone(body, 1.5))  # lip under the slot
        c.set(cx, 12, LED_GREEN if on else tone(LED_GREEN, 0.3))  # a little lamp above each slot
    # olive trim along the bottom, and two knobs
    c.rect(2, 27, 30, 28, OLIVE)
    for kx in (6, 26):
        c.rect(kx - 1, 29, kx + 2, 31, (70, 72, 80))
        c.set(kx, 29, (150, 152, 160))
    return round_edges(c)


CARD_COLORS = {"drive": (200, 60, 40), "modulation": (150, 80, 190), "delay": (60, 120, 220), "reverb": (40, 170, 160),
               "amp_cab": (230, 140, 40), "dynamics": (220, 200, 60)}


def effect_card(seed, kind):
    """An effect card: a dark circuit card with gold contacts, a coloured band and its effect's sign."""
    c = Canvas(seed)
    color = CARD_COLORS[kind]
    x1, y1, x2, y2 = 10, 2, 22, 30
    c.rect(x1, y1, x2, y2, (36, 48, 40))
    c.noise_fill(x1, y1, x2, y2, (36, 48, 40), 0.06)
    c.bevel(x1, y1, x2, y2, (70, 90, 76), (18, 24, 20))
    c.rect(x1 + 1, y1 + 1, x2 - 1, y1 + 5, color)
    c.rect(x1 + 1, y1 + 5, x2 - 1, y1 + 6, tone(color, 0.6))
    for x in range(x1 + 2, x2 - 1, 2):  # gold contacts
        c.rect(x, y2 - 4, x + 1, y2 - 1, (220, 180, 70))
    bright = tone(color, 1.35)
    mid_y = 16
    if kind == "drive":  # a clipped, squared-off wave
        for x, y in ((12, 18), (13, 18), (14, 13), (15, 13), (16, 13), (17, 18), (18, 18), (19, 13), (20, 13)):
            c.set(x, y, bright)
        for x in (14, 17, 19):
            for y in range(13, 19):
                c.set(x, y, bright)
    elif kind == "modulation":  # a sine wave
        for x in range(12, 21):
            c.set(x, round(mid_y + 3 * math.sin((x - 12) / 8 * 2 * math.pi)), bright)
    elif kind == "delay":  # echoes fading away
        for i, x in enumerate((12, 15, 18)):
            c.rect(x, 13, x + 2, 19, tone(bright, 1 - i * 0.3))
    elif kind == "reverb":  # rings spreading out
        for r in (2, 4, 6):
            for a in range(0, 360, 12):
                x = round(16 + r * math.cos(math.radians(a)))
                y = round(mid_y + r * math.sin(math.radians(a)) * 0.9)
                if y <= 22 and y >= 9:
                    c.set(x, y, tone(bright, 1.2 - r * 0.1))
    elif kind == "amp_cab":  # a little speaker cabinet
        c.rect(12, 11, 20, 22, (20, 20, 22))
        for y in range(12, 22, 2):
            for x in range(13, 20, 2):
                c.set(x, y, tone(bright, 0.8))
        c.rect(12, 11, 20, 12, bright)
    else:  # dynamics: meter bars
        for i, h in enumerate((4, 7, 9, 6)):
            c.rect(12 + i * 2, 21 - h, 13 + i * 2, 21, bright)
    c.outline(0.5)
    return c


def preset_chip(seed):
    """A Preset Chip: a small black chip with pins, an olive label and a notch."""
    c = Canvas(seed)
    c.rect(9, 9, 23, 23, (28, 28, 32))
    c.noise_fill(9, 9, 23, 23, (28, 28, 32), 0.05)
    c.bevel(9, 9, 23, 23, (70, 70, 78), (12, 12, 14))
    for i in range(4):
        p = 11 + i * 3
        for x, y in ((p, 6), (p, 7), (p, 8), (p, 23), (p, 24), (p, 25), (6, p), (7, p), (8, p), (23, p), (24, p), (25, p)):
            c.set(x, y, (190, 190, 196) if (x + y) % 2 else (150, 150, 158))
    c.rect(11, 12, 21, 17, OLIVE)
    c.rect(12, 13, 20, 14, tone(OLIVE, 1.5))
    c.rect(12, 15, 17, 16, tone(OLIVE, 1.3))
    c.set(11, 21, (90, 90, 96))  # pin 1 notch
    c.outline(0.5)
    return c


def gui_meu():
    """The Multi-Effects Unit's screen, 304 × 240 on a 512 × 512 sheet, in vanilla's grey: six card slots
    and the pedal along the top, a dark panel for the chosen card's knobs, then the inventory with the
    preset controls and the Preset Chip slot beside it. Empty slots show a faint card or chip, like
    vanilla's empty armour slots."""
    c = Canvas(213, size=512)
    vanilla_panel(c, 304, 240)
    ghost = (120, 120, 120)
    for i in range(6):  # card slots (MeuMenu.cardX), each with a card's outline
        x = 8 + i * 20
        vanilla_slot(c, x, 18)
        c.rect(x + 5, 19, x + 11, 33, ghost)
        c.rect(x + 6, 20, x + 10, 32, (139, 139, 139))
        c.rect(x + 6, 20, x + 10, 23, ghost)  # the coloured band at the top
    vanilla_well(c, 8, 52, 296, 146, GUI_WELL)  # the chosen card's panel
    vanilla_inventory(c, 8, 160)
    vanilla_slot(c, 179, 182)  # Preset Chip (MeuMenu.CHIP_X/Y): a chip with pins
    c.rect(183, 186, 191, 194, ghost)
    for i in range(3):
        for x, y in ((184 + i * 3, 185), (184 + i * 3, 194), (182, 187 + i * 3), (191, 187 + i * 3)):
            c.set(x, y, ghost)
    return c


def gui_desk():
    """The Writer's Desk screen, 176 × 176 on a 256 sheet: the score and copy slots (each with a faint sheet
    of paper, like vanilla's empty armour slots) and the player's inventory. Must match DeskMenu."""
    c = Canvas(214, size=256)
    vanilla_panel(c, 176, 176)
    ghost = (120, 120, 120)
    for x in (44, 116):
        vanilla_slot(c, x, 35)
        c.rect(x + 4, 37, x + 12, 49, ghost)
        c.rect(x + 5, 38, x + 11, 48, (139, 139, 139))
        for y in (40, 43, 46):  # staff lines on the sheet
            c.rect(x + 5, y, x + 11, y + 1, ghost)
    vanilla_inventory(c, 8, 94)
    return c


def creative_power(seed):
    """Dev-only endless FE source: a magenta lattice round a bright lightning bolt."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, (70, 20, 80), 0.08)
    for i in range(0, SIZE, 4):
        for j in range(SIZE):
            c.set(i, j, (120, 40, 140))
            c.set(j, i, (120, 40, 140))
    bolt = [(18, 3), (11, 16), (16, 16), (12, 29), (22, 13), (17, 13), (21, 3)]
    for (x0, y0), (x1, y1) in zip(bolt, bolt[1:]):
        c.line(x0, y0, x1, y1, (255, 230, 90))
    for y in range(SIZE):  # fill the bolt between its outline
        xs = [x for x in range(SIZE) if c.get(x, y)[:3] == (255, 230, 90)]
        if len(xs) >= 2:
            c.rect(xs[0], y, xs[-1] + 1, y + 1, (255, 230, 90))
    return round_edges(c, width=4)


def mp3_player(seed):
    c = Canvas(seed)
    body = MP3_BODY
    c.rect(9, 3, 23, 29, body)
    c.noise_fill(9, 3, 23, 29, body, 0.02)
    for x, y in ((9, 3), (22, 3), (9, 28), (22, 28)):
        c.set(x, y, CLEAR)
    # screen with a scrolling title
    rim, glow, text_line = MP3_SCREEN
    c.rect(11, 5, 21, 14, rim)
    c.rect(12, 6, 20, 13, glow)
    c.rect(13, 8, 19, 9, text_line)
    c.rect(13, 10, 17, 11, text_line)
    # click wheel
    c.disc(16, 21.5, 5, lambda d, a: (190, 192, 200, 255) if d > 2 else (236, 238, 244, 255))
    c.disc(16, 21.5, 1.9, lambda d, a: tone((210, 212, 220), 1.05))
    c.outline(0.6)
    return c


def wire_coil(seed, color):
    c = Canvas(seed)

    def coil(d, a):
        if d < 6.5:
            return None
        if d > 12.5:
            return None
        band = int((d - 6.5) / 1.5) % 2
        light = 1.18 if math.sin(a) < 0 else 0.86
        return tone(color, light * (1.0 if band == 0 else 0.78))

    c.disc(16, 17, 12.5, coil)
    # the loose end, with a bare copper tip
    c.line(22, 7, 27, 2, tone(color, 1.0))
    c.line(23, 7, 28, 2, tone(color, 0.8))
    c.rect(27, 1, 30, 3, COPPER)
    c.set(29, 1, (240, 170, 110))
    c.outline(0.55)
    return c


def wire_cutters(seed):
    """Closed side cutters: short steel jaws almost touching, handles curving out from the pivot and then
    running straight down side by side."""
    c = Canvas(seed)
    steel = (196, 198, 206)
    handle = (196, 52, 42)
    # jaws: two blades tapering to a point, a hairline apart
    for y in range(3, 13):
        w = 1 + (y - 3) // 3
        c.rect(15 - w, y, 16, y + 1, tone(steel, 1.15))
        c.rect(16, y, 17 + w, y + 1, tone(steel, 0.85))
        if y < 10:
            c.set(16, y, (40, 40, 46))  # the cutting gap
    # pivot
    c.disc(16, 13.5, 2.8, lambda d, a: tone((165, 167, 176), 1.35 - d / 3))
    c.set(16, 13, (70, 70, 78))
    # handles: curve outward, then parallel and vertical
    curve = [(14, 16), (13, 17), (12, 18), (11, 19), (11, 20)]
    for x, y in curve:
        c.rect(x - 1, y, x + 2, y + 1, tone(handle, 1.1))
        c.rect(32 - x - 2, y, 32 - x + 1, y + 1, tone(handle, 0.9))
    for y in range(21, 30):
        c.rect(10, y, 13, y + 1, tone(handle, 1.12 if y % 4 else 1.0))
        c.set(10, y, tone(handle, 1.3))
        c.rect(19, y, 22, y + 1, tone(handle, 0.9 if y % 4 else 0.8))
        c.set(19, y, tone(handle, 1.05))
    c.rect(10, 29, 13, 30, tone(handle, 0.75))
    c.rect(19, 29, 22, 30, tone(handle, 0.65))
    c.outline(0.55)
    return c


def score_sheet(seed, notes):
    c = Canvas(seed)
    c.rect(6, 3, 26, 29, PAPER)
    c.noise_fill(6, 3, 26, 29, PAPER, 0.02)
    for y in range(3, 29):  # slight curl on the right edge
        c.set(25, y, tone(PAPER, 0.85))
    c.rect(22, 3, 26, 7, CLEAR)
    c.line(22, 3, 25, 6, tone(PAPER, 0.75))
    c.rect(22, 4, 23, 7, tone(PAPER, 0.9))
    staff_lines(c, 8, 24, 8, (160, 150, 175))
    staff_lines(c, 8, 24, 19, (160, 150, 175))
    if notes:
        # treble clef hints
        for y0 in (8, 19):
            c.rect(9, y0 - 1, 10, y0 + 9, INK)
            c.set(10, y0 + 2, INK)
            c.set(10, y0 + 6, INK)
        for x, y in ((13, 12), (16, 10), (19, 13), (22, 9), (13, 22), (17, 24), (21, 21)):
            c.rect(x, y, x + 2, y + 2, INK)
            c.rect(x + 1, y - 5, x + 2, y, INK)
    c.outline(0.7)
    return c


def manual(seed):
    c = Canvas(seed)
    cover = BOOK_COVER
    c.rect(6, 3, 26, 29, cover)
    c.noise_fill(6, 3, 26, 29, cover, 0.05)
    c.rect(6, 3, 9, 29, tone(cover, 0.7))  # spine
    c.rect(24, 5, 26, 28, PAPER)  # page edges
    for y in range(5, 28, 2):
        c.set(25, y, tone(PAPER, 0.85))
    # gold note emblem
    c.rect(16, 9, 18, 19, GOLD)
    c.rect(18, 9, 21, 11, GOLD)
    c.disc(15, 19.5, 2.6, lambda d, a: tone(GOLD, 1.15 if a < 0 else 0.85))
    c.outline(0.55)
    return c


def pack_icon(style, half):
    """A built-in pack's icon, 64 x 64: a record on a panel in the style's colours (at 16 × the record is
    drawn chunky, two pixels to a dot)."""
    c = Canvas(300, size=64)
    step = 2 if half else 1
    face = WALNUT if style != "retro" else PANEL
    for y in range(0, 64, step):
        for x in range(0, 64, step):
            d = math.hypot(x + step / 2 - 32, y + step / 2 - 32)
            edge = min(x, y, 63 - x, 63 - y)
            if edge < 2:
                p = tone(face, 0.55)
            elif d < 4:
                p = (12, 12, 14)
            elif d < 11:
                p = tone(LED_AMBER if style != "minecraft" else (190, 50, 60), 1.0 if y < 32 else 0.85)
            elif d < 27:
                p = tone(DISPLAY_SURFACE, 0.8 if int(d) % 3 == 0 else 1.0 + (0.5 if x < y - 6 else 0))
            else:
                p = tone(face, 1.1 - 0.2 * y / 64 + c.rng.uniform(-0.04, 0.04) * NOISE)
            for dy in range(step):
                for dx in range(step):
                    c.set(x + dx, y + dy, p)
    return c


# --- 16× pack: hand-placed versions of the faces too detailed to halve well ----------------------

def _panel16(seed, base=PANEL):
    c = Canvas(seed, size=16)
    c.noise_fill(0, 0, 16, 16, base, 0.05)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, (190, 190, 196))
    return c


def amplifier_front16(on=True):
    c = _panel16(330)
    c.rect(2, 2, 9, 7, VU_LIGHT if on else tone(VU_LIGHT, 0.5))
    c.inset(2, 2, 9, 7, (30, 30, 34), (90, 92, 100))
    c.set(7, 3, (190, 40, 30) if on else (84, 64, 58))
    c.line(5, 5, 7, 3, (30, 28, 26)) if on else c.line(5, 5, 3, 4, (30, 28, 26))
    for kx, ky in ((11, 3), (11, 9), (5, 9)):
        c.rect(kx, ky, kx + 2, ky + 2, KNOB)
        c.set(kx + 1, ky + 1, tone(KNOB, 0.7))
        c.set(kx, ky, tone(KNOB, 1.15))
    for x in range(2, 9, 2):
        c.rect(x, 12, x + 1, 14, (26, 26, 30))
    c.set(13, 13, LED_RED if on else tone(LED_RED, 0.26))
    return round_edges(c, width=3)


def channel_manager_front16(on=True):
    c = _panel16(331)
    c.rect(2, 2, 14, 14, WINDOW)
    c.inset(2, 2, 14, 14, (18, 18, 22), (90, 92, 100))
    for (label, color), x in zip((("L", LAMPS[0]), ("M", LAMPS[1]), ("R", LAMPS[2])), (3, 7, 11)):
        text(c, label, x - 0, 4, (220, 220, 226) if on else (120, 120, 126))
        c.rect(x, 10, x + 2, 12, color if on else (64, 66, 72))
        c.set(x, 10, tone(color if on else (64, 66, 72), 1.3))
    return round_edges(c, width=3)


def mixer_top16():
    c = _panel16(332)
    for x, h in ((3, 9), (6, 6), (9, 11), (12, 8)):
        c.rect(x, 5, x + 1, 14, (16, 16, 20))
        c.rect(x - 1, h, x + 2, h + 1, FADER)
        c.set(x, 2, KNOB)
    return round_edges(c, width=3)


def score_sheet16(notes):
    c = Canvas(333, size=16)
    c.rect(3, 1, 13, 15, PAPER)
    c.noise_fill(3, 1, 13, 15, PAPER, 0.02)
    c.set(12, 1, CLEAR)
    for y in (4, 6, 8, 10, 12):
        c.rect(4, y, 12, y + 1, (160, 150, 175))
    if notes:
        c.rect(4, 3, 5, 13, INK)  # clef
        for x, y in ((6, 9), (8, 7), (10, 10)):
            c.set(x, y, INK)
            c.rect(x + 1, y - 3, x + 2, y + 1, INK)
    c.outline(0.7)
    return c


SIXTEEN = {
    "block/amplifier_front": lambda: amplifier_front16(True),
    "block/amplifier_front_off": lambda: amplifier_front16(False),
    "block/channel_manager_front": lambda: channel_manager_front16(True),
    "block/channel_manager_front_off": lambda: channel_manager_front16(False),
    "block/audio_mixer_top": mixer_top16,
    "item/score": lambda: score_sheet16(True),
    "item/blank_score": lambda: score_sheet16(False),
}


def sound_pack_icon(style):
    """A sound pack's icon, 64 x 64: a speaker cone with sound waves, warm for Retro, cool for Studio."""
    c = Canvas(301, size=64)
    back, wave = ((150, 82, 48), (255, 180, 60)) if style == "retro" else ((34, 35, 38), (120, 200, 255))
    c.noise_fill(0, 0, 64, 64, back, 0.04)
    c.bevel(0, 0, 64, 64, tone(back, 1.3), tone(back, 0.6))
    c.disc(22, 32, 15, lambda d, a: tone((40, 40, 46), 1.4 - d / 20) if d > 4 else tone((200, 200, 206), 1.2 - d / 6))
    for r in (22, 30, 38):
        for a in range(-50, 51, 3):
            x = round(22 + r * math.cos(math.radians(a)))
            y = round(32 + r * math.sin(math.radians(a)))
            c.set(x, y, wave)
            c.set(x + 1, y, tone(wave, 0.8))
    return c


def gui_manual():
    """The Manual open flat, 256 x 180 on a 256 sheet: its cover round two cream pages with the spine's
    shadow between them. Below, the page-turn arrows: next at (0, 184), previous at (0, 196), each 18 x 10,
    with a hover version 20 pixels to the right. Must match ManualScreen."""
    c = Canvas(215, size=256)
    W, H = 256, 180
    c.noise_fill(0, 0, W, H, BOOK_COVER, 0.05)
    c.bevel(0, 0, W, H, tone(BOOK_COVER, 1.35), tone(BOOK_COVER, 0.55))
    for x1, x2 in ((6, 127), (129, 250)):
        c.noise_fill(x1, 5, x2, 175, PAPER, 0.02)
        for x in range(x1, x2):  # page edges along the bottom
            c.set(x, 174, tone(PAPER, 0.8))
            c.set(x, 173, tone(PAPER, 0.9))
    for x in range(112, 144):  # the spine's shadow, deepest in the middle
        f = 1 - 0.28 * max(0.0, 1 - abs(x - 128) / 16) ** 2
        for y in range(5, 175):
            c.set(x, y, tone(c.get(x, y), f))
    c.rect(127, 3, 129, 177, tone(BOOK_COVER, 0.6))
    ink, glow = tone(BOOK_COVER, 0.9), tone(BOOK_COVER, 1.5)
    for v, right in ((184, True), (196, False)):
        for u, color in ((0, ink), (20, glow)):
            for i in range(10):
                half = 4.5 - abs(i - 4.5)  # 0.5 at the tips, 4.5 in the middle
                for dx in range(18):
                    d = dx if right else 17 - dx
                    stem = 3 <= i <= 6 and d < 12
                    head = 12 <= d < 12 + half + 1.5
                    if stem or head:
                        c.set(u + dx, v + i, color)
    return c


# --- the 3D drivers (SpeakerRenderer maps u round the circle and v from the outer edge in) ----------

def speaker_cone(seed):
    """A speaker cone, outer edge at the top: paper with the corrugated rings near the surround."""
    c = Canvas(seed)
    for y in range(SIZE):
        ring = 0.82 if y < 10 and y % 3 == 0 else 1.0
        depth = 1.08 - 0.25 * y / SIZE  # darker deeper in
        for x in range(SIZE):
            c.set(x, y, tone(CONE, ring * depth * (1 + c.rng.uniform(-0.04, 0.04) * NOISE)))
    return c


def speaker_cap(seed):
    """The dust cap, outer edge at the top, brightest at its crown."""
    c = Canvas(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            c.set(x, y, tone(CAP, (1.0 + 0.35 * y / SIZE) * (1 + c.rng.uniform(-0.03, 0.03) * NOISE)))
    return c


def tweeter_dome(seed):
    """The tweeter's soft dome, rim at the top, with a highlight towards its crown."""
    c = Canvas(seed)
    for y in range(SIZE):
        for x in range(SIZE):
            c.set(x, y, tone(TWEETER_DOME, (0.85 + 0.4 * y / SIZE) * (1 + c.rng.uniform(-0.02, 0.02) * NOISE)))
    return c


def speaker_pocket(seed):
    """The dark recess behind a driver."""
    c = Canvas(seed)
    c.noise_fill(0, 0, SIZE, SIZE, tone(BAFFLE, 0.5), 0.05)
    return c


# --- particles (16 x 16; ModParticles, MineScoreParticle) -----------------------------------------

def particle_note(variant):
    """A music note in light greys, tinted in game with its band's colour: a quaver, or two beamed."""
    c = Canvas(500 + variant, size=16)
    white, shade = (250, 250, 250), (200, 200, 200)
    if variant == 0:
        c.rect(9, 2, 10, 11, white)          # stem
        c.rect(10, 3, 12, 4, white)          # flag
        c.rect(12, 4, 13, 6, shade)
        c.rect(5, 10, 9, 13, white)          # head
        c.rect(5, 12, 9, 13, shade)
    else:
        c.rect(5, 3, 13, 5, white)           # beam
        c.rect(5, 3, 6, 11, white)
        c.rect(12, 3, 13, 10, white)
        c.rect(2, 10, 6, 13, white)
        c.rect(9, 9, 13, 12, white)
        c.rect(2, 12, 6, 13, shade)
        c.rect(9, 11, 13, 12, shade)
    c.outline(0.55)
    return c


def particle_shaving(variant):
    """A curl of black lacquer swarf from the cutter."""
    c = Canvas(510 + variant, size=16)
    for i in range(14):
        a = i / 13 * math.pi * (1.6 if variant == 0 else 1.2)
        r = 2 + i * 0.35
        x, y = 8 + math.cos(a + variant) * r, 8 + math.sin(a + variant) * r
        c.set(int(x), int(y), tone((30, 30, 34), 1.3 if i % 4 == 0 else 1.0))
    return c


def particle_shard(variant):
    """A shard of a broken record: black with a sharp bright edge."""
    c = Canvas(520 + variant, size=16)
    shapes = [[(3, 4), (12, 6), (6, 12)], [(4, 3), (11, 4), (12, 11), (5, 9)], [(5, 2), (10, 8), (4, 13)]]
    pts = shapes[variant]
    for y in range(16):
        for x in range(16):
            inside = True
            for i in range(len(pts)):  # inside a convex polygon: on the same side of every edge
                (x1, y1), (x2, y2) = pts[i], pts[(i + 1) % len(pts)]
                if (x2 - x1) * (y + 0.5 - y1) - (y2 - y1) * (x + 0.5 - x1) < 0:
                    inside = False
            if inside:
                c.set(x, y, tone((24, 24, 28), 1.0 + 0.8 * max(0.0, 1 - (x + y) / 12)))
    c.outline(1.8)  # a glint along the broken edges
    return c


def particle_spark(variant):
    """A spark: a hot white core with a yellow-orange glow."""
    c = Canvas(530 + variant, size=16)
    r = 3.5 if variant == 0 else 2.2
    c.disc(8, 8, r, lambda d, a: (255, 255, 230, 255) if d < r * 0.4 else (255, 210, 90, 255) if d < r * 0.75 else (240, 130, 40, 200))
    return c


def particle_smoke(variant):
    """A puff of smoke from a burnt-out speaker, thinning as it spreads."""
    c = Canvas(540 + variant, size=16)
    r = 4 + variant * 1.2
    alpha = 230 - variant * 45
    c.disc(8, 8, r, lambda d, a: (70, 68, 66, int(alpha * (1 - d / (r + 0.5)))) if c.rng.random() > 0.15 * variant else None)
    return c


# --- branding ------------------------------------------------------------------------------------

BIG_FONT = {  # 5 x 7 capitals for the logo
    "M": ["X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"],
    "I": ["XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "XXXXX"],
    "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X", "X...X", "X...X"],
    "E": ["XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"],
    "S": [".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."],
    "C": [".XXXX", "X....", "X....", "X....", "X....", "X....", ".XXXX"],
    "O": [".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."],
    "R": ["XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"],
}


def logo():
    """The Mods-list logo, 256 x 64: a record with a red label, then MINESCORE in gold capitals."""
    c = Canvas(400, size=256)
    def disc(d, a):
        if d < 1.6:
            return None
        if d < 10:
            return tone((190, 50, 60), 1.1 if a < -0.3 else 0.9)
        if d > 27:
            return tone((36, 36, 43), 1.6)
        shine = max(0.0, math.cos(2 * (a + 2.3))) ** 6
        return tone((24, 24, 28), (0.8 if int(d * 1.2) % 2 else 1.0) * (1 + 1.2 * shine))
    c.disc(32, 32, 28, disc)
    c.outline(0.45)
    scale = 3
    for dx, dy, color in ((2, 2, (60, 40, 20)), (0, 0, None)):  # a drop shadow, then the gold letters
        x = 70
        for ch in "MINESCORE":
            for row, bits in enumerate(BIG_FONT[ch]):
                for col, bit in enumerate(bits):
                    if bit == "X":
                        fill = color or tone(GOLD, 1.25 - 0.07 * row)  # lit from above
                        c.rect(x + col * scale + dx, 21 + row * scale + dy, x + (col + 1) * scale + dx, 21 + (row + 1) * scale + dy, fill)
            x += 6 * scale
    return c
