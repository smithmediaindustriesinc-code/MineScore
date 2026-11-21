package com.minescore.audio;

import java.util.Random;

/**
 * How each {@link SynthSound} is made, in each sound style (SCOPE.md §13.1): the default, plus the Retro and
 * Studio sound packs. Retro is heavier and warmer, with mechanical clunks and a touch of mains hum; Studio
 * is clean, short and precise. Everything is mono 16-bit at {@link Track#SAMPLE_RATE}.
 */
public final class SynthRecipes {
    public enum Style {
        MINECRAFT, RETRO, STUDIO;

        public static Style of(String name) {
            for (Style style : values()) {
                if (style.name().equalsIgnoreCase(name)) {
                    return style;
                }
            }
            return MINECRAFT;
        }
    }

    private static final int RATE = Track.SAMPLE_RATE;

    private SynthRecipes() {}

    public static short[] render(SynthSound sound, Style style) {
        Random random = new Random(0x5EC0DL + sound.ordinal() * 7919L + style.ordinal());
        float[] out = switch (sound) {
            case RECORD_SHATTER -> shatter(style, random);
            case JACK_PLUG -> plug(style, random, true);
            case JACK_UNPLUG -> plug(style, random, false);
            case WIRE_CUT -> snip(style, random);
            case SPEAKER_SHORT -> shortOut(style, random);
            case NEEDLE_DROP -> needleDrop(style, random);
            case DOCK -> dock(style, random);
        };
        return toPcm(out);
    }

    // --- building blocks ---

    /** A decaying sine from {@code start}, {@code decay} seconds to fall to 1/e. */
    private static void ring(float[] out, double start, double freq, double level, double decay) {
        int from = (int) (start * RATE);
        for (int i = 0; i < decay * 6 * RATE && from + i < out.length; i++) {
            double t = (double) i / RATE;
            out[from + i] += (float) (level * Math.sin(2 * Math.PI * freq * t) * Math.exp(-t / decay));
        }
    }

    /** A click: a burst of high-passed noise, {@code length} seconds long. */
    private static void click(float[] out, Random random, double start, double level, double length, double brightness) {
        int from = (int) (start * RATE);
        double hp = 0, last = 0;
        for (int i = 0; i < length * RATE && from + i < out.length; i++) {
            double noise = random.nextDouble() * 2 - 1;
            hp = brightness * (hp + noise - last);
            last = noise;
            out[from + i] += (float) (level * hp * Math.exp(-i / (length * RATE / 4)));
        }
    }

    /** Mains hum (fundamental plus odd harmonics), faded in and out. */
    private static void hum(float[] out, double start, double length, double level, double freq) {
        int from = (int) (start * RATE);
        int n = (int) (length * RATE);
        for (int i = 0; i < n && from + i < out.length; i++) {
            double t = (double) i / RATE;
            double env = Math.sin(Math.PI * i / n);
            double wave = Math.sin(2 * Math.PI * freq * t) + 0.4 * Math.sin(2 * Math.PI * freq * 3 * t)
                    + 0.2 * Math.sin(2 * Math.PI * freq * 5 * t);
            out[from + i] += (float) (level * env * wave);
        }
    }

    /** Sparse surface crackle, thinning out over {@code length} seconds. */
    private static void crackle(float[] out, Random random, double start, double length, double perSecond, double level) {
        int count = (int) (perSecond * length);
        for (int k = 0; k < count; k++) {
            double when = start + Math.pow(random.nextDouble(), 1.4) * length;
            click(out, random, when, level * (0.3 + random.nextDouble() * 0.7) * (1 - (when - start) / length * 0.7), 0.0015, 0.9);
        }
    }

    private static short[] toPcm(float[] out) {
        float peak = 1e-6f;
        for (float v : out) {
            peak = Math.max(peak, Math.abs(v));
        }
        short[] pcm = new short[out.length];
        int fadeLength = Math.min(out.length, (int) (RATE * 0.02));
        for (int i = 0; i < out.length; i++) {
            double fade = i > out.length - fadeLength ? (out.length - i) / (double) fadeLength : 1;
            pcm[i] = (short) Math.round(out[i] / peak * 0.85 * fade * Short.MAX_VALUE);
        }
        return pcm;
    }

    // --- the sounds ---

    /**
     * A record snapping: a sharp crack with a low knock as it gives, then dozens of hard plastic shards
     * pinging and clattering down, thinning out over most of a second. Retro's thicker pressing rings lower;
     * Studio's is brighter and settles sooner.
     */
    static float[] shatter(Style style, Random random) {
        float[] out = new float[(int) (RATE * 1.1)];
        click(out, random, 0, 1, 0.06, 0.85);
        ring(out, 0, style == Style.RETRO ? 110 : 140, 0.5, 0.02);
        double low = style == Style.RETRO ? 900 : style == Style.STUDIO ? 1700 : 1300;
        double settle = style == Style.STUDIO ? 0.65 : 0.85;
        for (int n = 0; n < 70; n++) {
            double when = Math.pow(random.nextDouble(), 1.8) * settle; // bunched towards the start
            double freq = low + random.nextDouble() * 3600;
            double decay = 0.012 + random.nextDouble() * 0.05;
            double level = (0.18 + random.nextDouble() * 0.3) * (1 - when * 0.8);
            ring(out, when, freq, level, decay);
            ring(out, when, freq * 2.76, level * 0.5, decay * 0.8);
            ring(out, when, freq * 5.4, level * 0.3, decay * 0.6);
            click(out, random, when, level * 0.6, 0.004, 0.6); // a tick as it lands
        }
        return out;
    }

