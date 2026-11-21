"""Generates MineScore's resources: blockstates, models (with jacks from ports.json), loot tables,
mining tags, lang, and every texture (drawn from scratch in tools/textures.py).

Every placeable block gets rounded edges: its body is built from overlapping boxes inset by a hair
(chamfered), and every block texture gets rim shading (textures.round_edges) that does most of the work.
The front and back each wrap around their own edges, so a dark front never shows a strip of the side's
material.

Run from anywhere:  python tools/gen_resources.py
Re-run after editing ports.json, textures.py or the tables below.

It then runs itself once per built-in resource pack (PACKS, SCOPE.md §13.1), with MINESCORE_PACK naming the
pack: those runs draw the textures in the pack's style and write only the ones that differ from the default
into resourcepacks/<pack>, skipping models, lang and data.
"""
import json
import os
import shutil
import struct
import subprocess
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import textures as tx  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "common" / "src" / "main" / "resources"
ASSETS = RES / "assets" / "minescore"
DATA = RES / "data"

# The built-in resource packs: id -> (MINESCORE_STYLE, half resolution, name, description).
PACKS = {
    "minescore_retro": ("retro", False, "MineScore: Retro Hi-Fi", "80s hi-fi: brushed silver, rosewood, teal displays"),
    "minescore_modern": ("modern", False, "MineScore: Modern Studio", "Studio gear: matte black, birch, ice-blue lights"),
    "minescore_16x": ("minecraft", True, "MineScore: 16×", "MineScore's default look at 16×"),
}
PACK = os.environ.get("MINESCORE_PACK")
PACK_ROOT = RES / "resourcepacks" / PACK if PACK else None
HALF = PACK is not None and PACKS[PACK][1]

# Colours for the screens drawn in code (UiTheme.java reads assets/minescore/minescore/theme.json). Keys
# missing from a style keep the default's.
THEMES = {
    "minecraft": {
        "panel": "C0101014", "panel_solid": "F03A3A44", "edge": "FF5A5A66", "frame": "FF2B2B33", "section": "FF4A4A56",
        "section_hover": "FF56566A", "tab": "FF42424E", "tab_hover": "FF4C4C5C", "tab_open": "FF505064",
        "tab_on": "FF3F5F9F", "text": "FFE8E8EE", "dim": "FFA0A0AE", "selected": "FF2F6FE0", "highlight": "5530A0FF",
        "selected_text": "FF8FC0FF", "play": "FF2EA043", "warn": "FFFFB060", "error": "FFFF6060", "server": "FFE0B060",
        "changed": "FFFFE080", "waiting": "FFFFD060", "label": "FF404040", "dropdown": "FF15151A",
        "device_text": "FFE6F2F0", "device_dim": "FF8FA3A0", "device_accent": "FF7FE0D0", "device_pick": "FF2E4A47",
        "device_hover": "FF1E2C2A", "device_line": "FF35504C", "device_track": "FF1A2624", "device_thumb": "FF6F9590",
        "device_box": "FF101A19", "device_box_edge": "FF7FA8A2", "device_bar": "FF203230", "bluetooth": "FF7FB0FF",
        "list_row": "FF333038", "list_pick": "FF5A4A2A", "list_header": "FF38353E", "list_header_hover": "FF4A4650",
        "list_text": "FFDDDDDD", "list_text_on": "FFFFE9A8", "list_dim": "FF8F8A80", "list_dim_on": "FFE0C890",
        "energy": "FFC8402C", "card_on": "FF5CE03A", "card_off": "FF7A3A2A",
        "config_tab": "FF2F2F38", "editor_panel": "FF3A3A44", "meu_off": "FFB07A64", "desk_playing": "FF2E7D32",
        "book_ink": "FF3A3040", "book_heading": "FF7A2E2E", "book_link": "FF2F4F8A", "book_link_hover": "FF7A2E2E",
        "book_dim": "FF9A9080",
        "energy_light": "FFE8735C", "list_header_text": "FFCCCCCC", "list_empty": "FFAAAAAA",
    },
    "retro": {
        "panel": "C8141E1E", "panel_solid": "F0A4A6AC", "edge": "FF6C6E74", "frame": "FF2A2C30", "section": "FF5E6066",
        "section_hover": "FF6E7076", "tab": "FF4A4C52", "tab_hover": "FF56585E", "tab_open": "FF62646A",
        "tab_on": "FF2F8F86", "dim": "FFB4BAB8", "selected": "FF2FB8A8", "highlight": "5530E0C8",
        "selected_text": "FF6FF0D8", "play": "FFFF9A3A", "server": "FFFFB43C", "changed": "FFFFC860",
        "device_text": "FF7FF0DC", "device_dim": "FF3F9C90", "device_accent": "FFFFB43C", "device_pick": "FF1E4A44",
        "device_hover": "FF143430", "device_line": "FF1F5C54", "device_track": "FF0E2624", "device_thumb": "FF4FC0B0",
        "device_box": "FF081A18", "device_box_edge": "FF4FC0B0", "device_bar": "FF0E3430", "bluetooth": "FFFFB43C",
        "list_row": "FF1A2C2C", "list_pick": "FF5A3E1E", "list_header": "FF24383A", "list_header_hover": "FF2E4648",
        "list_text": "FF9FF0E0", "list_text_on": "FFFFC860", "list_dim": "FF4F9A90", "list_dim_on": "FFE0A860",
        "card_on": "FFFFB43C", "config_tab": "FF3A4446", "editor_panel": "FF54565C", "desk_playing": "FF1F7A70",
        "list_header_text": "FF9FD8D0", "list_empty": "FF6FA8A0", "book_heading": "FF8A3A1E", "book_link": "FF1F6A62",
        "book_link_hover": "FFB0601E",
    },
    "modern": {
        "panel": "D00C0C0E", "panel_solid": "F01C1C20", "edge": "FF34343A", "frame": "FF141416", "section": "FF26262C",
        "section_hover": "FF303038", "tab": "FF1E1E24", "tab_hover": "FF2A2A32", "tab_open": "FF32323C",
        "tab_on": "FF2A6FD6", "text": "FFF4F6FA", "dim": "FF9AA0AA", "selected": "FF58B4FF", "highlight": "5558B4FF",
        "selected_text": "FF9AD2FF", "play": "FF58B4FF", "server": "FFC8E0FF", "changed": "FF9AD2FF",
        "device_text": "FFF4F6FA", "device_dim": "FF8A9098", "device_accent": "FF58B4FF", "device_pick": "FF1C2C40",
        "device_hover": "FF14181E", "device_line": "FF2A2E36", "device_track": "FF14161A", "device_thumb": "FF7A8898",
        "device_box": "FF0A0B0D", "device_box_edge": "FF58B4FF", "device_bar": "FF16191E", "bluetooth": "FF58B4FF",
        "list_row": "FF1A1A1E", "list_pick": "FF1C2C40", "list_header": "FF222228", "list_header_hover": "FF2C2C34",
        "list_text": "FFE4E6EA", "list_text_on": "FF9AD2FF", "list_dim": "FF7A808A", "list_dim_on": "FF7AB8E8",
        "energy": "FF58B4FF", "card_on": "FF58B4FF", "card_off": "FF3A3A44", "config_tab": "FF1E1E24",
        "editor_panel": "FF1C1C20", "energy_light": "FF9AD2FF", "desk_playing": "FF1C5FA8", "list_header_text": "FFC8CCD4",
        "list_empty": "FF8A9098", "book_ink": "FF26282C", "book_heading": "FF1C1C20", "book_link": "FF1C5FA8",
        "book_link_hover": "FF58B4FF", "book_dim": "FF8A9098",
    },
}
PORTS = json.loads((RES / "minescore" / "ports.json").read_text(encoding="utf-8"))

# Block textures: file name -> (drawing, give it rounded-edge shading)
TEXTURES = {
    "walnut_planks": (lambda: tx.planks(1), True),
    "machine_bottom": (lambda: tx.bottom(2), True),
    "iron_casing": (lambda: tx.iron_plate(3), True),
    "dark_casing": (lambda: tx.dark_panel(4), True),
    "dark_vents": (lambda: tx.vents(6, tx.PANEL), True),
    "iron_vents": (lambda: tx.vents(7, tx.IRON), True),
    "anchor_post": (lambda: tx.anchor_post(9), False),
    "anchor_flow": (lambda: tx.anchor_flow(10), False),
    "anchor_in": (lambda: tx.anchor_end(11, output=False), False),
    "anchor_out": (lambda: tx.anchor_end(12, output=True), False),
    "disc_cutting_lathe_side": (lambda: tx.lathe_side(21), True),
    "disc_cutting_lathe_front": (lambda: tx.lathe_front(22), True),
    "disc_cutting_lathe_deck": (lambda: tx.lathe_deck(23), True),
    "disc_cutting_lathe_well": (lambda: tx.lathe_well(24), False),
    "disc_cutting_lathe_platter": (lambda: tx.lathe_platter(25), False),
    "brass": (lambda: tx.brass(26), False),
    "tonearm": (lambda: tx.tonearm(36), False),
    "vinyl_player_front": (lambda: tx.vinyl_front(37), True),
    "walnut_deck": (lambda: tx.planks(38, horizontal=False), True),
    "audio_mixer_top": (lambda: tx.mixer_top(27), True),
    "audio_mixer_front": (lambda: tx.mixer_front(28), True),
    "channel_manager_front": (lambda: tx.channel_manager_front(29), True),
    "amplifier_front": (lambda: tx.amplifier_front(30), True),
    "tweeter_speaker_front": (lambda: tx.tweeter_front(31), True),
    "middle_speaker_front": (lambda: tx.speaker_front(32, radius=12.5, cap=4), True),
    "subwoofer_front": (lambda: tx.speaker_front(33, radius=13.8, cap=5), True),
    "writers_desk_top": (lambda: tx.desk_tops(34)[0], False),
    "writers_desk_top_right": (lambda: tx.desk_tops(34)[1], False),
    "writers_desk_front": (lambda: tx.desk_front(35), True),
    "multi_effects_unit_front": (lambda: tx.meu_front(39), False),
    "speaker_cone": (lambda: tx.speaker_cone(40), False),
    "speaker_cap": (lambda: tx.speaker_cap(41), False),
    "tweeter_dome": (lambda: tx.tweeter_dome(42), False),
    "speaker_pocket": (lambda: tx.speaker_pocket(43), False),
    "music_cabinet_inside": (lambda: tx.cabinet_inside(93), False),
    "music_cabinet_score": (lambda: tx.cabinet_score(94), False),
    "music_cabinet_pages": (lambda: tx.cabinet_pages(95), False),
    "music_cabinet_sleeve": (lambda: tx.cabinet_sleeve(96), False),
    "music_cabinet_sleeve_side": (lambda: tx.cabinet_sleeve_side(96), False),
    "music_cabinet_record": (lambda: tx.cabinet_record(97), False),
    "music_cabinet_record_label": (lambda: tx.cabinet_record_label(97), False),
    # The hubs with their lights off, for when their system has no power.
    "audio_mixer_front_off": (lambda: tx.mixer_front(28, on=False), True),
    "audio_mixer_front_docked": (lambda: tx.mixer_front(28, docked=True), True),
    "audio_mixer_front_docked_off": (lambda: tx.mixer_front(28, on=False, docked=True), True),
    "channel_manager_front_off": (lambda: tx.channel_manager_front(29, on=False), True),
    "amplifier_front_off": (lambda: tx.amplifier_front(30, on=False), True),
    "multi_effects_unit_front_off": (lambda: tx.meu_front(39, on=False), False),
    # Every block's own underside.
    **{f"{name}_bottom": (lambda name=name, i=i: tx.underside(100 + i, name), True) for i, name in enumerate((
        "disc_cutting_lathe", "audio_mixer", "channel_manager", "amplifier", "tweeter_speaker", "middle_speaker", "subwoofer",
        "vinyl_player", "multi_effects_unit", "writers_desk", "vinyl_display", "cable_anchor", "music_cabinet"))},
}

# Each speaker's driver hole, matching SpeakerRenderer.Driver: (front face z, centre x, centre y, hole radius,
# pocket depth, the texture at the back of the pocket). Its front texture is clear inside the surround, and
# a dark pocket behind shows the 3D cone (drawn by SpeakerRenderer) in a real recess.
SPEAKER_DRIVERS = {
    "subwoofer": (0, 8, 8, 5.6, 4, "#cone"),
    "middle_speaker": (2, 8, 6, 3.7, 3, "#cone"),
    "tweeter_speaker": (4, 8, 4, 1.9, 0.5, "#dome"),
}


