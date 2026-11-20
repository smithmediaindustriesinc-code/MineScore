"""MineScore recipe maker: a temporary helper for designing the mod's recipes.

Run:  python tools/recipe_maker.py

Left: every MineScore item. Pick one, build its recipes in the middle, then press Confirm to lock it (it greys
out with a green tick; Unlock to change it again). Right: every MineScore and vanilla item, plus item tags
("any planks"). Click one to pick it up, then click grid slots to place it.

Grid controls:
  left-click            put the picked-up ingredient in the slot (replaces what's there)
  shift + left-click    add it as another choice for that slot (any of them will do)
  right-click           empty the slot
  ctrl + left-click     toggle "comes back": the item stays in the grid after crafting (like a tool)

Work is saved as you go to tools/recipe_maker_session.json. Export writes tools/recipes_export.json (everything,
read by gen_resources.py) and a ready-to-use datapack folder beside it.
"""
import base64
import copy
import glob
import io
import json
import os
import re
import sys
import zipfile
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
MOD_ASSETS = ROOT / "common" / "src" / "main" / "resources" / "assets" / "minescore"
MOD_DATA = ROOT / "common" / "src" / "main" / "resources" / "data" / "minescore"
SESSION = TOOLS / "recipe_maker_session.json"
DEFAULT_EXPORT = TOOLS / "recipes_export.json"
GRADLE = Path.home() / ".gradle" / "caches"

TYPES = {  # key: (label, input slots used, has count, has cooking settings)
    "shaped": ("Crafting table: shaped", list(range(9)), True, False),
    "shapeless": ("Crafting table: formless (any arrangement)", list(range(9)), True, False),
    "smelting": ("Furnace", [4], False, True),
    "blasting": ("Blast furnace", [4], False, True),
    "smoking": ("Smoker", [4], False, True),
    "campfire": ("Campfire", [4], False, True),
    "stonecutting": ("Stonecutter", [4], True, False),
    "smithing": ("Smithing table (template, base, addition)", [3, 4, 5], False, False),
}
TYPE_BY_LABEL = {v[0]: k for k, v in TYPES.items()}
COOK_DEFAULTS = {"smelting": 200, "blasting": 100, "smoking": 100, "campfire": 600}
CATEGORIES = {"crafting": ["misc", "building", "redstone", "equipment"], "cooking": ["misc", "blocks", "food"]}
SMITHING_NAMES = {3: "template", 4: "base", 5: "addition"}


def new_recipe(kind="shaped"):
    return {"type": kind, "grid": [None] * 9, "count": 1, "group": "", "category": "misc", "mirror": True,
            "xp": 0.1, "time": COOK_DEFAULTS.get(kind, 200), "requires_mod": "", "unlock": True, "notes": ""}


# --- game data ------------------------------------------------------------------------------------

class Assets:
    """Reads models, textures, names and tags from the Minecraft client jar and the mod's own resources."""

    def __init__(self, jar_path):
        self.jar = zipfile.ZipFile(jar_path)
        self.names = set(self.jar.namelist())
        self.lang = json.loads(self.jar.read("assets/minecraft/lang/en_us.json"))
        mod_lang = MOD_ASSETS / "lang" / "en_us.json"
        self.lang.update(json.loads(mod_lang.read_text(encoding="utf-8")))
        self.extra_tag_jars = [zipfile.ZipFile(p) for p in glob.glob(str(GRADLE / "modules-2" / "files-2.1" / "net.minecraftforge"
                                                                           / "forge" / "1.20.1-*" / "*" / "forge-1.20.1-*-universal.jar"))]

    def read(self, ns, path):
        if ns == "minescore":
            f = MOD_ASSETS.parent.parent / "assets" / ns / path
            return f.read_bytes() if f.exists() else None
        name = f"assets/{ns}/{path}"
        return self.jar.read(name) if name in self.names else None

    def name_of(self, item_id):
        ns, path = item_id.split(":")
        for key in (f"item.{ns}.{path}", f"block.{ns}.{path}"):
            if key in self.lang:
                return self.lang[key]
        return path.replace("_", " ").title()

    def vanilla_items(self):
        out = []
        for n in self.names:
            m = re.match(r"assets/minecraft/models/item/([a-z0-9_]+)\.json$", n)
            if m and m.group(1) != "air" and (f"item.minecraft.{m.group(1)}" in self.lang or f"block.minecraft.{m.group(1)}" in self.lang):
                out.append("minecraft:" + m.group(1))
        return sorted(out, key=self.name_of)

    def mod_items(self):
        out = []
        for f in sorted((MOD_ASSETS / "models" / "item").glob("*.json")):
            path = f.stem
            if (f"item.minescore.{path}" in self.lang or f"block.minescore.{path}" in self.lang) and not path.endswith("_trim"):
                out.append("minescore:" + path)
        return out

    def tags(self):
        """Every item tag, fully expanded: '#ns:path' -> set of item ids."""
        raw = {}
        sources = [(self.jar, n) for n in self.names if n.startswith("data/") and "/tags/items/" in n]
        for j in self.extra_tag_jars:
            sources += [(j, n) for n in j.namelist() if n.startswith("data/") and "/tags/items/" in n and n.endswith(".json")]
        for j, n in sources:
            m = re.match(r"data/([^/]+)/tags/items/(.+)\.json$", n)
            if not m:
                continue
            values = json.loads(j.read(n)).get("values", [])
            raw.setdefault(f"#{m.group(1)}:{m.group(2)}", []).extend(v["id"] if isinstance(v, dict) else v for v in values)
        done = {}

        def expand(tag, seen=()):
            if tag in done:
                return done[tag]
            items = set()
            for v in raw.get(tag, []):
                if v.startswith("#"):
                    if v not in seen:
                        items |= expand(v, seen + (tag,))
                else:
                    items.add(v)
            done[tag] = items
            return items

        for t in raw:
            expand(t)
        return {t: s for t, s in done.items() if s}

    def vanilla_recipes(self):
        out = []
        for n in self.names:
            if n.startswith("data/minecraft/recipes/") and n.endswith(".json"):
                r = json.loads(self.jar.read(n))
                if r.get("type") in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
                    out.append((n.rsplit("/", 1)[1][:-5], r))
        return out

    # icons -----------------------------------------------------------------------------------

    def _model(self, ref, depth=0):
        """(textures, is_flat_item) for a model reference, following parents."""
        if depth > 12:
            return {}, False
        ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
        if path in ("item/generated", "item/handheld", "builtin/generated"):
            return {}, True
        data = self.read(ns, f"models/{path}.json")
        if data is None:
            return {}, False
        model = json.loads(data)
        textures, flat = ({}, False)
        if "parent" in model:
            textures, flat = self._model(model["parent"], depth + 1)
        textures = {**textures, **model.get("textures", {})}
        return textures, flat

    def texture_for(self, item_id):
        ns, path = item_id.split(":")
        textures, flat = self._model(f"{ns}:item/{path}")

        def resolve(v, n=0):
            while isinstance(v, str) and v.startswith("#") and n < 10:
                v, n = textures.get(v[1:]), n + 1
            return v

        order = ["layer0"] if flat else ["layer0", "front", "all", "side", "top", "texture", "end", "cross", "particle"]
        for key in order + list(textures):
            v = resolve(textures.get(key))
            if isinstance(v, str) and not v.startswith("#"):
                tns, tpath = v.split(":") if ":" in v else ("minecraft", v)
                data = self.read(tns, f"textures/{tpath}.png")
                if data:
                    return data
        return None


