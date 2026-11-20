package com.minescore.audio.notes;

import org.jetbrains.annotations.Nullable;

/**
 * Holds a note block sound for as long as a note lasts, or cuts it off when the note is short (SCOPE.md §8.1).
 *
 * <p>A note block sample dies away, so a long note can't simply play it slower. Instead a short stretch of
 * the sample just after its attack, a whole number of the note's own cycles long, is looped: its fading level
 * is evened out so every pass is as loud as the first, and its end is crossfaded into its start so the join
 * can't be heard. The note then holds its tone like an organ until it ends, and fades out quickly. A note
 * shorter than its sample is faded out at its end the same way.
 */
public final class NoteSustain {
    /** How long a held note takes to fade out once it ends, in seconds. */
    public static final double RELEASE = 0.06;
    /** The loop starts this long after the sample's loudest point (past the attack), in seconds. */
    private static final double AFTER_PEAK = 0.04;
    /** About how long the looped stretch is, and its crossfade, in seconds. */
    private static final double LOOP = 0.12, CROSSFADE = 0.03;
    /** How far the loop's level may be evened out. */
    private static final float MIN_GAIN = 0.2f, MAX_GAIN = 5f;

    private NoteSustain() {}

    /**
     * The loop worked out for one sample.
     *
     * @param start where it starts in the sample
     * @param gain  for each sample from {@code start} over the first pass, the gain that evens the level out
     * @param loop  the stretch played over and over after the first pass (its start crossfaded from past its end)
     */
    public record Loop(int start, float[] gain, float[] loop) {
        public int length() {
            return loop.length;
        }
    }

    /**
     * Finds where to loop a sample that plays MIDI note {@code centerMidi} as recorded, or null if it's too short
     * to loop (it's then only ever cut short).
     */
    public static @Nullable Loop find(float[] sample, int sampleRate, int centerMidi) {
        int block = Math.max(1, sampleRate / 100);
        int blocks = sample.length / block;
        if (blocks < 4) {
            return null;
        }
        float[] env = new float[blocks];
        int peak = 0;
        for (int k = 0; k < blocks; k++) {
            double sum = 0;
            for (int i = k * block; i < (k + 1) * block; i++) {
                sum += sample[i] * sample[i];
            }
            env[k] = (float) Math.sqrt(sum / block);
            if (env[k] > env[peak]) {
                peak = k;
            }
        }
        if (env[peak] <= 1e-5f) {
            return null;
        }
        double period = sampleRate / (440 * Math.pow(2, (centerMidi - 69) / 12.0));
        int cycles = (int) Math.max(1, Math.round(LOOP * sampleRate / period));
        int length = (int) Math.round(cycles * period);
        int fade = (int) Math.min(CROSSFADE * sampleRate, length / 2.0);
        int start = peak * block + block / 2 + (int) (AFTER_PEAK * sampleRate);
        int search = (int) Math.ceil(period / 2);
        if (start + length + search + fade >= sample.length) {
            start = peak * block + block; // a short sample: loop closer to the attack
            if (start + length + search + fade >= sample.length) {
                return null;
            }
        }
        length = bestLength(sample, start, length, search, Math.min(1024, Math.max(64, fade)));
        float level = envAt(env, block, start);
        if (level < env[peak] * 0.02f) {
            return null; // it has all but died away by then
        }
        float[] gain = new float[length + fade];
        for (int i = 0; i < gain.length; i++) {
            float here = envAt(env, block, start + i);
            gain[i] = here <= 1e-6f ? MAX_GAIN : Math.max(MIN_GAIN, Math.min(MAX_GAIN, level / here));
        }
        float[] loop = new float[length];
        for (int i = 0; i < length; i++) {
            float flat = sample[start + i] * gain[i];
            if (i < fade) {
                // fades in from what follows the loop's end, so going round again joins up smoothly
                float t = i / (float) fade;
                loop[i] = flat * t + sample[start + length + i] * gain[length + i] * (1 - t);
            } else {
                loop[i] = flat;
            }
        }
        return new Loop(start, gain, loop);
    }

    /** The loop length near {@code guess} (within {@code search}) whose end lines up best with its start. */
    private static int bestLength(float[] sample, int start, int guess, int search, int window) {
        int best = guess;
        double bestScore = -Double.MAX_VALUE;
        for (int l = Math.max(8, guess - search); l <= guess + search; l++) {
            if (start + l + window >= sample.length) {
                break;
            }
            double dot = 0, a2 = 0, b2 = 0;
            for (int i = 0; i < window; i++) {
                float a = sample[start + i], b = sample[start + l + i];
                dot += a * b;
                a2 += a * a;
                b2 += b * b;
            }
            double score = dot / Math.sqrt(a2 * b2 + 1e-12);
            if (score > bestScore) {
                bestScore = score;
                best = l;
            }
        }
        return best;
    }

    /** The level at a point, between the 10 ms blocks it was measured in. */
    private static float envAt(float[] env, int block, int pos) {
        double k = (pos - block / 2.0) / block;
        int i = (int) Math.floor(k);
        if (i < 0) {
            return env[0];
        }
        if (i >= env.length - 1) {
            return env[env.length - 1];
        }
        double t = k - i;
        return (float) (env[i] * (1 - t) + env[i + 1] * t);
    }

    /**
     * One note: {@code sample} played at {@code rate}, held for {@code holdFrames} output frames (from the loop,
     * once the sample's own sound runs past it) and then faded out over {@code releaseFrames}. With no loop it's
     * only cut short, never longer than the sample.
     */
    public static float[] render(float[] sample, @Nullable Loop loop, double rate, int holdFrames, int releaseFrames) {
        int natural = (int) (sample.length / rate);
        int total = holdFrames + releaseFrames;
        if (loop == null) {
            total = Math.min(total, natural);
        }
        float[] out = new float[Math.max(0, total)];
        for (int i = 0; i < out.length; i++) {
            double src = i * rate;
            float v = loop == null || src < loop.start() ? raw(sample, src) : looped(sample, loop, src - loop.start());
            if (i >= holdFrames) {
                v *= (float) (0.5 * (1 + Math.cos(Math.PI * (i - holdFrames) / Math.max(1, releaseFrames))));
            }
            out[i] = v;
        }
        return out;
    }

    private static float raw(float[] sample, double src) {
        int a = (int) src;
        if (a >= sample.length) {
            return 0;
        }
        double t = src - a;
        return (float) (sample[a] * (1 - t) + (a + 1 < sample.length ? sample[a + 1] : 0) * t);
    }

    /** {@code offset} samples past the loop's start: the evened-out first pass, then the loop round and round. */
    private static float looped(float[] sample, Loop loop, double offset) {
        int length = loop.length();
        if (offset < length) {
            int a = (int) offset;
            double t = offset - a;
            int s = loop.start() + a;
            float v0 = sample[s] * loop.gain()[a];
            float v1 = a + 1 < loop.gain().length && s + 1 < sample.length ? sample[s + 1] * loop.gain()[a + 1] : v0;
            return (float) (v0 * (1 - t) + v1 * t);
        }
        double q = (offset - length) % length;
        int a = (int) q;
        double t = q - a;
        float[] l = loop.loop();
        return (float) (l[a] * (1 - t) + l[(a + 1) % length] * t);
    }
}