def driver_pocket(face, cx, cy, r, depth, back):
    """The square pocket behind a round driver hole: four walls facing in and a back, all inside the body. It
    reaches a pixel past the hole all round: the hole's edge pixels poke a little past its true radius, and the
    walls must be behind solid face, or you'd see past them into the block."""
    r += 1
    x1, x2, y1, y2, z1, z2 = cx - r, cx + r, cy - r, cy + r, face + 0.01, face + depth
    uv = {"uv": [0, 0, 16, 16]}
    return [
        {"from": [x1, y1, z2], "to": [x2, y2, z2], "faces": {"north": {**uv, "texture": back}}},
        {"from": [x1, y1, z1], "to": [x1, y2, z2], "faces": {"east": {**uv, "texture": "#pocket"}}},
        {"from": [x2, y1, z1], "to": [x2, y2, z2], "faces": {"west": {**uv, "texture": "#pocket"}}},
        {"from": [x1, y1, z1], "to": [x2, y1, z2], "faces": {"up": {**uv, "texture": "#pocket"}}},
        {"from": [x1, y2, z1], "to": [x2, y2, z2], "faces": {"down": {**uv, "texture": "#pocket"}}},
    ]


# Hubs that go dark when their system has no power (HubPower): a "powered" blockstate and an _off model.
POWERED_HUBS = {"audio_mixer", "channel_manager", "amplifier", "multi_effects_unit"}

# name: (display name, box, {face: texture}, full_uv, tool)
# A "back" of ("plate", base, hub) is drawn from the block's jacks in ports.json.
# full_uv squeezes the whole texture onto each face, for blocks smaller than a full cube.
BLOCKS = {
    "disc_cutting_lathe": ("Disc Cutting Lathe", [0, 0, 0, 16, 6, 16], {
        "front": "disc_cutting_lathe_front", "back": ("plate", "iron", False), "side": "disc_cutting_lathe_side",
        "top": "disc_cutting_lathe_well", "bottom": "disc_cutting_lathe_bottom"}, False, "pickaxe"),
    "audio_mixer": ("Audio Mixer", [0, 0, 0, 16, 16, 16], {
        "front": "audio_mixer_front", "back": ("plate", "panel", True), "side": "dark_casing",
        "top": "audio_mixer_top", "bottom": "audio_mixer_bottom"}, False, "pickaxe"),
    "channel_manager": ("Channel Manager", [0, 0, 0, 16, 16, 16], {
        "front": "channel_manager_front", "back": ("plate", "panel", True), "side": "dark_casing",
        "top": "dark_vents", "bottom": "channel_manager_bottom"}, False, "pickaxe"),
    "amplifier": ("Amplifier", [0, 0, 0, 16, 16, 16], {
        "front": "amplifier_front", "back": ("plate", "panel", True), "side": "dark_casing",
        "top": "dark_vents", "bottom": "amplifier_bottom"}, False, "pickaxe"),
    "tweeter_speaker": ("Tweeter Speaker", [4, 0, 4, 12, 8, 12], {
        "front": "tweeter_speaker_front", "back": ("plate", "wood", False), "side": "walnut_planks",
        "top": "walnut_planks", "bottom": "tweeter_speaker_bottom"}, True, "pickaxe"),
    "middle_speaker": ("Middle Speaker", [2, 0, 2, 14, 12, 14], {
        "front": "middle_speaker_front", "back": ("plate", "wood", False), "side": "walnut_planks",
        "top": "walnut_planks", "bottom": "middle_speaker_bottom"}, True, "pickaxe"),
    "subwoofer": ("Subwoofer", [0, 0, 0, 16, 16, 16], {
        "front": "subwoofer_front", "back": ("plate", "wood", False), "side": "walnut_planks",
        "top": "walnut_planks", "bottom": "subwoofer_bottom"}, False, "pickaxe"),
    # The Lathe's shape in the speakers' walnut, with a tonearm in place of the cutting head.
    "vinyl_player": ("Vinyl Player", [0, 0, 0, 16, 6, 16], {
        "front": "vinyl_player_front", "back": ("plate", "wood", False), "side": "walnut_planks",
        "top": "disc_cutting_lathe_well", "bottom": "vinyl_player_bottom"}, False, "axe"),
    "multi_effects_unit": ("Multi-Effects Unit", [0, 0, 0, 16, 16, 16], {
        "front": "multi_effects_unit_front", "back": ("plate", "panel", True), "side": "dark_casing",
        "top": "dark_vents", "bottom": "multi_effects_unit_bottom"}, False, "pickaxe"),
    # Placeholder single block: in M9 two desk blocks placed side by side join into the full 2-wide desk.
    "writers_desk": ("Writer's Desk", [0, 0, 0, 16, 16, 16], {
        "front": "writers_desk_front", "back": ("plate", "wood", True), "side": "walnut_planks",
        "top": "writers_desk_top", "bottom": "writers_desk_bottom"}, False, "axe"),
}

# Item sprites: name -> (display name, drawing)
ITEMS = {
    "blank_cd": ("Blank Vinyl", lambda: tx.vinyl_body(40, grooves=False)),
    "encoded_cd": ("Vinyl Record", lambda: tx.vinyl_body(41, grooves=True)),
    "mp3_player": ("MP3 Player", lambda: tx.mp3_player(42)),
    "speaker_wire_12": ("12 Gauge Speaker Wire", None),
    "speaker_wire_16": ("16 Gauge Speaker Wire", None),
    "speaker_wire_20": ("20 Gauge Speaker Wire", None),
    "wire_cutters": ("Wire Cutters", lambda: tx.wire_cutters(43)),
    "blank_score": ("Blank Score", lambda: tx.score_sheet(44, notes=False)),
    "score": ("Score", lambda: tx.score_sheet(45, notes=True)),
    "manual": ("MineScore Manual", lambda: tx.manual(46)),
    "card_drive": ("Drive & Distortion Card", lambda: tx.effect_card(70, "drive")),
    "card_modulation": ("Modulation Card", lambda: tx.effect_card(71, "modulation")),
    "card_delay": ("Delay Card", lambda: tx.effect_card(72, "delay")),
    "card_reverb": ("Reverb Card", lambda: tx.effect_card(73, "reverb")),
    "card_amp_cab": ("Amp & Cabinet Card", lambda: tx.effect_card(74, "amp_cab")),
    "card_dynamics": ("Dynamics & Utility Card", lambda: tx.effect_card(75, "dynamics")),
    "preset_chip": ("Preset Chip", lambda: tx.preset_chip(76)),
}

# Jack colours. Band jacks are coloured by band; link jacks by the pair of blocks they join, so both ends
# of every link are the same colour and a cable only ever joins matching colours.
BAND_COLORS = {
    "band_12": (192, 57, 43),
    "band_16": (225, 177, 44),
    "band_20": (46, 134, 222),
}
LINK_COLORS = {
    ("channel_manager", "source"): (142, 68, 173),
    ("amplifier", "source"): (230, 126, 34),
    ("amplifier", "channel_manager"): (40, 170, 140),
    # A Multi-Effects Unit takes the Mixer's place in front of a Channel Manager or Amplifier, so its outputs
    # to them are the Mixer's purple and orange. Its own input, and its link to a Lathe, are olive green.
    ("meu", "source"): (128, 128, 32),
    ("channel_manager", "meu"): (142, 68, 173),
    ("amplifier", "meu"): (230, 126, 34),
    ("lathe", "meu"): (128, 128, 32),
}

PORT_NAMES = {
    "band_12": ("12 ga in", "12 ga out"),
    "band_16": ("16 ga in", "16 ga out"),
    "band_20": ("20 ga in", "20 ga out"),
    "link_source": ("Source in", "Source out"),
    "link_channel_manager": ("Channel Manager in", "Channel Manager out"),
    "link_amplifier": ("Amplifier in", "Amplifier out"),
    "link_meu": ("Multi-Effects Unit in", "Multi-Effects Unit out"),
    "link_lathe": ("Lathe in", "Lathe out"),
}

