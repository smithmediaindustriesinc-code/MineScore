package com.minescore.client.screen;

import com.minescore.Tr;
import com.minescore.client.UiTheme;
import com.minescore.MineScoreSettings;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.client.audio.EditorPlayer;
import com.minescore.client.score.EditorKeys;
import com.minescore.client.score.EditorPalettes;
import com.minescore.client.score.ScoreEditor;
import com.minescore.client.score.ScoreEditor.ChordRef;
import com.minescore.client.score.ScoreElement;
import com.minescore.client.score.ScoreGlyphs;
import com.minescore.client.score.ScoreView;
import com.minescore.network.MineScoreNetwork;
import com.minescore.score.Chord;
import com.minescore.score.Frac;
import com.minescore.score.Notation.Dynamic;
import com.minescore.score.Notation.SpannerType;
import com.minescore.score.Note;
import com.minescore.score.Part;
import com.minescore.score.Score;
import com.minescore.score.ScorePackets;
import com.minescore.score.ScorePlayback;
import com.minescore.score.VoiceEdit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The score editor (SCOPE.md §9): a MuseScore-style notation editor, drawn in MineScore's pixel style.
 * A toolbar of note values and tools along the top, palettes on the left, the score in the middle,
 * properties on the right, and what's selected along the bottom.
 */
public class ScoreEditorScreen extends Screen {
    private static final int TOOLBAR = 24, STATUS = 12, LEFT = 120, RIGHT = 134, NAMES = 58;
    private static final float[] ZOOMS = {4, 5, 6, 7, 8, 10, 12, 14, 16};
    private static final String[] DURATION_NAMES = {Tr.en("Breve"), Tr.en("Whole"), Tr.en("Half"), Tr.en("Quarter"), Tr.en("Eighth"),
            Tr.en("16th"), Tr.en("32nd"), Tr.en("64th")};

    private final ScoreEditor editor;
    private ScoreView view;
    private int zoom = 4;
    private float scrollX, scrollY;
    /** Which palettes are open and which property sections are folded up, kept while the game runs. */
    private static final Set<String> openPalettes = new HashSet<>(Set.of("Clefs", "Dynamics", "Lines"));
    private static final Set<String> closedSections = new HashSet<>();
    private static final int DROP_ROWS = 4, DROP_ROW = 11, DROP_W = 40, PALETTE_SEARCH = 20;
    /** What the palette search box holds, kept while the game runs. */
    private static String paletteQuery = "";
    private @Nullable EditBox paletteSearch;
    private float paletteScroll;
    private @Nullable ScorePlayback.Result playback;
    private @Nullable ChordRef dragging;
    /** Where a Shift+drag box selection started (screen x, y), while it's being drawn. */
    private double @Nullable [] lassoFrom;
    private boolean rebuildAfterDrag;
    private double dragStartY;
    private final List<EditBox> fields = new ArrayList<>();
    private @Nullable EditBox title, subtitle, composer, lyricist, tempo, partName;
    /** The property panel's section headings, where init put them. */
    private final List<Section> sections = new ArrayList<>();
    private int tempoY = -1;
    /** Dragging the view around, from open space or with the middle button: where the mouse and the view started. */
    private double @Nullable [] panFrom;
    /** The list of dynamics opened by clicking one. */
    private @Nullable Dropdown dropdown;
    /** Lyrics, a chord symbol, staff text or a rehearsal mark being typed. */
    private @Nullable TextEntry text;

    private record Section(String key, String label, int y) {}

    /** A part's subtab under Instruments. */
    private record Subtab(int part, String label, int y, boolean open) {}

    private final List<Subtab> subtabs = new ArrayList<>();
    /** The part picked in the Instruments list (-1: follow what's being edited), and whether its subtab is folded. */
    private int shownPart = -1;
    private boolean partFolded;
    /** How far the properties panel is scrolled, and how tall its contents are. */
    private int propScroll, propertiesHeight;

    private static final class Dropdown {
        final ScoreElement element;
        final int x, y;
        int scroll;

        Dropdown(ScoreElement element, int x, int y, int scroll) {
            this.element = element;
            this.x = x;
            this.y = y;
            this.scroll = scroll;
        }
    }

    private static final class TextEntry {
        final ScoreElement.Kind kind;
        @Nullable ChordRef ref;
        int verse;
        int measure;
        final EditBox box;

        TextEntry(ScoreElement.Kind kind, EditBox box) {
            this.kind = kind;
            this.box = box;
        }
    }

    public ScoreEditorScreen(BlockPos desk, String scoreId, Score score) {
        super(Component.translatable("gui.minescore.editor.title"));
        this.editor = new ScoreEditor(desk, scoreId, score, e -> scoreChanged());
        com.minescore.client.score.InstrumentPacks.reload(); // the instruments today's resource packs add
        ScoreGlyphs.reload(); // and their notation symbols
    }

    /** The editor's desk and score, so arriving packets can find it. */
    public ScoreEditor editor() {
        return editor;
    }

    private float space() {
        return ZOOMS[zoom];
    }

    private void scoreChanged() {
        view = new ScoreView(editor.score, font, space());
        int[] focus = editor.takeFocus();
        if (focus != null && minecraft != null) {
            reveal(focus[0], focus[1]); // undo, paste and the like happen where you can see them
        }
        if (minecraft != null) {
            if (isDragging()) {
                rebuildAfterDrag = true; // a slider being dragged would be swapped out from under the mouse
            } else {
                rebuildWidgets();
            }
        }
    }

    // --- layout -----------------------------------------------------------------------------

    private int areaLeft() {
        return LEFT;
    }

    private int areaRight() {
        return width - RIGHT;
    }

    private int areaTop() {
        return TOOLBAR;
    }

    private int areaBottom() {
        return height - STATUS;
    }

    /** Screen x of score-space x. */
    private float sx(float x) {
        return areaLeft() + NAMES + x - scrollX;
    }

    private float sy(float y) {
        return areaTop() + y - scrollY;
    }

