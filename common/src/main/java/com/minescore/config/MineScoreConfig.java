package com.minescore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minescore.MineScore;
import com.minescore.MineScoreSettings;
import com.minescore.config.Setting.Side;
import com.minescore.power.KineticSettings;
import com.minescore.power.PowerSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every MineScore setting (SCOPE.md §9), in the config menu's categories. Server settings are saved in
 * {@code config/minescore-server.json} and sent to every player; client settings in
 * {@code config/minescore-client.json}. Both load at startup (from the old {@code .toml} files the first
 * time) and save whenever something changes. The rest of the mod reads them through the settings
 * classes it always has ({@link MineScoreSettings}, {@link PowerSettings}, {@link KineticSettings}).
 */
public final class MineScoreConfig {
    /** The config menu's tabs, in order. */
    public enum Category {
        POWER("Power"), SINGLE_SPEAKER("Single-speaker mode"), HEARING("Hearing"), DAISY_CHAIN("Daisy chain"),
        BLOWOUTS("Blowouts and distortion"), WIRES("Wires"), CHUNK_LOADING("Chunk loading"), COPIES("Copies and erasing"),
        REDSTONE("Redstone"), AUDIO_ENGINE("Audio engine"), SCORE_EDITOR("Score editor"), INSTRUMENTS("Instruments"),
        IMPORT_EXPORT("Import/export"), SERVER_LIMITS("Server limits"), PERFORMANCE("Performance"), ACCESSIBILITY("Accessibility"),
        HOTKEYS("Hotkeys"), DEBUG("Debug");

        public final String label;

        Category(String label) {
            this.label = label;
        }