MESSAGES = {
    "msg.minescore.wire.started": "Connecting from %s · %s. Click another jack (Shift-right-click the air to cancel).",
    "msg.minescore.wire.connected": "Connected %s → %s",
    "msg.minescore.wire.jack": "%s (%s)",
    "msg.minescore.wire.cancelled": "Cable cancelled",
    "msg.minescore.wire.click_jack": "Click one of the jacks",
    "msg.minescore.wire.anchor_needs_cable": "Start a cable at a jack first, then click the anchor",
    "msg.minescore.wire.anchor_used": "This cable already runs through that anchor",
    "msg.minescore.wire.anchored": "Cable mounted on anchor %s",
    "msg.minescore.wire.other_dimension": "The cable's start is in another dimension, so it was cancelled",
    "msg.minescore.wire.too_far": "This run ends %s blocks from you, farther than speakers can be heard (%s blocks). The connection still works.",
    "msg.minescore.wire.same_block": "Both ends are on the same block",
    "msg.minescore.wire.jack_gone": "One of the jacks no longer exists",
    "msg.minescore.wire.need_in_and_out": "Connect an output jack to an input jack",
    "msg.minescore.wire.shift_for_hubs": "Hold Shift to connect two hubs",
    "msg.minescore.wire.no_shift_for_speakers": "Don't hold Shift when wiring a speaker",
    "msg.minescore.wire.lathe_needs_amp": "The Lathe connects to an Amplifier band output or a Multi-Effects Unit",
    "msg.minescore.wire.lathe_meu_or_amp": "A Lathe takes a Multi-Effects Unit or an Amplifier, not both",
    "msg.minescore.wire.link_mismatch": "Those jacks are different colours: links only join matching colours",
    "msg.minescore.wire.band_mismatch": "Those jacks are different colours: they carry different bands",
    "msg.minescore.wire.not_allowed": "Not allowed: %s can't feed %s",
    "msg.minescore.wire.wrong_gauge": "This speaker needs %s Gauge Speaker Wire",
    "msg.minescore.wire.jack_in_use": "That input already has a cable",
    "msg.minescore.wire.jack_busy": "That output already feeds a hub",
    "msg.minescore.wire.jack_feeds_hub": "That output feeds a hub, so it can't feed speakers too",
    "msg.minescore.wire.jack_feeds_speakers": "That output feeds speakers, so it can't feed a hub too",
    "msg.minescore.wire.loop": "That would make a loop",
    "msg.minescore.wire.other_system": "Those blocks belong to different speaker systems",
    "msg.minescore.wire.amp_role": "An Amplifier can't be both a main amp and a line amp",
    "msg.minescore.wire.line_amp_band": "A line amplifier only handles one speaker type",
    "msg.minescore.wire.one_main_amp": "This system already has a main Amplifier",
    "msg.minescore.track_failed": "Couldn't play %s: %s",
    "commands.minescore.play": "Playing %s on the source at %s",
    "commands.minescore.stop": "Stopped the source at %s",
    "commands.minescore.not_source": "That block isn't an Audio Mixer or Writer's Desk",
    "msg.minescore.cutters.cut": "Cable cut",
    "msg.minescore.cutters.none": "No cable there",
    "kind.minescore.source": "a source",
    "kind.minescore.channel_manager": "a Channel Manager",
    "kind.minescore.amplifier": "an Amplifier",
    "kind.minescore.speaker": "a speaker",
    "kind.minescore.lathe": "a Disc Cutting Lathe",
    "tooltip.minescore.speaker_wire": "Click a jack, then another. Shift-click to link two hubs.",
    "tooltip.minescore.speaker_wire.pending": "Laying a cable…",
    "tooltip.minescore.wire_cutters": "Right-click a cable to remove it",
    "tooltip.minescore.blank_cd": "Cut a song onto it at a Disc Cutting Lathe",
    "tooltip.minescore.cd.unreadable": "Nothing readable is engraved on it",
    "tooltip.minescore.cd.length": "Length: %s",
    "tooltip.minescore.cd.cut_by": "Cut by %s",
    "tooltip.minescore.cd.original": "Original",
    "tooltip.minescore.cd.copy": "Copy #%s",
    "gui.minescore.lathe.name": "Name",
    "gui.minescore.lathe.preview": "Preview",
    "gui.minescore.lathe.stop": "Stop",
    "gui.minescore.lathe.cut": "Cut",
    "gui.minescore.lathe.copy": "Copy",
    "gui.minescore.lathe.blank": "Blank Vinyl",
    "gui.minescore.lathe.copy_from": "Record to copy",
    "gui.minescore.lathe.copy_to": "Copy onto",
    "gui.minescore.lathe.empty_folder": "Put MP3, OGG, WAV, FLAC, MIDI or NBS files in the blkcdsongs folder of your game folder",
    "msg.minescore.lathe.cutting": "Cutting: %s%%",
    "msg.minescore.lathe.busy": "The Lathe is already cutting",
    "msg.minescore.lathe.needs_blank": "Put a Blank Vinyl in the first slot and keep the output empty",
    "msg.minescore.lathe.needs_copy": "Put the Vinyl Record in the first slot and a Blank Vinyl in the second",
    "msg.minescore.lathe.failed": "Couldn't read that song: %s",
    "msg.minescore.lathe.needs_load": "Put a Vinyl Record in the first slot and the MP3 Player in the second",
    "msg.minescore.lathe.loaded": "Added to the MP3 Player",
    "msg.minescore.lathe.already_loaded": "That song is already on the MP3 Player",
    "gui.minescore.lathe.load": "Load",
    "gui.minescore.lathe.load_into": "MP3 Player",
    "kind.minescore.vinyl_player": "a Vinyl Player",
    "msg.minescore.mp3.connected": "Playing through this speaker",
    "msg.minescore.mp3.disconnected": "Stopped playing through this speaker",
    "msg.minescore.mp3.out_of_range": "Too far from the speaker: disconnected",
    "msg.minescore.mp3.empty": "Load songs onto the MP3 Player at a Disc Cutting Lathe first",
    "msg.minescore.mixer.dock": "Right-click with an MP3 Player to dock it",
    "tooltip.minescore.mp3.songs": "%s songs",
    "tooltip.minescore.mp3.playing": "Playing: %s",
    "tooltip.minescore.mp3.paused": "Paused: %s",
    "tooltip.minescore.mp3.linked": "Playing through a speaker",
    "tooltip.minescore.mp3.help": "Right-click to open · right-click a speaker to play through it",
    "gui.minescore.mp3.tab.now": "Playing",
    "gui.minescore.mp3.tab.library": "Library",
    "gui.minescore.mp3.tab.playlists": "Playlists",
    "gui.minescore.mp3.tab.eq": "EQ",
    "gui.minescore.mp3.tab.mixer": "Mixer",
    "gui.minescore.mp3.shuffle_on": "Shuffle ✔",
    "gui.minescore.mp3.shuffle_off": "Shuffle",
    "gui.minescore.mp3.repeat.off": "Repeat",
    "gui.minescore.mp3.repeat.all": "Repeat all",
    "gui.minescore.mp3.repeat.one": "Repeat 1",
    "gui.minescore.mp3.volume": "Volume: %s%%",
    "gui.minescore.mp3.play": "Play",
    "gui.minescore.mp3.remove": "Delete",
    "gui.minescore.mp3.add_to_menu": "Add to playlist ▾",
    "gui.minescore.mp3.make_playlist_first": "Make one in Playlists first",
    "gui.minescore.mp3.empty_playlist": "Nothing in this playlist yet. Add songs from the Library.",
    "gui.minescore.mp3.nothing_next": "Nothing after this song",
    "gui.minescore.mp3.playlist_name": "Playlist name",
    "gui.minescore.mp3.new": "New",
    "gui.minescore.mp3.delete": "Delete",
    "gui.minescore.mp3.take_out": "Take out",
    "gui.minescore.mp3.eq_title": "Equaliser (for listening on the go)",
    "gui.minescore.mp3.eq_reset": "Reset",
    "gui.minescore.mp3.eq_docked": "Docked: these set every Amplifier in the system.",
    "gui.minescore.mp3.eq_needs_amp": "Docked: connect an Amplifier to use EQ.",
    "gui.minescore.mp3.eq_title_docked": "System EQ (every Amplifier)",
    "gui.minescore.mp3.no_songs": "No songs yet",
    "gui.minescore.mp3.empty_library": "No songs yet. Put a Vinyl Record and this MP3 Player in a Disc Cutting Lathe and press Load.",
    "gui.minescore.mp3.no_playlists": "Type a name below and press New",
    "gui.minescore.mp3.up_next": "Up next",
    "gui.minescore.mp3.bluetooth": "Playing through a speaker",
    "gui.minescore.mixer.speakers": "Speakers playing: %s",
    "gui.minescore.mixer.channels": "Channels",
    "gui.minescore.mixer.channels_locked": "Connect a Channel Manager to set Left / Right",
    "gui.minescore.mixer.boost": "Boost above 100%",
    "gui.minescore.mixer.boost_locked": "Connect an Amplifier to boost the volume",
    "gui.minescore.mixer.eject": "Take out MP3 Player",
    "gui.minescore.mixer.boost_value": "Boost: %s%% (cap %s%%)",
    "gui.minescore.mixer.channels_far": "The Channel Manager is too far away to show",
    "gui.minescore.search": "Search",
    "gui.minescore.lathe.sort.name": "Name",
    "gui.minescore.lathe.sort.date": "Date",
    "gui.minescore.lathe.sort.type": "Type",
    "gui.minescore.lathe.sort.size": "Size",
    "gui.minescore.lathe.details": "%s · %s · modified %s",
    "gui.minescore.no_matches": "Nothing matches",
    "gui.minescore.on": "On",
    "gui.minescore.off": "Off",
    "gui.minescore.save": "Save",
    "gui.minescore.channel.left": "Left: %s",
    "gui.minescore.channel.right": "Right: %s",
    "gui.minescore.channel.mono_auto": "Mono: auto",
    "gui.minescore.channel.mono_forced": "Mono: forced",
    "gui.minescore.channel.short.left": "L",
    "gui.minescore.channel.short.right": "R",
    "gui.minescore.channel.short.mono": "M",
    "gui.minescore.channel.cycle": "Click to change: Left, Right, Mono",
    "gui.minescore.channel.name.left": "Left",
    "gui.minescore.channel.name.right": "Right",
    "gui.minescore.channel.name.mono": "Mono",
    "gui.minescore.amp.master": "Master: %s%%",
    "gui.minescore.amp.main": "Main Amplifier",
    "gui.minescore.amp.line": "Line Amplifier",
    "gui.minescore.amp.alone": "Not wired into a system",
    "gui.minescore.amp.cap": "Boost cap %s%% (thinnest hub link: %s ga)",
    "gui.minescore.amp.cap_no_wire": "Link it to the Mixer or Channel Manager to set its boost cap",
    "gui.minescore.amp.cap_line": "Held to the main Amplifier's cap: %s%%",
    "gui.minescore.amp.cap_alone": "Up to 100% until it's in a system",
    "gui.minescore.amp.held": "Held at %s%% by the cap",
    "gui.minescore.cm.name": "Speaker",
    "gui.minescore.cm.type": "Type",
    "gui.minescore.cm.range": "Range",
    "gui.minescore.cm.channel": "Ch",
    "gui.minescore.cm.trim": "Trim",
    "gui.minescore.cm.type.g12": "Sub",
    "gui.minescore.cm.type.g16": "Mid",
    "gui.minescore.cm.type.g20": "Twt",
    "gui.minescore.cm.chained": "chain",
    "gui.minescore.cm.locate": "Locate this speaker",
    "gui.minescore.cm.empty": "No speakers are wired after this Channel Manager yet.",
    "gui.minescore.cm.rename_hint": "Click a speaker's name to rename it",
    "gui.minescore.speaker.title": "Speaker",
    "gui.minescore.speaker.name": "Name",
    "gui.minescore.speaker.unnamed": "Unnamed",
    "gui.minescore.speaker.type.g12": "Subwoofer",
    "gui.minescore.speaker.type.g16": "Middle Speaker",
    "gui.minescore.speaker.type.g20": "Tweeter Speaker",
    "gui.minescore.speaker.channel": "Channel: %s",
    "gui.minescore.speaker.channel_unmanaged": "Channel: Mono (no Channel Manager)",
    "gui.minescore.speaker.trim": "Trim: %s%%",
    "gui.minescore.speaker.power_none": "Power: not counted",
    "gui.minescore.speaker.power_full": "Power: full (%s FE/t)",
    "gui.minescore.speaker.power_over": "Power: %s%%, past the budget (%s FE/t)",
    "gui.minescore.mixer.power": "Power: %s / %s FE/t",
    "gui.minescore.mixer.power_over": "Power: %s / %s FE/t, some speakers quieter",
    "gui.minescore.mixer.power_short": "Not enough power: the hubs need %s FE/t",
    "gui.minescore.power.stored": "%s / %s FE",
    "item.minescore.score.untitled": "Untitled Score",
    "tooltip.minescore.score.empty": "Put it on a Writer's Desk to start a score",
    "tooltip.minescore.score.composer": "by %s",
    "tooltip.minescore.score.original": "Original",
    "tooltip.minescore.score.copy": "Copy #%s",
    "gui.minescore.desk.score": "Score",
    "gui.minescore.desk.copy": "Copy",
    "gui.minescore.desk.open_editor": "Open Editor",
    "gui.minescore.desk.editor_hint": "Edit the score on the desk. Anyone else at the desk can edit it with you.",
    "gui.minescore.desk.play": "Play the score through the desk's speakers (or from the desk). Pressing it again plays the latest edits.",
    "gui.minescore.desk.stop": "Stop",
    "gui.minescore.desk.loop": "Loop: start again from the top when it ends",
    "gui.minescore.desk.playing": "Playing",
    "msg.minescore.score.render_failed": "Couldn't play the score: %s",
    "gui.minescore.lathe.score": "Score",
    "gui.minescore.editor.title": "Score Editor",
    "gui.minescore.editor.preferences": "Score Editor Preferences",
    "gui.minescore.editor.keys_hint": "Click a shortcut, then press the new keys. Delete clears it, Esc cancels.",
    "gui.minescore.editor.keys_reset": "Reset all to MuseScore's",
    "gui.minescore.editor.input": "Note input (N): click the staff or type A–G to write notes",
    "gui.minescore.editor.instrument": "Click for the next instrument, Shift-click for the one before",
    "gui.minescore.editor.change_instrument": "Change instrument",
    "gui.minescore.editor.add_part": "+ Add part…",
    "gui.minescore.editor.add_part_title": "Add a Part",
    "gui.minescore.editor.instrument_hint": "Hover an instrument for its range",
    "gui.minescore.editor.choose_instruments": "Choose instruments…",
    "gui.minescore.editor.choose_instruments_title": "Instruments the List Offers",
    "gui.minescore.editor.choose_hint": "Click an instrument to switch it on or off",
    "gui.minescore.editor.arrow_select": "Arrow keys select",
    "key.categories.minescore": "MineScore",
    "key.minescore.config": "Open the MineScore config menu",
    "key.minescore.invisible_wires": "Invisible wires on/off",
    "key.minescore.room_sound": "Room Sound Physics on/off",
    "key.minescore.through_blocks": "Sound through blocks on/off",
    "gui.minescore.config.title": "MineScore Settings",
    "gui.minescore.config.search": "Search every setting",
    "gui.minescore.config.presets": "Presets…",
    "gui.minescore.config.presets_title": "Presets",
    "gui.minescore.config.presets_hint": "A preset changes only the settings it holds. Server settings need an operator.",
    "gui.minescore.config.import": "Import",
    "gui.minescore.config.import_hint": "Apply settings copied from Export (from the clipboard)",
    "gui.minescore.config.export": "Export",
    "gui.minescore.config.export_hint": "Copy every setting to the clipboard, and save it to config/minescore-settings-export.json",
    "gui.minescore.config.exported": "Copied to the clipboard and saved to %s",
    "gui.minescore.config.exported_clipboard": "Copied to the clipboard",
    "gui.minescore.config.imported": "Settings imported",
    "gui.minescore.config.imported_some": "Settings imported; %s server settings left alone (only operators can change them)",
    "gui.minescore.config.import_failed": "The clipboard doesn't hold exported MineScore settings",
    "gui.minescore.config.preset_applied": "Preset applied: %s",
    "gui.minescore.config.preset_some": "Preset applied: %s; %s server settings left alone (only operators can change them)",
    "gui.minescore.config.preset_name": "Name a new preset",
    "gui.minescore.config.save_preset": "Save current",
    "gui.minescore.config.preset_saved": "Saved the current settings as %s",
    "gui.minescore.config.delete_preset": "Delete this preset",
    "gui.minescore.config.reset": "Back to the default (%s)",
    "gui.minescore.config.default": "Default: %s",
    "gui.minescore.config.server_setting": "A server setting: changing it changes it for everyone.",
    "gui.minescore.config.server_locked": "A server setting: only operators can change it.",
    "gui.minescore.config.press_keys": "press keys",
    "gui.minescore.config.results": "%s found",
    "gui.minescore.config.legend": "Yellow: changed from the default · ◆ server setting · ◇ server setting you can't change",
    "msg.minescore.config.on": "%s: on",
    "msg.minescore.config.off": "%s: off",
    "msg.minescore.config.not_op": "Only operators can change MineScore's server settings",
    "msg.minescore.lathe.no_copies": "Copying records is turned off on this server",
    "msg.minescore.song.too_big": "That song is bigger than this server allows (%s MB)",
    "msg.minescore.score.limit": "This server allows %s parts and %s measures in a score",
    "gui.minescore.editor.import": "Import…",
    "gui.minescore.editor.import_hint": "Replace this score with a MIDI, Note Block Studio or MineScore file",
    "gui.minescore.editor.export_as": "Export as a %s file",
    "gui.minescore.editor.export_folder": "Export folder…",
    "gui.minescore.editor.exporting": "Exporting %s…",
    "gui.minescore.editor.exported": "Exported to %s",
    "gui.minescore.editor.export_failed": "Couldn't export: %s",
    "gui.minescore.editor.export_folder_set": "Exports now go to %s",
    "gui.minescore.import.title": "Import a Score",
    "gui.minescore.import.hint": "MIDI, Note Block Studio and MineScore files from blkcdsongs/ and minescore/",
    "gui.minescore.import.reading": "Reading…",
    "gui.minescore.import.none": "No files to import there yet",
    "gui.minescore.import.browse": "Browse…",
    "gui.minescore.import.failed": "Couldn't import that: %s",
    "gui.minescore.import.replaces": "Importing %s replaces everything in this score.",
    "gui.minescore.import.copy_question": "Keep the score as it is now as a copy first? (uses a Blank Score from your inventory)",
    "gui.minescore.import.copy_then": "Copy, then import",
    "gui.minescore.import.without_copy": "Just import",
    "msg.minescore.score.copy_needs_paper": "You need a Blank Score in your inventory to keep a copy",
    "gui.minescore.editor.search_palettes": "Search palettes (Ctrl+F)",
    "gui.minescore.editor.long_notes": "Long notes in playback (every part)",
    "gui.minescore.editor.restrike": "Strike this part's long notes again every beat, instead of holding them",
    "gui.minescore.editor.restrike_all": "Every part re-strikes: Preferences (or the client config) has it on for all",
    "gui.minescore.redstone": "Redstone Control: %s",
    "gui.minescore.redstone.mixer": "While on, and the Mixer feeds speakers: a signal on the front, back, top or bottom plays and pauses; a pulse on its left skips ahead; a pulse on its right restarts the song, or goes back one in the first 2 s. A comparator reads 15 while it plays.",
    "gui.minescore.redstone.cm": "While on, and it feeds speakers: weak power on its left or right turns that channel off; strong power forces mono. A comparator reads 15 from its left while Left plays, its right while Right plays, and every side in mono.",
    "gui.minescore.redstone.amp": "While on, and it feeds speakers: 1–15 on the back, left, right or front sets bands 1–3, 4–5, 6–7 or 8–10 (15 = 400%); on the bottom it sets the loudest band and moves the rest with it. 0 gives control back. Comparators read each group's loudest band.",
    "gui.minescore.redstone.meu": "While on, and it feeds speakers: any signal switches the unit off, as if on bypass. A comparator reads how many cards aren't bypassed.",
    "gui.minescore.lathe.power_use": "Uses %s FE/t while it works",
    "msg.minescore.lathe.no_power": "The Lathe needs power to cut, copy or load songs",
    "msg.minescore.lathe.waiting_power": "Waiting for power (%s FE/t)",
    "msg.minescore.short.cycle_power": "A speaker shorted. Switch the source's power off for a second, then on again",
    "gui.minescore.speaker.state.idle": "Idle",
    "gui.minescore.speaker.state.playing": "Playing",
    "gui.minescore.speaker.state.bluetooth": "Playing an MP3 Player over Bluetooth",
    "gui.minescore.speaker.hint": "Set its channel and trim on the Channel Manager.",
    "msg.minescore.locate": "%s is at %s, %s, %s (%s blocks away)",
    "msg.minescore.short.system": "A speaker blew! Wait 5 seconds, then press Play to start the system again",
    "msg.minescore.short.line": "A speaker blew! Its Line Amplifier is off until the system is started again",
    "msg.minescore.short.wait": "Still recovering from a short. Wait a few seconds, then try again",
    "kind.minescore.meu": "a Multi-Effects Unit",
    "item.minescore.preset_chip.named": "Preset Chip: %s",
    "tooltip.minescore.card.bypassed": "Bypassed",
    "tooltip.minescore.card.set": "Settings changed",
    "tooltip.minescore.chip.blank": "Blank: save a preset onto it in a Multi-Effects Unit",
    "tooltip.minescore.chip.card": "%s: %s",
    "effect.minescore.overdrive": "Overdrive",
    "effect.minescore.fuzz": "Fuzz",
    "effect.minescore.high_gain": "High-gain",
    "effect.minescore.chorus": "Chorus",
    "effect.minescore.flanger": "Flanger",
    "effect.minescore.phaser": "Phaser",
    "effect.minescore.rotary": "Rotary speaker",
    "effect.minescore.pitch": "Pitch shifter",
    "effect.minescore.speed": "Speed",
    "effect.minescore.straight": "Straight delay",
    "effect.minescore.stereo": "Stereo delay",
    "effect.minescore.ping_pong": "Ping-pong delay",
    "effect.minescore.room": "Room",
    "effect.minescore.spring": "Spring",
    "effect.minescore.plate": "Plate",
    "effect.minescore.hall": "Hall",
    "effect.minescore.clean_tube": "Clean Tube",
    "effect.minescore.british_crunch": "British Crunch",
    "effect.minescore.boutique_lead": "Boutique Lead",
    "effect.minescore.modern_high_gain": "Modern High-gain",
    "effect.minescore.wah": "Wah",
    "effect.minescore.gate": "Noise gate",
    "effect.minescore.compressor": "Compressor",
    "effect.minescore.tuner": "Tuner",
    "effect.minescore.expression": "Expression sweep",
    "knob.minescore.drive": "Drive",
    "knob.minescore.tone": "Tone",
    "knob.minescore.level": "Level",
    "knob.minescore.mix": "Mix",
    "knob.minescore.rate": "Rate",
    "knob.minescore.depth": "Depth",
    "knob.minescore.feedback": "Feedback",
    "knob.minescore.semitones": "Pitch",
    "knob.minescore.fine": "Fine",
    "knob.minescore.speed": "Speed",
    "knob.minescore.time": "Time",
    "knob.minescore.sync": "Sync",
    "knob.minescore.spread": "Spread",
    "knob.minescore.size": "Size",
    "knob.minescore.decay": "Decay",
    "knob.minescore.damping": "Damping",
    "knob.minescore.predelay": "Pre-delay",
    "knob.minescore.gain": "Gain",
    "knob.minescore.bass": "Bass",
    "knob.minescore.mid": "Mid",
    "knob.minescore.treble": "Treble",
    "knob.minescore.cabinet": "Cab",
    "knob.minescore.mode": "Mode",
    "knob.minescore.sensitivity": "Sensitivity",
    "knob.minescore.resonance": "Resonance",
    "knob.minescore.threshold": "Threshold",
    "knob.minescore.release": "Release",
    "knob.minescore.ratio": "Ratio",
    "knob.minescore.attack": "Attack",
    "knob.minescore.makeup": "Makeup",
    "knob.minescore.shift": "Key shift",
    "knob.minescore.reference": "Tune A to",
    "knob.minescore.target_card": "Sweeps",
    "knob.minescore.retune": "Retune",
    "knob.minescore.onoff.0": "Off",
    "knob.minescore.onoff.1": "On",
    "knob.minescore.target_knob": "Knob",
    "knob.minescore.from": "From",
    "knob.minescore.to": "To",
    "knob.minescore.sync.0": "Off",
    "knob.minescore.sync.1": "1/4",
    "knob.minescore.sync.2": "1/8",
    "knob.minescore.sync.3": "1/8 dotted",
    "knob.minescore.sync.4": "1/16",
    "knob.minescore.cab.0": "None",
    "knob.minescore.cab.1": "1×12 Open",
    "knob.minescore.cab.2": "2×12",
    "knob.minescore.cab.3": "4×12 Closed",
    "knob.minescore.wah.0": "Pedal",
    "knob.minescore.wah.1": "Auto",
    "knob.minescore.card.-1": "Nothing",
    "knob.minescore.card.0": "Drive",
    "knob.minescore.card.1": "Modulation",
    "knob.minescore.card.2": "Delay",
    "knob.minescore.card.3": "Reverb",
    "knob.minescore.card.4": "Amp & Cab",
    "gui.minescore.meu.slot_on": "On",
    "gui.minescore.meu.slot_off": "Off",
    "gui.minescore.meu.reset_card": "Reset",
    "gui.minescore.meu.expression": "Expression pedal: %s%%",
    "gui.minescore.meu.load_hint": "Load the built-in preset %s. Changing it afterwards makes a new preset you can save onto a chip; the built-in one never changes.",
    "gui.minescore.meu.chip_name": "Name for the chip",
    "gui.minescore.meu.save_chip": "Save chip",
    "gui.minescore.meu.load_chip": "Load chip",
    "gui.minescore.meu.master": "Unit: %s",
    "gui.minescore.meu.chip": "Chip",
    "gui.minescore.meu.preset": "Preset: %s",
    "gui.minescore.meu.empty": "Empty slot",
    "gui.minescore.meu.empty_slot": "Slot %s is empty. Put an effect card in it.",
    "gui.minescore.meu.slot": "Slot %s · %s",
    "gui.minescore.meu.analysing": "Listening to the song…",
    "gui.minescore.meu.analysis": "%s · A = %s Hz · %s BPM",
    "msg.minescore.meu.saved": "Saved \"%s\" onto the chip",
    "msg.minescore.meu.blank_chip": "That chip is blank",
    "msg.minescore.meu.missing": "This preset also uses: %s",
    "gui.minescore.mixer.effects": "Effects: %s",
    "gui.minescore.mixer.effects_far": "(too far away to show)",
    "gui.minescore.mixer.effects_bypassed": "Bypassed: the music plays untouched",
    "msg.minescore.lathe.prints": "Prints %s effect(s) onto the record · %s s to cut",
    "pack.minescore.minescore_retro": "MineScore: Retro Hi-Fi",
    "pack.minescore.minescore_modern": "MineScore: Modern Studio",
    "pack.minescore.minescore_16x": "MineScore: 16×",
    "gui.minescore.manual.contents": "Contents",
    "hud.minescore.wiring": "Cable from %s · %s blocks · %s anchors",
    "info.minescore.power": "Power: %s FE/t",
    "info.minescore.band.bass": "Bass · 12 ga",
    "info.minescore.band.mid": "Middle · 16 ga",
    "info.minescore.band.treble": "Treble · 20 ga",
    "info.minescore.channel": "Channel: %s",
    "info.minescore.channel.left": "Left",
    "info.minescore.channel.right": "Right",
    "info.minescore.channel.mono": "Mono",
    "info.minescore.playing": "Playing: %s",
    "info.minescore.record": "Record: %s",
    "info.minescore.showing": "Showing: %s",
    "info.minescore.cabinet": "Scores %s/%s · Records %s/%s",
    "info.minescore.score": "Score: %s",
    "info.minescore.redstone.on": "Redstone Control on",
    "info.minescore.redstone.off": "Redstone Control off",
    "jei.minescore.lathe.cut": "Pick a song file, then Cut",
    "jei.minescore.lathe.score": "Presses the score onto the record; the Score stays",
    "jei.minescore.lathe.copy": "Copies the record (Copy #n); the original stays",
    "jei.minescore.lathe.load": "Adds the song to the MP3 Player; the record stays",
    "jei.minescore.info.manual": "MineScore's guidebook. Right-click to read it.",
    "jei.minescore.info.blank_score": "Put it on a Writer's Desk to start a new score, or in the desk's copy slot to copy the score on it.",
    "jei.minescore.info.score": "Made from a Blank Score at the Writer's Desk. Edit it there; press it onto a record at the Disc Cutting Lathe.",
    "jei.minescore.info.speaker_wire_12": "Joins Subwoofers and the 12 ga (red) jacks. Shift+right-click jack to jack to link hubs with any gauge.",
    "jei.minescore.info.speaker_wire_16": "Joins Middle Speakers and the 16 ga (yellow) jacks. Shift+right-click jack to jack to link hubs with any gauge.",
    "jei.minescore.info.speaker_wire_20": "Joins Tweeter Speakers and the 20 ga (blue) jacks. Shift+right-click jack to jack to link hubs with any gauge.",
    "pack.minescore.minescore_sounds_retro": "MineScore Sounds: Retro",
    "pack.minescore.minescore_sounds_studio": "MineScore Sounds: Studio",
}


