package com.minescore.audio.fx;

import com.minescore.audio.Track;

/**
 * WSOLA time-stretching: the song is cut into overlapping 40 ms grains taken {@code speed} times as far apart
 * as they're laid down, and each grain is nudged (within 10 ms) to where it lines up best with the end of
 * the one before, so waveforms join without the phasing a plain overlap-add gives. Pitch stays put.
 */
final class WsolaStretch implements TimeStretch {
    private static final int N = 1764;
    private static final int HOP = N / 2;
    private static final int TOLERANCE = 441;
    private static final float[] WINDOW = new float[N];

    static {
        for (int i = 0; i < N; i++) {
            WINDOW[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
        }
    }

    private final Track track;
    private final double speed;
    /** Stride when comparing grains: coarser is faster. */
    private final int stride;
    private final float[] accLeft = new float[N], accRight = new float[N];
    private final float[] outLeft = new float[HOP], outRight = new float[HOP];
    private int outPos = HOP;
    private double nominal;
    private long previous;
    private boolean first = true;

    WsolaStretch(Track track, double speed, boolean high) {
        this.track = track;
        this.speed = speed;
        this.stride = high ? 3 : 6;
    }

    @Override
    public void seek(double sourceFrame) {
        nominal = sourceFrame;
        first = true;
        java.util.Arrays.fill(accLeft, 0);
        java.util.Arrays.fill(accRight, 0);
        outPos = HOP;
    }

    @Override
    public void read(float[] left, float[] right, int n) {
        for (int i = 0; i < n; i++) {
            if (outPos == HOP) {
                step();
            }
            left[i] = outLeft[outPos];
            right[i] = outRight[outPos];
            outPos++;
        }
    }

    private float mono(long frame) {
        return (Dsp.sample(track, 0, frame) + Dsp.sample(track, 1, frame)) * 0.5f;
    }

    /** How well a candidate grain lines up with the natural continuation of the last one (normalised). */
    private double similarity(long natural, long candidate, int stride) {
        double cross = 0, energy = 1e-9;
        for (int i = 0; i < N; i += stride) {
            float b = mono(candidate + i);
            cross += mono(natural + i) * b;
            energy += b * b;
        }
        return cross / Math.sqrt(energy);
    }

    private void step() {
        long center = Math.round(nominal);
        long best = center;
        if (!first) {
            long natural = previous + HOP;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int d = -TOLERANCE; d <= TOLERANCE; d += 4) { // coarse search...
                double score = similarity(natural, center + d, stride);
                if (score > bestScore) {
                    bestScore = score;
                    best = center + d;
                }
            }
            long coarse = best;
            for (int d = -3; d <= 3; d++) { // ...then the exact sample
                double score = similarity(natural, coarse + d, stride);
                if (score > bestScore) {
                    bestScore = score;
                    best = coarse + d;
                }
            }
        }
        first = false;
        for (int i = 0; i < N; i++) {
            accLeft[i] += WINDOW[i] * Dsp.sample(track, 0, best + i);
            accRight[i] += WINDOW[i] * Dsp.sample(track, 1, best + i);
        }
        System.arraycopy(accLeft, 0, outLeft, 0, HOP);
        System.arraycopy(accRight, 0, outRight, 0, HOP);
        System.arraycopy(accLeft, HOP, accLeft, 0, N - HOP);
        System.arraycopy(accRight, HOP, accRight, 0, N - HOP);
        java.util.Arrays.fill(accLeft, N - HOP, N, 0);
        java.util.Arrays.fill(accRight, N - HOP, N, 0);
        outPos = 0;
        previous = best;
        nominal += HOP * speed;
    }
}