        public String labelKey() {
            return "config.minescore.category." + name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<Setting> ALL = new ArrayList<>();
    private static Path folder = Path.of("config");

    private static Setting add(Setting s) {
        ALL.add(s);
        return s;
    }

    private static final Category P = Category.POWER;
    // --- power (server) -----------------------------------------------------------------------
    public static final Setting POWER_MODE = add(Setting.choice("powerMode", P, Side.SERVER, "energy", List.of("energy", "none"),
            "Power mode", "Energy: blocks need FE, systems have a budget and a minimum. None: nothing needs power and daisy chains lose nothing."));
    public static final Setting SYSTEM_CAP = add(Setting.integer("systemCap", P, Side.SERVER, 500, 1, 1_000_000, "FE/t",
            "System cap", "The most FE/t one speaker system draws."));
    public static final Setting HUB_COST = add(Setting.integer("hubCost", P, Side.SERVER, 50, 0, 100_000, "FE/t",
            "Hub cost", "Each hub: Mixer, Desk, Channel Manager, Amplifier, Line Amplifier, and a Multi-Effects Unit before its cards."));
    public static final Setting MEU_CARD_COST = add(Setting.integer("meuCardCost", P, Side.SERVER, 10, 0, 100_000, "FE/t",
            "Effect card cost", "Each Multi-Effects Unit card in use."));
    public static final Setting TWEETER_COST = add(Setting.integer("tweeterCost", P, Side.SERVER, 5, 0, 100_000, "FE/t",
            "Tweeter Speaker cost", "The first Tweeter Speaker in a chain."));
    public static final Setting MIDDLE_COST = add(Setting.integer("middleCost", P, Side.SERVER, 10, 0, 100_000, "FE/t",
            "Middle Speaker cost", "The first Middle Speaker in a chain."));
    public static final Setting SUBWOOFER_COST = add(Setting.integer("subwooferCost", P, Side.SERVER, 25, 0, 100_000, "FE/t",
            "Subwoofer cost", "The first Subwoofer in a chain."));
    public static final Setting CHAIN_STEP = add(Setting.integer("chainStep", P, Side.SERVER, 5, 0, 100_000, "FE/t",
            "Daisy chain step", "Extra FE/t for each speaker further down a daisy chain."));
    public static final Setting LATHE_COST = add(Setting.integer("latheCost", P, Side.SERVER, 20, 0, 100_000, "FE/t",
            "Lathe cost", "The Disc Cutting Lathe while it cuts, copies or loads a song."));
    public static final Setting CREATE_MAX_RPM = add(Setting.integer("createMaxRpm", P, Side.SERVER, 64, 1, 256, "RPM",
            "Create: Lathe maximum speed", "The fastest a Lathe turned by Create may go: a cut at this speed takes 3 s; any faster and the record shatters."));
    public static final Setting CREATE_STRESS = add(Setting.number("createStressPerRpm", P, Side.SERVER, 1, 0, 1000, "SU/RPM",
            "Create: stress per RPM", "Stress units the Lathe puts on its network for each RPM."));
    // --- single-speaker mode (Bluetooth) -----------------------------------------------------
    public static final Setting BLUETOOTH_SHARE = add(Setting.number("bluetoothShare", Category.SINGLE_SPEAKER, Side.SERVER, 60, 1, 100, "%",
            "Volume and range", "How loud, and how far, a speaker an MP3 Player is linked to plays, against a wired system's."));
    public static final Setting BLUETOOTH_LOSS = add(Setting.number("bluetoothChainLoss", Category.SINGLE_SPEAKER, Side.SERVER, 15, 0, 100, "%",
            "Daisy chain loss", "Each speaker daisy-chained after the linked one plays this much quieter."));
    // --- hearing -----------------------------------------------------------------------------
    public static final Setting SPEAKER_RANGE = add(Setting.number("speakerRange", Category.HEARING, Side.SERVER, 32, 8, 256, "blocks",
            "Speaker range", "How far a speaker at 100% volume is heard. Boosted speakers carry further."));
    public static final Setting BEHIND_VOLUME = add(Setting.number("behindVolume", Category.HEARING, Side.SERVER, 30, 0, 100, "%",
            "Volume behind a speaker", "How loud a speaker sounds from directly behind it, against straight in front."));
    // --- daisy chains ------------------------------------------------------------------------
    public static final Setting CHAIN_LOSS = add(Setting.number("chainLoss", Category.DAISY_CHAIN, Side.SERVER, 5, 0, 50, "%",
            "Loss past the budget", "Past a system's power budget, each speaker plays this much quieter than the one before it."));
    public static final Setting CHAIN_FLOOR = add(Setting.number("chainFloor", Category.DAISY_CHAIN, Side.SERVER, 10, 0, 100, "%",
            "Quietest", "…down to this."));
    // --- blowouts ----------------------------------------------------------------------------
    public static final Setting BLOWOUTS = add(Setting.bool("blowouts", Category.BLOWOUTS, Side.SERVER, true,
            "Speakers can blow", "Driven too hard for too long, a speaker shorts and pops off."));
    public static final Setting STRAIN_CHECKS = add(Setting.integer("strainChecks", Category.BLOWOUTS, Side.SERVER, 2, 1, 30, "s",
            "Seconds over the limit", "How long a speaker must be over its limit before it blows, so a single loud hit doesn't."));
    public static final Setting RECOVERY = add(Setting.number("recoverySeconds", Category.BLOWOUTS, Side.SERVER, 5, 0, 120, "s",
            "Restart wait (None mode)", "In None mode, how long after a short before the system can start again."));
    public static final Setting SUB_LIMIT = add(Setting.number("subwooferLimitDb", Category.BLOWOUTS, Side.SERVER, 9, 0, 40, "dB",
            "Subwoofer limit", "How hard a Subwoofer may be driven, over 100% volume at normal music level."));
    public static final Setting MID_LIMIT = add(Setting.number("middleLimitDb", Category.BLOWOUTS, Side.SERVER, 10, 0, 40, "dB",
            "Middle Speaker limit", "How hard a Middle Speaker may be driven."));
    public static final Setting TWEETER_LIMIT = add(Setting.number("tweeterLimitDb", Category.BLOWOUTS, Side.SERVER, 11, 0, 40, "dB",
            "Tweeter limit", "How hard a Tweeter Speaker may be driven."));
    // --- wires -------------------------------------------------------------------------------
    public static final Setting INVISIBLE_WIRES = add(Setting.bool("invisibleWires", Category.WIRES, Side.CLIENT, false,
            "Invisible wires", "Only show speaker cables while holding speaker wire or Wire Cutters."));
    public static final Setting DISTANCE_WARNING = add(Setting.bool("distanceWarning", Category.WIRES, Side.SERVER, true,
            "Distance warning", "Warn in chat when a cable is longer than the hearing range."));
    // --- chunk loading -----------------------------------------------------------------------
    public static final Setting KEEP_LOADED = add(Setting.bool("keepSystemsLoaded", Category.CHUNK_LOADING, Side.SERVER, true,
            "Keep playing systems loaded", "Every chunk a playing system touches stays loaded, so it keeps playing when you walk away."));
    // --- copies ------------------------------------------------------------------------------
    public static final Setting RECORD_COPIES = add(Setting.bool("recordCopies", Category.COPIES, Side.SERVER, true,
            "Copying records", "The Lathe can copy Vinyl Records."));
    public static final Setting SCORE_COPIES = add(Setting.bool("scoreCopies", Category.COPIES, Side.SERVER, true,
            "Copying scores", "The Writer's Desk can copy scores (and importing can keep a copy)."));
    // --- redstone ----------------------------------------------------------------------------
    public static final Setting REDSTONE_DEFAULT = add(Setting.bool("redstoneControlByDefault", Category.REDSTONE, Side.SERVER, false,
            "Redstone Control on by default", "Whether newly placed hubs start with Redstone Control on. Comparator outputs always work."));
    // --- audio engine (client) ---------------------------------------------------------------
    public static final Setting ROOM_SOUND = add(Setting.bool("roomSoundPhysics", Category.AUDIO_ENGINE, Side.CLIENT, true,
            "Room Sound Physics", "Speakers in a sealed room sound fuller (bass most), and every gap lets sound escape."));
    public static final Setting THROUGH_BLOCKS = add(Setting.bool("soundThroughBlocks", Category.AUDIO_ENGINE, Side.CLIENT, true,
            "Sound through blocks", "Blocks between a speaker and you muffle it, harder blocks more."));
    public static final Setting SPEED_ALGORITHM = add(Setting.choice("speedAlgorithm", Category.AUDIO_ENGINE, Side.CLIENT, "WSOLA",
            List.of("WSOLA", "PHASE_VOCODER", "TAPE"), "Speed algorithm",
            "The MEU's Speed effect. WSOLA keeps the pitch and suits most music; PHASE_VOCODER is smoother on long notes but heavier; TAPE lets the pitch follow the speed."));
    public static final Setting VINYL_STATIC = add(Setting.bool("vinylStatic", Category.AUDIO_ENGINE, Side.CLIENT, true,
            "Vinyl static", "A faint hiss and the odd crackle on records played through a Vinyl Player, as on real vinyl."));
    public static final Setting VINYL_STATIC_AMOUNT = add(Setting.number("vinylStaticAmount", Category.AUDIO_ENGINE, Side.CLIENT, 100, 0, 400, "%",
            "Vinyl static amount", "How loud the static is. At 100% you only notice it in the music's quiet parts, under the rest."));
    public static final Setting LOW_SPLIT = add(Setting.number("lowSplit", Category.AUDIO_ENGINE, Side.CLIENT, 200, 40, 1000, "Hz",
            "Bass crossover", "Where the Subwoofer's band ends and the Middle Speaker's starts."));
    public static final Setting HIGH_SPLIT = add(Setting.number("highSplit", Category.AUDIO_ENGINE, Side.CLIENT, 2000, 500, 12_000, "Hz",
            "Treble crossover", "Where the Middle Speaker's band ends and the Tweeter's starts."));
    // --- score editor (client) ---------------------------------------------------------------
    public static final Setting ARROW_SELECT = add(Setting.choice("arrowSelect", Category.SCORE_EDITOR, Side.CLIENT, "TOP", List.of("TOP", "ALL", "BOTTOM"),
            "Arrow keys select", "Which note of a chord the arrow keys select as they move: the top note, the whole chord or the bottom note."));
    public static final Setting RESTRIKE = add(Setting.bool("restrikeLongNotes", Category.SCORE_EDITOR, Side.CLIENT, false,
            "Re-strike long notes", "Score playback strikes long notes again every beat in every part, instead of holding them."));
    public static final Setting EDITOR_SHORTCUTS = add(Setting.button("editorShortcuts", Category.SCORE_EDITOR,
            "Editor shortcuts…", "Change the score editor's keyboard shortcuts."));
    // --- instruments (client) ----------------------------------------------------------------
    public static final Setting CHOOSE_INSTRUMENTS = add(Setting.button("chooseInstruments", Category.INSTRUMENTS,
            "Choose instruments…", "Which instruments (the note block's and resource packs') the instrument list offers."));
    // --- import/export (client) --------------------------------------------------------------
    public static final Setting IMPORT_GRID = add(Setting.choice("importGrid", Category.IMPORT_EXPORT, Side.CLIENT, "16th", List.of("8th", "16th", "32nd"),
            "MIDI import grid", "The shortest step imported MIDI notes are placed on."));
    public static final Setting EXPORT_FOLDER = add(Setting.text("exportFolder", Category.IMPORT_EXPORT, Side.CLIENT, "",
            "Export folder", "Where exported scores go. Empty: .minecraft/minescore/."));
    public static final Setting PICK_EXPORT_FOLDER = add(Setting.button("pickExportFolder", Category.IMPORT_EXPORT,
            "Pick export folder…", "Choose the export folder with your system's folder dialog."));
    // --- server limits -----------------------------------------------------------------------
    public static final Setting MAX_PARTS = add(Setting.integer("maxParts", Category.SERVER_LIMITS, Side.SERVER, 16, 1, 16, "",
            "Parts per score", "The most parts (instruments) a score may have."));
    public static final Setting MAX_MEASURES = add(Setting.integer("maxMeasures", Category.SERVER_LIMITS, Side.SERVER, 2000, 1, 2000, "",
            "Measures per score", "The most measures a score may have."));
    public static final Setting MAX_SONG_MB = add(Setting.integer("maxSongMb", Category.SERVER_LIMITS, Side.SERVER, 64, 1, 1024, "MB",
            "Largest song", "The biggest song file players may cut onto a record (or play from a desk)."));
    // --- performance (client) ----------------------------------------------------------------
    public static final Setting MAX_VOICES = add(Setting.integer("maxSpeakerVoices", Category.PERFORMANCE, Side.CLIENT, 48, 4, 200, "",
            "Speakers at once", "The most speakers your game plays at once; the nearest win. Leaves room for Minecraft's own sounds."));
    public static final Setting EFFECTS_HQ = add(Setting.bool("effectsHighQuality", Category.PERFORMANCE, Side.CLIENT, true,
            "High-quality effects", "Run the Multi-Effects Unit's effects at high quality. Turn off on slower computers."));
    public static final Setting PARTICLES = add(Setting.bool("particles", Category.PERFORMANCE, Side.CLIENT, false,
            "Particles", "MineScore's particles: music notes rising from playing speakers, shavings off the Lathe, and shards, sparks and smoke when things break. Off unless you turn it on."));
    // --- accessibility (client) --------------------------------------------------------------
    public static final Setting NOW_PLAYING = add(Setting.bool("nowPlaying", Category.ACCESSIBILITY, Side.CLIENT, true,
            "Now Playing messages", "Show 'Now Playing' above the hotbar when a record starts near you."));
    // --- debug -------------------------------------------------------------------------------
    public static final Setting LOG_PLAYBACK = add(Setting.bool("logPlayback", Category.DEBUG, Side.SERVER, false,
            "Log playback", "Write every system starting and stopping to the log."));

    private MineScoreConfig() {}

    // --- reading ------------------------------------------------------------------------------

    public static List<Setting> all() {
        return List.copyOf(ALL);
    }

    public static List<Setting> in(Category category) {
        return ALL.stream().filter(s -> s.category == category).toList();
    }

    public static Setting byId(String id) {
        for (Setting s : ALL) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** The client adds its hotkeys' rows once they exist. */
    public static void addHotkey(Setting hotkey) {
        ALL.removeIf(s -> s.id.equals(hotkey.id));
        ALL.add(hotkey);
    }

    /** MIDI import's grid, in 64ths of a whole note. */
    public static int importGrid() {
        return switch (IMPORT_GRID.text()) {
            case "8th" -> 8;
            case "32nd" -> 2;
            default -> 4;
        };
    }

    // --- loading and saving ---------------------------------------------------------------------

    private static Path file(Side side) {
        return folder.resolve(side == Side.SERVER ? "minescore-server.json" : "minescore-client.json");
    }

    /** Loads both files (migrating the old Forge ones the first time) and points the settings classes here. */
    public static void load(Path configFolder) {
        folder = configFolder;
        for (Side side : Side.values()) {
            Path file = file(side);
            if (Files.isRegularFile(file)) {
                read(side, file);
            } else {
                migrate(side);
                save(side);
            }
        }
        bind();
    }

    /** Reads the server settings back from this computer's file (after leaving a server that sent its own). */
    public static void reloadServerSide() {
        Path file = file(Side.SERVER);
        ALL.stream().filter(s -> s.side == Side.SERVER && s.saved()).forEach(Setting::reset);
        if (Files.isRegularFile(file)) {
            read(Side.SERVER, file);
        }
    }

    private static void read(Side side, Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Setting s : ALL) {
                if (s.side == side && s.saved() && json.has(s.id)) {
                    s.decode(json.get(s.id).getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read {}; using defaults for what's missing", file, e);
        }
    }

    public static void save(Side side) {
        JsonObject json = new JsonObject();
        for (Setting s : ALL) {
            if (s.side == side && s.saved()) {
                json.addProperty(s.id, s.encode());
            }
        }
        try {
            Files.createDirectories(folder);
            Files.writeString(file(side), GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't save {}", file(side), e);
        }
    }

    /** The values in the Forge config files used before M11, the first time the new files are made. */
    private static void migrate(Side side) {
        Path toml = folder.resolve(side == Side.SERVER ? "minescore-common.toml" : "minescore-client.toml");
        if (!Files.isRegularFile(toml)) {
            return;
        }
        Map<String, String> renamed = Map.of("mode", "powerMode", "maxRpm", "createMaxRpm", "stressPerRpm", "createStressPerRpm");
        Pattern line = Pattern.compile("^\\s*([A-Za-z]+)\\s*=\\s*\"?([^\"#]*?)\"?\\s*(#.*)?$");
        try (Stream<String> lines = Files.lines(toml, StandardCharsets.UTF_8)) {
            lines.forEach(l -> {
                Matcher m = line.matcher(l);
                if (m.matches()) {
                    Setting s = byId(renamed.getOrDefault(m.group(1), m.group(1)));
                    if (s != null && s.side == side) {
                        s.decode(m.group(2));
                    }
                }
            });
            MineScore.LOGGER.info("Moved MineScore's settings from {} to {}", toml.getFileName(), file(side).getFileName());
        } catch (IOException | RuntimeException e) {
            MineScore.LOGGER.warn("Couldn't read the old settings in {}", toml, e);
        }
    }

    /** Points the settings classes the rest of the mod reads at these settings. */
    private static void bind() {
        MineScoreSettings.speakerRange = SPEAKER_RANGE::number;
        MineScoreSettings.invisibleWires = INVISIBLE_WIRES::bool;
        MineScoreSettings.roomSoundPhysics = ROOM_SOUND::bool;
        MineScoreSettings.speedAlgorithm = SPEED_ALGORITHM::text;
        MineScoreSettings.effectsHighQuality = EFFECTS_HQ::bool;
        MineScoreSettings.redstoneControlByDefault = REDSTONE_DEFAULT::bool;
        MineScoreSettings.restrikeLongNotes = RESTRIKE::bool;
        MineScoreSettings.setRestrikeLongNotes = on -> {
            RESTRIKE.decode(String.valueOf(on));
            save(Side.CLIENT);
        };
        PowerSettings.energy = () -> POWER_MODE.text().equals("energy");
        PowerSettings.systemCap = SYSTEM_CAP::integer;
        PowerSettings.hubCost = HUB_COST::integer;
        PowerSettings.meuCardCost = MEU_CARD_COST::integer;
        PowerSettings.tweeterCost = TWEETER_COST::integer;
        PowerSettings.middleCost = MIDDLE_COST::integer;
        PowerSettings.subwooferCost = SUBWOOFER_COST::integer;
        PowerSettings.chainStep = CHAIN_STEP::integer;
        PowerSettings.latheCost = LATHE_COST::integer;
        KineticSettings.maxRpm = CREATE_MAX_RPM::integer;
        KineticSettings.stressPerRpm = CREATE_STRESS::number;
    }

    // --- server values travelling, presets, import and export -----------------------------------

    /** Every server setting's value, as text, for sending to players. */
    public static Map<String, String> serverValues() {
        Map<String, String> values = new LinkedHashMap<>();
        ALL.stream().filter(s -> s.side == Side.SERVER && s.saved()).forEach(s -> values.put(s.id, s.encode()));
        return values;
    }

    /** Sets what it can from id → value text, only on the given side (null: both); returns whether anything changed. */
    public static boolean apply(Map<String, String> values, Side side) {
        boolean changed = false;
        for (Map.Entry<String, String> e : values.entrySet()) {
            Setting s = byId(e.getKey());
            if (s != null && s.saved() && (side == null || s.side == side)) {
                String before = s.encode();
                if (s.decode(e.getValue()) && !before.equals(s.encode())) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Everything (both sides) as JSON, for exporting or saving as a preset. */
    public static String toJson() {
        JsonObject json = new JsonObject();
        ALL.stream().filter(Setting::saved).forEach(s -> json.addProperty(s.id, s.encode()));
        return GSON.toJson(json);
    }

    /** id → value text from JSON (an import or a preset); throws if it isn't JSON. */
    public static Map<String, String> fromJson(String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            values.put(e.getKey(), e.getValue().getAsString());
        }
        return values;
    }

    /** A preset: a name and the settings it changes. */
    public record Preset(String name, String description, Map<String, String> values, boolean builtIn) {}

    /** The presets MineScore comes with. "Defaults" puts everything back. */
    public static List<Preset> builtInPresets() {
        Map<String, String> defaults = new LinkedHashMap<>();
        ALL.stream().filter(Setting::saved).forEach(s -> defaults.put(s.id, s.defaultValue()));
        return List.of(
                new Preset("Defaults", "Every setting as MineScore ships it.", defaults, true),
                new Preset("No power", "Nothing needs FE: for creative builds and maps.", Map.of("powerMode", "none"), true),
                new Preset("Light on resources", "For slower computers: fewer speakers at once, lighter effects and no room sound.",
                        Map.of("maxSpeakerVoices", "24", "effectsHighQuality", "false", "roomSoundPhysics", "false", "speedAlgorithm", "WSOLA"), true),
                new Preset("Big venues", "Speakers carry twice as far and sound fuller from behind.",
                        Map.of("speakerRange", "64", "behindVolume", "40"), true),
                new Preset("Forgiving speakers", "Speakers take 6 dB more before they blow, and must be over for longer.",
                        Map.of("subwooferLimitDb", "15", "middleLimitDb", "16", "tweeterLimitDb", "17", "strainChecks", "5"), true));
    }

    private static Path presetFolder() {
        return folder.resolve("minescore-presets");
    }

    /** Presets this player saved, from {@code config/minescore-presets/}. */
    public static List<Preset> savedPresets() {
        List<Preset> presets = new ArrayList<>();
        if (!Files.isDirectory(presetFolder())) {
            return presets;
        }
        try (Stream<Path> files = Files.list(presetFolder())) {
            files.filter(f -> f.toString().endsWith(".json")).sorted().forEach(f -> {
                try {
                    String name = f.getFileName().toString().replaceFirst("\\.json$", "");
                    presets.add(new Preset(name, "Saved by you", fromJson(Files.readString(f, StandardCharsets.UTF_8)), false));
                } catch (IOException | RuntimeException e) {
                    MineScore.LOGGER.warn("Couldn't read preset {}", f, e);
                }
            });
        } catch (IOException e) {
            MineScore.LOGGER.warn("Couldn't list {}", presetFolder(), e);
        }
        return presets;
    }

    /** Saves every setting as it is now as a preset. */
    public static void savePreset(String name) throws IOException {
        String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_").strip();
        if (clean.isEmpty()) {
            throw new IOException("A preset needs a name");
        }
        Files.createDirectories(presetFolder());
        Files.writeString(presetFolder().resolve(clean + ".json"), toJson(), StandardCharsets.UTF_8);
    }

    public static void deletePreset(String name) throws IOException {
        Files.deleteIfExists(presetFolder().resolve(name + ".json"));
    }

    /** Where Export writes its file (besides the clipboard). */
    public static Path exportFile() {
        return folder.resolve("minescore-settings-export.json");
    }
}