# --- PNG writing (no third-party libraries) ---------------------------------------------------

def halve(pixels):
    """Half resolution for the 16× pack, kept crisp: each 2 × 2 block becomes one of its own pixels, never a
    blend. A pixel that stands out from the area round it (a line, a lamp, a letter's stroke) wins, so thin
    details survive; otherwise the one closest to the block's average. A block is clear if most of it is."""
    h, w = len(pixels), len(pixels[0])

    def dist(p, q):
        return sum((p[i] - q[i]) ** 2 for i in range(3))

    out = []
    for y in range(0, h - 1, 2):
        row = []
        for x in range(0, w - 1, 2):
            block = [pixels[y][x], pixels[y][x + 1], pixels[y + 1][x], pixels[y + 1][x + 1]]
            solid = [p for p in block if p[3] > 0]
            if len(solid) < 2:
                row.append((0, 0, 0, 0))
                continue
            around = [pixels[j][i] for j in range(max(0, y - 1), min(h, y + 3)) for i in range(max(0, x - 1), min(w, x + 3))
                      if pixels[j][i][3] > 0]
            area = [sum(p[i] for p in around) / len(around) for i in range(3)]
            mean = [sum(p[i] for p in solid) / len(solid) for i in range(3)]
            odd = max(solid, key=lambda p: dist(p, area))
            row.append(odd if dist(odd, area) > 75 ** 2 else min(solid, key=lambda p: dist(p, mean)))
        out.append(row)
    return out


