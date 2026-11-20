package com.minescore.meu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every effect a Multi-Effects Unit card can run (SCOPE.md §3.10), the card it belongs to, and its knobs.
 * Knob values are stored as real values (dB, ms, Hz, %), not 0–1.
 */
public enum Effect {
    OVERDRIVE(CardType.DRIVE, Knobs.DRIVE),
    FUZZ(CardType.DRIVE, Knobs.DRIVE),
    HIGH_GAIN(CardType.DRIVE, Knobs.DRIVE),

    CHORUS(CardType.MODULATION, new Knob[] {Knob.of("rate", 0.05f, 5, 0.8f, "Hz"), Knob.of("depth", 0, 1, 0.5f, "%"), Knobs.MIX}),
    FLANGER(CardType.MODULATION, new Knob[] {Knob.of("rate", 0.05f, 5, 0.25f, "Hz"), Knob.of("depth", 0, 1, 0.7f, "%"),
        Knob.of("feedback", -0.9f, 0.9f, 0.5f, "%"), Knobs.MIX}),
    PHASER(CardType.MODULATION, new Knob[] {Knob.of("rate", 0.05f, 5, 0.5f, "Hz"), Knob.of("depth", 0, 1, 0.7f, "%"),
        Knob.of("feedback", 0, 0.9f, 0.4f, "%"), Knobs.MIX}),
    ROTARY(CardType.MODULATION, new Knob[] {Knob.of("rate", 0.5f, 7, 0.8f, "Hz"), Knob.of("depth", 0, 1, 0.6f, "%"), Knobs.MIX}),
    PITCH(CardType.MODULATION, new Knob[] {Knob.stepped("semitones", -12, 12, 0, "st"), Knob.of("fine", -50, 50, 0, "ct"), Knobs.MIX}),
    /** Changes the song's speed for the whole system (25–200%); see the Speed algorithm setting. */
    SPEED(CardType.MODULATION, new Knob[] {Knob.of("speed", 25, 200, 100, "%x")}),

    STRAIGHT(CardType.DELAY, new Knob[] {Knobs.TIME, Knobs.SYNC, Knobs.FEEDBACK, Knobs.MIX}),
    STEREO(CardType.DELAY, new Knob[] {Knobs.TIME, Knobs.SYNC, Knob.of("spread", 0, 1, 0.5f, "%"), Knobs.FEEDBACK, Knobs.MIX}),
    PING_PONG(CardType.DELAY, new Knob[] {Knobs.TIME, Knobs.SYNC, Knobs.FEEDBACK, Knobs.MIX}),

    ROOM(CardType.REVERB, Knobs.reverb(0.3f, 0.45f)),
    SPRING(CardType.REVERB, Knobs.reverb(0.4f, 0.6f)),
    PLATE(CardType.REVERB, Knobs.reverb(0.6f, 0.7f)),
    HALL(CardType.REVERB, Knobs.reverb(0.9f, 0.8f)),

    CLEAN_TUBE(CardType.AMP_CAB, Knobs.amp(0.2f)),
    BRITISH_CRUNCH(CardType.AMP_CAB, Knobs.amp(0.5f)),
    BOUTIQUE_LEAD(CardType.AMP_CAB, Knobs.amp(0.65f)),
    MODERN_HIGH_GAIN(CardType.AMP_CAB, Knobs.amp(0.85f)),

    WAH(CardType.DYNAMICS, new Knob[] {Knob.stepped("mode", 0, 1, 0, "wah"), Knob.of("sensitivity", 0, 1, 0.5f, "%"),
        Knob.of("resonance", 0, 1, 0.6f, "%"), Knobs.MIX}),
    GATE(CardType.DYNAMICS, new Knob[] {Knob.of("threshold", -80, -10, -80, "dB"), Knob.of("release", 10, 1000, 150, "ms")}),
    COMPRESSOR(CardType.DYNAMICS, new Knob[] {Knob.of("threshold", -40, 0, -18, "dB"), Knob.of("ratio", 1, 20, 1, ":1"),
        Knob.of("attack", 0.1f, 100, 10, "ms"), Knob.of("release", 10, 1000, 120, "ms"), Knob.of("makeup", 0, 24, 0, "dB")}),
    /** Shows the song's key and tuning, and retunes it: a key shift, and (when switched on) a new reference pitch for A. */
    TUNER(CardType.DYNAMICS, new Knob[] {Knob.stepped("shift", -12, 12, 0, "st"), Knob.stepped("retune", 0, 1, 0, "onoff"),
        Knob.of("reference", 415, 466, 440, "Hz")}),
    /** The expression pedal (the MEU's slider) sweeps one knob of another card between two points (-1 = no card). */
    EXPRESSION(CardType.DYNAMICS, new Knob[] {Knob.stepped("target_card", -1, 4, -1, "card"), Knob.stepped("target_knob", 1, 6, 1, "knob"),
        Knob.of("from", 0, 1, 0, "%"), Knob.of("to", 0, 1, 1, "%")});

