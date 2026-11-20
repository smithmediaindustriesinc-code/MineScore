package com.minescore.audio.fx;

import com.minescore.meu.CardSettings;
import com.minescore.meu.Effect;

/**
 * Reverb, after the classic "Freeverb" design: eight damped comb filters and four all-passes per side, the
 * right side slightly detuned for width. Room, Plate and Hall differ in their size and brightness; Spring
 * adds the boingy, band-limited character of a spring tank.
 */
final class ReverbFx extends Fx {
    private static final int[] COMBS = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
    private static final int[] ALLPASSES = {556, 441, 341, 225};
    private static final int SPREAD = 23;
    /** Combs are built long enough for the largest size, and read shorter for smaller rooms. */
    private static final double MAX_SCALE = 1.8;

    private final Effect type;
    private final Comb[][] combs = new Comb[2][COMBS.length];
    private final Dsp.Allpass[][] allpasses = new Dsp.Allpass[2][ALLPASSES.length];
    private final Dsp.Allpass[][] springs = new Dsp.Allpass[2][3];
    private final Dsp.Biquad[] springBand = {new Dsp.Biquad(), new Dsp.Biquad()};
    private final Dsp.DelayLine predelay = new Dsp.DelayLine((int) (Dsp.SR * 0.25));
    private double predelaySamples;
    private float feedback, damp;

    ReverbFx(Effect type) {
        this.type = type;
        double typeScale = switch (type) {
            case ROOM -> 0.6;
            case SPRING -> 0.75;
            case PLATE -> 0.85;
            default -> 1.0;
        };
        for (int ch = 0; ch < 2; ch++) {
            for (int i = 0; i < COMBS.length; i++) {
                combs[ch][i] = new Comb((int) ((COMBS[i] + ch * SPREAD) * MAX_SCALE), typeScale);
            }
            for (int i = 0; i < ALLPASSES.length; i++) {
                allpasses[ch][i] = new Dsp.Allpass(ALLPASSES[i] + ch * SPREAD);
            }
            for (int i = 0; i < 3; i++) {
                springs[ch][i] = new Dsp.Allpass(97 + i * 61 + ch * 13);
            }
            springBand[ch].bandPass(1500, 0.6);
        }
    }

    @Override
    public void set(CardSettings card, Context context) {
        double size = card.value("size");
        for (Comb[] side : combs) {
            for (Comb comb : side) {
                comb.scale(0.5 + size * 0.75);
            }
        }
        feedback = (float) (0.7 + 0.28 * card.value("decay"));
        damp = card.value("damping") * (type == Effect.PLATE ? 0.25f : 0.45f);
        predelaySamples = card.value("predelay") / 1000 * Dsp.SR;
    }

    @Override
    public void process(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            predelay.push((left[i] + right[i]) * 0.5f);
            float in = (predelaySamples < 1 ? (left[i] + right[i]) * 0.5f : predelay.read(predelaySamples)) * 0.03f;
            for (int ch = 0; ch < 2; ch++) {
                float x = in;
                if (type == Effect.SPRING) {
                    x = springBand[ch].process(x) * 2;
                    for (Dsp.Allpass a : springs[ch]) {
                        x = a.process(x, 0.6f);
                    }
                }
                float sum = 0;
                for (Comb comb : combs[ch]) {
                    sum += comb.process(x, feedback, damp);
                }
                for (Dsp.Allpass a : allpasses[ch]) {
                    sum = a.process(sum, 0.5f);
                }
                if (ch == 0) {
                    left[i] = sum;
                } else {
                    right[i] = sum;
                }
            }
        }
    }

    @Override
    public void reset() {
        for (int ch = 0; ch < 2; ch++) {
            for (Comb c : combs[ch]) {
                c.reset();
            }
            for (Dsp.Allpass a : allpasses[ch]) {
                a.reset();
            }
            for (Dsp.Allpass a : springs[ch]) {
                a.reset();
            }
            springBand[ch].reset();
        }
        predelay.reset();
    }

    /** A feedback comb whose length can be shortened without reallocating. */
    private static final class Comb {
        private final float[] buffer;
        private final double typeScale;
        private int length;
        private int index;
        private float store;

        Comb(int capacity, double typeScale) {
            buffer = new float[capacity];
            this.typeScale = typeScale;
            length = capacity;
        }

        void scale(double sizeScale) {
            length = Math.max(16, Math.min(buffer.length, (int) (buffer.length / MAX_SCALE * sizeScale * typeScale)));
            index %= length;
        }

        float process(float x, float feedback, float damp) {
            float y = buffer[index];
            store = y * (1 - damp) + store * damp;
            buffer[index] = x + store * feedback;
            index = (index + 1) % length;
            return y;
        }

        void reset() {
            java.util.Arrays.fill(buffer, 0);
            store = 0;
        }
    }
}