def png(path: Path, pixels) -> None:
    """pixels: rows of (r, g, b, a) tuples. In a pack run, only textures go out, into the pack, and only
    where they differ from the default's."""
    if PACK:
        rel = path.relative_to(ASSETS)
        if rel.parts[0] != "textures" or rel == Path("textures/gui/score_glyphs.png") or (HALF and rel.parts[1] == "gui"):
            return
        key = "/".join(rel.with_suffix("").parts[1:])
        if HALF and key in tx.SIXTEEN:
            pixels = tx.SIXTEEN[key]().rows()
        elif HALF and len(pixels) >= 32 and len(pixels[0]) >= 32:
            pixels = halve(pixels)
        default, path = path, PACK_ROOT / "assets" / "minescore" / rel
        data = png_bytes(pixels)
        if not HALF and default.exists() and default.read_bytes() == data:
            return
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png_bytes(pixels))


def png_bytes(pixels) -> bytes:
    height, width = len(pixels), len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("4B", *px) for px in row) for row in pixels)

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def cable_texture(color):
    """Insulation along v with a darker spiral stripe; edges shaded so ribbons read as round."""
    px = []
    for y in range(16):
        row = []
        for x in range(16):
            edge = 0.7 if x in (0, 15) else 0.85 if x in (1, 14) else 1.0
            stripe = 0.75 if (x + y * 2) % 16 < 3 else 1.0
            row.append(tx.tone(color, edge * stripe))
        px.append(row)
    return px


# --- models ----------------------------------------------------------------------------------

FULL = [0, 0, 0, 16, 16, 16]
FACES = ("north", "south", "east", "west", "up", "down")
FACE_OF = {"north": "front", "south": "back", "east": "side", "west": "side", "up": "top", "down": "bottom"}


def write(path: Path, obj) -> None:
    if PACK:
        return  # packs only carry textures
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2) + "\n", encoding="utf-8")


def frame_uv(box, face, frame):
    """UVs for one face of `box`, mapping `frame` onto the whole texture. With frame = FULL this is
    Minecraft's own automatic UV; with the block's box it squeezes the texture onto a small block."""
    x1, y1, z1, x2, y2, z2 = box
    fx1, fy1, fz1, fx2, fy2, fz2 = frame
    u = lambda a, lo, hi: (a - lo) / (hi - lo) * 16  # noqa: E731
    return {
        "north": [u(fx2 - x2 + fx1, fx1, fx2), u(fy2 - y2 + fy1, fy1, fy2), u(fx2 - x1 + fx1, fx1, fx2), u(fy2 - y1 + fy1, fy1, fy2)],
        "south": [u(x1, fx1, fx2), u(fy2 - y2 + fy1, fy1, fy2), u(x2, fx1, fx2), u(fy2 - y1 + fy1, fy1, fy2)],
        "east": [u(fz2 - z2 + fz1, fz1, fz2), u(fy2 - y2 + fy1, fy1, fy2), u(fz2 - z1 + fz1, fz1, fz2), u(fy2 - y1 + fy1, fy1, fy2)],
        "west": [u(z1, fz1, fz2), u(fy2 - y2 + fy1, fy1, fy2), u(z2, fz1, fz2), u(fy2 - y1 + fy1, fy1, fy2)],
        "up": [u(x1, fx1, fx2), u(z1, fz1, fz2), u(x2, fx1, fx2), u(z2, fz1, fz2)],
        "down": [u(x1, fx1, fx2), u(fz2 - z2 + fz1, fz1, fz2), u(x2, fx1, fx2), u(fz2 - z1 + fz1, fz1, fz2)],
    }[face]


def element(box, textures, frame=FULL, uv_override=None, rotations=None):
    """textures: a texture ref for every face, or a dict per face."""
    faces = {}
    for face in FACES:
        tex = textures[face] if isinstance(textures, dict) else textures
        faces[face] = {"uv": uv_override or [round(v, 4) for v in frame_uv(box, face, frame)], "texture": tex}
        if rotations and face in rotations:
            faces[face]["rotation"] = rotations[face]
    return {"from": [round(v, 4) for v in box[:3]], "to": [round(v, 4) for v in box[3:]], "faces": faces}


def chamfered(box, textures, frame=FULL):
    """A box with softly bevelled edges: two cross boxes plus a cap on the front and back. The caps carry
    the front and back textures round their own edges."""
    x1, y1, z1, x2, y2, z2 = box
    i = 0.5 if min(x2 - x1, y2 - y1, z2 - z1) >= 12 else 0.25
    front = {face: textures["north"] for face in FACES}
    back = {face: textures["south"] for face in FACES}
    return [
        element([x1, y1 + i, z1 + i, x2, y2 - i, z2 - i], textures, frame),
        element([x1 + i, y1, z1 + i, x2 - i, y2, z2 - i], textures, frame),
        element([x1 + i, y1 + i, z1, x2 - i, y2 - i, z1 + i], front, frame),
        element([x1 + i, y1 + i, z2 - i, x2 - i, y2 - i, z2], back, frame),
    ] + edge_plugs(box, i, textures, frame)


def edge_plugs(box, i, textures, frame):
    """The bevels leave a thin channel along each of a box's 12 edges, open at both ends, so you could see
    straight through it looking along the edge. Each end gets a tiny wall where it meets the face across it,
    textured like that face, so it reads as the corner's own bevel rather than a stray pixel."""
    x1, y1, z1, x2, y2, z2 = box
    plugs = []

    def wall(lo, hi, face):
        e = element(lo + hi, {f: textures[f] for f in FACES}, frame)
        e["faces"] = {face: e["faces"][face]}
        plugs.append(e)

    for xa, xb in ((x1, x1 + i), (x2 - i, x2)):
        for ya, yb in ((y1, y1 + i), (y2 - i, y2)):  # channels along z
            wall([xa, ya, z1 + i], [xb, yb, z1 + i], "north")
            wall([xa, ya, z2 - i], [xb, yb, z2 - i], "south")
        for za, zb in ((z1, z1 + i), (z2 - i, z2)):  # channels along y
            wall([xa, y1 + i, za], [xb, y1 + i, zb], "down")
            wall([xa, y2 - i, za], [xb, y2 - i, zb], "up")
    for ya, yb in ((y1, y1 + i), (y2 - i, y2)):
        for za, zb in ((z1, z1 + i), (z2 - i, z2)):  # channels along x
            wall([x1 + i, ya, za], [x1 + i, yb, zb], "west")
            wall([x2 - i, ya, za], [x2 - i, yb, zb], "east")
    return plugs


def jack_texture_key(kind, port):
    signal = port["signal"]
    if signal.startswith("link_"):
        a, b = sorted((kind, signal[len("link_"):]))  # LINK_COLORS keys are sorted the same way
        return f"link_{a}_{b}"
    return signal


def jack_element(kind, port):
    return element(port["from"] + port["to"], f"#jack_{port['dir']}_{jack_texture_key(kind, port)}",
                   uv_override=[0, 0, 16, 16])


def deck_and_platter():
    """A raised deck frame round a recessed well with an empty platter: the top of the Lathe and Vinyl Player."""
    deck = {face: "#deck" if face == "up" else "#side" for face in FACES}
    well = {face: "#platter" if face == "up" else "#well" for face in FACES}
    return [
        element([0.5, 6, 0.5, 15.5, 8, 3], deck),
        element([0.5, 6, 14, 15.5, 8, 15.5], deck),
        element([0.5, 6, 3, 2, 8, 14], deck),
        element([13, 6, 3, 15.5, 8, 14], deck),
        element([3, 6, 4, 12, 6.5, 13], well, uv_override=None, frame=[3, 0, 4, 12, 16, 13]),
        element([7.25, 6.5, 8.25, 7.75, 7.25, 8.75], "#deck"),
    ]


def lathe_extras():
    """The Lathe's cutting arm and brass cutting head."""
    deck = {face: "#deck" if face == "up" else "#side" for face in FACES}
    return deck_and_platter() + [
        element([13.5, 8, 12.5, 15, 9.5, 14], deck),
        element([9.5, 8.75, 12.75, 13.5, 9.25, 13.25], "#deck"),
        element([8.5, 8.5, 12.5, 9.5, 9.5, 13.5], "#brass"),
    ]


def vinyl_extras():
    """The Vinyl Player's slim tonearm, resting beside the record."""
    return deck_and_platter() + [
        element([13.25, 8, 12.75, 14.75, 9, 14.25], "#brass"),       # pivot
        element([12.5, 8.6, 6.2, 13.1, 9, 13.5], "#arm"),             # arm, running toward the front
        element([12.1, 8.3, 5.2, 13.5, 8.8, 6.6], "#arm"),            # headshell
    ]


def desk_half(right):
    """One half of the Writer's Desk, facing north (its viewer's left is east): the top, a drawer pedestal at
    its outer end (east for the left half, west for the right), and a back panel the jacks sit on."""
    px1, px2 = (0, 7) if right else (9, 16)
    sides = {"north": "#side", "south": "#side", "east": "#side", "west": "#side"}
    return [
        element([0, 13, 0, 16, 16, 16], {**sides, "up": "#top", "down": "#side"}),
        element([px1, 0, 1, px2, 13, 15], {**sides, "north": "#front", "up": "#side", "down": "#bottom"}),
        element([0, 1, 14, 16, 13, 16], {**sides, "south": "#back", "up": "#side", "down": "#side"}),
    ]


def _same_mirrored(recipe, pattern, key):
    """Whether a shaped recipe is another one flipped left to right, whatever letters each happened to use."""
    def cells(p, k):
        return [[json.dumps(k[ch], sort_keys=True) if ch != " " else None for ch in row] for row in p]
    return cells(recipe["pattern"], recipe["key"]) == [row[::-1] for row in cells(pattern, key)]


