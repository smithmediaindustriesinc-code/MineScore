package com.minescore.client.screen;

import com.minescore.Tr;
import com.minescore.client.UiTheme;
import com.minescore.audio.notes.NoteInstrument;
import com.minescore.client.score.EditorPrefs;
import com.minescore.score.Instruments;
import com.minescore.score.Note;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The instrument list (SCOPE.md §8.1, §8.3), shown over the score editor when adding a part or when its
 * pencil button changes one: every note block instrument (the drums together as one drum kit), then those
 * the resource packs add. "Choose instruments…" switches the list to picking which ones it offers.
 */
public class InstrumentPickerScreen extends Screen {
    private static final int COLS = 3, BUTTON_W = 104, BUTTON_H = 20, GAP = 4;

    private final Screen parent;
    private final @Nullable Instruments.Choice current;
    private final Consumer<Instruments.Choice> chosen;
    /** Picking which instruments the list offers, instead of one for the part. */
    private boolean choosing;

    /**
     * @param current the part's instrument now (lit in the list), or null when adding a part
     * @param chosen  what to do with the instrument picked; the editor comes back first
     */
    public InstrumentPickerScreen(Screen parent, Component title, @Nullable Instruments.Choice current, Consumer<Instruments.Choice> chosen) {
        super(title);
        this.parent = parent;
        this.current = current;
        this.chosen = chosen;
    }

    /** The list opened from the config menu: only for choosing which instruments it offers. */
    public static InstrumentPickerScreen choosing(Screen parent) {
        InstrumentPickerScreen screen = new InstrumentPickerScreen(parent, Component.translatable("gui.minescore.editor.choose_instruments_title"),
                null, c -> {});
        screen.choosing = true;
        return screen;
    }

    /** Every instrument there is: the pitched note block ones, the drum kit, then the packs'. */
    private static List<Instruments.Choice> all() {
        List<Instruments.Choice> list = new ArrayList<>();
        for (NoteInstrument i : NoteInstrument.values()) {
            if (i.pitched()) {
                list.add(Instruments.Choice.of(i));
            }
        }
        list.add(Instruments.Choice.of(NoteInstrument.BASEDRUM));
        for (Instruments.Custom c : Instruments.customs()) {
            list.add(new Instruments.Choice(c.pitched() ? NoteInstrument.HARP : NoteInstrument.BASEDRUM, c.id()));
        }
        return list;
    }

    /** What the list shows: everything while choosing, else the enabled ones (and the part's own). */
    private List<Instruments.Choice> shown() {
        List<Instruments.Choice> list = new ArrayList<>();
        for (Instruments.Choice c : all()) {
            if (choosing || EditorPrefs.enabled(c.key()) || isCurrent(c)) {
                list.add(c);
            }
        }
        return list;
    }

    private boolean isCurrent(Instruments.Choice c) {
        return current != null && current.key().equals(c.key());
    }

    private int rows() {
        return Math.max(1, (shown().size() + COLS - 1) / COLS);
    }

    private int panelW() {
        return COLS * BUTTON_W + (COLS - 1) * GAP + 16;
    }

    private int panelH() {
        return 30 + rows() * (BUTTON_H + GAP) + 40;
    }

    @Override
    protected void init() {
        parent.init(minecraft, width, height); // the editor stays drawn, at this size, underneath
        int left = (width - panelW()) / 2 + 8, top = (height - panelH()) / 2 + 26;
        List<Instruments.Choice> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Instruments.Choice choice = list.get(i);
            int x = left + (i % COLS) * (BUTTON_W + GAP), y = top + (i / COLS) * (BUTTON_H + GAP);
            String label;
            if (choosing) {
                label = (EditorPrefs.enabled(choice.key()) ? "✔ " : "✘ ") + choice.name();
            } else {
                label = isCurrent(choice) ? "▶ " + choice.name() : choice.name();
            }
            Button b = addRenderableWidget(Button.builder(Component.literal(label), btn -> press(choice))
                    .bounds(x, y, BUTTON_W, BUTTON_H).tooltip(Tooltip.create(Component.literal(describe(choice)))).build());
            b.active = choosing || !isCurrent(choice);
        }
        int y = top + rows() * (BUTTON_H + GAP) + 2;
        addRenderableWidget(Button.builder(Component.translatable(choosing ? "gui.done" : "gui.minescore.editor.choose_instruments"), b -> {
            choosing = !choosing;
            rebuildWidgets();
        }).bounds(width / 2 - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).bounds(width / 2 + 4, y, 100, 20).build());
    }

    private void press(Instruments.Choice choice) {
        if (choosing) {
            EditorPrefs.setEnabled(choice.key(), !EditorPrefs.enabled(choice.key()));
            rebuildWidgets();
            return;
        }
        minecraft.setScreen(parent);
        chosen.accept(choice);
    }

    /** What an instrument's button says when hovered: its range, or the drums it has, and where it's from. */
    private static String describe(Instruments.Choice choice) {
        Instruments.Custom c = Instruments.custom(choice.custom());
        if (c != null) {
            return Tr.c("%s · from a resource pack (%s)", c.pitched() ? Tr.c("Range %s–%s", noteName(c.low()), noteName(c.high())).getString()
                    : Tr.s("A drum"), c.id()).getString();
        }
        if (!choice.instrument().pitched()) {
            return Tr.s("Bass drum, snare and hi-hat, one line each on a percussion staff");
        }
        int center = choice.instrument().center();
        return Tr.c("Note block range %s–%s", noteName(center - 12), noteName(center + 12)).getString();
    }

    private static String noteName(int midi) {
        Note n = Note.fromPitch(midi);
        return "CDEFGAB".charAt(n.step()) + (n.alter() > 0 ? "♯" : "") + n.octave();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        parent.render(g, -1, -1, partialTick);
        g.fill(0, 0, width, height, 0x90000000);
        int x = (width - panelW()) / 2, y = (height - panelH()) / 2;
        g.fill(x - 1, y - 1, x + panelW() + 1, y + panelH() + 1, UiTheme.c("edge"));
        g.fill(x, y, x + panelW(), y + panelH(), UiTheme.c("panel_solid"));
        g.drawCenteredString(font, choosing ? Component.translatable("gui.minescore.editor.choose_instruments_title") : title, width / 2, y + 8, UiTheme.c("text"));
        g.drawCenteredString(font, Component.translatable(choosing ? "gui.minescore.editor.choose_hint" : "gui.minescore.editor.instrument_hint"),
                width / 2, y + panelH() - 12, UiTheme.c("dim"));
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
