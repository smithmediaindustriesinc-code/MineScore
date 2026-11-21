package com.minescore.client.score;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.minescore.MineScore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;

/**
 * The score editor's other preferences (SCOPE.md §9.3), beside its shortcuts: saved per player in
 * {@code config/minescore-editor.json}.
 */
public final class EditorPrefs {
    /** Which note of a chord the arrow keys select as they move through the music. */
    public enum ArrowSelect {
        TOP("Top note"), ALL("Whole chord"), BOTTOM("Bottom note");

        public final String label;

        ArrowSelect(String label) {
            this.label = label;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Instruments switched off in the instrument list (SCOPE.md §9.11), by {@link com.minescore.score.Instruments.Choice#key()}. */
    private static final java.util.Set<String> disabled = new java.util.TreeSet<>();
    private static boolean loaded;

    private EditorPrefs() {}

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("minescore-editor.json");
    }

    /** Kept with the config menu's settings (SCOPE.md §10). */
    public static ArrowSelect arrowSelect() {
        try {
            return ArrowSelect.valueOf(com.minescore.config.MineScoreConfig.ARROW_SELECT.text());
        } catch (IllegalArgumentException e) {
            return ArrowSelect.TOP;
        }
    }

    public static void setArrowSelect(ArrowSelect value) {
        com.minescore.config.MineScoreConfig.ARROW_SELECT.decode(value.name());
        com.minescore.config.MineScoreConfig.save(com.minescore.config.Setting.Side.CLIENT);
    }

    public static boolean enabled(String instrumentKey) {
        load();
        return !disabled.contains(instrumentKey);
    }

    public static void setEnabled(String instrumentKey, boolean on) {
        load();
        if (on) {
            disabled.remove(instrumentKey);
        } else {
            disabled.add(instrumentKey);
        }
        save();
    }

    /** The folder exports go to. */
    public static java.nio.file.Path exportFolder() {
        String exportFolder = com.minescore.config.MineScoreConfig.EXPORT_FOLDER.text();
        java.nio.file.Path standard = Minecraft.getInstance().gameDirectory.toPath().resolve("minescore");
        if (exportFolder.isBlank()) {
            return standard;
        }
        try {
            return java.nio.file.Path.of(exportFolder);
        } catch (RuntimeException e) {
            return standard;
        }
    }

    public static void setExportFolder(java.nio.file.Path folder) {
        com.minescore.config.MineScoreConfig.EXPORT_FOLDER.decode(folder.toString());
        com.minescore.config.MineScoreConfig.save(com.minescore.config.Setting.Side.CLIENT);
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

            if (json.has("disabledInstruments")) {
                json.getAsJsonArray("disabledInstruments").forEach(e -> disabled.add(e.getAsString()));
            }

        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read {}", file, e);
        }
    }

    private static void save() {
        JsonObject json = new JsonObject();
        com.google.gson.JsonArray off = new com.google.gson.JsonArray();
        disabled.forEach(off::add);
        json.add("disabledInstruments", off);
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save the editor's preferences", e);
        }
    }
}