def lowered(elements, dy):
    """Every element moved down {@code dy} pixels (a speaker sitting flush on the one below, SpeakerBlock.DROP)."""
    out = []
    for e in elements:
        e = json.loads(json.dumps(e))
        e["from"][1] -= dy
        e["to"][1] -= dy
        if "rotation" in e:
            e["rotation"]["origin"][1] -= dy
        out.append(e)
    return out


def rotated(elements, angle):
    """Turns every element about the block's vertical centre line (Minecraft turns positive angles
    counter-clockwise seen from above, so -45 is clockwise)."""
    return [{**e, "rotation": {"origin": [8, 0, 8], "axis": "y", "angle": angle}} for e in elements]


# --- generation ----------------------------------------------------------------------------

lang = {"itemGroup.minescore": "MineScore", **MESSAGES}
if PACK and PACK_ROOT.exists():
    shutil.rmtree(PACK_ROOT)  # drawn afresh
pickaxe, axe = [], []
block_tex = ASSETS / "textures" / "block"

for name, (draw, rounded) in TEXTURES.items():
    canvas = draw()
    if rounded:
        tx.round_edges(canvas)
    png(block_tex / f"{name}.png", canvas.rows())

for signal, color in BAND_COLORS.items():
    for d in ("in", "out"):
        png(block_tex / f"jack_{d}_{signal}.png", tx.jack(color, d == "out").rows())
for (a, b), color in LINK_COLORS.items():
    for d in ("in", "out"):
        png(block_tex / f"jack_{d}_link_{a}_{b}.png", tx.jack(color, d == "out").rows())
for signal, names in PORT_NAMES.items():
    lang[f"port.minescore.{signal}.in"] = names[0]
    lang[f"port.minescore.{signal}.out"] = names[1]

for awg in (12, 16, 20):
    color = BAND_COLORS[f"band_{awg}"]
    png(ASSETS / "textures" / "entity" / f"cable_{awg}.png", cable_texture(color))
    png(ASSETS / "textures" / "item" / f"speaker_wire_{awg}.png", tx.wire_coil(50 + awg, color).rows())

for index, (name, (display, box, faces, full_uv, tool)) in enumerate(BLOCKS.items()):
    kind = PORTS[name]["kind"] if name in PORTS else None
    ports = PORTS.get(name, {}).get("ports", [])
    frame = box if full_uv else FULL
    faces = dict(faces)
    if isinstance(faces["back"], tuple):
        _, base, hub = faces["back"]
        back_frame = (frame[0], frame[1], frame[3], frame[4])
        jacks = [(p["from"][0], p["from"][1], p["to"][0], p["to"][1], p["dir"] == "out") for p in ports]
        canvas = tx.round_edges(tx.back_plate(60 + index, base, jacks, back_frame, hub))
        png(block_tex / f"{name}_back.png", canvas.rows())
        faces["back"] = f"{name}_back"
    textures = {"particle": f"minescore:block/{faces['side']}"}
    for role, texture in faces.items():
        textures[role] = f"minescore:block/{texture}"
    elements = chamfered(box, {face: f"#{role}" for face, role in FACE_OF.items()}, frame)
    if name in SPEAKER_DRIVERS:
        elements += driver_pocket(*SPEAKER_DRIVERS[name])
        textures.update({"pocket": "minescore:block/speaker_pocket", "cone": "minescore:block/speaker_cone",
                         "dome": "minescore:block/tweeter_dome"})
        if name != "tweeter_speaker":  # the cap's texture, so it's on the atlas for SpeakerRenderer
            face, cx, cy = SPEAKER_DRIVERS[name][:3]
            elements.append({"from": [cx - 1, cy - 1, face + SPEAKER_DRIVERS[name][4] - 0.01],
                             "to": [cx + 1, cy + 1, face + SPEAKER_DRIVERS[name][4] - 0.01],
                             "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#cap"}}})
            textures["cap"] = "minescore:block/speaker_cap"
    if name == "writers_desk":
        elements = desk_half(right=False)
    if name == "disc_cutting_lathe":
        textures.update({"deck": "minescore:block/disc_cutting_lathe_deck", "well": "minescore:block/disc_cutting_lathe_well",
                         "platter": "minescore:block/disc_cutting_lathe_platter", "brass": "minescore:block/brass"})
        elements += lathe_extras()
    if name == "vinyl_player":
        textures.update({"deck": "minescore:block/walnut_deck", "well": "minescore:block/disc_cutting_lathe_well",
                         "platter": "minescore:block/disc_cutting_lathe_platter", "brass": "minescore:block/brass",
                         "arm": "minescore:block/tonearm"})
        elements += vinyl_extras()
    for port in ports:
        key = jack_texture_key(kind, port)
        textures[f"jack_{port['dir']}_{key}"] = f"minescore:block/jack_{port['dir']}_{key}"
        elements.append(jack_element(kind, port))
    model = {"parent": "minecraft:block/block", "textures": textures, "elements": elements}
    if name in SPEAKER_DRIVERS:
        model["render_type"] = "minecraft:cutout"  # the clear driver hole
    write(ASSETS / "models" / "block" / f"{name}.json", model)
    if name in POWERED_HUBS:  # the same block with its lights off
        write(ASSETS / "models" / "block" / f"{name}_off.json",
              {**model, "textures": {**textures, "front": f"minescore:block/{name}_front_off"}})
    if name == "audio_mixer":  # with an MP3 Player in its dock, lit and unlit
        for suffix in ("_docked", "_docked_off"):
            write(ASSETS / "models" / "block" / f"{name}{suffix}.json",
                  {**model, "textures": {**textures, "front": f"minescore:block/{name}_front{suffix}"}})
    if name in SPEAKER_DRIVERS:
        # Sitting flush on the speaker below: the model lowered by 4 pixels a step. The Tweeter also turns to
        # eight directions: the four facings, each optionally a further 45° clockwise.
        for drop in range(1, 5):
            write(ASSETS / "models" / "block" / f"{name}_drop{drop}.json", {**model, "elements": lowered(elements, 4 * drop)})
        variants = {}
        for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for drop in range(5):
                lower = f"_drop{drop}" if drop else ""
                if name == "tweeter_speaker":
                    for diagonal in ("false", "true"):
                        suffix = ("_diagonal" if diagonal == "true" else "") + lower
                        variants[f"diagonal={diagonal},drop={drop},facing={d}"] = {"model": f"minescore:block/{name}{suffix}",
                                                                                    **({"y": y} if y else {})}
                else:
                    variants[f"drop={drop},facing={d}"] = {"model": f"minescore:block/{name}{lower}", **({"y": y} if y else {})}
        if name == "tweeter_speaker":
            for drop in range(5):
                lower = f"_drop{drop}" if drop else ""
                write(ASSETS / "models" / "block" / f"{name}_diagonal{lower}.json",
                      {**model, "elements": rotated(lowered(elements, 4 * drop), -45)})
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": variants})
    elif name == "audio_mixer":
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {
            f"docked={str(docked).lower()},facing={d},powered={str(on).lower()}": {
                "model": f"minescore:block/{name}{'_docked' if docked else ''}{'' if on else '_off'}", **({"y": y} if y else {})}
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for on in (True, False)
            for docked in (True, False)
        }})
    elif name in POWERED_HUBS:
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {
            f"facing={d},powered={str(on).lower()}": {"model": f"minescore:block/{name}{'' if on else '_off'}", **({"y": y} if y else {})}
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for on in (True, False)
        }})
    else:
        write(ASSETS / "blockstates" / f"{name}.json", {"variants": {
            f"facing={d}": {"model": f"minescore:block/{name}", **({"y": y} if y else {})}
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))
        }})
    write(ASSETS / "models" / "item" / f"{name}.json", {"parent": f"minescore:block/{name}"})
    lang[f"block.minescore.{name}"] = display
    (pickaxe if tool == "pickaxe" else axe).append(f"minescore:{name}")

# Writer's Desk halves. Must match WritersDeskBlock: each half is the desk top over a drawer pedestal at its
# outer end, with a back panel across; the two halves are mirror images, knee space between them. The left
# half's back panel (the model above, also a lone desk) carries the jacks; the right half has none.
DESK_RIGHT_TEXTURES = {"particle": "minescore:block/walnut_planks", "top": "minescore:block/writers_desk_top_right",
                       "front": "minescore:block/writers_desk_front", "side": "minescore:block/walnut_planks",
                       "back": "minescore:block/walnut_planks", "bottom": "minescore:block/writers_desk_bottom"}
write(ASSETS / "models" / "block" / "writers_desk_right.json", {
    "parent": "minecraft:block/block", "textures": DESK_RIGHT_TEXTURES, "elements": desk_half(right=True)})
write(ASSETS / "blockstates" / "writers_desk.json", {"variants": {
    f"facing={d},half={half}": {"model": f"minescore:block/writers_desk{'_right' if half == 'right' else ''}", **({"y": y} if y else {})}
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)) for half in ("single", "left", "right")
}})

# Cable Anchor. Must match CableAnchorBlock: the floor model sticks up with its input facing north; the
# wall model is mounted on a wall to the south, sticks out north, and has its input facing west.
ANCHOR_TEXTURES = {"particle": "minescore:block/anchor_post", "post": "minescore:block/anchor_post",
                   "flow": "minescore:block/anchor_flow", "in": "minescore:block/anchor_in",
                   "out": "minescore:block/anchor_out", "bottom": "minescore:block/cable_anchor_bottom"}


def stub(box, tex):
    return element(box, tex, uv_override=[0, 0, 16, 16])


write(ASSETS / "models" / "block" / "cable_anchor_floor.json", {
    "parent": "minecraft:block/block", "textures": ANCHOR_TEXTURES,
    "elements": [
        element([6.5, 0, 6.5, 9.5, 4, 9.5], {"north": "#post", "south": "#post", "east": "#post", "west": "#post",
                                              "up": "#flow", "down": "#bottom"}, uv_override=[0, 0, 16, 16]),
        stub([7.25, 1.5, 4.5, 8.75, 3, 6.5], "#in"),
        stub([7.25, 1.5, 9.5, 8.75, 3, 11.5], "#out"),
    ],
})
for mirrored in (False, True):
    write(ASSETS / "models" / "block" / f"cable_anchor_wall{'_mirrored' if mirrored else ''}.json", {
        "parent": "minecraft:block/block", "textures": ANCHOR_TEXTURES,
        "elements": [
            element([6.5, 6.5, 12, 9.5, 9.5, 16], {"north": "#flow", "south": "#bottom", "east": "#post", "west": "#post",
                                                   "up": "#post", "down": "#post"},
                    uv_override=[0, 0, 16, 16], rotations={"north": 270 if mirrored else 90}),
            stub([4.5, 7.25, 13, 6.5, 8.75, 14.5], "#out" if mirrored else "#in"),
            stub([9.5, 7.25, 13, 11.5, 8.75, 14.5], "#in" if mirrored else "#out"),
        ],
    })

Y_ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
variants = {}
for attach in ("up", "down", "north", "east", "south", "west"):
    for inp in ("north", "east", "south", "west"):
        if attach == "up":
            v = {"model": "minescore:block/cable_anchor_floor", "y": Y_ROT[inp]}
        elif attach == "down":
            v = {"model": "minescore:block/cable_anchor_floor", "x": 180, "y": (Y_ROT[inp] + 180) % 360}
        else:
            mirrored = inp == CW[attach]
            v = {"model": f"minescore:block/cable_anchor_wall{'_mirrored' if mirrored else ''}", "y": Y_ROT[attach]}
        variants[f"facing={attach},input={inp}"] = {k: val for k, val in v.items() if val != 0 or k == "model"}
write(ASSETS / "blockstates" / "cable_anchor.json", {"variants": variants})
write(ASSETS / "models" / "item" / "cable_anchor.json", {"parent": "minescore:block/cable_anchor_floor"})
lang["block.minescore.cable_anchor"] = "Cable Anchor"