# --- the app --------------------------------------------------------------------------------------

class Scrollable(ttk.Frame):
    """A frame whose contents scroll up and down (wheel or bar) when the window's too short for them."""

    def __init__(self, parent):
        super().__init__(parent)
        bg = ttk.Style().lookup("TFrame", "background")
        self.canvas = tk.Canvas(self, highlightthickness=0, bg=bg)
        bar = ttk.Scrollbar(self, orient="vertical", command=self.canvas.yview)
        self.inner = ttk.Frame(self.canvas, padding=8)
        window = self.canvas.create_window(0, 0, window=self.inner, anchor="nw")
        self.inner.bind("<Configure>", lambda e: self.canvas.configure(scrollregion=self.canvas.bbox("all")))
        self.canvas.bind("<Configure>", lambda e: self.canvas.itemconfigure(window, width=e.width))
        self.canvas.configure(yscrollcommand=bar.set)
        bar.pack(side="right", fill="y")
        self.canvas.pack(side="left", fill="both", expand=True)
        self.bind("<Enter>", lambda e: self.bind_all("<MouseWheel>", self._wheel))
        self.bind("<Leave>", lambda e: self.unbind_all("<MouseWheel>"))

    def _wheel(self, event):
        if self.canvas.yview() != (0.0, 1.0) and not isinstance(event.widget, tk.Text):
            self.canvas.yview_scroll(int(-event.delta / 120), "units")


