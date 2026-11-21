package com.minescore;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/** Settings read by game logic, bound to the config menu's settings ({@link com.minescore.config.MineScoreConfig}, SCOPE.md §10). */
public final class MineScoreSettings {
    /** Client: show cables only while holding speaker wire or Wire Cutters. */
    public static BooleanSupplier invisibleWires = () -> false;
    /** Client: "Room Sound Physics" (SCOPE.md §8.3): sealed rooms sound fuller, and gaps let sound escape. */
    public static BooleanSupplier roomSoundPhysics = () -> true;
    /** How far a speaker at 100% is heard, in blocks: 32 by default ("speakerRange" in the common config). */
    public static DoubleSupplier speakerRange = () -> 32;
    /** Client: how the MEU's Speed effect stretches songs: "wsola", "phase_vocoder" or "tape" (SCOPE.md §5.5). */
    public static Supplier<String> speedAlgorithm = () -> "wsola";
    /** Server: whether newly placed hubs start with Redstone Control on (SCOPE.md §7, S8: off). */
    public static BooleanSupplier redstoneControlByDefault = () -> false;
    /**
     * Client: score playback strikes long notes again every beat, in every part, instead of holding them
     * (SCOPE.md §9.9). Off by default; also in the score editor's Preferences, which change it with
     * {@link #setRestrikeLongNotes}.
     */
    public static BooleanSupplier restrikeLongNotes = () -> false;
    public static Consumer<Boolean> setRestrikeLongNotes = on -> {};
    /** Client: the MEU's effects at High quality (Low is lighter for slower computers). */
    public static BooleanSupplier effectsHighQuality = () -> true;

    private MineScoreSettings() {}

    /** How far speakers can be heard at 100%, in blocks. */
    public static double hearingRange() {
        return speakerRange.getAsDouble();
    }

    /** Volume heard directly behind a speaker, relative to straight in front. */
    public static float behindVolume() {
        return com.minescore.config.MineScoreConfig.BEHIND_VOLUME.share();
    }

    /** Sub/mid and mid/tweeter crossover frequencies in Hz. */
    public static double lowSplit() {
        return com.minescore.config.MineScoreConfig.LOW_SPLIT.number();
    }

    public static double highSplit() {
        return Math.max(lowSplit() * 2, com.minescore.config.MineScoreConfig.HIGH_SPLIT.number());
    }

    /** Most speakers the client plays at once; the nearest win. Leaves room for Minecraft's own sounds. */
    public static int maxSpeakerVoices() {
        return com.minescore.config.MineScoreConfig.MAX_VOICES.integer();
    }

    /** Client: blocks between a speaker and the listener muffle it (SCOPE.md §8.2). */
    public static boolean soundThroughBlocks() {
        return com.minescore.config.MineScoreConfig.THROUGH_BLOCKS.bool();
    }

    /** Server: keep every chunk of a playing system loaded. */
    public static boolean keepPlayingSystemsLoaded() {
        return com.minescore.config.MineScoreConfig.KEEP_LOADED.bool();
    }
}