# Vinyl Display. Must match VinylDisplayBlock: a half-record plaque, front facing north, holding a record
# halfway in a slot along its top. On a wall it's flat against the wall to the south; on the floor it
# stands upright on a foot in the middle of the block.
png(ASSETS / "textures" / "block" / "vinyl_display_front.png", tx.vinyl_display_front(90).rows())
png(ASSETS / "textures" / "block" / "vinyl_display_edge.png", tx.vinyl_display_edge(91).rows())
png(ASSETS / "textures" / "block" / "vinyl_display_label.png", tx.vinyl_display_label(90).rows())
png(ASSETS / "textures" / "block" / "vinyl_display_top.png", tx.vinyl_display_top(91).rows())
DISPLAY_TEXTURES = {"particle": "minescore:block/vinyl_display_edge", "front": "minescore:block/vinyl_display_front",
                    "edge": "minescore:block/vinyl_display_edge", "top": "minescore:block/vinyl_display_top",
                    "bottom": "minescore:block/vinyl_display_bottom", "label": "minescore:block/vinyl_display_label"}


def display_plaque(z1, z2, both_sides):
    """The half-record plaque, with its label laid a hair in front of each face it shows, tinted (tint 0)."""
    sides = {"north": "#front", "south": "#front", "east": "#edge", "west": "#edge", "down": "#bottom"}
    rows = [([1, 6], "#top"), ([2, 4], "#edge"), ([4, 2], "#edge")]

    def uv(box, face):  # the same UVs as the face underneath
        return [round(v, 4) for v in frame_uv(box, face, FULL)]

    heights = {6: 9, 4: 6, 2: 4}
    elements = []
    for (x1, y1), up in rows:
        x2, y2 = 16 - x1, heights[y1]
        elements.append(element([x1, y1, z1, x2, y2, z2], {**sides, "up": up}))
        elements.append({"from": [x1, y1, z1 - 0.01], "to": [x2, y2, z1],
                         "faces": {"north": {"uv": uv([x1, y1, z1, x2, y2, z2], "north"), "texture": "#label", "tintindex": 0}}})
        if both_sides:
            elements.append({"from": [x1, y1, z2], "to": [x2, y2, z2 + 0.01],
                             "faces": {"south": {"uv": uv([x1, y1, z1, x2, y2, z2], "south"), "texture": "#label", "tintindex": 0}}})
    return elements


for kind, elements in (("wall", display_plaque(13, 16, False)),
                       ("floor", display_plaque(6.5, 9.5, True) + [element([4, 0, 5, 12, 2, 11], {
                           **{f: "#edge" for f in ("north", "south", "east", "west", "up")}, "down": "#bottom"})])):
    write(ASSETS / "models" / "block" / f"vinyl_display_{kind}.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": DISPLAY_TEXTURES,
        "elements": elements,
    })
write(ASSETS / "blockstates" / "vinyl_display.json", {"variants": {
    f"facing={f},floor={str(floor).lower()}": {k: v for k, v in
                                               {"model": f"minescore:block/vinyl_display_{'floor' if floor else 'wall'}",
                                                "y": Y_ROT[f]}.items() if v != 0 or k == "model"}
    for f in ("north", "east", "south", "west") for floor in (False, True)}})
write(ASSETS / "models" / "item" / "vinyl_display.json", {"parent": "minescore:block/vinyl_display_floor"})
lang["block.minescore.vinyl_display"] = "Vinyl Display"

# Music Cabinet. Must match MusicCabinetBlock: front facing north (z = 0), so the viewer's left is high x.
# A walnut shell, open at the front, with a middle shelf; Scores stand on the top shelf and record sleeves
# on the bottom one, each filling from the viewer's left. The blockstate counts each shelf.
CABINET_TEXTURES = {"particle": "minescore:block/walnut_planks", "side": "minescore:block/walnut_planks",
                    "inside": "minescore:block/music_cabinet_inside", "bottom": "minescore:block/music_cabinet_bottom",
                    "score": "minescore:block/music_cabinet_score", "pages": "minescore:block/music_cabinet_pages",
                    "sleeve": "minescore:block/music_cabinet_sleeve", "sleeve_side": "minescore:block/music_cabinet_sleeve_side",
                    "record": "minescore:block/music_cabinet_record", "label": "minescore:block/music_cabinet_record_label"}
CABINET_LEFT, CABINET_RIGHT = 14.5, 1.5
SCORE_SLOTS, VINYL_SLOTS = 16, 8
SCORE_WIDTH = (CABINET_LEFT - CABINET_RIGHT) / SCORE_SLOTS
VINYL_WIDTH = (CABINET_LEFT - CABINET_RIGHT) / VINYL_SLOTS


def spine_top(i):  # MusicCabinetBlock.spineTop
    return 13.0 + (i * 7 % 5) * 0.25


def cabinet_shell():
    """Two sides, a top, a bottom and a back, each softly rounded, with the middle shelf between."""
    def faces(inner, outer="#side", down="#side"):
        f = {face: "#side" for face in FACES}
        f["down"] = down
        f[inner] = "#inside"
        if outer:
            f[outer[0]] = outer[1]
        return f

    return (chamfered([0, 0, 0, 1.5, 16, 16], faces("east", None, "#bottom"))
            + chamfered([14.5, 0, 0, 16, 16, 16], faces("west", None, "#bottom"))
            + chamfered([1.5, 14.5, 0, 14.5, 16, 16], faces("down"))
            + chamfered([1.5, 0, 0, 14.5, 1.5, 16], faces("up", None, "#bottom"))
            + chamfered([1.5, 1.5, 14.5, 14.5, 14.5, 16], faces("north"))
            + [element([1.5, 7.25, 0.5, 14.5, 8.25, 14.5], {**{face: "#inside" for face in FACES}, "north": "#side"})])


def score_spines(n):
    elements = []
    for i in range(n):
        x2 = CABINET_LEFT - i * SCORE_WIDTH
        elements.append(element([x2 - SCORE_WIDTH + 0.06, 8.25, 2, x2 - 0.06, spine_top(i), 13.5],
                                {**{face: "#score" for face in FACES}, "up": "#pages"}))
    return elements


def sleeves(n):
    """Record sleeves on edge, tinted (tint = place) in their record's dye; through a hole in each side the
    record shows, and its label in the same dye."""
    elements = []
    for i in range(n):
        x2 = CABINET_LEFT - i * VINYL_WIDTH
        x1 = x2 - VINYL_WIDTH
        box = [x1 + 0.1, 1.5, 1.5, x2 - 0.1, 7.0, 13.5]
        sleeve = element(box, "#sleeve")
        for face, f in sleeve["faces"].items():
            f["tintindex"] = i
            if face in ("east", "west"):
                f["texture"], f["uv"] = "#sleeve_side", [0, 0, 16, 16]
        mid = (x1 + x2) / 2
        side = {"uv": [0, 0, 16, 16]}
        elements += [
            sleeve,
            {"from": [mid, 1.5, 1.5], "to": [mid, 7.0, 13.5],
             "faces": {f: {**side, "texture": "#record"} for f in ("east", "west")}},
            {"from": [mid - 0.03, 1.5, 1.5], "to": [mid + 0.03, 7.0, 13.5],
             "faces": {f: {**side, "texture": "#label", "tintindex": i} for f in ("east", "west")}},
        ]
    return elements


def cabinet_model(name, elements):
    write(ASSETS / "models" / "block" / f"{name}.json", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": CABINET_TEXTURES, "elements": elements})


cabinet_model("music_cabinet", cabinet_shell())
for n in range(1, SCORE_SLOTS + 1):
    cabinet_model(f"music_cabinet_scores_{n}", score_spines(n))
for n in range(1, VINYL_SLOTS + 1):
    cabinet_model(f"music_cabinet_vinyls_{n}", sleeves(n))
parts = []
for f in ("north", "east", "south", "west"):
    turn = {"y": Y_ROT[f]} if Y_ROT[f] else {}
    parts.append({"when": {"facing": f}, "apply": {"model": "minescore:block/music_cabinet", **turn}})
    parts += [{"when": {"facing": f, "scores": str(n)}, "apply": {"model": f"minescore:block/music_cabinet_scores_{n}", **turn}}
              for n in range(1, SCORE_SLOTS + 1)]
    parts += [{"when": {"facing": f, "vinyls": str(n)}, "apply": {"model": f"minescore:block/music_cabinet_vinyls_{n}", **turn}}
              for n in range(1, VINYL_SLOTS + 1)]
write(ASSETS / "blockstates" / "music_cabinet.json", {"multipart": parts})
# The item shows it part full, with five sleeves in MusicCabinetRenderer.ITEM_SLEEVES' colours.
write(ASSETS / "models" / "item" / "music_cabinet.json", {
    "parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": CABINET_TEXTURES,
    "elements": cabinet_shell() + score_spines(11) + sleeves(5)})
axe.append("minescore:music_cabinet")
lang["block.minescore.music_cabinet"] = "Music Cabinet"
lang["msg.minescore.music_cabinet.full"] = "That shelf is full"

# Development only (DevPower): an endless FE source for testing.
png(ASSETS / "textures" / "block" / "creative_power_source.png", tx.creative_power(92).rows())
write(ASSETS / "models" / "block" / "creative_power_source.json",
      {"parent": "minecraft:block/cube_all", "textures": {"all": "minescore:block/creative_power_source"}})
write(ASSETS / "blockstates" / "creative_power_source.json", {"variants": {"": {"model": "minescore:block/creative_power_source"}}})
write(ASSETS / "models" / "item" / "creative_power_source.json", {"parent": "minescore:block/creative_power_source"})
lang["block.minescore.creative_power_source"] = "Creative Power Source (dev only)"

# Files earlier versions generated that nothing uses now.
for stale in ("textures/block/jack_plate.png", "textures/block/speaker_back.png", "models/block/cable_anchor.json",
              "textures/block/disc_cutting_lathe_top.png", "textures/block/jack_in_lathe.png",
              "textures/block/jack_out_lathe.png", *[f"textures/block/jack_{d}_link_{s}.png" for d in ("in", "out")
                                                     for s in ("source", "channel_manager", "amplifier")]):
    if not PACK and (ASSETS / stale).exists():
        (ASSETS / stale).unlink()

for name in list(BLOCKS) + ["cable_anchor", "vinyl_display", "music_cabinet", "creative_power_source"]:
    write(DATA / "minescore" / "loot_tables" / "blocks" / f"{name}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"minescore:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    })

for name, (display, draw) in ITEMS.items():
    if draw is not None:
        png(ASSETS / "textures" / "item" / f"{name}.png", draw().rows())
    lang[f"item.minescore.{name}"] = display
    if name in ("blank_cd", "encoded_cd"):
        continue
    write(ASSETS / "models" / "item" / f"{name}.json",
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"minescore:item/{name}"}})

# Records: body, a dyeable label (tint 1) and, once trimmed, a pattern overlay tinted by the trim material (tint 2).
# The "minescore:trim" item property is (pattern index + 1) / 16, so each override matches one pattern.
png(ASSETS / "textures" / "item" / "cd_label_blank.png", tx.vinyl_label(47, printed=False).rows())
png(ASSETS / "textures" / "item" / "cd_label_encoded.png", tx.vinyl_label(48, printed=True).rows())
for i, pattern in enumerate(tx.TRIM_PATTERNS):
    png(ASSETS / "textures" / "item" / f"cd_trim_{pattern}.png", tx.vinyl_trim(i).rows())
for name, label in (("blank_cd", "cd_label_blank"), ("encoded_cd", "cd_label_encoded")):
    layers = {"layer0": f"minescore:item/{name}", "layer1": f"minescore:item/{label}"}
    overrides = []
    for i, pattern in enumerate(tx.TRIM_PATTERNS):
        trimmed = f"{name}_{pattern}_trim"
        write(ASSETS / "models" / "item" / f"{trimmed}.json", {"parent": "minecraft:item/generated", "textures": {
            **layers, "layer2": f"minescore:item/cd_trim_{pattern}"}})
        overrides.append({"predicate": {"minescore:trim": round((i + 1) / 16, 4)}, "model": f"minescore:item/{trimmed}"})
    write(ASSETS / "models" / "item" / f"{name}.json",
          {"parent": "minecraft:item/generated", "textures": layers, "overrides": overrides})

