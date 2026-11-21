package com.minescore.config;

import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * One setting in the config menu (SCOPE.md §10): a toggle, a whole number, a number, a choice, some text, a
 * button that does something, or a hotkey. Server settings are kept by the server, sent to every player and
 * changed in-game only by operators; client settings are each player's own.
 */
public final class Setting {
    public enum Type { BOOL, INT, DOUBLE, CHOICE, TEXT, ACTION, HOTKEY }

    public enum Side { SERVER, CLIENT }

    public final String id;
    public final MineScoreConfig.Category category;
    public final Side side;
    public final Type type;
    /** What it's called, and a sentence or two on what it does (English; translatable by {@link #labelKey()}). */
    public final String label, help;
    /** A unit to show after numbers ("FE/t", "%", "blocks"…), or "". */
    public final String unit;
    private final Object fallback;
    private final double min, max;
    private final List<String> choices;
    private volatile Object value;
    /** For buttons and hotkeys: set by the client, run when pressed. */
    public @Nullable Runnable action;
    /** For hotkeys: the client's key mapping. */
    public @Nullable Object key;

    private Setting(String id, MineScoreConfig.Category category, Side side, Type type, String label, String help, String unit, Object fallback,
                    double min, double max, List<String> choices) {
        this.id = id;
        this.category = category;
        this.side = side;
        this.type = type;
        this.label = label;
        this.help = help;
        this.unit = unit;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
        this.choices = choices;
        this.value = fallback;
    }

    static Setting bool(String id, MineScoreConfig.Category c, Side side, boolean fallback, String label, String help) {
        return new Setting(id, c, side, Type.BOOL, label, help, "", fallback, 0, 1, List.of());
    }

    static Setting integer(String id, MineScoreConfig.Category c, Side side, int fallback, int min, int max, String unit, String label, String help) {
        return new Setting(id, c, side, Type.INT, label, help, unit, fallback, min, max, List.of());
    }

    static Setting number(String id, MineScoreConfig.Category c, Side side, double fallback, double min, double max, String unit, String label,
                          String help) {
        return new Setting(id, c, side, Type.DOUBLE, label, help, unit, fallback, min, max, List.of());
    }

    static Setting choice(String id, MineScoreConfig.Category c, Side side, String fallback, List<String> choices, String label, String help) {
        return new Setting(id, c, side, Type.CHOICE, label, help, "", fallback, 0, 0, List.copyOf(choices));
    }

    static Setting text(String id, MineScoreConfig.Category c, Side side, String fallback, String label, String help) {
        return new Setting(id, c, side, Type.TEXT, label, help, "", fallback, 0, 0, List.of());
    }

    static Setting button(String id, MineScoreConfig.Category c, String label, String help) {
        return new Setting(id, c, Side.CLIENT, Type.ACTION, label, help, "", "", 0, 0, List.of());
    }

    /** A hotkey's row; its key lives in the game's own controls, so nothing is saved here. */
    public static Setting hotkey(String id, String label, String help, Object key, Runnable pressed) {
        Setting s = new Setting(id, MineScoreConfig.Category.HOTKEYS, Side.CLIENT, Type.HOTKEY, label, help, "", "", 0, 0, List.of());
        s.key = key;
        s.action = pressed;
        return s;
    }

    /** Whether it holds a value that's saved (buttons and hotkeys don't). */
    public boolean saved() {
        return type != Type.ACTION && type != Type.HOTKEY;
    }

    public String labelKey() {
        return "config.minescore." + id;
    }

    public boolean bool() {
        return (Boolean) value;
    }

    public int integer() {
        return ((Number) value).intValue();
    }

    public double number() {
        return ((Number) value).doubleValue();
    }

    /** For a percentage setting, its value as a share of 1. */
    public float share() {
        return (float) (number() / 100);
    }

    public String text() {
        return String.valueOf(value);
    }

    public List<String> choices() {
        return choices;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public boolean isDefault() {
        return encode().equals(encode(fallback));
    }

    public String encode() {
        return encode(value);
    }

    public String defaultValue() {
        return encode(fallback);
    }

    private static String encode(Object v) {
        return String.valueOf(v);
    }

    /** Sets it from text, kept within its range; false if the text isn't a value it can take. */
    public boolean decode(String text) {
        if (!saved() || text == null) {
            return false;
        }
        String t = text.strip();
        try {
            value = switch (type) {
                case BOOL -> {
                    if (!t.equalsIgnoreCase("true") && !t.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException(t);
                    }
                    yield Boolean.parseBoolean(t);
                }
                case INT -> (int) Math.max(min, Math.min(max, Math.round(Double.parseDouble(t))));
                case DOUBLE -> {
                    double d = Double.parseDouble(t);
                    if (Double.isNaN(d)) {
                        throw new IllegalArgumentException(t);
                    }
                    yield Math.max(min, Math.min(max, d));
                }
                case CHOICE -> {
                    String match = null;
                    for (String c : choices) {
                        if (c.equalsIgnoreCase(t)) {
                            match = c;
                        }
                    }
                    if (match == null) {
                        throw new IllegalArgumentException(t);
                    }
                    yield match;
                }
                default -> t.length() > 512 ? t.substring(0, 512) : t;
            };
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void reset() {
        value = fallback;
    }

    /** The next choice (or the other state of a toggle), for clicking through. */
    public String next(boolean backwards) {
        if (type == Type.BOOL) {
            return String.valueOf(!bool());
        }
        int i = choices.indexOf(text());
        int n = choices.size();
        return choices.get(Math.floorMod(i + (backwards ? -1 : 1), n));
    }

    @Override
    public String toString() {
        return id.toLowerCase(Locale.ROOT) + "=" + encode();
    }
}