    private final CardType card;
    private final List<Knob> knobs;

    Effect(CardType card, Knob[] knobs) {
        this.card = card;
        this.knobs = List.of(knobs);
    }

    public CardType card() {
        return card;
    }

    public List<Knob> knobs() {
        return knobs;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Which knob is the dry/wet mix, or -1 for effects that are all wet. */
    public int mixKnob() {
        for (int i = 0; i < knobs.size(); i++) {
            if (knobs.get(i).id().equals("mix")) {
                return i;
            }
        }
        return -1;
    }

    public int knob(String id) {
        for (int i = 0; i < knobs.size(); i++) {
            if (knobs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public float[] defaults() {
        float[] values = new float[knobs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = knobs.get(i).defaultValue();
        }
        return values;
    }

    /** The effects one card can choose from, in order. */
    public static List<Effect> of(CardType card) {
        List<Effect> result = new ArrayList<>();
        for (Effect effect : values()) {
            if (effect.card == card) {
                result.add(effect);
            }
        }
        return result;
    }

    public static Effect byId(String id, CardType fallbackCard) {
        try {
            Effect effect = valueOf(id.toUpperCase(Locale.ROOT));
            return effect.card == fallbackCard ? effect : fallbackCard.defaultEffect();
        } catch (IllegalArgumentException e) {
            return fallbackCard.defaultEffect();
        }
    }

    /**
     * One knob.
     *
     * @param unit how to show it: "dB", "ms", "Hz", "%" (0–1 shown as percent), "%x" (already a percent),
     *             or a word naming stepped choices
     */
    public record Knob(String id, float min, float max, float defaultValue, String unit, boolean stepped) {
        static Knob of(String id, float min, float max, float defaultValue, String unit) {
            return new Knob(id, min, max, defaultValue, unit, false);
        }

        static Knob stepped(String id, float min, float max, float defaultValue, String unit) {
            return new Knob(id, min, max, defaultValue, unit, true);
        }

        public float clamp(float value) {
            float v = Math.max(min, Math.min(max, Float.isFinite(value) ? value : defaultValue));
            return stepped ? Math.round(v) : v;
        }

        /** 0–1 along the knob's travel. */
        public float fraction(float value) {
            return max == min ? 0 : (value - min) / (max - min);
        }

        public float at(float fraction) {
            return clamp(min + (max - min) * fraction);
        }
    }

    /** Knob sets several effects share. */
    private static final class Knobs {
        /**
         * Every mix knob starts fully dry, and the knobs of effects without one start where they do nothing,
         * so a fresh card never changes the sound until it's turned up.
         */
        static final Knob MIX = Knob.of("mix", 0, 1, 0, "%");
        static final Knob[] DRIVE = {Knob.of("drive", 0, 1, 0.5f, "%"), Knob.of("tone", 0, 1, 0.5f, "%"),
            Knob.of("level", -12, 12, 0, "dB"), MIX};
        static final Knob TIME = Knob.of("time", 10, 2000, 350, "ms");
        /** 0 = off (use the time), 1 = quarter notes, 2 = eighths, 3 = dotted eighths, 4 = sixteenths. */
        static final Knob SYNC = Knob.stepped("sync", 0, 4, 0, "sync");
        static final Knob FEEDBACK = Knob.of("feedback", 0, 0.95f, 0.35f, "%");

        static Knob[] reverb(float size, float decay) {
            return new Knob[] {Knob.of("size", 0, 1, size, "%"), Knob.of("decay", 0.1f, 1, decay, "%"), Knob.of("damping", 0, 1, 0.5f, "%"),
                Knob.of("predelay", 0, 200, 20, "ms"), MIX};
        }

        /** 0 = no cabinet, 1 = 1×12 open back, 2 = 2×12, 3 = 4×12 closed back. */
        static Knob[] amp(float gain) {
            return new Knob[] {Knob.of("gain", 0, 1, gain, "%"), Knob.of("bass", -12, 12, 0, "dB"), Knob.of("mid", -12, 12, 0, "dB"),
                Knob.of("treble", -12, 12, 0, "dB"), Knob.stepped("cabinet", 0, 3, 2, "cab"), Knob.of("level", -12, 12, 0, "dB"), MIX};
        }
    }
}