    /** A jack going in (a click, the body's knock, a detent) or coming out (a pop and a click). */
    static float[] plug(Style style, Random random, boolean in) {
        float[] out = new float[(int) (RATE * (style == Style.RETRO ? 0.3 : 0.16))];
        double body = style == Style.RETRO ? 620 : style == Style.STUDIO ? 1450 : 950;
        if (in) {
            click(out, random, 0, 1, 0.004, 0.9);
            ring(out, 0, body, 0.6, 0.012);
            ring(out, 0, style == Style.RETRO ? 90 : 120, style == Style.STUDIO ? 0.25 : 0.5, 0.03);
            click(out, random, 0.028, 0.45, 0.003, 0.9); // the detent
        } else {
            int sweep = (int) (RATE * 0.012);
            for (int i = 0; i < sweep; i++) { // a short rising pop as the plug lets go
                double t = (double) i / RATE;
                double freq = 400 + 900 * i / (double) sweep;
                out[i] += (float) (0.7 * Math.sin(2 * Math.PI * freq * t) * Math.sin(Math.PI * i / sweep));
            }
            click(out, random, 0.01, 0.7, 0.003, 0.9);
            ring(out, 0.01, body * 1.2, 0.35, 0.01);
        }
        if (style == Style.RETRO) {
            hum(out, in ? 0.02 : 0, 0.12, 0.12, 60); // the hum of an old amp as the lead goes in or out
        }
        return out;
    }

    /** Cutters snipping through a cable: a metallic snap with a short ring. */
    static float[] snip(Style style, Random random) {
        float[] out = new float[(int) (RATE * 0.2)];
        double pitch = style == Style.RETRO ? 0.75 : style == Style.STUDIO ? 1.25 : 1;
        click(out, random, 0, 1, 0.005, 0.95);
        ring(out, 0, 3200 * pitch, 0.4, style == Style.STUDIO ? 0.02 : 0.04);
        ring(out, 0, 5700 * pitch, 0.25, 0.025);
        click(out, random, 0.045, 0.35, 0.004, 0.9); // the blades meeting
        return out;
    }

    /** A speaker shorting: a hard pop and thump, then fizzing crackle dying away. */
    static float[] shortOut(Style style, Random random) {
        float[] out = new float[(int) (RATE * 0.9)];
        click(out, random, 0, 1, 0.03, 0.7);
        ring(out, 0, 55, 0.9, 0.06);
        crackle(out, random, 0.02, 0.7, style == Style.STUDIO ? 60 : 110, 0.5);
        if (style == Style.RETRO) {
            hum(out, 0.0, 0.5, 0.25, 50); // a mains buzz before it gives out
        } else if (style == Style.STUDIO) {
            // a hard digital glitch: a few square-wave stutters
            for (int k = 0; k < 4; k++) {
                int from = (int) (RATE * (0.03 + k * 0.05));
                for (int i = 0; i < RATE * 0.015 && from + i < out.length; i++) {
                    out[from + i] += (float) (0.25 * Math.signum(Math.sin(2 * Math.PI * 1800 * i / RATE)));
                }
            }
        }
        return out;
    }

    /** The needle settling into the groove: a soft thump, then surface crackle. */
    static float[] needleDrop(Style style, Random random) {
        float[] out = new float[(int) (RATE * 0.9)];
        ring(out, 0, 75, 0.8, 0.04);
        click(out, random, 0, 0.35, 0.006, 0.5);
        crackle(out, random, 0.05, 0.8, style == Style.RETRO ? 45 : style == Style.STUDIO ? 10 : 25, 0.3);
        return out;
    }

    /** An MP3 Player clicking into the Mixer's dock: a latch, then the seat. */
    static float[] dock(Style style, Random random) {
        float[] out = new float[(int) (RATE * 0.15)];
        double body = style == Style.RETRO ? 900 : style == Style.STUDIO ? 2100 : 1700;
        click(out, random, 0, 0.8, 0.004, 0.9);
        ring(out, 0, body, 0.5, 0.01);
        click(out, random, 0.03, style == Style.STUDIO ? 0.4 : 0.7, 0.004, 0.9);
        ring(out, 0.03, style == Style.STUDIO ? 160 : 110, style == Style.RETRO ? 0.6 : 0.35, 0.025);
        return out;
    }
}
