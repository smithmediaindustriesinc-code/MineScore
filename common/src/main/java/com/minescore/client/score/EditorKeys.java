package com.minescore.client.score;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.minescore.MineScore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The score editor's shortcuts (SCOPE.md §8.1), MuseScore's by default, each one rebindable in the
 * editor's Preferences. Saved per player in {@code config/minescore-editor-keys.json}.
 */
public final class EditorKeys {
    /** A key with the modifiers that must be held with it. */
    public record Combo(int key, boolean ctrl, boolean shift, boolean alt) {
        public static Combo of(int key) {
            return new Combo(key, false, false, false);
        }

        public static Combo ctrl(int key) {
            return new Combo(key, true, false, false);
        }

        public static Combo shift(int key) {
            return new Combo(key, false, true, false);
        }

        public static Combo alt(int key) {
            return new Combo(key, false, false, true);
        }

        public static Combo ctrlShift(int key) {
            return new Combo(key, true, true, false);
        }

        public boolean matches(int pressed) {
            return key != GLFW.GLFW_KEY_UNKNOWN && pressed == key && ctrl == Screen.hasControlDown() && shift == Screen.hasShiftDown() && alt == Screen.hasAltDown();
        }

        /** "Ctrl+Shift+Z", for the preferences list. */
        public String describe() {
            if (key == GLFW.GLFW_KEY_UNKNOWN) {
                return "—";
            }
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name == null) {
                name = switch (key) {
                    case GLFW.GLFW_KEY_SPACE -> "Space";
                    case GLFW.GLFW_KEY_UP -> "↑";
                    case GLFW.GLFW_KEY_DOWN -> "↓";
                    case GLFW.GLFW_KEY_LEFT -> "←";
                    case GLFW.GLFW_KEY_RIGHT -> "→";
                    case GLFW.GLFW_KEY_DELETE -> "Delete";
                    case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
                    case GLFW.GLFW_KEY_INSERT -> "Insert";
                    case GLFW.GLFW_KEY_ENTER -> "Enter";
                    case GLFW.GLFW_KEY_TAB -> "Tab";
                    case GLFW.GLFW_KEY_HOME -> "Home";
                    case GLFW.GLFW_KEY_END -> "End";
                    case GLFW.GLFW_KEY_PAGE_UP -> "Page Up";
                    case GLFW.GLFW_KEY_PAGE_DOWN -> "Page Down";
                    default -> key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25 ? "F" + (key - GLFW.GLFW_KEY_F1 + 1) : "Key " + key;
                };
            } else {
                name = name.toUpperCase(Locale.ROOT);
            }
            return (ctrl ? "Ctrl+" : "") + (alt ? "Alt+" : "") + (shift ? "Shift+" : "") + name;
        }
    }

    /** Everything a shortcut can do. The order is the order of the preferences list. */
    public enum Action {
        NOTE_INPUT("Note input on/off", Combo.of(GLFW.GLFW_KEY_N)),
        NOTE_C("Note C", Combo.of(GLFW.GLFW_KEY_C)),
        NOTE_D("Note D", Combo.of(GLFW.GLFW_KEY_D)),
        NOTE_E("Note E", Combo.of(GLFW.GLFW_KEY_E)),
        NOTE_F("Note F", Combo.of(GLFW.GLFW_KEY_F)),
        NOTE_G("Note G", Combo.of(GLFW.GLFW_KEY_G)),
        NOTE_A("Note A", Combo.of(GLFW.GLFW_KEY_A)),
        NOTE_B("Note B", Combo.of(GLFW.GLFW_KEY_B)),
        REST("Rest", Combo.of(GLFW.GLFW_KEY_0)),
        DURATION_64("64th note", Combo.of(GLFW.GLFW_KEY_1)),
        DURATION_32("32nd note", Combo.of(GLFW.GLFW_KEY_2)),
        DURATION_16("16th note", Combo.of(GLFW.GLFW_KEY_3)),
        DURATION_8("Eighth note", Combo.of(GLFW.GLFW_KEY_4)),
        DURATION_4("Quarter note", Combo.of(GLFW.GLFW_KEY_5)),
        DURATION_2("Half note", Combo.of(GLFW.GLFW_KEY_6)),
        DURATION_1("Whole note", Combo.of(GLFW.GLFW_KEY_7)),
        DURATION_BREVE("Breve", Combo.of(GLFW.GLFW_KEY_8)),
        DOT("Dot", Combo.of(GLFW.GLFW_KEY_PERIOD)),
        TIE("Tie", Combo.shift(GLFW.GLFW_KEY_EQUAL)),
        FLAT("Flat", Combo.of(GLFW.GLFW_KEY_MINUS)),
        SHARP("Sharp", Combo.of(GLFW.GLFW_KEY_EQUAL)),
        NATURAL("Natural", Combo.of(GLFW.GLFW_KEY_BACKSLASH)),
        PITCH_UP("Pitch up a semitone", Combo.of(GLFW.GLFW_KEY_UP)),
        PITCH_DOWN("Pitch down a semitone", Combo.of(GLFW.GLFW_KEY_DOWN)),
        OCTAVE_UP("Up an octave", Combo.ctrl(GLFW.GLFW_KEY_UP)),
        OCTAVE_DOWN("Down an octave", Combo.ctrl(GLFW.GLFW_KEY_DOWN)),
        STEP_UP("Up a line or space (in the key)", Combo.alt(GLFW.GLFW_KEY_UP)),
        STEP_DOWN("Down a line or space (in the key)", Combo.alt(GLFW.GLFW_KEY_DOWN)),
        NEXT("Next note", Combo.of(GLFW.GLFW_KEY_RIGHT)),
        PREVIOUS("Previous note", Combo.of(GLFW.GLFW_KEY_LEFT)),
        NEXT_MEASURE("Next measure", Combo.ctrl(GLFW.GLFW_KEY_RIGHT)),
        PREVIOUS_MEASURE("Previous measure", Combo.ctrl(GLFW.GLFW_KEY_LEFT)),
        EXTEND_NEXT("Extend selection to the next note", Combo.shift(GLFW.GLFW_KEY_RIGHT)),
        EXTEND_PREVIOUS("Extend selection to the previous note", Combo.shift(GLFW.GLFW_KEY_LEFT)),
        EXTEND_NEXT_MEASURE("Extend selection to the measure's end", Combo.ctrlShift(GLFW.GLFW_KEY_RIGHT)),
        EXTEND_PREVIOUS_MEASURE("Extend selection to the measure's start", Combo.ctrlShift(GLFW.GLFW_KEY_LEFT)),
        STAFF_UP("Move to the staff above", Combo.alt(GLFW.GLFW_KEY_PAGE_UP)),
        STAFF_DOWN("Move to the staff below", Combo.alt(GLFW.GLFW_KEY_PAGE_DOWN)),
        FIRST("Go to the start", Combo.ctrl(GLFW.GLFW_KEY_HOME)),
        LAST("Go to the end", Combo.ctrl(GLFW.GLFW_KEY_END)),
        DELETE("Delete", Combo.of(GLFW.GLFW_KEY_DELETE)),
        DELETE_BACK("Delete (second key)", Combo.of(GLFW.GLFW_KEY_BACKSPACE)),
        REPEAT("Repeat the selection", Combo.of(GLFW.GLFW_KEY_R)),
        UNDO("Undo", Combo.ctrl(GLFW.GLFW_KEY_Z)),
        REDO("Redo", Combo.ctrl(GLFW.GLFW_KEY_Y)),
        COPY("Copy", Combo.ctrl(GLFW.GLFW_KEY_C)),
        CUT("Cut", Combo.ctrl(GLFW.GLFW_KEY_X)),
        PASTE("Paste", Combo.ctrl(GLFW.GLFW_KEY_V)),
        SELECT_ALL("Select all", Combo.ctrl(GLFW.GLFW_KEY_A)),
        PLAY("Play / stop", Combo.of(GLFW.GLFW_KEY_SPACE)),
        ADD_MEASURE("Add a measure at the end", Combo.ctrl(GLFW.GLFW_KEY_B)),
        INSERT_MEASURE("Insert a measure before", Combo.of(GLFW.GLFW_KEY_INSERT)),
        VOICE_1("Voice 1", Combo.alt(GLFW.GLFW_KEY_1)),
        VOICE_2("Voice 2", new Combo(GLFW.GLFW_KEY_2, true, false, true)),
        VOICE_3("Voice 3", new Combo(GLFW.GLFW_KEY_3, true, false, true)),
        VOICE_4("Voice 4", new Combo(GLFW.GLFW_KEY_4, true, false, true)),
        INTERVAL_2("Add a 2nd above", Combo.alt(GLFW.GLFW_KEY_2)),
        INTERVAL_3("Add a 3rd above", Combo.alt(GLFW.GLFW_KEY_3)),
        INTERVAL_4("Add a 4th above", Combo.alt(GLFW.GLFW_KEY_4)),
        INTERVAL_5("Add a 5th above", Combo.alt(GLFW.GLFW_KEY_5)),
        INTERVAL_6("Add a 6th above", Combo.alt(GLFW.GLFW_KEY_6)),
        INTERVAL_7("Add a 7th above", Combo.alt(GLFW.GLFW_KEY_7)),
        INTERVAL_8("Add an octave above", Combo.alt(GLFW.GLFW_KEY_8)),
        SLUR("Slur", Combo.of(GLFW.GLFW_KEY_S)),
        CRESCENDO("Crescendo hairpin", Combo.shift(GLFW.GLFW_KEY_COMMA)),
        DIMINUENDO("Diminuendo hairpin", Combo.shift(GLFW.GLFW_KEY_PERIOD)),
        TUPLET_2("Duplet", Combo.ctrl(GLFW.GLFW_KEY_2)),
        TUPLET_3("Triplet", Combo.ctrl(GLFW.GLFW_KEY_3)),
        TUPLET_4("Quadruplet", Combo.ctrl(GLFW.GLFW_KEY_4)),
        TUPLET_5("Quintuplet", Combo.ctrl(GLFW.GLFW_KEY_5)),
        TUPLET_6("Sextuplet", Combo.ctrl(GLFW.GLFW_KEY_6)),
        TUPLET_7("Septuplet", Combo.ctrl(GLFW.GLFW_KEY_7)),
        TUPLET_8("Octuplet", Combo.ctrl(GLFW.GLFW_KEY_8)),
        TUPLET_9("Nonuplet", Combo.ctrl(GLFW.GLFW_KEY_9)),
        ACCIACCATURA("Grace note (acciaccatura)", Combo.of(GLFW.GLFW_KEY_SLASH)),
        APPOGGIATURA("Grace note (appoggiatura)", Combo.of(GLFW.GLFW_KEY_UNKNOWN)),
        LYRICS("Lyrics", Combo.ctrl(GLFW.GLFW_KEY_L)),
        CHORD_SYMBOL("Chord symbol", Combo.ctrl(GLFW.GLFW_KEY_K)),
        STAFF_TEXT("Staff text", Combo.ctrl(GLFW.GLFW_KEY_T)),
        REHEARSAL_MARK("Rehearsal mark", Combo.ctrl(GLFW.GLFW_KEY_M)),
        ZOOM_IN("Zoom in", Combo.ctrl(GLFW.GLFW_KEY_EQUAL)),
        ZOOM_OUT("Zoom out", Combo.ctrl(GLFW.GLFW_KEY_MINUS)),
        SEARCH_PALETTES("Search the palettes", Combo.ctrl(GLFW.GLFW_KEY_F)),
        PREFERENCES("Preferences", Combo.ctrl(GLFW.GLFW_KEY_COMMA));

        public final String label;
        public final Combo defaultCombo;

        Action(String label, Combo defaultCombo) {
            this.label = label;
            this.defaultCombo = defaultCombo;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Action, Combo> BOUND = new EnumMap<>(Action.class);
    private static boolean loaded;

    private EditorKeys() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("minescore-editor-keys.json");
    }

    public static Combo get(Action action) {
        load();
        return BOUND.getOrDefault(action, action.defaultCombo);
    }

    /** The action a key press means, if any. */
    public static @Nullable Action match(int key) {
        load();
        for (Action action : Action.values()) {
            if (get(action).matches(key)) {
                return action;
            }
        }
        return null;
    }

    /** Binds an action, taking the combo off any other action that had it. */
    public static void set(Action action, Combo combo) {
        load();
        for (Action other : Action.values()) {
            if (other != action && get(other).equals(combo)) {
                BOUND.put(other, Combo.of(GLFW.GLFW_KEY_UNKNOWN));
            }
        }
        BOUND.put(action, combo);
        save();
    }

    public static void resetAll() {
        BOUND.clear();
        save();
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject json = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
            for (Action action : Action.values()) {
                if (json.has(action.name())) {
                    JsonObject c = json.getAsJsonObject(action.name());
                    BOUND.put(action, new Combo(c.get("key").getAsInt(), c.get("ctrl").getAsBoolean(), c.get("shift").getAsBoolean(),
                            c.get("alt").getAsBoolean()));
                }
            }
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read {}", file, e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        for (Map.Entry<Action, Combo> e : BOUND.entrySet()) {
            JsonObject c = new JsonObject();
            c.addProperty("key", e.getValue().key());
            c.addProperty("ctrl", e.getValue().ctrl());
            c.addProperty("shift", e.getValue().shift());
            c.addProperty("alt", e.getValue().alt());
            json.add(e.getKey().name(), c);
        }
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save the editor's shortcuts", e);
        }
    }
}
