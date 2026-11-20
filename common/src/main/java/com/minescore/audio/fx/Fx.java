package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * One effect running on a stereo signal. It produces only the wet sound; {@link EffectProcessor} blends it
 * with the dry sound by the card's mix knob.
 */
public abstract class Fx {
    /**
     * What effects need to know about the song and the unit.
     *
     * @param bpm        the song's tempo, for synced delays
     * @param tuningHz   the song's detected reference pitch for A, for the Tuner
     * @param expression the expression pedal, 0–1
     * @param high       the High quality setting
     */
    public record Context(double bpm, double tuningHz, float expression, boolean high) {
        public static final Context DEFAULT = new Context(120, 440, 0, true);
    }

    /** Processes {@code n} frames in place. */
    public abstract void process(float[] left, float[] right, int n);

    /** New knob settings (called whenever they change, without losing the effect's state). */
    public abstract void set(CardSettings card, Context context);

    public abstract void reset();

    /** A fresh effect for a card, or null for ones that aren't audio (Speed and Expression are handled elsewhere). */
    public static Fx create(Effect effect, Context context) {
        return switch (effect) {
            case OVERDRIVE, FUZZ, HIGH_GAIN -> new DriveFx(effect);
            case CHORUS, FLANGER -> new ModDelayFx(effect);
            case PHASER -> new PhaserFx();
            case ROTARY -> new RotaryFx();
            case PITCH, TUNER -> new PitchFx(effect);
            case STRAIGHT, STEREO, PING_PONG -> new DelayFx(effect);
            case ROOM, SPRING, PLATE, HALL -> new ReverbFx(effect);
            case CLEAN_TUBE, BRITISH_CRUNCH, BOUTIQUE_LEAD, MODERN_HIGH_GAIN -> new AmpCabFx(effect, context.high());
            case WAH -> new WahFx();
            case GATE, COMPRESSOR -> new DynamicsFx(effect);
            case SPEED, EXPRESSION -> null;
        };
    }
}
