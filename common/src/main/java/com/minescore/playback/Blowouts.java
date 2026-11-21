package com.minescore.playback;

import com.minescore.audio.EqCurve;
import com.minescore.wiring.Gauge;

/**
 * When a speaker blows (SCOPE.md §6.5): both how far it's turned up and how loud the music is count. Bass
 * gives out first, then mid, then tweeters.
 */
public final class Blowouts {
    /** Checks in a row (one a second) a speaker must be over its limit before it blows, so a single loud hit doesn't. */
    public static int strainChecks() {
        return com.minescore.config.MineScoreConfig.STRAIN_CHECKS.integer();
    }

    /** Ticks after a short before the system can be started again in None mode. */
    public static int recoveryTicks() {
        return (int) Math.round(com.minescore.config.MineScoreConfig.RECOVERY.number() * 20);
    }

    public static boolean enabled() {
        return com.minescore.config.MineScoreConfig.BLOWOUTS.bool();
    }

    private Blowouts() {}

    /** How hard a band may be driven, in dB over 100% volume at normal music level. */
    public static double limitDb(Gauge band) {
        return switch (band) {
            case G12 -> com.minescore.config.MineScoreConfig.SUB_LIMIT.number();
            case G16 -> com.minescore.config.MineScoreConfig.MID_LIMIT.number();
            case G20 -> com.minescore.config.MineScoreConfig.TWEETER_LIMIT.number();
        };
    }

    /**
     * How hard a speaker is driven, in dB: its volume, plus the most its EQ boosts its band, plus how far
     * the music in that band is above normal right now.
     */
    public static double driveDb(float gain, EqCurve eq, Gauge band, double musicExcessDb) {
        return 20 * Math.log10(Math.max(gain, 1e-6f)) + eq.boostDb(band) + musicExcessDb;
    }

    public static boolean overLimit(float gain, EqCurve eq, Gauge band, double musicExcessDb) {
        return driveDb(gain, eq, band, musicExcessDb) > limitDb(band);
    }
}