class RecipeMaker:
    def __init__(self, root, assets):
        self.root = root
        self.assets = assets
        root.title("MineScore Recipe Maker")
        root.geometry("1240x720")
        root.minsize(860, 480)
        ttk.Style().theme_use("clam")

        self.mod_items = assets.mod_items()
        self.vanilla_items = assets.vanilla_items()
        self.tags = assets.tags()
        self.vanilla_recipes = assets.vanilla_recipes()
        self.icons16, self.icons32 = {}, {}
        self.check_icon = self._check_icon()
        self.session = self._load_session()
        self.current = None  # output item id
        self.recipe_index = 0
        self.brush = None
        self.loading = False

        self._build()
        self._fill_outputs()
        self._fill_ingredients()
        if self.mod_items:
            self.outputs.selection_set(self.mod_items[0])

    # session ---------------------------------------------------------------------------------

    def _load_session(self):
        data = {"version": 1, "outputs": {}}
        if SESSION.exists():
            try:
                data = json.loads(SESSION.read_text(encoding="utf-8"))
            except (OSError, ValueError):
                messagebox.showwarning("Recipe Maker", f"Couldn't read {SESSION.name}; starting fresh (the old file is kept as .bak).")
                SESSION.replace(SESSION.with_suffix(".json.bak"))
        for item in self.mod_items:
            data["outputs"].setdefault(item, {"confirmed": False, "no_recipe": False, "notes": "", "recipes": []})
        self._preload_existing(data)
        return data

    def _preload_existing(self, data):
        """The mod's recipes that already exist (the furnace eraser), once, so they show up as made."""
        if data.get("preloaded"):
            return
        for f in sorted(MOD_DATA.glob("recipes/*.json")):
            r = json.loads(f.read_text(encoding="utf-8"))
            kind = {"minecraft:smelting": "smelting", "minecraft:blasting": "blasting", "minecraft:smoking": "smoking",
                    "minecraft:campfire_cooking": "campfire"}.get(r.get("type"))
            result = r.get("result")
            if kind and isinstance(result, str) and result in data["outputs"]:
                recipe = new_recipe(kind)
                recipe["grid"][4] = {"choices": [r["ingredient"].get("item") or "#" + r["ingredient"]["tag"]], "keep": False}
                recipe["xp"], recipe["time"] = r.get("experience", 0.1), r.get("cookingtime", 200)
                recipe["notes"] = f"Already in the mod ({f.stem})."
                recipe["existing_id"] = f.stem  # exported under its own name, so it replaces rather than duplicates
                data["outputs"][result]["recipes"].append(recipe)
        data["preloaded"] = True

    def save(self):
        tmp = SESSION.with_suffix(".tmp")
        tmp.write_text(json.dumps(self.session, indent=1), encoding="utf-8")
        tmp.replace(SESSION)

    # icons -----------------------------------------------------------------------------------

    def _check_icon(self):
        img = tk.PhotoImage(width=16, height=16)
        for x, y in [(3, 8), (4, 9), (5, 10), (6, 11), (7, 10), (8, 9), (9, 8), (10, 7), (11, 6), (12, 5), (13, 4)]:
            for dy in (0, 1):
                img.put("#2ea043", (x, y + dy))
        return img

    def icon(self, ref, size=16):
        cache = self.icons16 if size == 16 else self.icons32
        if ref in cache:
            return cache[ref]
        item = ref
        if ref.startswith("#"):
            members = sorted(self.tags.get(ref, []))
            item = members[0] if members else None
        img = None
        data = self.assets.texture_for(item) if item else None
        if data:
            try:
                full = tk.PhotoImage(data=base64.b64encode(data).decode())
                w = full.width()
                square = tk.PhotoImage(width=w, height=w)
                square.tk.call(square, "copy", full, "-from", 0, 0, w, w)  # animated textures: the first frame
                img = square
                if w > size:
                    img = img.subsample(max(1, w // size))
                elif w < size:
                    img = img.zoom(max(1, size // w))
            except tk.TclError:
                img = None
        cache[ref] = img
        return img

    def label_of(self, ref):
        if ref.startswith("#"):
            return f"Any {ref.split(':', 1)[1].replace('_', ' ').replace('/', ' / ')} (tag, {len(self.tags.get(ref, []))} items)"
        return self.assets.name_of(ref)

    # layout ----------------------------------------------------------------------------------

    def _build(self):
        panes = ttk.PanedWindow(self.root, orient="horizontal")
        panes.pack(fill="both", expand=True)

        # outputs
        left = ttk.Frame(panes, padding=6)
        ttk.Label(left, text="MineScore items", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        self.progress = ttk.Label(left, text="")
        self.progress.pack(anchor="w")
        listf = ttk.Frame(left)
        listf.pack(fill="both", expand=True)
        self.outputs = ttk.Treeview(listf, columns=("recipes",), show="tree headings", selectmode="browse")
        self.outputs.heading("#0", text="Item")
        self.outputs.heading("recipes", text="Recipes")
        self.outputs.column("#0", width=200)
        self.outputs.column("recipes", width=56, anchor="center")
        self.outputs.tag_configure("done", foreground="#9a9a9a")
        bar = ttk.Scrollbar(listf, command=self.outputs.yview)
        self.outputs.configure(yscrollcommand=bar.set)
        bar.pack(side="right", fill="y")
        self.outputs.pack(side="left", fill="both", expand=True)
        self.outputs.bind("<<TreeviewSelect>>", lambda e: self._select_output())
        panes.add(left, weight=1)

        # editor: scrolls when the window's too short for it
        scroller = Scrollable(panes)
        panes.add(scroller, weight=3)
        mid = scroller.inner
        head = ttk.Frame(mid)
        head.pack(fill="x")
        self.title = ttk.Label(head, text="", font=("Segoe UI", 13, "bold"))
        self.title.pack(side="left")
        self.confirm_btn = ttk.Button(head, text="Confirm", command=self._toggle_confirm, width=9)
        self.confirm_btn.pack(side="right")
        ttk.Button(head, text="Export…", command=self.export, width=8).pack(side="right", padx=4)
        ttk.Button(head, text="Clashes", command=self._check_clashes_dialog, width=8).pack(side="right")

        self.no_recipe = tk.BooleanVar()
        self.no_recipe_box = ttk.Checkbutton(mid, text="No crafting recipe (made another way, or creative only)",
                                             variable=self.no_recipe, command=self._edited)
        self.no_recipe_box.pack(anchor="w", pady=(4, 0))

        bar = ttk.Frame(mid)
        bar.pack(fill="x", pady=(6, 2))
        self.recipe_pick = ttk.Combobox(bar, state="readonly", width=20)
        self.recipe_pick.pack(side="left")
        self.recipe_pick.bind("<<ComboboxSelected>>", lambda e: self._show_recipe(self.recipe_pick.current()))
        self.add_btn = ttk.Button(bar, text="+ Add", command=self._add_recipe, width=6)
        self.add_btn.pack(side="left", padx=(4, 0))
        self.dup_btn = ttk.Button(bar, text="Copy", command=self._duplicate_recipe, width=6)
        self.dup_btn.pack(side="left", padx=2)
        self.del_btn = ttk.Button(bar, text="Delete", command=self._delete_recipe, width=7)
        self.del_btn.pack(side="left")
        self.type_pick = ttk.Combobox(mid, state="readonly", width=44, values=[v[0] for v in TYPES.values()])
        self.type_pick.pack(anchor="w", pady=2)
        self.type_pick.bind("<<ComboboxSelected>>", lambda e: self._change_type())

        # the grid, the result, and what you're holding, side by side
        work = ttk.Frame(mid)
        work.pack(fill="x", pady=6)
        self.blank = tk.PhotoImage(width=32, height=32)
        gridf = tk.Frame(work, bg="#8b8b8b", padx=3, pady=3)
        gridf.pack(side="left", anchor="n")
        self.slots = []
        for i in range(9):
            lbl = tk.Label(gridf, image=self.blank, width=34, height=34, bg="#c6c6c6", relief="sunken", bd=2, compound="center",
                           font=("Segoe UI", 7, "bold"), fg="#1f4fa0", cursor="hand2")
            lbl.grid(row=i // 3, column=i % 3, padx=1, pady=1)
            lbl.bind("<ButtonPress-1>", lambda e, i=i: self._slot_press(i, e))
            lbl.bind("<B1-Motion>", lambda e: self._slot_drag(e, clear=False))
            lbl.bind("<ButtonPress-3>", lambda e, i=i: self._slot_press_clear(i))
            lbl.bind("<B3-Motion>", lambda e: self._slot_drag(e, clear=True))
            lbl.bind("<Enter>", lambda e, i=i: self._slot_hover(i))
            lbl.bind("<Leave>", lambda e: setattr(self, "hovered", None))
            self.slots.append(lbl)
        ttk.Label(work, text="➜", font=("Segoe UI", 16)).pack(side="left", padx=6, anchor="n", pady=40)
        outf = ttk.Frame(work)
        outf.pack(side="left", anchor="n", pady=28)
        self.result = tk.Label(outf, image=self.blank, width=40, height=40, bg="#c6c6c6", relief="sunken", bd=2)
        self.result.pack()
        countrow = ttk.Frame(outf)
        countrow.pack(pady=3)
        ttk.Label(countrow, text="×").pack(side="left")
        self.count = tk.IntVar(value=1)
        self.count_box = ttk.Spinbox(countrow, from_=1, to=64, width=3, textvariable=self.count, command=self._edited)
        self.count_box.pack(side="left", padx=2)

        hand = ttk.LabelFrame(work, text="Holding", padding=4)
        hand.pack(side="left", fill="both", expand=True, padx=(12, 0), anchor="n")
        self.brush_label = ttk.Label(hand, text="nothing yet: click an ingredient on the right", compound="left", wraplength=200)
        self.brush_label.pack(anchor="w")
        ttk.Label(hand, text="Recent", foreground="#666").pack(anchor="w", pady=(6, 0))
        self.recent_row = ttk.Frame(hand)
        self.recent_row.pack(anchor="w")

        self.hover = ttk.Label(mid, text="Click: place · Shift-click: add a choice · Right-click: empty · Ctrl-click: comes back · "
                                         "drag to paint several · 1–9: place in that slot",
                               foreground="#555", wraplength=520)
        self.hover.pack(anchor="w", pady=(0, 6))
        mid.bind("<Configure>", lambda e: self.hover.configure(wraplength=max(200, e.width - 20)))

        opts = ttk.LabelFrame(mid, text="Options", padding=6)
        opts.pack(fill="x")
        self.mirror = tk.BooleanVar(value=True)
        self.mirror_box = ttk.Checkbutton(opts, text="Can be crafted mirrored", variable=self.mirror, command=self._edited)
        self.mirror_box.grid(row=0, column=0, columnspan=2, sticky="w")
        self.unlock = tk.BooleanVar(value=True)
        self.unlock_box = ttk.Checkbutton(opts, text="Recipe book unlocks with an ingredient", variable=self.unlock,
                                          command=self._edited)
        self.unlock_box.grid(row=0, column=2, columnspan=2, sticky="w", padx=(12, 0))
        self.group = tk.StringVar()
        self.requires = tk.StringVar()
        self.xp = tk.DoubleVar(value=0.1)
        self.time = tk.IntVar(value=200)
        ttk.Label(opts, text="Book group").grid(row=1, column=0, sticky="w", pady=2)
        self.group_entry = ttk.Entry(opts, textvariable=self.group, width=14)
        self.group_entry.grid(row=1, column=1, sticky="w", padx=4)
        ttk.Label(opts, text="Book tab").grid(row=1, column=2, sticky="w", padx=(12, 0))
        self.category = ttk.Combobox(opts, state="readonly", width=11)
        self.category.grid(row=1, column=3, sticky="w", padx=4)
        self.category.bind("<<ComboboxSelected>>", lambda e: self._edited())
        ttk.Label(opts, text="Needs mod").grid(row=2, column=0, sticky="w", pady=2)
        self.requires_entry = ttk.Entry(opts, textvariable=self.requires, width=14)
        self.requires_entry.grid(row=2, column=1, sticky="w", padx=4)
        ttk.Label(opts, text="XP / ticks").grid(row=2, column=2, sticky="w", padx=(12, 0))
        cook = ttk.Frame(opts)
        cook.grid(row=2, column=3, sticky="w", padx=4)
        self.xp_box = ttk.Spinbox(cook, from_=0, to=100, increment=0.05, width=5, textvariable=self.xp, command=self._edited)
        self.xp_box.pack(side="left")
        self.time_box = ttk.Spinbox(cook, from_=1, to=10000, width=6, textvariable=self.time, command=self._edited)
        self.time_box.pack(side="left", padx=(4, 0))
        ttk.Label(opts, text="Book group: recipes sharing one share a recipe book slot. Needs mod: a mod id like create, "
                             "blank = always.", foreground="#777", wraplength=480).grid(row=3, column=0, columnspan=4, sticky="w", pady=(4, 0))
        for v in (self.group, self.requires, self.xp, self.time, self.count):
            v.trace_add("write", lambda *a: self._edited())

        notes = ttk.LabelFrame(mid, text="Notes", padding=6)
        notes.pack(fill="x", pady=(8, 0))
        ttk.Label(notes, text="This recipe", foreground="#666").pack(anchor="w")
        self.recipe_notes = tk.Text(notes, height=2, wrap="word", width=40)
        self.recipe_notes.pack(fill="x")
        ttk.Label(notes, text="This item", foreground="#666").pack(anchor="w", pady=(4, 0))
        self.item_notes = tk.Text(notes, height=2, wrap="word", width=40)
        self.item_notes.pack(fill="x")
        for t in (self.recipe_notes, self.item_notes):
            t.bind("<KeyRelease>", lambda e: self._edited())

        # ingredients
        right = ttk.Frame(panes, padding=6)
        panes.add(right, weight=2)
        ttk.Label(right, text="Ingredients", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        self.search = tk.StringVar()
        self.search_entry = ttk.Entry(right, textvariable=self.search)
        self.search_entry.pack(fill="x", pady=4)
        self.search_entry.bind("<Return>", lambda e: self._pick_top_result())
        self.search_entry.bind("<Down>", lambda e: self._focus_results())
        self.search.trace_add("write", lambda *a: self._fill_ingredients())
        filt = ttk.Frame(right)
        filt.pack(fill="x")
        self.filter = tk.StringVar(value="all")
        for value, text in (("all", "All"), ("mod", "MineScore"), ("vanilla", "Minecraft"), ("tags", "Tags")):
            ttk.Radiobutton(filt, text=text, value=value, variable=self.filter, command=self._fill_ingredients).pack(side="left")
        self.ingredients = ttk.Treeview(right, columns=("id",), show="tree headings", selectmode="browse")
        self.ingredients.heading("#0", text="Name")
        self.ingredients.heading("id", text="ID")
        self.ingredients.column("#0", width=220)
        self.ingredients.column("id", width=170)
        scroll = ttk.Scrollbar(right, command=self.ingredients.yview)
        self.ingredients.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right", fill="y")
        self.ingredients.pack(fill="both", expand=True)
        self.ingredients.bind("<<TreeviewSelect>>", lambda e: self._pick_brush())
        self.ingredients.bind("<Double-1>", lambda e: self._place_in_first_empty())

        self.status = ttk.Label(self.root, text="", relief="sunken", anchor="w", padding=3)
        self.status.pack(fill="x", side="bottom")
        self.root.protocol("WM_DELETE_WINDOW", self._close)
        self._shortcuts()
        self._draw_recent()

    def _typing(self):
        return isinstance(self.root.focus_get(), (tk.Entry, ttk.Entry, tk.Text, ttk.Spinbox, ttk.Combobox))

    def _shortcuts(self):
        r = self.root
        self.hovered = None
        self._painted = set()
        r.bind("<Control-Return>", lambda e: self._toggle_confirm())
        r.bind("<Control-n>", lambda e: self._add_recipe())
        r.bind("<Control-d>", lambda e: self._duplicate_recipe())
        r.bind("<Control-e>", lambda e: self.export())
        r.bind("<Control-f>", lambda e: (self.search_entry.focus_set(), self.search_entry.select_range(0, "end")))
        r.bind("<Control-Tab>", lambda e: self._step_recipe(1))
        r.bind("<Control-Shift-Tab>", lambda e: self._step_recipe(-1))
        r.bind("<Alt-Down>", lambda e: self._step_output(1))
        r.bind("<Alt-Up>", lambda e: self._step_output(-1))
        r.bind("<Escape>", lambda e: self._drop_brush())
        r.bind("<Delete>", lambda e: None if self._typing() or self.hovered is None else self._slot_clear(self.hovered))
        for n in range(1, 10):  # 1-9 place what you're holding, reading order (1 top-left, 9 bottom-right)
            r.bind(str(n), lambda e, n=n: None if self._typing() else self._slot_click(n - 1, type("E", (), {"state": 0})()))
        self.status.configure(text="Shortcuts: Ctrl+Enter confirm · Ctrl+N add recipe · Ctrl+D copy · Ctrl+Tab next recipe · "
                                   "Alt+↑/↓ next item · Ctrl+F search · Esc drop · Del empty slot · Ctrl+E export")

    def _step_recipe(self, by):
        if self.current and self.entry()["recipes"]:
            self._show_recipe((self.recipe_index + by) % len(self.entry()["recipes"]))
        return "break"

    def _step_output(self, by):
        if self.current:
            i = (self.mod_items.index(self.current) + by) % len(self.mod_items)
            self.outputs.selection_set(self.mod_items[i])
            self.outputs.see(self.mod_items[i])
        return "break"

    def _drop_brush(self):
        self.brush = None
        self.brush_label.configure(text="nothing", image="")

    # lists -----------------------------------------------------------------------------------

    def _fill_outputs(self):
        sel = self.outputs.selection()
        self.outputs.delete(*self.outputs.get_children())
        for item in self.mod_items:
            self._insert_output(item)
        if sel and self.outputs.exists(sel[0]):
            self.outputs.selection_set(sel[0])
        self._update_progress()

    def _row(self, item):
        entry = self.session["outputs"][item]
        done = entry["confirmed"]
        recipes = "none" if entry["no_recipe"] else str(len(entry["recipes"]))
        return {"text": " " + self.assets.name_of(item), "values": (recipes,),
                "image": self.check_icon if done else (self.icon(item) or ""), "tags": ("done",) if done else ()}

    def _insert_output(self, item):
        self.outputs.insert("", "end", iid=item, **self._row(item))

    def _refresh_output_row(self, item):
        self.outputs.item(item, **self._row(item))  # in place, so the selection (and the recipe shown) stays put
        self._update_progress()

    def _update_progress(self):
        done = sum(1 for e in self.session["outputs"].values() if e["confirmed"])
        self.progress.configure(text=f"{done} of {len(self.mod_items)} confirmed")

    def _fill_ingredients(self):
        q = self.search.get().lower().strip()
        f = self.filter.get()
        self.ingredients.delete(*self.ingredients.get_children())
        pool = []
        if f in ("all", "mod"):
            pool += self.mod_items
        if f in ("all", "vanilla"):
            pool += self.vanilla_items
        if f in ("all", "tags"):
            pool += sorted(self.tags)
        shown = 0
        for ref in pool:
            name = self.label_of(ref)
            if q and q not in name.lower() and q not in ref:
                continue
            self.ingredients.insert("", "end", iid=ref, text=" " + name, values=(ref,), image=self.icon(ref) or "")
            shown += 1
            if shown >= 600 and not q:  # the rest appear as you search
                break
        self.status.configure(text=f"{shown} ingredients shown" + ("" if q or shown < 600 else " — type to search the rest"))

    # selection -------------------------------------------------------------------------------

    def entry(self):
        return self.session["outputs"][self.current]

    def recipe(self):
        recipes = self.entry()["recipes"]
        return recipes[self.recipe_index] if 0 <= self.recipe_index < len(recipes) else None

    def locked(self):
        return self.current is not None and self.entry()["confirmed"]

    def _select_output(self):
        if self.loading:
            return
        sel = self.outputs.selection()
        if not sel:
            return
        self._commit_text()
        self.current = sel[0]
        self.recipe_index = 0
        e = self.entry()
        self.loading = True
        self.title.configure(text=self.assets.name_of(self.current))
        self.result.configure(image=self.icon(self.current, 32) or self.blank)
        self.no_recipe.set(e["no_recipe"])
        self.item_notes.configure(state="normal")
        self.item_notes.delete("1.0", "end")
        self.item_notes.insert("1.0", e.get("notes", ""))
        self.loading = False
        self._show_recipe(0)

    def _show_recipe(self, index):
        self._commit_text()
        self.recipe_index = index
        e = self.entry()
        r = self.recipe()
        self.loading = True
        self.recipe_pick.configure(values=[f"{i + 1}: {TYPES[x['type']][0].split(':')[0]}" for i, x in enumerate(e["recipes"])])
        if r is None:
            self.recipe_pick.set("(none yet — press Add)")
            self.recipe_notes.configure(state="normal")
            self.recipe_notes.delete("1.0", "end")
        else:
            self.recipe_pick.current(index)
            self.type_pick.set(TYPES[r["type"]][0])
            self.count.set(r["count"])
            self.mirror.set(r["mirror"])
            self.unlock.set(r["unlock"])
            self.group.set(r["group"])
            self.requires.set(r["requires_mod"])
            self.xp.set(r["xp"])
            self.time.set(r["time"])
            cats = CATEGORIES["cooking" if TYPES[r["type"]][3] else "crafting"]
            self.category.configure(values=cats)
            self.category.set(r["category"] if r["category"] in cats else cats[0])
            self.recipe_notes.configure(state="normal")
            self.recipe_notes.delete("1.0", "end")
            self.recipe_notes.insert("1.0", r.get("notes", ""))
        self.loading = False
        self._draw_grid()
        self._apply_lock()

    def _apply_lock(self):
        locked = self.locked()
        r = self.recipe()
        state = "disabled" if locked else "normal"
        has = r is not None and not self.no_recipe.get()
        kind = r["type"] if r else "shaped"
        for w in (self.add_btn,):
            w.configure(state="disabled" if locked or self.no_recipe.get() else "normal")
        for w in (self.dup_btn, self.del_btn):
            w.configure(state="disabled" if locked or not has else "normal")
        self.no_recipe_box.configure(state=state)
        self.type_pick.configure(state="disabled" if locked or not has else "readonly")
        self.recipe_pick.configure(state="readonly" if r else "disabled")
        self.count_box.configure(state="normal" if has and not locked and TYPES[kind][2] else "disabled")
        self.mirror_box.configure(state="normal" if has and not locked and kind == "shaped" else "disabled")
        self.unlock_box.configure(state="normal" if has and not locked else "disabled")
        for w in (self.group_entry, self.requires_entry):
            w.configure(state="normal" if has and not locked else "disabled")
        self.category.configure(state="readonly" if has and not locked and kind not in ("stonecutting", "smithing") else "disabled")
        cooking = TYPES[kind][3]
        self.xp_box.configure(state="normal" if has and not locked and cooking else "disabled")
        self.time_box.configure(state="normal" if has and not locked and cooking else "disabled")
        for t in (self.recipe_notes, self.item_notes):
            t.configure(state=state, bg="#eeeeee" if locked else "white")
        self.confirm_btn.configure(text="Unlock" if locked else "Confirm")

    def _draw_grid(self):
        r = self.recipe()
        used = TYPES[r["type"]][1] if r and not self.no_recipe.get() else []
        for i, lbl in enumerate(self.slots):
            slot = r["grid"][i] if r else None
            if i not in used:
                lbl.configure(image=self.blank, text="", bg="#7a7a7a", relief="flat")
                continue
            text = ""
            if r["type"] == "smithing":
                text = SMITHING_NAMES[i] if not slot else ""
            img = self.blank
            if slot:
                img = self.icon(slot["choices"][0], 32) or self.blank
                extra = len(slot["choices"]) - 1
                text = (f"+{extra}" if extra else "") + (" ↺" if slot["keep"] else "")
                if img is self.blank:
                    text = self.label_of(slot["choices"][0])[:6] + "\n" + text
            lbl.configure(image=img, text=text, bg="#d8e6ff" if slot and slot["keep"] else "#c6c6c6", relief="sunken",
                          fg="#1f4fa0" if img is not self.blank else "#222")

    # editing ---------------------------------------------------------------------------------

    def _edited(self):
        if self.loading or self.current is None or self.locked():
            return
        e = self.entry()
        e["no_recipe"] = self.no_recipe.get()
        r = self.recipe()
        if r:
            try:
                r["count"] = max(1, min(64, int(self.count.get())))
                r["xp"] = round(float(self.xp.get()), 3)
                r["time"] = max(1, int(self.time.get()))
            except (tk.TclError, ValueError):
                pass
            r["mirror"] = self.mirror.get()
            r["unlock"] = self.unlock.get()
            r["group"] = self.group.get().strip()
            r["requires_mod"] = self.requires.get().strip()
            if self.category.get():
                r["category"] = self.category.get()
        self._commit_text()
        self.save()
        self._refresh_output_row(self.current)
        self._apply_lock()
        self._draw_grid()

    def _commit_text(self):
        if self.current is None or self.loading:
            return
        e = self.entry()
        e["notes"] = self.item_notes.get("1.0", "end").strip()
        r = self.recipe()
        if r:
            r["notes"] = self.recipe_notes.get("1.0", "end").strip()

    def _add_recipe(self):
        if self.locked():
            return
        self.entry()["recipes"].append(new_recipe())
        self.save()
        self._refresh_output_row(self.current)
        self._show_recipe(len(self.entry()["recipes"]) - 1)

    def _duplicate_recipe(self):
        r = self.recipe()
        if r and not self.locked():
            self.entry()["recipes"].append(copy.deepcopy(r))
            self.save()
            self._refresh_output_row(self.current)
            self._show_recipe(len(self.entry()["recipes"]) - 1)

    def _delete_recipe(self):
        r = self.recipe()
        if r and not self.locked() and messagebox.askyesno("Delete recipe", "Delete this recipe?"):
            del self.entry()["recipes"][self.recipe_index]
            self.save()
            self._refresh_output_row(self.current)
            self._show_recipe(max(0, self.recipe_index - 1))

    def _change_type(self):
        r = self.recipe()
        if not r or self.locked():
            return
        kind = TYPE_BY_LABEL[self.type_pick.get()]
        used = TYPES[kind][1]
        kept = [s for i, s in enumerate(r["grid"]) if s and i in used] or [s for s in r["grid"] if s]
        r["type"] = kind
        if kind not in ("shaped", "shapeless"):  # move what's there into the slots this station uses
            r["grid"] = [None] * 9
            for i, s in zip(used, kept):
                r["grid"][i] = s
            r["count"] = r["count"] if TYPES[kind][2] else 1
        if TYPES[kind][3]:
            r["time"] = COOK_DEFAULTS[kind]
        self.save()
        self._show_recipe(self.recipe_index)
        self._refresh_output_row(self.current)

    def _pick_brush(self):
        sel = self.ingredients.selection()
        if sel:
            self._hold(sel[0])

    def _hold(self, ref):
        self.brush = ref
        self.brush_label.configure(text=f"{self.label_of(ref)}\n{ref}", image=self.icon(ref, 32) or "")
        recent = self.session.setdefault("recent", [])
        if ref in recent:
            recent.remove(ref)
        recent.insert(0, ref)
        del recent[10:]
        self._draw_recent()

    def _draw_recent(self):
        for w in self.recent_row.winfo_children():
            w.destroy()
        for n, ref in enumerate(self.session.get("recent", [])):
            img = self.icon(ref) or ""
            b = tk.Button(self.recent_row, image=img or self.blank, width=20, height=20, relief="flat", bg="#e4e4e4",
                          command=lambda r=ref: self._hold(r), cursor="hand2")
            b.grid(row=n // 5, column=n % 5, padx=1, pady=1)
            b.bind("<Enter>", lambda e, r=ref: self.status.configure(text=self.label_of(r)))

    def _pick_top_result(self):
        kids = self.ingredients.get_children()
        if kids:
            self.ingredients.selection_set(kids[0])
            self.ingredients.see(kids[0])

    def _focus_results(self):
        kids = self.ingredients.get_children()
        if kids:
            self.ingredients.focus_set()
            self.ingredients.focus(kids[0])
            self.ingredients.selection_set(kids[0])

    def _slot_press(self, i, event):
        self._painted = {i}
        self._slot_click(i, event)

    def _slot_press_clear(self, i):
        self._painted = {i}
        self._slot_clear(i)

    def _slot_drag(self, event, clear):
        """Dragging across the grid paints (or with the right button, empties) every slot it passes."""
        w = self.root.winfo_containing(event.x_root, event.y_root)
        if w in self.slots:
            i = self.slots.index(w)
            if i not in self._painted:
                self._painted.add(i)
                if clear:
                    self._slot_clear(i)
                else:
                    self._slot_click(i, type("E", (), {"state": 0})())

    def _slot_click(self, i, event):
        r = self.recipe()
        if not r or self.locked() or self.no_recipe.get() or i not in TYPES[r["type"]][1]:
            return
        slot = r["grid"][i]
        ctrl, shift = event.state & 0x4, event.state & 0x1
        if ctrl:
            if slot:
                slot["keep"] = not slot["keep"]
        elif self.brush is None:
            self.status.configure(text="Pick an ingredient on the right first.")
            return
        elif shift and slot:
            if self.brush not in slot["choices"]:
                slot["choices"].append(self.brush)
        else:
            r["grid"][i] = {"choices": [self.brush], "keep": False}
        self.save()
        self._draw_grid()
        self._slot_hover(i)

    def _slot_clear(self, i):
        r = self.recipe()
        if r and not self.locked():
            r["grid"][i] = None
            self.save()
            self._draw_grid()

    def _slot_hover(self, i):
        self.hovered = i
        r = self.recipe()
        slot = r["grid"][i] if r else None
        if not slot:
            self.hover.configure(text="Empty slot.")
            return
        names = " or ".join(self.label_of(c) for c in slot["choices"])
        self.hover.configure(text=names + ("   ↺ comes back after crafting (not used up)" if slot["keep"] else ""))

    def _place_in_first_empty(self):
        self._pick_brush()
        r = self.recipe()
        if not r or self.locked() or self.brush is None:
            return
        for i in TYPES[r["type"]][1]:
            if r["grid"][i] is None:
                r["grid"][i] = {"choices": [self.brush], "keep": False}
                self.save()
                self._draw_grid()
                return

    # confirming ------------------------------------------------------------------------------

    def _problems(self, item):
        e = self.session["outputs"][item]
        if e["no_recipe"]:
            return []
        if not e["recipes"]:
            return ["it has no recipe (tick 'No crafting recipe' if that's on purpose)"]
        out = []
        for n, r in enumerate(e["recipes"], 1):
            used = [r["grid"][i] for i in TYPES[r["type"]][1]]
            if r["type"] == "smithing":
                if not all(used):
                    out.append(f"recipe {n} needs a template, a base and an addition")
            elif not any(used):
                out.append(f"recipe {n} has no ingredients")
        return out

    def _toggle_confirm(self):
        if self.current is None:
            return
        self._commit_text()
        e = self.entry()
        if e["confirmed"]:
            e["confirmed"] = False
        else:
            problems = self._problems(self.current)
            if problems:
                messagebox.showwarning("Not ready", "Can't confirm yet:\n• " + "\n• ".join(problems))
                return
            clashes = self._clashes_for(self.current)
            if clashes and not messagebox.askyesno("Recipe clash", "\n".join(clashes) + "\n\nConfirm anyway?"):
                return
            e["confirmed"] = True
        self.save()
        self._refresh_output_row(self.current)
        self._apply_lock()
        if e["confirmed"]:  # move on to the next unconfirmed item
            for item in self.mod_items:
                if not self.session["outputs"][item]["confirmed"]:
                    self.outputs.selection_set(item)
                    self.outputs.see(item)
                    break

    # clashes ---------------------------------------------------------------------------------

    def _ingredient_set(self, choices):
        items = set()
        for c in choices:
            items |= self.tags.get(c, set()) if c.startswith("#") else {c}
        return frozenset(items)

    def _shape(self, r):
        """A recipe as something comparable: ('shaped', rows of item sets) or ('shapeless', [item sets])."""
        cells = [self._ingredient_set(s["choices"]) if s else None for s in r["grid"]]
        if r["type"] == "shapeless":
            return "shapeless", [c for c in cells if c]
        rows = [cells[i * 3:i * 3 + 3] for i in range(3)]
        rows = [row for row in rows if any(row)]
        cols = [j for j in range(3) if any(row[j] for row in rows)]
        return "shaped", [[row[j] for j in range(min(cols), max(cols) + 1)] for row in rows] if cols else []

    def _vanilla_shape(self, r):
        def ing(j):
            choices = j if isinstance(j, list) else [j]
            return self._ingredient_set([c.get("item") or "#" + c["tag"] for c in choices])
        if r["type"].endswith("shapeless"):
            return "shapeless", [ing(j) for j in r["ingredients"]]
        return "shaped", [[ing(r["key"][ch]) if ch != " " else None for ch in row] for row in r["pattern"]]

    @staticmethod
    def _matches(a, b):
        """Whether one crafting grid could satisfy both recipes."""
        (ka, sa), (kb, sb) = a, b
        flat = lambda s: [c for row in s for c in row if c] if s and isinstance(s[0], list) else list(s)
        if ka == "shaped" and kb == "shaped":
            if len(sa) != len(sb) or not sa or len(sa[0]) != len(sb[0]):
                return False
            same = lambda x, y: all((p is None) == (q is None) and (p is None or p & q) for rx, ry in zip(x, y) for p, q in zip(rx, ry))
            return same(sa, sb) or same(sa, [list(reversed(row)) for row in sb])
        xs, ys = flat(sa), flat(sb)
        if len(xs) != len(ys) or not xs:
            return False
        used = [False] * len(ys)

        def place(k):  # match every ingredient to a different one that overlaps it
            if k == len(xs):
                return True
            for j, y in enumerate(ys):
                if not used[j] and xs[k] & y:
                    used[j] = True
                    if place(k + 1):
                        return True
                    used[j] = False
            return False
        return place(0)

    def _clashes_for(self, item):
        out = []
        mine = [(item, n, r) for n, r in enumerate(self.session["outputs"][item]["recipes"], 1) if r["type"] in ("shaped", "shapeless")]
        others = [(o, n, r) for o, e in self.session["outputs"].items() if not e["no_recipe"]
                  for n, r in enumerate(e["recipes"], 1) if r["type"] in ("shaped", "shapeless") and not (o == item)]
        for _, n, r in mine:
            s = self._shape(r)
            for name, v in self.vanilla_recipes:
                if self._matches(s, self._vanilla_shape(v)):
                    out.append(f"Recipe {n} clashes with vanilla's {name.replace('_', ' ')}.")
            for o, m, r2 in others:
                if self._matches(s, self._shape(r2)):
                    out.append(f"Recipe {n} clashes with {self.assets.name_of(o)}'s recipe {m}.")
        return out

    def _check_clashes_dialog(self):
        self._commit_text()
        lines = []
        for item in self.mod_items:
            for c in self._clashes_for(item):
                lines.append(f"{self.assets.name_of(item)}: {c}")
        messagebox.showinfo("Clashes", "\n".join(dict.fromkeys(lines)) if lines else "No clashes with vanilla or each other.")

    # export ----------------------------------------------------------------------------------

    @staticmethod
    def _ingredient_json(slot):
        parts = [{"tag": c[1:]} if c.startswith("#") else {"item": c} for c in slot["choices"]]
        return parts[0] if len(parts) == 1 else parts

    def _recipe_json(self, item, r):
        kind = r["type"]
        extras = {}
        if kind in ("shaped", "shapeless"):
            cells = r["grid"]
            if kind == "shapeless":
                body = {"type": "minecraft:crafting_shapeless", "category": r["category"],
                        "ingredients": [self._ingredient_json(s) for s in cells if s]}
            else:
                rows = [cells[i * 3:i * 3 + 3] for i in range(3)]
                rows = [row for row in rows if any(row)]
                cols = [j for j in range(3) if any(row[j] for row in rows)]
                rows = [row[min(cols):max(cols) + 1] for row in rows]
                key, letters = {}, {}
                for row in rows:
                    for s in row:
                        if s:
                            sig = json.dumps(s["choices"])
                            if sig not in letters:
                                name = self.label_of(s["choices"][0])
                                letter = next((ch.upper() for ch in name if ch.isalpha() and ch.upper() not in key), None)
                                letter = letter or next(ch for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ" if ch not in key)
                                letters[sig] = letter
                                key[letter] = self._ingredient_json(s)
                body = {"type": "minecraft:crafting_shaped", "category": r["category"],
                        "pattern": ["".join(letters[json.dumps(s["choices"])] if s else " " for s in row) for row in rows], "key": key}
                if not r["mirror"]:
                    extras["no_mirror"] = True
            body["result"] = {"item": item, "count": r["count"]} if r["count"] > 1 else {"item": item}
            kept = [s["choices"] for s in cells if s and s["keep"]]
            if kept:
                extras["comes_back"] = kept
        elif kind in COOK_DEFAULTS:
            body = {"type": {"campfire": "minecraft:campfire_cooking"}.get(kind, f"minecraft:{kind}"), "category": r["category"],
                    "ingredient": self._ingredient_json(r["grid"][4]), "result": item, "experience": r["xp"], "cookingtime": r["time"]}
        elif kind == "stonecutting":
            body = {"type": "minecraft:stonecutting", "ingredient": self._ingredient_json(r["grid"][4]), "result": item, "count": r["count"]}
        else:
            body = {"type": "minecraft:smithing_transform", "template": self._ingredient_json(r["grid"][3]),
                    "base": self._ingredient_json(r["grid"][4]), "addition": self._ingredient_json(r["grid"][5]), "result": {"item": item}}
        if r["group"]:
            body["group"] = r["group"]
        if r["requires_mod"]:
            body["conditions"] = [{"type": "forge:mod_loaded", "modid": r["requires_mod"]}]
        return body, extras

    def export(self):
        self._commit_text()
        unconfirmed = [self.assets.name_of(i) for i in self.mod_items if not self.session["outputs"][i]["confirmed"]]
        if unconfirmed and not messagebox.askyesno(
                "Export", f"{len(unconfirmed)} items aren't confirmed yet:\n{', '.join(unconfirmed[:12])}"
                          f"{'…' if len(unconfirmed) > 12 else ''}\n\nExport anyway (they're marked unconfirmed)?"):
            return
        path = filedialog.asksaveasfilename(initialdir=TOOLS, initialfile=DEFAULT_EXPORT.name, defaultextension=".json",
                                            filetypes=[("JSON", "*.json")])
        if not path:
            return
        path = Path(path)
        pack = path.with_name(path.stem + "_datapack")
        bundle = {"format": "minescore-recipes-1", "minecraft": "1.20.1", "items": []}
        files = {}
        for item in self.mod_items:
            e = self.session["outputs"][item]
            short = item.split(":")[1]
            entry = {"item": item, "name": self.assets.name_of(item), "confirmed": e["confirmed"], "no_recipe": e["no_recipe"],
                     "notes": e["notes"], "recipes": []}
            for n, r in enumerate(e["recipes"] if not e["no_recipe"] else [], 1):
                body, extras = self._recipe_json(item, r)
                rid = r.get("existing_id") or (short if n == 1 else f"{short}_{n}")
                ingredients = sorted({c for s in r["grid"] if s for c in s["choices"]})
                entry["recipes"].append({"id": f"minescore:{rid}", "recipe": body, "extras": extras, "notes": r["notes"],
                                         "unlock_with": ingredients if r["unlock"] else []})
                files[rid] = body
            bundle["items"].append(entry)
        path.write_text(json.dumps(bundle, indent=2), encoding="utf-8")
        recipes_dir = pack / "data" / "minescore" / "recipes"
        recipes_dir.mkdir(parents=True, exist_ok=True)
        for old in recipes_dir.glob("*.json"):
            old.unlink()
        for rid, body in files.items():
            (recipes_dir / f"{rid}.json").write_text(json.dumps(body, indent=2), encoding="utf-8")
        (pack / "pack.mcmeta").write_text(json.dumps({"pack": {"pack_format": 15, "description": "MineScore recipes (draft)"}}, indent=2),
                                          encoding="utf-8")
        messagebox.showinfo("Exported", f"Saved {sum(len(i['recipes']) for i in bundle['items'])} recipes to\n{path}\n\n"
                                        f"and a datapack you can drop into a world's datapacks folder:\n{pack}")

    def _close(self):
        self._commit_text()
        self.save()
        self.root.destroy()


def find_client_jar():
    for candidate in [GRADLE / "fabric-loom" / "1.20.1" / "minecraft-client.jar",
                      *map(Path, glob.glob(str(GRADLE / "**" / "minecraft-client.jar"), recursive=True))]:
        if candidate.exists():
            return candidate
    return None


def main():
    root = tk.Tk()
    jar = Path(sys.argv[1]) if len(sys.argv) > 1 else find_client_jar()
    if jar is None or not jar.exists():
        messagebox.showinfo("Recipe Maker", "Couldn't find Minecraft 1.20.1's client jar. Pick it (minecraft-client.jar, or "
                                            ".minecraft/versions/1.20.1/1.20.1.jar).")
        chosen = filedialog.askopenfilename(filetypes=[("Jar", "*.jar")])
        if not chosen:
            return
        jar = Path(chosen)
    root.title("MineScore Recipe Maker — loading…")
    root.update()
    RecipeMaker(root, Assets(jar))
    root.mainloop()


if __name__ == "__main__":
    main()