png(ASSETS / "textures" / "gui" / "disc_cutting_lathe.png", tx.gui_lathe().rows())
png(ASSETS / "textures" / "gui" / "mp3_player.png", tx.gui_mp3().rows())
png(ASSETS / "textures" / "gui" / "amplifier.png", tx.gui_amplifier().rows())
png(ASSETS / "textures" / "gui" / "channel_manager.png", tx.gui_channel_manager().rows())
png(ASSETS / "textures" / "gui" / "speaker.png", tx.gui_speaker().rows())
png(ASSETS / "textures" / "gui" / "multi_effects_unit.png", tx.gui_meu().rows())
png(ASSETS / "textures" / "gui" / "writers_desk.png", tx.gui_desk().rows())
png(ASSETS / "textures" / "gui" / "manual.png", tx.gui_manual().rows())
# The score editor's notation symbols (SCOPE.md §9): the atlas, and where each symbol sits in it for ScoreGlyphs.
import glyphs
glyph_atlas, glyph_layout = glyphs.atlas()
png(ASSETS / "textures" / "gui" / "score_glyphs.png", glyph_atlas.rows())
write(ASSETS / "score_glyphs.json", glyph_layout)  # in assets, so a resource pack can replace it along with the atlas

# Particles (ModParticles): their textures, and the list of frames for each.
PARTICLES = {"note": (tx.particle_note, 2), "shaving": (tx.particle_shaving, 2), "shard": (tx.particle_shard, 3),
             "spark": (tx.particle_spark, 2), "smoke": (tx.particle_smoke, 4)}
for name, (draw, frames) in PARTICLES.items():
    for i in range(frames):
        png(ASSETS / "textures" / "particle" / f"{name}_{i}.png", draw(i).rows())
    write(ASSETS / "particles" / f"{name}.json", {"textures": [f"minescore:{name}_{i}" for i in range(frames)]})

# Advancements (SCOPE.md §14): one tree, every criterion the minescore:event trigger (ModAdvancements) but the root.
# id: (parent, icon, frame, title, description, events: all must happen)
ADVANCEMENTS = {
    "fresh_cut": ("root", "minescore:encoded_cd", "task", "Fresh Cut", "Cut your first record", ["fresh_cut"]),
    "bootleg": ("fresh_cut", "minescore:blank_cd", "task", "Bootleg", "Copy a record", ["bootleg"]),
    "custom_pressing": ("fresh_cut", "minecraft:red_dye", "task", "Custom Pressing", "Dye and trim a record", ["custom_pressing"]),
    "throwback": ("fresh_cut", "minescore:vinyl_player", "task", "Throwback", "Play a Vinyl Record on a Vinyl Player", ["throwback"]),
    "overclocked": ("fresh_cut", "minecraft:clock", "task", "Overclocked", "Break a record by spinning the Lathe above 64 RPM",
                    ["overclocked"]),
    "mixtape": ("fresh_cut", "minescore:mp3_player", "task", "Mixtape", "Load 10 songs into an MP3 Player", ["mixtape"]),
    "bluetooth": ("mixtape", "minescore:tweeter_speaker", "task", "Bluetooth", "Play your MP3 Player through a single speaker",
                  ["bluetooth"]),
    "plugged_in": ("root", "minescore:speaker_wire_16", "task", "Plugged In", "Wire your first speaker", ["plugged_in"]),
    "full_range": ("plugged_in", "minescore:subwoofer", "goal", "Full Range", "Have all three speaker types playing at once",
                   ["full_range"]),
    "daisy_chain": ("full_range", "minescore:middle_speaker", "goal", "Daisy Chain", "Chain 10 speakers", ["daisy_chain"]),
    "power_budget": ("full_range", "minecraft:redstone_block", "challenge", "Power Budget", "Run a system that draws 500 FE/t",
                     ["power_budget"]),
    "stereo": ("plugged_in", "minescore:channel_manager", "task", "Stereo", "Set up a Left and a Right speaker",
               ["stereo_left", "stereo_right"]),
    "too_loud": ("plugged_in", "minescore:amplifier", "task", "Too Loud!", "Short a speaker", ["too_loud"]),
    "redstone_dj": ("plugged_in", "minecraft:comparator", "task", "Redstone DJ", "Control a hub with redstone", ["redstone_dj"]),
    "pedalboard": ("plugged_in", "minescore:multi_effects_unit", "goal", "Pedalboard", "Fill all six slots of a Multi-Effects Unit",
                   ["pedalboard"]),
    "slowed_reverb": ("pedalboard", "minescore:card_reverb", "challenge", "Slowed + Reverb", "Play a song slowed down with reverb on",
                      ["slowed_reverb"]),
    "composer": ("root", "minescore:score", "task", "Composer", "Name a score", ["composer"]),
    "duet": ("composer", "minescore:writers_desk", "goal", "Duet", "Edit a score with another player", ["duet"]),
    "pressed_score": ("composer", "minescore:encoded_cd", "goal", "Pressed Score", "Cut a score onto a record", ["pressed_score"]),
}
png(ASSETS / "textures" / "gui" / "advancements.png", tx.planks(216).rows())
ADV = DATA / "minescore" / "advancements"
lang["advancements.minescore.root.title"] = "MineScore"
lang["advancements.minescore.root.description"] = "Hi-fi, records and music of your own"
write(ADV / "root.json", {
    "display": {"icon": {"item": "minescore:manual"}, "title": {"translate": "advancements.minescore.root.title"},
                "description": {"translate": "advancements.minescore.root.description"},
                "background": "minescore:textures/gui/advancements.png", "show_toast": False, "announce_to_chat": False},
    "criteria": {"has_music": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [
        "minescore:blank_cd", "minescore:encoded_cd", "minescore:mp3_player", "minescore:manual", "minescore:blank_score",
        "minescore:speaker_wire_12", "minescore:speaker_wire_16", "minescore:speaker_wire_20"]}]}}},
})
for name, (parent, icon, frame, title, description, events) in ADVANCEMENTS.items():
    lang[f"advancements.minescore.{name}.title"] = title
    lang[f"advancements.minescore.{name}.description"] = description
    write(ADV / f"{name}.json", {
        "parent": f"minescore:{parent}",
        "display": {"icon": {"item": icon}, "title": {"translate": f"advancements.minescore.{name}.title"},
                    "description": {"translate": f"advancements.minescore.{name}.description"}, "frame": frame},
        "criteria": {e: {"trigger": "minescore:event", "conditions": {"event": e}} for e in events},
        "requirements": [[e] for e in events],
    })

# Records take armour trims at a smithing table, and a furnace erases them.
write(DATA / "minecraft" / "tags" / "items" / "trimmable_armor.json",
      {"replace": False, "values": ["minescore:blank_cd", "minescore:encoded_cd"]})
write(DATA / "minescore" / "recipes" / "erase_encoded_cd.json", {
    "type": "minecraft:smelting", "category": "misc", "ingredient": {"item": "minescore:encoded_cd"},
    "result": "minescore:blank_cd", "experience": 0.35, "cookingtime": 200})

# Crafting recipes, designed in tools/recipe_maker.py and exported to tools/recipes_export.json
# (2026-11-20). Each gets a recipe-book unlock: once the player has any of its ingredients. A shaped recipe that's
# only another one flipped left to right is skipped: Minecraft already accepts every shaped recipe mirrored.
EXPORT = ROOT / "tools" / "recipes_export.json"
RECIPES = DATA / "minescore" / "recipes"
UNLOCKS = DATA / "minescore" / "advancements" / "recipes"
if EXPORT.exists() and not PACK:
    made_now = {"erase_encoded_cd"}  # written above
    for entry in json.loads(EXPORT.read_text(encoding="utf-8"))["items"]:
        shapes = []
        for made in entry["recipes"]:
            recipe = made["recipe"]
            if recipe["type"] == "minecraft:crafting_shaped":
                if any(_same_mirrored(recipe, p, k) for p, k in shapes):
                    continue
                shapes.append((recipe["pattern"], recipe["key"]))
            name = made["id"].split(":", 1)[1]
            made_now.add(name)
            write(RECIPES / f"{name}.json", recipe)
            if made["unlock_with"]:
                has = {f"has_{i}": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [
                    {"tag": ref[1:]} if ref.startswith("#") else {"items": [ref]}]}} for i, ref in enumerate(made["unlock_with"])}
                write(UNLOCKS / f"{name}.json", {
                    "parent": "minecraft:recipes/root",
                    "criteria": {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": made["id"]}},
                                 **has},
                    "requirements": [["has_the_recipe", *has]],
                    "rewards": {"recipes": [made["id"]]},
                })
    for stale in list(RECIPES.glob("*.json")) + list(UNLOCKS.glob("*.json")):  # recipes since removed in the maker
        if stale.stem not in made_now:
            stale.unlink()

write(DATA / "minecraft" / "tags" / "blocks" / "mineable" / "pickaxe.json", {"replace": False, "values": pickaxe})
write(DATA / "minecraft" / "tags" / "blocks" / "mineable" / "axe.json", {"replace": False, "values": axe})
write(ASSETS / "lang" / "en_us.json", dict(sorted(lang.items())))

# Every other language (tools/i18n.py, translations in tools/lang): the game's lang file and a translated Manual.
if not PACK:
    import i18n
    if not i18n.DATA_TEXT.exists():
        print("  note: run the Gradle build first (common/build/minescore-text.json), or some screen text stays English")
    english_text = i18n.english()
    for code, file in i18n.LANGUAGES.items():
        tr = i18n.translation(file)
        if not tr:
            continue
        for path, obj in ((ASSETS / "lang" / f"{code}.json", i18n.lang_file(english_text, tr)),
                          (ASSETS / "minescore" / "manual" / f"{code}.json", i18n.manual_file(english_text, tr))):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(obj, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  translations: {len(i18n.LANGUAGES)} languages")

write(ASSETS / "minescore" / "theme.json", THEMES["minecraft"])
write(ASSETS / "minescore" / "sound_style.json", {"style": "minecraft"})

# The built-in sound packs: just a sound style for SynthSounds, made in code from it.
SOUND_PACKS = {"minescore_sounds_retro": ("retro", "MineScore Sounds: Retro", "Warm, mechanical, a touch of mains hum"),
               "minescore_sounds_studio": ("studio", "MineScore Sounds: Studio", "Clean, short and precise")}
if not PACK:
    for pack, (style, name, description) in SOUND_PACKS.items():
        root = RES / "resourcepacks" / pack
        if root.exists():
            shutil.rmtree(root)
        (root / "assets" / "minescore" / "minescore").mkdir(parents=True)
        (root / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": description}}, indent=2) + "\n",
                                          encoding="utf-8")
        (root / "assets" / "minescore" / "minescore" / "sound_style.json").write_text(json.dumps({"style": style}) + "\n",
                                                                                         encoding="utf-8")
        (root / "pack.png").write_bytes(png_bytes(tx.sound_pack_icon(style).rows()))

if not PACK:  # the Mods list's logo (mods.toml logoFile), at the root of the Forge jar
    png_rows = tx.logo().rows()[:64]
    (ROOT / "forge" / "src" / "main" / "resources" / "minescore_logo.png").write_bytes(png_bytes(png_rows))
    # The square icon for mod pages and the repo, blown up 8 x with hard pixel edges (512 x 512).
    small = tx.icon().rows()
    big = [[p for p in row for _ in range(8)] for row in small for _ in range(8)]
    (ROOT / "branding").mkdir(exist_ok=True)
    (ROOT / "branding" / "icon.png").write_bytes(png_bytes(big))
    (ROOT / "branding" / "icon_64.png").write_bytes(png_bytes(small))

if PACK:
    style, half, name, description = PACKS[PACK]
    PACK_ROOT.mkdir(parents=True, exist_ok=True)
    (PACK_ROOT / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": description}}, indent=2,
                                                      ensure_ascii=False) + "\n", encoding="utf-8")
    if style in THEMES and style != "minecraft":
        theme = PACK_ROOT / "assets" / "minescore" / "minescore" / "theme.json"
        theme.parent.mkdir(parents=True, exist_ok=True)
        theme.write_text(json.dumps(THEMES[style], indent=2) + "\n", encoding="utf-8")
    icon = tx.pack_icon(style, half)
    (PACK_ROOT / "pack.png").write_bytes(png_bytes(icon.rows()))
    print(f"  {name}: {sum(1 for _ in PACK_ROOT.rglob('*.png'))} textures")
else:
    print(f"Generated resources for {len(BLOCKS) + 1} blocks, {len(ITEMS)} items")
    for pack, (style, *_rest) in PACKS.items():
        subprocess.run([sys.executable, __file__], check=True,
                       env={**os.environ, "MINESCORE_PACK": pack, "MINESCORE_STYLE": style})