    @Override
    protected void init() {
        if (view == null) {
            view = new ScoreView(editor.score, font, space());
        }
        boolean searching = paletteSearch != null && paletteSearch.isFocused();
        fields.clear();
        // the palettes' search box (Ctrl+F)
        paletteSearch = new EditBox(font, 3, TOOLBAR + 3, LEFT - 6, 14, Component.translatable("gui.minescore.editor.search_palettes"));
        paletteSearch.setMaxLength(40);
        paletteSearch.setValue(paletteQuery);
        paletteSearch.setHint(Component.translatable("gui.minescore.editor.search_palettes").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        paletteSearch.setResponder(q -> {
            paletteQuery = q;
            paletteScroll = 0;
        });
        paletteSearch.setFocused(searching);
        addRenderableWidget(paletteSearch);
        fields.add(paletteSearch);
        // toolbar
        int x = 4;
        Button input = addRenderableWidget(Button.builder(Component.literal(editor.inputMode ? "N ✎" : "N"), b -> {
            editor.inputMode = !editor.inputMode;
            rebuildWidgets();
        }).bounds(x, 3, 22, 18).tooltip(Tooltip.create(Component.translatable("gui.minescore.editor.input"))).build());
        x += 26;
        for (int log = 6; log >= -1; log--) {
            int l = log;
            addRenderableWidget(new GlyphButton(x, 3, 16, 18, durationGlyph(l), editor.durationLog == l,
                    DURATION_NAMES[l + 1] + " (" + (log >= 0 ? 7 - log : 8) + ")", () -> chooseDuration(l)));
            x += 17;
        }
        x += 4;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "dot", editor.dots == 1, Tr.s("Dot (.)"), () -> chooseDots(1)));
        x += 17;
        addRenderableWidget(new GlyphButton(x, 3, 18, 18, "dot", editor.dots == 2, Tr.s("Double dot"), () -> chooseDots(2)));
        x += 19;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "rest_quarter", false, Tr.s("Rest (0)"), this::rest));
        x += 17;
        addRenderableWidget(Button.builder(Component.literal("‿"), b -> editor.toggleTie()).bounds(x, 3, 16, 18)
                .tooltip(Tooltip.create(Tr.c("Tie (+)"))).build());
        x += 17;
        addRenderableWidget(Button.builder(Component.literal("3"), b -> editor.makeTuplet(3)).bounds(x, 3, 16, 18)
                .tooltip(Tooltip.create(Tr.c("Triplet (Ctrl+3; Ctrl+2…9 for others)"))).build());
        x += 17;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "grace", false, Tr.s("Grace note (/)"),
                () -> editor.addGrace(com.minescore.score.Notation.Grace.ACCIACCATURA)));
        x += 21;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "flat", editor.accidental == -1, Tr.s("Flat"), () -> editor.applyAccidental(-1)));
        x += 17;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "natural", editor.accidental == 0, Tr.s("Natural"), () -> editor.applyAccidental(0)));
        x += 17;
        addRenderableWidget(new GlyphButton(x, 3, 16, 18, "sharp", editor.accidental == 1, Tr.s("Sharp"), () -> editor.applyAccidental(1)));
        x += 21;
        for (int v = 0; v < Part.VOICES; v++) {
            int voice = v;
            Button b = addRenderableWidget(Button.builder(Component.literal(String.valueOf(v + 1)), btn -> {
                editor.voice = voice;
                rebuildWidgets();
            }).bounds(x, 3, 14, 18).tooltip(Tooltip.create(Tr.c("Voice %s", v + 1))).build());
            b.active = editor.voice != v;
            x += 15;
        }
        x += 6;
        Button undo = addRenderableWidget(Button.builder(Component.literal("↶"), b -> editor.undo()).bounds(x, 3, 18, 18)
                .tooltip(Tooltip.create(Tr.c("Undo (Ctrl+Z)"))).build());
        undo.active = editor.canUndo();
        x += 19;
        Button redo = addRenderableWidget(Button.builder(Component.literal("↷"), b -> editor.redo()).bounds(x, 3, 18, 18)
                .tooltip(Tooltip.create(Tr.c("Redo (Ctrl+Y)"))).build());
        redo.active = editor.canRedo();
        x += 23;
        addRenderableWidget(Button.builder(Component.literal(EditorPlayer.active() ? "■" : "▶"), b -> togglePlay()).bounds(x, 3, 18, 18)
                .tooltip(Tooltip.create(Tr.c("Play from the selection (Space)"))).build());
        x += 23;
        addRenderableWidget(Button.builder(Component.literal("−"), b -> setZoom(zoom - 1)).bounds(x, 3, 14, 18).build());
        x += 15;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> setZoom(zoom + 1)).bounds(x, 3, 14, 18).build());
        x += 19;
        addRenderableWidget(Button.builder(Component.literal("⚙"), b -> minecraft.setScreen(new EditorKeysScreen(this))).bounds(x, 3, 18, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.minescore.editor.preferences"))).build());

        // properties, in sections that fold up
        sections.clear();
        title = subtitle = composer = lyricist = tempo = partName = null;
        tempoY = -1;
        int px = width - RIGHT + 6, w = RIGHT - 12, half = (w - 2) / 2;
        subtabs.clear();
        int y = section("score", Tr.s("Score"), TOOLBAR + 2 - propScroll);
        if (!closedSections.contains("score")) {
            title = field(px, y, w, editor.score.title, Tr.s("Title"));
            y += 18;
            subtitle = field(px, y, w, editor.score.subtitle, Tr.s("Subtitle"));
            y += 18;
            composer = field(px, y, w, editor.score.composer, Tr.s("Composer"));
            y += 18;
            lyricist = field(px, y, w, editor.score.lyricist, Tr.s("Lyricist"));
            y += 18;
            tempo = field(px, y, 40, String.valueOf(Math.round(editor.score.tempo)), "BPM");
            tempo.setFilter(s -> s.matches("\\d{0,3}"));
            tempoY = y;
            y += 20;
        }
        // Instruments: a subtab per part, the shown one open with its settings
        y = section("instruments", Tr.s("Instruments"), y + 2);
        if (!closedSections.contains("instruments")) {
            int shown = shownPart();
            for (int part = 0; part < editor.score.parts.size(); part++) {
                Part p = editor.score.parts.get(part);
                boolean open = part == shown && !partFolded;
                subtabs.add(new Subtab(part, p.name.isBlank() ? com.minescore.score.Instruments.partName(p) : p.name, y, open));
                y += 13;
                if (open) {
                    y = partSettings(part, p, px + 4, y, w - 4) + 2;
                }
            }
            Button add = place(Button.builder(Component.translatable("gui.minescore.editor.add_part"), b -> addPart())
                    .bounds(px, y, w, 14).build());
            add.active = editor.score.parts.size() < Score.MAX_PARTS;
            y += 18;
        }
        // File: import, export (SCOPE.md §9.12)
        y = section("file", Tr.s("File"), y + 2);
        if (!closedSections.contains("file")) {
            place(Button.builder(Component.translatable("gui.minescore.editor.import"), b -> {
                commitFields();
                minecraft.setScreen(new ImportScreen(this, editor.desk));
            }).bounds(px, y, w, 14).tooltip(Tooltip.create(Component.translatable("gui.minescore.editor.import_hint"))).build());
            y += 16;
            com.minescore.client.score.ScoreFiles.Format[] formats = com.minescore.client.score.ScoreFiles.Format.values();
            for (int i = 0; i < formats.length; i++) {
                com.minescore.client.score.ScoreFiles.Format format = formats[i];
                Button b = place(Button.builder(Tr.c(format.label), btn -> export(format))
                        .bounds(px + (i % 2) * (half + 2), y + (i / 2) * 16, half, 14)
                        .tooltip(Tooltip.create(Component.translatable("gui.minescore.editor.export_as", "." + format.extension))).build());
                b.active = !exporting;
            }
            y += 32;
            place(Button.builder(Component.translatable("gui.minescore.editor.export_folder"), b -> pickExportFolder()).bounds(px, y, w, 14)
                    .tooltip(Tooltip.create(Component.literal(com.minescore.client.score.EditorPrefs.exportFolder().toString()))).build());
            y += 16;
        }
        y = section("measures", Tr.s("Measures"), y + 2);
        if (!closedSections.contains("measures")) {
            place(Button.builder(Tr.c("Insert before"), b -> editor.insertMeasure(false)).bounds(px, y, half, 14).build());
            place(Button.builder(Tr.c("Insert after"), b -> editor.insertMeasure(true)).bounds(px + half + 2, y, half, 14).build());
            y += 16;
            place(Button.builder(Component.literal("+4 at end"), b -> editor.appendMeasures(4)).bounds(px, y, half, 14).build());
            Button delete = place(Button.builder(Tr.c("Delete"), b -> editor.deleteMeasures())
                    .bounds(px + half + 2, y, half, 14).build());
            delete.active = editor.score.measureCount() > 1;
            y += 16;
        }
        propertiesHeight = y + propScroll - TOOLBAR;
    }

    /** One part's settings, under its open subtab; returns where they end. */
    private int partSettings(int part, Part p, int px, int y, int w) {
        int half = (w - 2) / 2;
        partName = field(px, y, w, p.name, Tr.s("Part name"));
        y += 18;
        Component change = Component.translatable("gui.minescore.editor.change_instrument");
        place(Button.builder(Component.literal(com.minescore.score.Instruments.Choice.of(p).name()),
                b -> pickInstrument(part)).bounds(px, y, w - 20, 16).tooltip(Tooltip.create(change)).build());
        place(Button.builder(Component.literal("✎"), b -> pickInstrument(part)).bounds(px + w - 18, y, 18, 16)
                .tooltip(Tooltip.create(change)).build());
        y += 18;
        place(new ValueSlider(px, y, w, 14, p.volume, v -> Tr.c("Volume: %s%%", Math.round(v * 100)),
                v -> editor.partHeader(part, q -> q.volume = (float) v)));
        y += 16;
        place(new ValueSlider(px, y, w, 14, (p.pan + 1) / 2, v -> Tr.c("Pan: %s", panName(v)),
                v -> editor.partHeader(part, q -> q.pan = (float) (v * 2 - 1))));
        y += 16;
        place(Button.builder(p.mute ? Tr.c("Muted") : Tr.c("Mute"), b -> editor.partHeader(part, q -> q.mute = !q.mute))
                .bounds(px, y, half, 14).build());
        place(Button.builder(p.solo ? Tr.c("Solo ✔") : Tr.c("Solo"), b -> editor.partHeader(part, q -> q.solo = !q.solo))
                .bounds(px + half + 2, y, half, 14).build());
        y += 16;
        if (com.minescore.score.Instruments.pitched(p)) {
            boolean global = MineScoreSettings.restrikeLongNotes.getAsBoolean();
            Button restrike = place(Button.builder(Tr.c("Re-strike long notes: %s", p.restrike || global ? Tr.s("on") : Tr.s("off")),
                    b -> editor.partHeader(part, q -> q.restrike = !q.restrike)).bounds(px, y, w, 14)
                    .tooltip(Tooltip.create(Component.translatable(global ? "gui.minescore.editor.restrike_all" : "gui.minescore.editor.restrike"))).build());
            restrike.active = !global;
            y += 16;
        }
        Button up = place(Button.builder(Component.literal("▲"), b -> {
            editor.movePart(part, -1);
            shownPart = part - 1;
        }).bounds(px, y, 20, 14).build());
        up.active = part > 0;
        Button down = place(Button.builder(Component.literal("▼"), b -> {
            editor.movePart(part, 1);
            shownPart = part + 1;
        }).bounds(px + 22, y, 20, 14).build());
        down.active = part < editor.score.parts.size() - 1;
        Button remove = place(Button.builder(Tr.c("Remove"), b -> {
            shownPart = -1;
            editor.removePart(part);
        }).bounds(px + 44, y, w - 44, 14).build());
        remove.active = editor.score.parts.size() > 1;
        return y + 16;
    }

    /** The part whose subtab is open: the one picked there, or else the one being edited. */
    private int shownPart() {
        int p = shownPart >= 0 ? shownPart : editor.currentPart();
        return Math.max(0, Math.min(p, editor.score.parts.size() - 1));
    }

    /** Adds a property widget if it's within the panel as scrolled; returns it either way. */
    private <T extends net.minecraft.client.gui.components.AbstractWidget> T place(T widget) {
        if (widget.getY() >= TOOLBAR && widget.getY() + widget.getHeight() <= height - STATUS) {
            addRenderableWidget(widget);
        }
        return widget;
    }

    private boolean exporting;
    /** A message for the status bar (an export's result), until {@code noticeUntil}. */
    private @Nullable Component notice;
    private long noticeUntil;

    private void notice(Component message) {
        notice = message;
        noticeUntil = net.minecraft.Util.getMillis() + 6000;
    }

    /** Writes the score out (off the game's thread: a WAV is rendered first). */
    private void export(com.minescore.client.score.ScoreFiles.Format format) {
        commitFields();
        Score copy = Score.load(editor.score.save());
        exporting = true;
        rebuildWidgets();
        notice(Component.translatable("gui.minescore.editor.exporting", Tr.s(format.label)));
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return com.minescore.client.score.ScoreFiles.export(copy, format);
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, net.minecraft.Util.backgroundExecutor()).whenCompleteAsync((file, failure) -> {
            exporting = false;
            if (failure != null) {
                Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
                notice(Component.translatable("gui.minescore.editor.export_failed", cause.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
            } else {
                notice(Component.translatable("gui.minescore.editor.exported", file.toString()));
            }
            if (minecraft.screen == this) {
                rebuildWidgets();
            }
        }, minecraft);
    }

    private void pickExportFolder() {
        com.minescore.client.score.ScoreFiles.pickFolder().whenCompleteAsync((folder, failure) -> {
            if (folder != null) {
                com.minescore.client.score.EditorPrefs.setExportFolder(folder);
                notice(Component.translatable("gui.minescore.editor.export_folder_set", folder.toString()));
                if (minecraft.screen == this) {
                    rebuildWidgets();
                }
            }
        }, minecraft);
    }

    /** A property section's heading; returns where its contents start. */
    private int section(String key, String label, int y) {
        sections.add(new Section(key, label, y));
        return y + 14;
    }

    private void addPart() {
        commitFields();
        minecraft.setScreen(new InstrumentPickerScreen(this, Component.translatable("gui.minescore.editor.add_part_title"), null, i -> {
            editor.addPart(i);
            shownPart = editor.score.parts.size() - 1; // the new part's subtab opens, at the bottom of the list
            partFolded = false;
            rebuildWidgets();
        }));
    }

    /** The pencil: the same instrument list, to change a part's instrument. */
    private void pickInstrument(int part) {
        commitFields();
        com.minescore.score.Instruments.Choice now = com.minescore.score.Instruments.Choice.of(editor.score.parts.get(part));
        minecraft.setScreen(new InstrumentPickerScreen(this, Component.translatable("gui.minescore.editor.change_instrument"), now,
                i -> editor.setInstrument(part, i)));
    }

    private static String panName(double v) {
        int pan = (int) Math.round(v * 200 - 100);
        return pan == 0 ? Tr.s("centre") : pan < 0 ? -pan + "% L" : pan + "% R";
    }

    private EditBox field(int x, int y, int w, String value, String hint) {
        EditBox box = new EditBox(font, x, y, w, 14, Component.literal(hint));
        box.setMaxLength(Score.MAX_TEXT);
        box.setValue(value);
        box.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        place(box);
        fields.add(box);
        return box;
    }

    /** Text boxes take effect when you press Enter or click away. */
    private void commitFields() {
        Score s = editor.score;
        if (title != null && subtitle != null && composer != null && lyricist != null && tempo != null) {
            commitHeader(s);
        }
        if (partName == null) {
            return;
        }
        int part = shownPart();
        String name = Score.clip(partName.getValue());
        if (!name.equals(s.parts.get(part).name)) {
            editor.partHeader(part, q -> q.name = name);
        }
    }

    private void commitHeader(Score s) {
        String t = Score.clip(title.getValue()), st = Score.clip(subtitle.getValue()), c = Score.clip(composer.getValue()),
                l = Score.clip(lyricist.getValue());
        double bpm = tempo.getValue().isEmpty() ? s.tempo : Math.max(10, Math.min(400, Integer.parseInt(tempo.getValue())));
        if (!t.equals(s.title) || !st.equals(s.subtitle) || !c.equals(s.composer) || !l.equals(s.lyricist) || bpm != s.tempo) {
            editor.header(h -> {
                h.title = t;
                h.subtitle = st;
                h.composer = c;
                h.lyricist = l;
                h.tempo = bpm;
            });
        }
    }

    private static String durationGlyph(int log) {
        return switch (log) {
            case -1 -> "head_breve";
            case 0 -> "head_whole";
            case 1 -> "head_half";
            default -> "head_black";
        };
    }

    private void chooseDuration(int log) {
        editor.durationLog = log;
        if (!editor.inputMode && editor.selected != null) {
            editor.setDuration(log, editor.dots);
        }
        rebuildWidgets();
    }

    private void chooseDots(int n) {
        editor.dots = editor.dots == n ? 0 : n;
        if (!editor.inputMode && editor.selectedChord() != null) {
            editor.setDuration(editor.selectedChord().duration.log(), editor.dots);
        }
        rebuildWidgets();
    }

    private void rest() {
        if (editor.inputMode) {
            editor.enterRest();
        } else {
            editor.delete();
        }
    }

    private void setZoom(int next) {
        float centre = (scrollX + (areaRight() - areaLeft() - NAMES) / 2f) / space();
        zoom = Math.max(0, Math.min(ZOOMS.length - 1, next));
        view = new ScoreView(editor.score, font, space());
        scrollX = Math.max(0, centre * space() - (areaRight() - areaLeft() - NAMES) / 2f);
        rebuildWidgets();
    }

    // --- playback ---------------------------------------------------------------------------

    private void togglePlay() {
        if (EditorPlayer.active()) {
            EditorPlayer.stop();
            playback = null;
        } else {
            playback = ScorePlayback.render(editor.score);
            double from = startSeconds(playback);
            // with several notes selected across time, only that stretch plays
            double to = editor.range != null && editor.rangeHasSeveral()
                    ? secondsAt(playback, editor.range.measure2(), editor.range.at2(), from) : -1;
            EditorPlayer.play(playback.events(), from, to);
        }
        rebuildWidgets();
    }

    /** When the selection (or the cursor) is first played, in seconds. */
    private double startSeconds(ScorePlayback.Result result) {
        Frac at = editor.selected != null ? editor.startOf(editor.selected)
                : editor.range != null ? editor.range.at1() : editor.inputMode ? editor.cursorAt : Frac.ZERO;
        return Math.max(0, secondsAt(result, editor.currentMeasure(), at, 0));
    }

    /** When a point in the score is first played at or after {@code notBefore} seconds (repeats play a measure more than once). */
    private double secondsAt(ScorePlayback.Result result, int m, Frac at, double notBefore) {
        Frac length = editor.score.lengthOf(m);
        for (int k = 0; k < result.order().size(); k++) {
            if (result.order().get(k) == m) {
                double start = result.measureStarts()[k];
                double end = k + 1 < result.order().size() ? result.measureStarts()[k + 1] : result.seconds();
                double t = start + (end - start) * Frac.min(at, length).divide(length).toDouble();
                if (t >= notBefore - 1e-6) {
                    return t;
                }
            }
        }
        return -1;
    }

    // --- drawing ----------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, UiTheme.c("frame"));
        int x1 = areaLeft(), y1 = areaTop(), x2 = areaRight(), y2 = areaBottom();
        g.fill(x1, y1, x2, y2, ScoreView.PAPER);
        g.enableScissor(x1 + NAMES, y1, x2, y2);
        view.selectedElement = editor.element;
        view.draw(g, x1 + NAMES, y1, scrollX, scrollY, x1 + NAMES, y1, x2, y2, this::highlight);
        drawPeers(g);
        drawSelection(g);
        drawCursors(g);
        if (editor.inputMode && mouseX >= x1 + NAMES && mouseX < x2 && mouseY >= y1 && mouseY < y2) {
            ScoreView.Hit hover = view.hit(mouseX - x1 - NAMES + scrollX, mouseY - y1 + scrollY);
            if (hover != null) {
                view.drawGhost(g, x1 + NAMES, y1, scrollX, scrollY, hover, editor.durationLog, 0x8830A0FF);
            }
        }
        if (lassoFrom != null) {
            int lx1 = (int) Math.min(lassoFrom[0], mouseX), ly1 = (int) Math.min(lassoFrom[1], mouseY);
            int lx2 = (int) Math.max(lassoFrom[0], mouseX), ly2 = (int) Math.max(lassoFrom[1], mouseY);
            g.fill(lx1, ly1, lx2, ly2, 0x2230A0FF);
            g.renderOutline(lx1, ly1, lx2 - lx1, ly2 - ly1, UiTheme.c("selected"));
        }
        g.disableScissor();
        // part names stay put while the music scrolls
        g.enableScissor(x1, y1, x1 + NAMES, y2);
        g.fill(x1, y1, x1 + NAMES, y2, ScoreView.PAPER);
        for (int p = 0; p < editor.score.parts.size(); p++) {
            String name = font.plainSubstrByWidth(editor.score.parts.get(p).name, NAMES - 6);
            float y = sy(view.staffTop(p) + 2 * view.space) - 4;
            g.drawString(font, name, x1 + 3, (int) y, p == editor.currentPart() ? UiTheme.c("selected") : ScoreView.INK, false);
        }
        g.disableScissor();
        drawPalettes(g, mouseX, mouseY);
        drawProperties(g, mouseX, mouseY);
        drawStatus(g);
        super.render(g, mouseX, mouseY, partialTick);
        if (text != null) {
            placeText();
            g.enableScissor(x1 + NAMES, y1, x2, y2);
            text.box.render(g, mouseX, mouseY, partialTick);
            g.disableScissor();
        }
        drawDropdown(g, mouseX, mouseY);
        drawPaletteTooltip(g, mouseX, mouseY);
    }

    // --- the dynamics dropdown ----------------------------------------------------------------

    private void openDropdown(ScoreElement e) {
        float[] box = view.boxOf(e);
        Chord c = editor.chordOf(e);
        if (box == null || c == null || c.dynamic == null) {
            dropdown = null;
            return;
        }
        int current = c.dynamic.ordinal();
        int scroll = Math.max(0, Math.min(Dynamic.values().length - DROP_ROWS, current - 1));
        int x = (int) Math.min(box[0], areaRight() - DROP_W - 2);
        int h = DROP_ROWS * DROP_ROW + 20;
        int y = (int) (box[3] + h < areaBottom() ? box[3] + 1 : box[1] - h - 1);
        dropdown = new Dropdown(e, x, y, scroll);
    }

    private void drawDropdown(GuiGraphics g, int mouseX, int mouseY) {
        if (dropdown == null) {
            return;
        }
        Dropdown d = dropdown;
        Chord c = editor.chordOf(d.element);
        if (c == null || c.dynamic == null) {
            dropdown = null;
            return;
        }
        int h = DROP_ROWS * DROP_ROW + 20;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(d.x - 1, d.y - 1, d.x + DROP_W + 1, d.y + h + 1, UiTheme.c("dropdown"));
        g.fill(d.x, d.y, d.x + DROP_W, d.y + h, UiTheme.c("editor_panel"));
        Dynamic[] all = Dynamic.values();
        boolean canUp = d.scroll > 0, canDown = d.scroll + DROP_ROWS < all.length;
        boolean overUp = inside(mouseX, mouseY, d.x, d.y, DROP_W, 10), overDown = inside(mouseX, mouseY, d.x, d.y + h - 10, DROP_W, 10);
        g.fill(d.x, d.y, d.x + DROP_W, d.y + 10, overUp && canUp ? UiTheme.c("section_hover") : UiTheme.c("section"));
        g.drawCenteredString(font, "▲", d.x + DROP_W / 2, d.y + 1, canUp ? UiTheme.c("text") : UiTheme.c("dim"));
        for (int i = 0; i < DROP_ROWS && d.scroll + i < all.length; i++) {
            Dynamic dyn = all[d.scroll + i];
            int ry = d.y + 10 + i * DROP_ROW;
            boolean over = inside(mouseX, mouseY, d.x, ry, DROP_W, DROP_ROW);
            g.fill(d.x, ry, d.x + DROP_W, ry + DROP_ROW, dyn == c.dynamic ? UiTheme.c("selected") : over ? 0xFFFFF6DE : ScoreView.PAPER);
            int ink = dyn == c.dynamic ? 0xFFFFFFFF : ScoreView.INK;
            String name = dyn.name().toLowerCase(Locale.ROOT);
            float w = 0;
            for (char ch : name.toCharArray()) {
                w += (ch == 'm' ? 12 : ch == 'f' ? 8 : 9) * 0.6f;
            }
            float dx = d.x + DROP_W / 2f - w / 2;
            for (char ch : name.toCharArray()) {
                ScoreGlyphs.draw(g, "dyn_" + ch, dx, ry + DROP_ROW - 2, 0.6f, ink);
                dx += (ch == 'm' ? 12 : ch == 'f' ? 8 : 9) * 0.6f;
            }
        }
        g.fill(d.x, d.y + h - 10, d.x + DROP_W, d.y + h, overDown && canDown ? UiTheme.c("section_hover") : UiTheme.c("section"));
        g.drawCenteredString(font, "▼", d.x + DROP_W / 2, d.y + h - 9, canDown ? UiTheme.c("text") : UiTheme.c("dim"));
        g.pose().popPose();
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** A click while the dropdown is open: picks, scrolls, or (anywhere else) closes it. */
    private boolean clickDropdown(double mx, double my) {
        Dropdown d = dropdown;
        int h = DROP_ROWS * DROP_ROW + 20;
        if (!inside(mx, my, d.x, d.y, DROP_W, h)) {
            dropdown = null;
            return false;
        }
        Dynamic[] all = Dynamic.values();
        if (my < d.y + 10) {
            d.scroll = Math.max(0, d.scroll - 1);
        } else if (my >= d.y + h - 10) {
            d.scroll = Math.min(all.length - DROP_ROWS, d.scroll + 1);
        } else {
            int i = d.scroll + (int) ((my - d.y - 10) / DROP_ROW);
            if (i < all.length) {
                dropdown = null;
                editor.changeDynamic(d.element, all[i]);
            }
        }
        return true;
    }

    // --- typing lyrics, chord symbols and text ------------------------------------------------

    /** Starts typing text of a kind on what's selected (lyrics on a note; a rehearsal mark on a measure). */
    private void startText(ScoreElement.Kind kind) {
        finishText();
        ChordRef ref = editor.selected;
        if (ref == null && editor.element != null && editor.element.kind().onChord()) {
            ScoreElement e = editor.element;
            ref = new ChordRef(e.part(), e.measure(), e.voice(), e.chord(), -1);
        }
        if (ref == null && editor.range != null) {
            ref = editor.chordAt(editor.range.part1(), editor.range.measure1(), editor.range.at1());
        }
        if (kind != ScoreElement.Kind.REHEARSAL) {
            if (ref == null) {
                return;
            }
            ref = new ChordRef(ref.part(), ref.measure(), ref.voice(), ref.chord(), -1);
            if (kind == ScoreElement.Kind.LYRIC && editor.voiceOf(ref).get(ref.chord()).isRest()) {
                ref = editor.neighbour(ref, 1, true); // lyrics go under notes
                if (ref == null) {
                    return;
                }
            }
        }
        editor.inputMode = false;
        EditBox box = new EditBox(font, 0, 0, 64, 12, Component.empty());
        box.setMaxLength(Score.MAX_TEXT);
        TextEntry entry = new TextEntry(kind, box);
        entry.ref = ref;
        entry.measure = kind == ScoreElement.Kind.REHEARSAL ? editor.currentMeasure() : ref.measure();
        if (ref != null && kind == ScoreElement.Kind.LYRIC) {
            entry.verse = editor.element != null && editor.element.kind() == ScoreElement.Kind.LYRIC ? editor.element.index() : 0;
        }
        text = entry;
        loadText();
        box.setFocused(true);
        if (ref != null) {
            editor.selected = ref;
            editor.element = null;
            editor.range = null;
        }
        scrollTo(entry.measure);
        rebuildWidgets();
    }

    /** Fills the box with what's already written where it now is. */
    private void loadText() {
        TextEntry t = text;
        String value = "";
        if (t.kind == ScoreElement.Kind.REHEARSAL) {
            value = editor.score.bars.get(t.measure).rehearsal;
        } else if (t.ref != null && t.ref.chord() < editor.voiceOf(t.ref).size()) {
            Chord c = editor.voiceOf(t.ref).get(t.ref.chord());
            value = switch (t.kind) {
                case LYRIC -> t.verse < c.lyrics.size() ? c.lyrics.get(t.verse) : "";
                case CHORD_SYMBOL -> c.chordSymbol;
                default -> c.staffText;
            };
        }
        t.box.setValue(value == null ? "" : value);
    }

    private void commitText() {
        TextEntry t = text;
        if (t == null) {
            return;
        }
        String value = t.box.getValue();
        switch (t.kind) {
            case LYRIC -> editor.setLyric(t.ref, t.verse, value);
            case CHORD_SYMBOL -> editor.setChordSymbol(t.ref, value);
            case STAFF_TEXT -> editor.setStaffText(t.ref, value);
            default -> editor.setRehearsal(t.measure, value);
        }
    }

    private void finishText() {
        if (text != null) {
            commitText();
            text = null;
            rebuildWidgets();
        }
    }

    /** Lyrics and chord symbols: on to the next (or previous) note, as Space and Shift+Space do in MuseScore. */
    private void moveText(int direction) {
        TextEntry t = text;
        commitText();
        ChordRef next = t.ref == null ? null : editor.neighbour(t.ref, direction, t.kind == ScoreElement.Kind.LYRIC);
        if (next == null) {
            text = null;
            rebuildWidgets();
            return;
        }
        t.ref = next;
        t.measure = next.measure();
        editor.selected = next;
        loadText();
        scrollTo(next.measure());
    }

    /** Keeps the text box over the note (or measure) it's for. */
    private void placeText() {
        TextEntry t = text;
        int m = Math.min(t.measure, editor.score.measureCount() - 1);
        if (t.ref != null && (t.ref.part() >= editor.score.parts.size() || t.ref.chord() >= editor.voiceOf(t.ref).size())) {
            text = null;
            return;
        }
        int part = t.ref != null ? t.ref.part() : 0;
        Frac at = t.ref != null ? editor.startOf(t.ref) : Frac.ZERO;
        float x = sx(view.measureLeft(m) + view.xAt(m, at)) - 2;
        float top = sy(view.staffTop(part));
        float y = switch (t.kind) {
            case LYRIC -> view.lyricY(top, t.verse);
            case CHORD_SYMBOL -> view.chordSymbolY(top);
            case STAFF_TEXT -> view.staffTextY(top);
            default -> top - 4.6f * view.space - 12;
        };
        t.box.setWidth(Math.max(40, font.width(t.box.getValue()) + 14));
        t.box.setX((int) x);
        t.box.setY((int) y - 2);
    }

    private boolean textKey(int key, int scan, int modifiers) {
        TextEntry t = text;
        boolean moves = t.kind == ScoreElement.Kind.LYRIC || t.kind == ScoreElement.Kind.CHORD_SYMBOL;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> finishText();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (t.kind == ScoreElement.Kind.LYRIC && t.verse < 7) {
                    commitText(); // Enter: the next verse, under the same note
                    t.verse++;
                    loadText();
                } else {
                    finishText();
                }
            }
            case GLFW.GLFW_KEY_TAB -> {
                if (moves) {
                    moveText(hasShiftDown() ? -1 : 1);
                } else {
                    finishText();
                }
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (moves && t.box.getValue().isEmpty()) {
                    moveText(-1); // back to the note before, as in MuseScore
                } else {
                    t.box.keyPressed(key, scan, modifiers);
                }
            }
            default -> t.box.keyPressed(key, scan, modifiers);
        }
        return true;
    }

    private boolean textChar(char c, int modifiers) {
        TextEntry t = text;
        if (c == ' ' && (t.kind == ScoreElement.Kind.LYRIC || t.kind == ScoreElement.Kind.CHORD_SYMBOL)) {
            moveText(hasShiftDown() ? -1 : 1);
            return true;
        }
        if (c == '-' && t.kind == ScoreElement.Kind.LYRIC) {
            if (!t.box.getValue().endsWith("-")) {
                t.box.setValue(t.box.getValue() + "-"); // a syllable joined to the next one
            }
            moveText(1);
            return true;
        }
        return t.box.charTyped(c, modifiers);
    }

    private int highlight(int part, int measure, int voice, int chord, int note) {
        return editor.isSelected(part, measure, voice, chord, note) ? ScoreView.SELECTED : 0;
    }

    private void drawSelection(GuiGraphics g) {
        ScoreEditor.Range r = editor.range;
        if (r == null) {
            return;
        }
        float x1 = sx(view.measureLeft(r.measure1()) + (r.at1().isZero() ? 0 : view.xAt(r.measure1(), r.at1()) - view.space * 0.6f));
        float x2 = sx(r.at2().equals(editor.score.lengthOf(r.measure2())) ? view.measureRight(r.measure2())
                : view.measureLeft(r.measure2()) + view.xAt(r.measure2(), r.at2()) - view.space * 0.6f);
        float y1 = sy(view.staffTop(r.part1()) - view.space), y2 = sy(view.staffTop(r.part2()) + 5 * view.space);
        g.fill((int) x1, (int) y1, (int) x2, (int) y2, UiTheme.c("highlight"));
        g.renderOutline((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1), UiTheme.c("selected"));
    }

    /** Everyone else editing: their selection outlined, or their note-input caret, in their colour with their name. */
    private void drawPeers(GuiGraphics g) {
        for (ScoreEditor.Peer peer : editor.peers.values()) {
            ScorePackets.CursorState c = peer.state();
            if (!c.shown() || c.part2() >= editor.score.parts.size() || c.measure2() >= editor.score.measureCount() || c.measure1() > c.measure2()) {
                continue;
            }
            int color = peer.color();
            float x1 = sx(view.measureLeft(c.measure1()) + view.xAt(c.measure1(), c.at1()) - (c.at1().isZero() ? view.xAt(c.measure1(), Frac.ZERO) : view.space * 0.6f));
            float y1 = sy(view.staffTop(c.part1()) - view.space), y2 = sy(view.staffTop(c.part2()) + 5 * view.space);
            if (c.input()) {
                float x = sx(view.measureLeft(c.measure1()) + view.xAt(c.measure1(), c.at1()));
                g.fill((int) x - 1, (int) y1, (int) x + 2, (int) y2, color);
                x1 = x;
            } else {
                float x2 = sx(c.at2().compareTo(editor.score.lengthOf(c.measure2())) >= 0 ? view.measureRight(c.measure2())
                        : view.measureLeft(c.measure2()) + view.xAt(c.measure2(), c.at2()) - view.space * 0.6f);
                g.fill((int) x1, (int) y1, (int) Math.max(x1 + 2, x2), (int) y2, (color & 0x00FFFFFF) | 0x28000000);
                g.renderOutline((int) x1, (int) y1, (int) Math.max(2, x2 - x1), (int) (y2 - y1), color);
            }
            int w = font.width(peer.name()) + 4;
            g.fill((int) x1, (int) y1 - 10, (int) x1 + w, (int) y1, color);
            g.drawString(font, peer.name(), (int) x1 + 2, (int) y1 - 9, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft.level != null && minecraft.level.getGameTime() % 2 == 0) {
            editor.sendCursor(); // others see where we are, ten times a second at most
        }
    }

    private void drawCursors(GuiGraphics g) {
        if (editor.inputMode && editor.cursorMeasure < editor.score.measureCount()) {
            float x = sx(view.measureLeft(editor.cursorMeasure) + view.xAt(editor.cursorMeasure, editor.cursorAt));
            float y = sy(view.staffTop(editor.cursorPart));
            g.fill((int) x - 1, (int) (y - view.space), (int) x + 2, (int) (y + 5 * view.space), UiTheme.c("selected"));
        }
        double t = EditorPlayer.seconds();
        if (playback != null && t >= 0) {
            double[] starts = playback.measureStarts();
            int k = 0;
            while (k + 1 < starts.length && starts[k + 1] <= t) {
                k++;
            }
            if (k < playback.order().size()) {
                int m = playback.order().get(k);
                double end = k + 1 < starts.length ? starts[k + 1] : playback.seconds();
                double share = Math.max(0, Math.min(1, (t - starts[k]) / Math.max(1e-6, end - starts[k])));
                Frac at = Frac.of(Math.round(share * 960), 960).times(editor.score.lengthOf(m));
                float x = view.measureLeft(m) + view.xAt(m, at);
                float visible = areaRight() - areaLeft() - NAMES;
                if (x < scrollX || x > scrollX + visible - 40) {
                    scrollX = Math.max(0, x - 40); // page along with the music
                }
                g.fill((int) sx(x), areaTop(), (int) sx(x) + 2, areaBottom(), UiTheme.c("play"));
            }
        } else if (playback != null && !EditorPlayer.active()) {
            playback = null;
        }
    }

    private void drawStatus(GuiGraphics g) {
        int y = height - STATUS;
        g.fill(0, y, width, height, UiTheme.c("editor_panel"));
        String text;
        Chord c = editor.selectedChord();
        if (notice != null && net.minecraft.Util.getMillis() < noticeUntil) {
            text = notice.getString();
        } else if (this.text != null) {
            text = switch (this.text.kind) {
                case LYRIC -> Tr.c("Lyrics, verse %s · Space or - for the next note, Enter for the next verse, Esc to finish",
                        this.text.verse + 1).getString();
                case CHORD_SYMBOL -> Tr.s("Chord symbol · Space for the next chord, Esc to finish");
                case STAFF_TEXT -> Tr.s("Staff text · Enter to finish");
                default -> Tr.s("Rehearsal mark · Enter to finish");
            };
        } else if (editor.element != null) {
            ScoreElement e = editor.element;
            Chord on = editor.chordOf(e);
            String what = e.kind() == ScoreElement.Kind.DYNAMIC && on != null && on.dynamic != null
                    ? Tr.c("Dynamic %s", on.dynamic.name().toLowerCase(Locale.ROOT)).getString() : Tr.s(e.kind().label);
            text = (e.kind() == ScoreElement.Kind.DYNAMIC ? Tr.c("%s · measure %s · click it to change · Del or Backspace removes it", what, e.measure() + 1)
                    : Tr.c("%s · measure %s · Del or Backspace removes it", what, e.measure() + 1)).getString();
        } else if (editor.inputMode) {
            String length = Tr.s(DURATION_NAMES[editor.durationLog + 1]);
            text = Tr.c("Note input · voice %s · %s · A–G notes, Shift adds to the chord, 0 rest, Esc to stop", editor.voice + 1,
                    editor.dots > 0 ? Tr.c("%s dotted", length).getString() : length).getString();
        } else if (c != null) {
            text = Tr.c("%s · measure %s · %s · ↑↓ pitch, 1–8 length, Del delete, N note input", editor.score.parts.get(editor.selected.part()).name,
                    editor.selected.measure() + 1, describe(c)).getString();
        } else if (editor.range != null) {
            text = Tr.c("Measures %s–%s · Ctrl+C copy, Ctrl+V paste, Del clear", editor.range.measure1() + 1, editor.range.measure2() + 1).getString();
        } else {
            text = Tr.s("Click a note, a mark or a measure · N: note input · Space: play · drag open space to move around · Ctrl+wheel zooms");
        }
        int right = width - 4;
        if (!editor.peers.isEmpty()) { // who else is editing, each in their colour
            for (ScoreEditor.Peer peer : editor.peers.values()) {
                right -= font.width(peer.name());
                g.drawString(font, peer.name(), right, y + 2, peer.color(), false);
                right -= 6;
            }
            String with = Tr.s("Editing with:");
            right -= font.width(with);
            g.drawString(font, with, right, y + 2, UiTheme.c("dim"), false);
            right -= 8;
        }
        g.drawString(font, font.plainSubstrByWidth(text, right - 4), 4, y + 2, UiTheme.c("text"), false);
    }

    private static String describe(Chord c) {
        String name = Tr.s(DURATION_NAMES[c.duration.log() + 1]).toLowerCase(Locale.ROOT);
        String length = c.duration.dots() > 0 ? Tr.c("%s dotted", name).getString() : name;
        if (c.isRest()) {
            return Tr.c("%s rest", length).getString();
        }
        StringBuilder names = new StringBuilder();
        for (Note n : c.notes) {
            names.append(names.isEmpty() ? "" : " ").append("CDEFGAB".charAt(n.step()))
                    .append(n.alter() > 0 ? "♯".repeat(n.alter()) : n.alter() < 0 ? "♭".repeat(-n.alter()) : "").append(n.octave());
        }
        return length + " " + names;
    }

    /** The properties panel: a heading per section, each folding up when clicked. */
    private void drawProperties(GuiGraphics g, int mouseX, int mouseY) {
        int x = width - RIGHT;
        g.fill(x, TOOLBAR, width, height - STATUS, UiTheme.c("editor_panel"));
        for (Section sec : sections) {
            if (sec.y() < TOOLBAR || sec.y() + 12 > height - STATUS) {
                continue;
            }
            boolean open = !closedSections.contains(sec.key());
            boolean hover = inside(mouseX, mouseY, x + 2, sec.y(), RIGHT - 4, 12);
            g.fill(x + 2, sec.y(), width - 2, sec.y() + 12, hover ? UiTheme.c("section_hover") : UiTheme.c("section"));
            String label = font.plainSubstrByWidth((open ? "▾ " : "▸ ") + sec.label(), RIGHT - 10);
            g.drawString(font, label, x + 5, sec.y() + 2, UiTheme.c("text"), false);
        }
        for (Subtab tab : subtabs) {
            if (tab.y() < TOOLBAR || tab.y() + 12 > height - STATUS) {
                continue;
            }
            boolean hover = inside(mouseX, mouseY, x + 8, tab.y(), RIGHT - 10, 12);
            g.fill(x + 8, tab.y(), width - 2, tab.y() + 12, tab.open() ? UiTheme.c("tab_open") : hover ? UiTheme.c("tab_hover") : UiTheme.c("tab"));
            String label = font.plainSubstrByWidth((tab.open() ? "▾ " : "▸ ") + tab.label(), RIGHT - 16);
            g.drawString(font, label, x + 11, tab.y() + 2, tab.part() == editor.currentPart() ? UiTheme.c("selected_text") : UiTheme.c("text"), false);
        }
        if (tempoY >= TOOLBAR && tempoY < height - STATUS) {
            g.drawString(font, "BPM", x + 50, tempoY + 3, UiTheme.c("dim"), false);
        }
    }

    private @Nullable String sectionAt(double mx, double my) {
        if (mx < width - RIGHT) {
            return null;
        }
        for (Section sec : sections) {
            if (my >= sec.y() && my < sec.y() + 12) {
                return sec.key();
            }
        }
        return null;
    }

    /** A palette as shown: during a search, only its matching items, and always open. */
    private record ShownPalette(EditorPalettes.Category category, List<EditorPalettes.Item> items, boolean open) {}

    private static List<ShownPalette> shownPalettes() {
        String q = paletteQuery.strip().toLowerCase(Locale.ROOT);
        List<ShownPalette> shown = new ArrayList<>();
        for (EditorPalettes.Category cat : EditorPalettes.CATEGORIES) {
            if (q.isEmpty()) {
                shown.add(new ShownPalette(cat, cat.items(), openPalettes.contains(cat.name())));
                continue;
            }
            boolean whole = Tr.s(cat.name()).toLowerCase(Locale.ROOT).contains(q);
            List<EditorPalettes.Item> items = new ArrayList<>();
            for (EditorPalettes.Item item : cat.items()) {
                if (whole || item.tooltip().toLowerCase(Locale.ROOT).contains(q) || item.text().toLowerCase(Locale.ROOT).contains(q)) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                shown.add(new ShownPalette(cat, items, true));
            }
        }
        return shown;
    }

    private static final int PALETTE_COLS = (LEFT - 4) / 23;

    private int paletteTop() {
        return TOOLBAR + PALETTE_SEARCH;
    }

    /** Palettes: the search box, then a heading per category; open ones show their items in a grid. */
    private void drawPalettes(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, TOOLBAR, LEFT, height - STATUS, UiTheme.c("editor_panel"));
        g.enableScissor(0, paletteTop(), LEFT, height - STATUS);
        float y = paletteTop() + 2 - paletteScroll;
        List<ShownPalette> shown = shownPalettes();
        for (ShownPalette sp : shown) {
            boolean hoverHeading = mouseX >= 2 && mouseX < LEFT - 2 && mouseY >= y && mouseY < y + 12 && mouseY >= paletteTop();
            g.fill(2, (int) y, LEFT - 2, (int) y + 12, hoverHeading ? UiTheme.c("section_hover") : UiTheme.c("section"));
            g.drawString(font, (sp.open() ? "▾ " : "▸ ") + Tr.s(sp.category().name()), 5, (int) y + 2, UiTheme.c("text"), false);
            y += 14;
            if (sp.open()) {
                for (int i = 0; i < sp.items().size(); i++) {
                    int cx = 3 + (i % PALETTE_COLS) * 23;
                    int cy = (int) y + (i / PALETTE_COLS) * 23;
                    boolean hover = mouseX >= cx && mouseX < cx + 22 && mouseY >= cy && mouseY < cy + 22;
                    g.fill(cx, cy, cx + 22, cy + 22, hover ? 0xFFFFF6DE : ScoreView.PAPER);
                    EditorPalettes.Item item = sp.items().get(i);
                    if (item.glyph() != null) {
                        drawPaletteGlyph(g, item.glyph(), cx, cy);
                    }
                    if (!item.text().isEmpty()) {
                        String t = font.plainSubstrByWidth(item.text(), 22);
                        g.drawString(font, t, cx + 11 - font.width(t) / 2, cy + (item.glyph() == null ? 7 : 13), ScoreView.INK, false);
                    }
                }
                y += ((sp.items().size() + PALETTE_COLS - 1) / PALETTE_COLS) * 23 + 2;
            }
        }
        if (shown.isEmpty()) {
            g.drawString(font, Tr.s("Nothing found"), 6, paletteTop() + 4, UiTheme.c("dim"), false);
        }
        g.disableScissor();
    }

    private static void drawPaletteGlyph(GuiGraphics g, String glyph, int cx, int cy) {
        if (glyph.startsWith("arpeggio")) { // drawn, not from the atlas
            com.minescore.score.Notation.Arpeggio type = glyph.endsWith("_up") ? com.minescore.score.Notation.Arpeggio.UP
                    : glyph.endsWith("_down") ? com.minescore.score.Notation.Arpeggio.DOWN : com.minescore.score.Notation.Arpeggio.NORMAL;
            ScoreView.drawArpeggio(g, cx + 10, cy + 5, cy + 17, type, 6, ScoreView.INK);
            return;
        }
        float scale = glyph.startsWith("clef_treble") ? 0.3f : glyph.startsWith("clef") ? 0.5f : 0.9f;
        float w = ScoreGlyphs.width(glyph, scale);
        float[] anchorY = {cy + 11};
        if (glyph.startsWith("clef_treble")) {
            anchorY[0] = cy + 15;
        }
        ScoreGlyphs.draw(g, glyph, cx + 11 - w / 2, anchorY[0], scale, ScoreView.INK);
    }

    private @Nullable EditorPalettes.Item paletteItemAt(double mx, double my) {
        if (mx < 0 || mx >= LEFT || my < paletteTop() || my >= height - STATUS) {
            return null;
        }
        float y = paletteTop() + 2 - paletteScroll;
        for (ShownPalette sp : shownPalettes()) {
            y += 14;
            if (sp.open()) {
                for (int i = 0; i < sp.items().size(); i++) {
                    int cx = 3 + (i % PALETTE_COLS) * 23;
                    int cy = (int) y + (i / PALETTE_COLS) * 23;
                    if (mx >= cx && mx < cx + 22 && my >= cy && my < cy + 22) {
                        return sp.items().get(i);
                    }
                }
                y += ((sp.items().size() + PALETTE_COLS - 1) / PALETTE_COLS) * 23 + 2;
            }
        }
        return null;
    }

    private @Nullable String paletteHeadingAt(double mx, double my) {
        if (mx < 0 || mx >= LEFT || my < paletteTop() || !paletteQuery.isBlank()) {
            return null; // while searching, every palette with a match stays open
        }
        float y = paletteTop() + 2 - paletteScroll;
        for (ShownPalette sp : shownPalettes()) {
            if (my >= y && my < y + 12) {
                return sp.category().name();
            }
            y += 14;
            if (sp.open()) {
                y += ((sp.items().size() + PALETTE_COLS - 1) / PALETTE_COLS) * 23 + 2;
            }
        }
        return null;
    }

    private void drawPaletteTooltip(GuiGraphics g, int mouseX, int mouseY) {
        EditorPalettes.Item item = paletteItemAt(mouseX, mouseY);
        if (item != null) {
            g.renderTooltip(font, Component.literal(item.tooltip()), mouseX, mouseY);
        }
    }

    // --- mouse --------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (dropdown != null && clickDropdown(mx, my)) {
            return true;
        }
        if (text != null && !text.box.isMouseOver(mx, my)) {
            finishText();
        } else if (text != null) {
            return text.box.mouseClicked(mx, my, button);
        }
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        boolean leftField = false;
        for (EditBox box : fields) {
            if (!box.isMouseOver(mx, my) && box.isFocused()) {
                box.setFocused(false);
                leftField = true;
            }
        }
        if (leftField) {
            commitFields(); // after the loop: an edit rebuilds the widgets, fields included
        }
        if (mx >= width - RIGHT + 8) {
            for (Subtab tab : subtabs) {
                if (my >= tab.y() && my < tab.y() + 12 && my >= TOOLBAR && my < height - STATUS) {
                    commitFields();
                    if (tab.open()) {
                        partFolded = true;
                    } else {
                        shownPart = tab.part();
                        partFolded = false;
                    }
                    rebuildWidgets();
                    return true;
                }
            }
        }
        String sectionKey = sectionAt(mx, my);
        if (sectionKey != null) {
            commitFields();
            if (!closedSections.remove(sectionKey)) {
                closedSections.add(sectionKey);
            }
            rebuildWidgets();
            return true;
        }
        String heading = paletteHeadingAt(mx, my);
        if (heading != null) {
            if (!openPalettes.remove(heading)) {
                openPalettes.add(heading);
            }
            return true;
        }
        EditorPalettes.Item item = paletteItemAt(mx, my);
        if (item != null) {
            if (editor.element != null && editor.element.kind().onChord()) {
                ScoreElement e = editor.element; // a palette item goes on the chord the selected mark is on
                editor.selected = new ChordRef(e.part(), e.measure(), e.voice(), e.chord(), -1);
                editor.element = null;
            }
            item.apply().accept(editor);
            if (editor.wantsText != null) {
                ScoreElement.Kind kind = editor.wantsText;
                editor.wantsText = null;
                startText(kind);
            }
            rebuildWidgets();
            return true;
        }
        if (my < areaTop() || my >= areaBottom() || mx < areaLeft() || mx >= areaRight()) {
            return false;
        }
        shownPart = -1; // the Instruments list follows the part being edited again
        if (button == 2) { // the middle button drags the view from anywhere
            panFrom = new double[] {mx, my, scrollX, scrollY};
            return true;
        }
        if (mx < areaLeft() + NAMES) { // a part's name: its whole staff
            ScoreView.Hit row = view.hit(0, (float) (my - areaTop() + scrollY));
            if (row != null) {
                editor.selectStaff(row.part());
                rebuildWidgets();
            }
            return true;
        }
        float lx = (float) (mx - areaLeft() - NAMES + scrollX), ly = (float) (my - areaTop() + scrollY);
        ScoreElement element = editor.inputMode || button != 0 ? null : view.elementAt(mx, my);
        if (element != null) { // a dynamic, articulation, line… on its own
            editor.selectElement(element);
            if (element.kind() == ScoreElement.Kind.DYNAMIC) {
                openDropdown(element);
            }
            rebuildWidgets();
            return true;
        }
        ScoreView.Hit hit = view.hit(lx, ly);
        if (hit == null || !view.onStaff(lx, ly)) {
            panFrom = new double[] {mx, my, scrollX, scrollY}; // open space: drag the view around
            return true;
        }
        if (editor.inputMode && button == 0) {
            if (hasShiftDown()) {
                editor.addNoteAt(hit.part(), hit.measure(), editor.voice, hit.at(), hit.step());
            } else {
                editor.enterStep(hit.part(), hit.measure(), hit.at(), hit.step());
            }
            return true;
        }
        ChordRef ref = chordAt(hit, lx);
        boolean selecting = editor.selected != null || editor.range != null;
        if (hasControlDown() && ref != null) {
            editor.toggleAlso(ref);
        } else if (hasShiftDown() && selecting) {
            if (ref != null) {
                Chord c = editor.voiceOf(ref).get(ref.chord());
                editor.extendTo(ref.part(), ref.measure(), editor.startOf(ref), c.length());
            } else {
                editor.extendTo(hit.part(), hit.measure(), Frac.ZERO, editor.score.lengthOf(hit.measure()));
            }
        } else if (ref != null) {
            editor.select(ref);
            dragging = ref;
            dragStartY = my;
        } else if (hasShiftDown()) {
            lassoFrom = new double[] {mx, my}; // Shift+drag on empty staff draws a box to select
        } else {
            editor.selectMeasures(hit.part(), hit.part(), hit.measure(), hit.measure());
            lassoFrom = new double[] {mx, my}; // or drag on from here to cover more measures
        }
        rebuildWidgets();
        return true;
    }

    /**
     * The chord (and note) at a clicked point: the chord starting at that time nearest the click's pitch, or a
     * grace note before it when the click is just to its left.
     */
    private @Nullable ChordRef chordAt(ScoreView.Hit hit, float x) {
        Part.Measure measure = editor.score.parts.get(hit.part()).measures.get(hit.measure());
        boolean graceSide = x < view.measureLeft(hit.measure()) + view.xAt(hit.measure(), hit.at()) - view.space * 0.8f;
        ChordRef best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int v = 0; v < Part.VOICES; v++) {
            List<Chord> voice = measure.voices[v];
            List<Frac> starts = VoiceEdit.starts(voice);
            for (int i = 0; i < voice.size(); i++) {
                if (!starts.get(i).equals(hit.at()) || voice.get(i).isGrace() != graceSide) {
                    continue;
                }
                Chord c = voice.get(i);
                if (c.isRest()) {
                    int d = 6 + (v == editor.voice ? 0 : 1);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = new ChordRef(hit.part(), hit.measure(), v, i, -1);
                    }
                    continue;
                }
                for (int n = 0; n < c.notes.size(); n++) {
                    int d = Math.abs(c.notes.get(n).staffStep() - hit.step()) * 2 + (v == editor.voice ? 0 : 1);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = new ChordRef(hit.part(), hit.measure(), v, i, n);
                    }
                }
            }
        }
        if (best == null && graceSide) {
            return chordAt(hit, Float.MAX_VALUE); // no grace note there: the chord itself
        }
        return bestDistance <= 12 ? best : null;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panFrom != null) {
            float visibleW = areaRight() - areaLeft() - NAMES, visibleH = areaBottom() - areaTop();
            scrollX = (float) Math.max(0, Math.min(Math.max(0, view.width() - visibleW + 40), panFrom[2] - (mx - panFrom[0])));
            scrollY = (float) Math.max(0, Math.min(Math.max(0, view.height() - visibleH + 20), panFrom[3] - (my - panFrom[1])));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null && editor.selected != null && editor.selected.note() >= 0) {
            int steps = (int) Math.round((dragStartY - my) / (view.space / 2));
            if (steps != 0) {
                editor.moveSteps(steps); // dragged a note up or down the staff
            }
        }
        dragging = null;
        panFrom = null;
        if (lassoFrom != null) {
            double[] from = lassoFrom;
            lassoFrom = null;
            if (Math.abs(mx - from[0]) > 4 || Math.abs(my - from[1]) > 4) {
                ScoreView.Hit a = view.hit((float) (Math.min(mx, from[0]) - areaLeft() - NAMES + scrollX), (float) (Math.min(my, from[1]) - areaTop() + scrollY));
                ScoreView.Hit b = view.hit((float) (Math.max(mx, from[0]) - areaLeft() - NAMES + scrollX), (float) (Math.max(my, from[1]) - areaTop() + scrollY));
                if (a != null && b != null) {
                    editor.selectMeasures(a.part(), b.part(), Math.min(a.measure(), b.measure()), Math.max(a.measure(), b.measure()));
                    rebuildWidgets();
                }
            }
        }
        boolean handled = super.mouseReleased(mx, my, button);
        if (rebuildAfterDrag) {
            rebuildAfterDrag = false;
            rebuildWidgets();
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (dropdown != null && inside(mx, my, dropdown.x, dropdown.y, DROP_W, DROP_ROWS * DROP_ROW + 20)) {
            dropdown.scroll = Math.max(0, Math.min(Dynamic.values().length - DROP_ROWS, dropdown.scroll - (int) Math.signum(delta)));
            return true;
        }
        if (mx >= width - RIGHT) {
            int room = height - STATUS - TOOLBAR;
            propScroll = Math.max(0, Math.min(Math.max(0, propertiesHeight - room + 8), propScroll - (int) Math.signum(delta) * 20));
            commitFields();
            rebuildWidgets();
            return true;
        }
        if (mx < LEFT) {
            paletteScroll = Math.max(0, paletteScroll - (float) delta * 20);
            return true;
        }
        if (hasControlDown()) {
            setZoom(zoom + (delta > 0 ? 1 : -1));
            return true;
        }
        float visibleW = areaRight() - areaLeft() - NAMES, visibleH = areaBottom() - areaTop();
        if (hasShiftDown()) {
            scrollY = Math.max(0, Math.min(Math.max(0, view.height() - visibleH + 20), scrollY - (float) delta * 30));
        } else {
            scrollX = Math.max(0, Math.min(Math.max(0, view.width() - visibleW + 40), scrollX - (float) delta * 40));
        }
        return true;
    }

    // --- keys -------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (text != null) {
            return textKey(key, scan, modifiers);
        }
        if (dropdown != null && key == GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        for (EditBox box : fields) {
            if (box.isFocused()) {
                if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                    box.setFocused(false);
                    commitFields();
                    return true;
                }
                return box.keyPressed(key, scan, modifiers) || box.canConsumeInput();
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (editor.inputMode) {
                editor.inputMode = false;
                rebuildWidgets();
                return true;
            }
            if (editor.selected != null || editor.range != null || editor.element != null) {
                editor.select(null);
                rebuildWidgets();
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        // A note name with Shift adds it to the chord, whatever the note's own shortcut is.
        int letter = noteLetter(key);
        if (letter >= 0) {
            if (!editor.inputMode && editor.selected != null) {
                editor.inputMode = true; // typing a note name over a selection starts note input there
            }
            if (editor.inputMode) {
                editor.enterLetter(letter, hasShiftDown());
                scrollTo(editor.cursorMeasure);
                rebuildWidgets();
            }
            return true;
        }
        EditorKeys.Action action = EditorKeys.match(key);
        if (action == null) {
            return super.keyPressed(key, scan, modifiers);
        }
        switch (action) {
            case NOTE_INPUT -> editor.inputMode = !editor.inputMode;
            case REST -> rest();
            case DURATION_64 -> chooseDuration(6);
            case DURATION_32 -> chooseDuration(5);
            case DURATION_16 -> chooseDuration(4);
            case DURATION_8 -> chooseDuration(3);
            case DURATION_4 -> chooseDuration(2);
            case DURATION_2 -> chooseDuration(1);
            case DURATION_1 -> chooseDuration(0);
            case DURATION_BREVE -> chooseDuration(-1);
            case DOT -> chooseDots(1);
            case TIE -> editor.toggleTie();
            case FLAT -> editor.applyAccidental(-1);
            case SHARP -> editor.applyAccidental(1);
            case NATURAL -> editor.applyAccidental(0);
            case PITCH_UP -> editor.transpose(1);
            case PITCH_DOWN -> editor.transpose(-1);
            case OCTAVE_UP -> editor.transpose(12);
            case OCTAVE_DOWN -> editor.transpose(-12);
            case STEP_UP -> editor.moveSteps(1);
            case STEP_DOWN -> editor.moveSteps(-1);
            case NEXT -> editor.step(1);
            case PREVIOUS -> editor.step(-1);
            case NEXT_MEASURE, PREVIOUS_MEASURE -> {
                int m = Math.max(0, Math.min(editor.score.measureCount() - 1, editor.currentMeasure() + (action == EditorKeys.Action.NEXT_MEASURE ? 1 : -1)));
                editor.select(editor.firstIn(editor.currentPart(), m));
            }
            case EXTEND_NEXT -> editor.extend(1, false);
            case EXTEND_PREVIOUS -> editor.extend(-1, false);
            case EXTEND_NEXT_MEASURE -> editor.extend(1, true);
            case EXTEND_PREVIOUS_MEASURE -> editor.extend(-1, true);
            case STAFF_UP -> editor.moveStaff(-1);
            case STAFF_DOWN -> editor.moveStaff(1);
            case FIRST -> editor.toEnd(false);
            case LAST -> editor.toEnd(true);
            case DELETE, DELETE_BACK -> editor.delete();
            case SLUR -> editor.addLine(SpannerType.SLUR);
            case CRESCENDO -> editor.addLine(SpannerType.CRESCENDO);
            case DIMINUENDO -> editor.addLine(SpannerType.DIMINUENDO);
            case TUPLET_2, TUPLET_3, TUPLET_4, TUPLET_5, TUPLET_6, TUPLET_7, TUPLET_8, TUPLET_9 ->
                    editor.makeTuplet(action.ordinal() - EditorKeys.Action.TUPLET_2.ordinal() + 2);
            case ACCIACCATURA -> editor.addGrace(com.minescore.score.Notation.Grace.ACCIACCATURA);
            case APPOGGIATURA -> editor.addGrace(com.minescore.score.Notation.Grace.APPOGGIATURA);
            case LYRICS, CHORD_SYMBOL, STAFF_TEXT, REHEARSAL_MARK -> {
                startText(switch (action) {
                    case LYRICS -> ScoreElement.Kind.LYRIC;
                    case CHORD_SYMBOL -> ScoreElement.Kind.CHORD_SYMBOL;
                    case STAFF_TEXT -> ScoreElement.Kind.STAFF_TEXT;
                    default -> ScoreElement.Kind.REHEARSAL;
                });
                return true;
            }
            case REPEAT -> editor.repeat();
            case UNDO -> editor.undo();
            case REDO -> editor.redo();
            case COPY -> editor.copy();
            case CUT -> editor.cut();
            case PASTE -> editor.paste();
            case SELECT_ALL -> editor.selectMeasures(0, editor.score.parts.size() - 1, 0, editor.score.measureCount() - 1);
            case PLAY -> togglePlay();
            case ADD_MEASURE -> editor.appendMeasures(1);
            case INSERT_MEASURE -> editor.insertMeasure(false);
            case VOICE_1 -> editor.voice = 0;
            case VOICE_2 -> editor.voice = 1;
            case VOICE_3 -> editor.voice = 2;
            case VOICE_4 -> editor.voice = 3;
            case INTERVAL_2, INTERVAL_3, INTERVAL_4, INTERVAL_5, INTERVAL_6, INTERVAL_7, INTERVAL_8 ->
                    editor.addInterval(action.ordinal() - EditorKeys.Action.INTERVAL_2.ordinal() + 2);
            case ZOOM_IN -> setZoom(zoom + 1);
            case ZOOM_OUT -> setZoom(zoom - 1);
            case PREFERENCES -> minecraft.setScreen(new EditorKeysScreen(this));
            case SEARCH_PALETTES -> {
                if (paletteSearch != null) {
                    for (EditBox box : fields) {
                        box.setFocused(false);
                    }
                    paletteSearch.setFocused(true);
                    setFocused(paletteSearch);
                }
                return true;
            }
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        scrollTo(editor.currentMeasure());
        rebuildWidgets();
        return true;
    }

    /** The letter (0 = C … 6 = B) a key types as a note name, with or without Shift; -1 if it isn't one. */
    private static int noteLetter(int key) {
        EditorKeys.Action[] names = {EditorKeys.Action.NOTE_C, EditorKeys.Action.NOTE_D, EditorKeys.Action.NOTE_E, EditorKeys.Action.NOTE_F,
                EditorKeys.Action.NOTE_G, EditorKeys.Action.NOTE_A, EditorKeys.Action.NOTE_B};
        for (int i = 0; i < names.length; i++) {
            EditorKeys.Combo combo = EditorKeys.get(names[i]);
            if (combo.key() == key && combo.ctrl() == hasControlDown() && combo.alt() == hasAltDown()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (text != null) {
            return textChar(c, modifiers);
        }
        for (EditBox box : fields) {
            if (box.isFocused()) {
                return box.charTyped(c, modifiers);
            }
        }
        return false;
    }

    /** Scrolls so a measure (and a part's staff, unless it's -1) is in view, if it's out of sight. */
    private void reveal(int m, int part) {
        if (m >= 0 && m < editor.score.measureCount()) {
            scrollTo(m);
        }
        if (part >= 0 && part < editor.score.parts.size()) {
            float visibleH = areaBottom() - areaTop();
            float rowTop = view.titleHeight + part * view.rowHeight;
            if (rowTop < scrollY) {
                scrollY = Math.max(0, rowTop - 10);
            } else if (rowTop + view.rowHeight > scrollY + visibleH) {
                scrollY = rowTop + view.rowHeight - visibleH + 10;
            }
        }
    }

    /** Scrolls so a measure is in view. */
    private void scrollTo(int m) {
        float visible = areaRight() - areaLeft() - NAMES;
        float left = view.measureLeft(m), right = view.measureRight(m);
        if (left < scrollX) {
            scrollX = Math.max(0, left - 20);
        } else if (right > scrollX + visible) {
            scrollX = right - visible + 20;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        finishText();
        commitFields();
        EditorPlayer.stop();
        MineScoreNetwork.sendToServer(new ScorePackets.Close(editor.desk));
        super.onClose();
    }

    /** A toolbar button showing a notation symbol, lit while its setting is on. */
    private static final class GlyphButton extends Button {
        private final String glyph;
        private final boolean on;

        GlyphButton(int x, int y, int w, int h, String glyph, boolean on, String tooltip, Runnable press) {
            super(x, y, w, h, Component.empty(), b -> press.run(), DEFAULT_NARRATION);
            this.glyph = glyph;
            this.on = on;
            setTooltip(Tooltip.create(Component.literal(tooltip)));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            if (on) {
                g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, 0x6630A0FF);
            }
            if (glyph.equals("grace")) { // a small note with a slash through its stem
                float cx = getX() + width / 2f - 3, cy = getY() + height / 2f + 3;
                ScoreGlyphs.draw(g, "head_black", cx, cy, 0.45f, 0xFFFFFFFF);
                g.fill((int) cx + 4, (int) cy - 9, (int) cx + 5, (int) cy, 0xFFFFFFFF);
                for (int k = 0; k < 5; k++) {
                    g.fill((int) cx + 2 + k, (int) cy - 3 - k, (int) cx + 3 + k, (int) cy - 2 - k, 0xFFFFFFFF);
                }
                return;
            }
            float scale = glyph.startsWith("rest") ? 0.6f : glyph.startsWith("head") ? 0.75f : 0.6f;
            float w = ScoreGlyphs.width(glyph, scale);
            ScoreGlyphs.draw(g, glyph, getX() + width / 2f - w / 2, getY() + height / 2f, scale, 0xFFFFFFFF);
        }
    }
}
